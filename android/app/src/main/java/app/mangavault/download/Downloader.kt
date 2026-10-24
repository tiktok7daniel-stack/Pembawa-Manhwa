package app.mangavault.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.net.Http
import app.mangavault.net.HttpStatusException
import app.mangavault.source.SourceException
import app.mangavault.source.Chapter
import app.mangavault.source.Series
import app.mangavault.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class Page(val bytes: ByteArray, val ext: String)
private class PageFile(val file: File, val ext: String)

/** A chapter whose pages are on disk, waiting to be packed. [pages] keeps reading order; null = page that failed. */
/** A chapter-level problem: that chapter is skipped, the rest of the job goes on. */
private class ChapterFailed(msg: String) : IOException(msg)

private class Fetched(val pages: List<PageFile?>, val error: Exception?)

/**
 * Downloads chapters as a pipeline:
 *  - several chapters are fetched at the same time (their page lists are read, then every page is requested in parallel),
 *  - all page requests share one limit, so the connection is always full but the site is not flooded,
 *  - pages land in a temp folder and are packed (CBZ, ZIP or PDF) strictly in chapter order.
 */
object Downloader {
    private val extMap = mapOf("image/jpeg" to "jpg", "image/jpg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/avif" to "avif", "image/gif" to "gif")
    private val extRe = Regex("\\.(jpe?g|png|webp|avif|gif)(\\?|#|$)", RegexOption.IGNORE_CASE)

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), " ").replace(Regex("\\s+"), " ").trim().take(100).ifEmpty { "manga" }

    private fun esc(s: String) = s.replace(Regex("[<>&\"']")) { "&#${it.value[0].code};" }

    private fun comicInfo(series: String, ch: Chapter?, pages: Int) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ComicInfo>\n  <Series>${esc(series)}</Series>\n" +
            (if (ch != null) "  <Number>${esc(ch.number ?: "")}</Number>\n  <Title>${esc(ch.title)}</Title>\n" else "") +
            (if (pages > 0) "  <PageCount>$pages</PageCount>\n" else "") + "</ComicInfo>\n"

    private fun label(ch: Chapter, idx: Int, width: Int): String {
        val n = ch.number
        if (n == null) return "Ch ${(idx + 1).toString().padStart(width, '0')}"
        val frac = if (n.contains('.')) "." + n.substringAfter('.') else ""
        return "Ch ${n.substringBefore('.').padStart(3, '0')}$frac"
    }

    /** Retries transient failures; a 429/503 waits as long as the site asks (or backs off harder). */
    private suspend fun <T> retry(times: Int, block: suspend () -> T): T {
        var last: Exception? = null
        for (i in 1..times) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                last = e
                if (e.code in 400..499 && e.code != 408 && e.code != 429) throw e // will not change by asking again
                if (i < times) delay(if (e.code == 429 || e.code == 503) maxOf(e.retryAfterMs, 1500L * i) else 500L * i)
            } catch (e: Exception) {
                last = e
                if (i < times) delay(400L * i)
            }
        }
        throw last ?: IOException("failed")
    }

    /** Cookies the in-app browser holds for this exact URL (e.g. a Cloudflare clearance on the image host). */
    private fun browserCookie(url: String): String? = try {
        android.webkit.CookieManager.getInstance().getCookie(url)?.ifBlank { null }
    } catch (_: Throwable) { null }

    private suspend fun fetchOnce(url: String, referer: String?, headers: Map<String, String>): Page {
        val h = HashMap(headers)
        h["Accept"] = "image/jpeg,image/png,image/webp;q=0.9,image/*;q=0.8,*/*;q=0.5"
        if (referer != null) h["Referer"] = referer else h.remove("Referer")
        val jar = browserCookie(url)
        if (jar != null) h["Cookie"] = listOfNotNull(h["Cookie"]?.ifBlank { null }, jar).joinToString("; ")
        val r = Http.bytes(url, h)
        val looksImage = r.contentType.startsWith("image/") || extRe.containsMatchIn(url) || r.contentType == "application/octet-stream"
        if (!looksImage || r.contentType.startsWith("text/") || r.bytes.size < 64) throw IOException("not an image")
        val ext = extMap[r.contentType] ?: extRe.find(url)?.groupValues?.get(1)?.lowercase()?.replace("jpeg", "jpg") ?: "jpg"
        return Page(r.bytes, ext)
    }

    /**
     * Image hosts disagree on what they accept: the site root as Referer, the chapter page itself, or no Referer at all.
     * Try them in that order when the host refuses the request.
     */
    private suspend fun fetchPage(url: String, referers: List<String?>, headers: Map<String, String>): Page {
        var last: Exception? = null
        for (ref in referers) {
            try {
                return retry(3) { fetchOnce(url, ref, headers) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                last = e
                if (e.code !in setOf(400, 401, 403, 404, 410)) throw e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("failed")
    }

    private fun reason(e: Throwable): String = when (e) {
        is HttpStatusException -> "HTTP ${e.code}"
        is SourceException -> e.code.name.lowercase().replace('_', ' ') + if (e.detail.isNotBlank() && e.detail.length < 12) " ${e.detail}" else ""
        else -> (e.message ?: e.javaClass.simpleName).take(60)
    }

    private suspend fun downloadAll(
        idx: Int, urls: List<String>, dir: File, referers: List<String?>, headers: Map<String, String>, pageGate: Semaphore, tag: String,
    ): Pair<List<PageFile?>, Exception?> {
        var lastError: Exception? = null
        val pages = coroutineScope {
            urls.mapIndexed { n, u ->
                async {
                    pageGate.withPermit {
                        try {
                            val p = fetchPage(u, referers, headers)
                            val f = File(dir, "$tag$n.${p.ext}")
                            withContext(Dispatchers.IO) { f.writeBytes(p.bytes) }
                            DownloadState.page(idx, p.bytes.size.toLong(), false)
                            PageFile(f, p.ext)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError = e
                            DownloadState.page(idx, 0, true)
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        return pages to lastError
    }

    /**
     * Second chance for pages that failed in the parallel burst: one at a time, with pauses, so a throttling host
     * lets them through. A chapter is only reported incomplete after this.
     */
    private suspend fun retryMissing(
        idx: Int, urls: List<String>, pages: List<PageFile?>, dir: File, referers: List<String?>, headers: Map<String, String>,
    ): List<PageFile?> {
        val out = pages.toMutableList()
        for (round in 1..2) {
            for (n in out.indices) {
                if (out[n] != null) continue
                delay(700L * round)
                try {
                    val p = fetchPage(urls[n], referers, headers)
                    val f = File(dir, "r$n.${p.ext}")
                    withContext(Dispatchers.IO) { f.writeBytes(p.bytes) }
                    out[n] = PageFile(f, p.ext)
                    DownloadState.recover(idx, p.bytes.size.toLong())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { /* next round */ }
            }
            if (out.none { it == null }) break
        }
        return out
    }

    /** Reads the chapter's page list, then pulls every page at once (bounded by [pageGate]) into [dir]. */
    private suspend fun fetchChapter(
        idx: Int, ch: Chapter, series: Series, dir: File, referer: String, headers: Map<String, String>, pageGate: Semaphore,
    ): Fetched = try {
        DownloadState.chapter(idx) { it.copy(state = CState.ACTIVE) }
        val referers = listOf(referer.ifBlank { null }, ch.url, null).distinct()
        withContext(Dispatchers.IO) { dir.mkdirs() }
        var urls: List<String>? = null
        var listError: Exception? = null
        try { urls = retry(3) { Sources.pages(series, ch) } } catch (e: CancellationException) { throw e } catch (e: Exception) { listError = e }
        var pages: List<PageFile?> = emptyList()
        var pageError: Exception? = null
        val list = urls
        if (list != null) {
            DownloadState.chapter(idx) { it.copy(total = list.size) }
            DownloadState.update { it.copy(pagesTotal = it.pagesTotal + list.size) }
            val r = downloadAll(idx, list, dir, referers, headers, pageGate, "a")
            pages = r.first; pageError = r.second
            if (pages.any { it != null } && pages.any { it == null }) pages = retryMissing(idx, list, pages, dir, referers, headers)
        }
        // Nothing usable from the plain page read: open the chapter in the real browser engine and take what it shows.
        if (pages.none { it != null } && !series.forceBrowser && !Sources.isMangaDex(series)) {
            try {
                val again = Sources.pages(series, ch, browser = true)
                DownloadState.chapter(idx) { it.copy(done = 0, total = again.size, failedPages = 0) }
                DownloadState.update { it.copy(pagesTotal = it.pagesTotal + again.size) }
                val r = downloadAll(idx, again, dir, referers, headers, pageGate, "b")
                pages = r.first; pageError = r.second ?: pageError
                if (pages.any { it != null } && pages.any { it == null }) pages = retryMissing(idx, again, pages, dir, referers, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (listError == null && pageError == null) listError = e
            }
        }
        val err = if (pages.any { it != null }) null else (pageError ?: listError ?: IOException("no pages could be downloaded"))
        Fetched(pages, err)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Fetched(emptyList(), e)
    }

    /** A page made ready for one output format; [size] is exactly what adding it will cost in the file (plus a small header). */
    private class Prepared(val size: Long, val data: Any)

    /** Writes pages into one output file in a given format. */
    private abstract class Packer {
        /** A new chapter starts (bookmark / folder). */
        abstract fun chapter(title: String)
        /** Converts a page for this format without writing it; null when the page cannot be used. */
        abstract fun prepare(name: String, pf: PageFile): Prepared?
        abstract fun add(p: Prepared)
        /** Returns false when the page could not be used. */
        fun page(name: String, pf: PageFile): Boolean {
            val p = prepare(name, pf) ?: return false
            add(p)
            return true
        }
        /** Adds trailing metadata and closes the underlying stream. */
        abstract fun close(info: String?)
    }

    private class ZipPacker(out: java.io.OutputStream) : Packer() {
        private val zip = ZipOutputStream(out.buffered(256 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }
        override fun chapter(title: String) = Unit
        // Local header + data descriptor + central directory entry, each carrying the name.
        override fun prepare(name: String, pf: PageFile) = Prepared(pf.file.length() + 3L * name.length + 140, name to pf)
        override fun add(p: Prepared) {
            @Suppress("UNCHECKED_CAST") val (name, pf) = p.data as Pair<String, PageFile>
            zip.putNextEntry(ZipEntry(name))
            pf.file.inputStream().use { it.copyTo(zip, 128 * 1024) }
            zip.closeEntry()
        }
        override fun close(info: String?) {
            if (info != null) {
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                zip.write(info.toByteArray())
                zip.closeEntry()
            }
            zip.close() // flushes and closes the file before it is published
        }
    }

    private class PdfPacker(out: java.io.OutputStream) : Packer() {
        private val pdf = PdfWriter(out)
        override fun chapter(title: String) = pdf.bookmark(title)
        override fun prepare(name: String, pf: PageFile): Prepared? {
            val bytes = pf.file.readBytes()
            val info = Jpeg.info(bytes)
            val slices = if (info != null) listOf(bytes to info) else toJpeg(bytes) ?: return null
            // Image streams + their dictionaries, the page and content objects, and their xref lines.
            return Prepared(slices.sumOf { it.first.size.toLong() + 260 } + 420, slices)
        }
        override fun add(p: Prepared) {
            @Suppress("UNCHECKED_CAST") pdf.addStack(p.data as List<Pair<ByteArray, JpegInfo>>)
        }
        override fun close(info: String?) = pdf.close()

        /**
         * Any page that is not a plain JPEG (PNG, WebP, GIF, AVIF, CMYK/12-bit JPEG…) is re-encoded as high-quality JPEG on
         * white. Long strips are cut into slices so even a 30000-pixel page never needs one huge bitmap.
         */
        private fun toJpeg(bytes: ByteArray): List<Pair<ByteArray, JpegInfo>>? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w0 = bounds.outWidth
            val h0 = bounds.outHeight
            for (extra in intArrayOf(1, 2, 4)) {
                var sample = extra
                if (w0 > 0) while (w0 / sample > 1800) sample *= 2
                val out = try {
                    if (w0 > 0 && h0 > 0) sliced(bytes, w0, h0, sample) ?: whole(bytes, sample) else whole(bytes, sample)
                } catch (_: Throwable) { null }
                if (!out.isNullOrEmpty()) return out
            }
            return null
        }

        private fun encode(bmp: Bitmap): Pair<ByteArray, JpegInfo> {
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
            val out = java.io.ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, 92, out)
            val info = JpegInfo(flat.width, flat.height, 3)
            flat.recycle()
            return out.toByteArray() to info
        }

        /** Decodes the page band by band (needs BitmapRegionDecoder support: JPEG, PNG, WebP, HEIF). */
        @Suppress("DEPRECATION")
        private fun sliced(bytes: ByteArray, w: Int, h: Int, sample: Int): List<Pair<ByteArray, JpegInfo>>? {
            val dec = try { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) } catch (_: Throwable) { null } ?: return null
            try {
                val band = 2048 * sample
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val out = ArrayList<Pair<ByteArray, JpegInfo>>()
                var y = 0
                while (y < h) {
                    val bmp = dec.decodeRegion(Rect(0, y, w, minOf(h, y + band)), opts) ?: return null
                    out.add(encode(bmp))
                    bmp.recycle()
                    y += band
                }
                return out
            } finally {
                dec.recycle()
            }
        }

        private fun whole(bytes: ByteArray, sample: Int): List<Pair<ByteArray, JpegInfo>>? {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            return try { listOf(encode(bmp)) } finally { bmp.recycle() }
        }
    }

    private fun packer(format: Format, out: java.io.OutputStream): Packer = if (format == Format.PDF) PdfPacker(out) else ZipPacker(out)

    private fun mime(format: Format) = when (format) {
        Format.PDF -> "application/pdf"
        Format.ZIP -> "application/zip"
        Format.CBZ -> "application/vnd.comicbook+zip"
    }

    /**
     * "Split by size": chapters go into the current part until the next one would push it past [limit]; then the part is
     * closed and saved as "<series> - Ch 100-112.<ext>", and a new part starts. A chapter bigger than the limit on its own
     * is split across parts at a page boundary. Parts are written to a temp file first because the name (the chapter range)
     * is only known when the part is complete.
     */
    private class Splitter(
        val ctx: Context, val folder: String, val base: String, val format: Format, val limit: Long, val series: String,
    ) {
        private var file: File? = null
        private var pack: Packer? = null
        private var bytes = 0L
        private var pagesInPart = 0
        private var firstNo: String? = null
        private var lastNo: String? = null
        private val pieces = HashMap<String, Int>()
        val saved = ArrayList<SavedInfo>()

        private fun open() {
            val f = File(ctx.cacheDir, "part-${System.nanoTime()}.${format.ext}")
            file = f
            pack = packer(format, f.outputStream())
            bytes = 0
            pagesInPart = 0
            firstNo = null
        }

        private fun close() {
            val p = pack ?: return
            if (pagesInPart == 0) { abort(); return } // never publish an empty part
            val f = file!!
            p.close(if (format == Format.CBZ) comicInfo(series, null, 0) else null)
            val a = firstNo ?: "?"
            val b = lastNo ?: a
            var range = if (a == b) "Ch $a" else "Ch $a-$b"
            // The same chapter split over several parts: number the pieces.
            val n = (pieces[range] ?: 0) + 1
            pieces[range] = n
            if (n > 1) range += " ($n)"
            val t = Storage.open(ctx, folder, "$base - $range.${format.ext}", mime(format))
            try {
                f.inputStream().use { it.copyTo(t.stream, 256 * 1024) }
                t.stream.close()
                saved.add(t.finish())
            } catch (e: Exception) {
                t.abort()
                throw e
            } finally {
                f.delete()
                pack = null
                file = null
            }
        }

        /** Room kept free in every part for what is written at the end (page tree, bookmarks, xref, zip directory). */
        private val reserve = 96L * 1024 + limit / 200

        /** Adds one chapter's pages; returns how many went in. No part ever grows past [limit]. */
        fun chapter(no: String, title: String, pages: List<Pair<String, PageFile>>): Int {
            if (pack == null) open()
            // Measure the whole chapter first: if it fits in a fresh part but not in this one, start the fresh part now
            // so a chapter is only cut when it is bigger than a part on its own.
            val prepared = pages.mapNotNull { (name, pf) -> pack!!.prepare(name, pf) }
            val size = prepared.sumOf { it.size } + 200 + 4L * title.length
            if (pagesInPart > 0 && bytes + size > limit - reserve) { close(); open() }
            pack!!.chapter(title)
            bytes += 200 + 4L * title.length
            if (firstNo == null) firstNo = no
            lastNo = no
            for (p in prepared) {
                if (pagesInPart > 0 && bytes + p.size > limit - reserve) {
                    // This chapter alone is larger than a part: continue it in the next one.
                    close()
                    open()
                    pack!!.chapter(title)
                    bytes += 200 + 4L * title.length
                    firstNo = no
                    lastNo = no
                }
                pack!!.add(p)
                bytes += p.size
                pagesInPart++
            }
            return prepared.size
        }

        fun finish() { close() }

        fun abort() {
            try { pack?.close(null) } catch (_: Exception) { }
            file?.delete()
            pack = null
        }
    }

    suspend fun run(ctx: Context, req: JobRequest) {
        val series = req.series
        val selected = req.chapters
        val format = req.format
        val ext = format.ext
        val base = safeName(series.title)
        val referer = Sources.referer(series)
        val headers = series.session?.let { mapOf("Cookie" to it.cookie, "User-Agent" to it.userAgent) } ?: emptyMap()
        val width = maxOf(3, selected.size.toString().length)
        val (pageParallel, chaptersAhead) = Prefs.concurrency()
        val pageGate = Semaphore(pageParallel)
        val chapterGate = Semaphore(chaptersAhead)
        val work = File(ctx.cacheDir, "dl-${System.currentTimeMillis()}")
        if (DownloadState.job.value?.status != JobStatus.RUNNING || DownloadState.job.value?.chapters?.size != selected.size) {
            DownloadState.start(series.title, selected, req.mode, format)
        }

        var singleTarget: OutputTarget? = null
        var single: Packer? = null
        val numberOf = { ch: Chapter, idx: Int -> ch.number ?: (idx + 1).toString() }
        val splitter = if (req.mode == Mode.SPLIT) {
            val a = numberOf(selected.first(), 0)
            val b = numberOf(selected.last(), selected.size - 1)
            Splitter(ctx, "$base - " + (if (a == b) "Ch $a" else "Ch $a-$b"), base, format, maxOf(1L shl 20, req.splitBytes), series.title)
        } else null
        var last: SavedInfo? = null
        var ok = 0
        var files = 0
        try {
            if (req.mode == Mode.SINGLE) {
                val t = withContext(Dispatchers.IO) { Storage.open(ctx, base, "$base.$ext", mime(format)) }
                singleTarget = t
                single = packer(format, t.stream)
            }
            coroutineScope {
                // Every chapter gets a fetcher; the fair semaphore lets them run [chaptersAhead] at a time, in order.
                val fetches: List<Deferred<Fetched>> = selected.mapIndexed { idx, ch ->
                    async { chapterGate.withPermit { fetchChapter(idx, ch, series, File(work, idx.toString()), referer, headers, pageGate) } }
                }
                for ((idx, ch) in selected.withIndex()) {
                    val fetched = fetches[idx].await()
                    currentCoroutineContext().ensureActive()
                    var target: OutputTarget? = null
                    try {
                        fetched.error?.let { throw it }
                        if (fetched.pages.none { it != null }) throw ChapterFailed("no pages could be downloaded")
                        val lbl = label(ch, idx, width)
                        val pw = maxOf(3, fetched.pages.size.toString().length)
                        var good = 0
                        if (splitter != null) withContext(Dispatchers.IO) {
                            val items = fetched.pages.mapIndexedNotNull { n, pf ->
                                if (pf == null) null else {
                                    val no = (n + 1).toString().padStart(pw, '0')
                                    (if (format == Format.ZIP) "$lbl/$no.${pf.ext}" else "${lbl.replace(' ', '_')}_p$no.${pf.ext}") to pf
                                }
                            }
                            good = splitter.chapter(numberOf(ch, idx), chapterTitle(ch, lbl), items)
                            if (good == 0) throw ChapterFailed("pages could not be converted")
                        } else withContext(Dispatchers.IO) {
                            val pack = single ?: run {
                                val t = Storage.open(ctx, base, "$base - $lbl.$ext", mime(format))
                                target = t
                                packer(format, t.stream)
                            }
                            pack.chapter(chapterTitle(ch, lbl))
                            fetched.pages.forEachIndexed { n, pf ->
                                if (pf == null) return@forEachIndexed
                                val no = (n + 1).toString().padStart(pw, '0')
                                val name = when {
                                    req.mode == Mode.PER_CHAPTER -> "$no.${pf.ext}"
                                    format == Format.ZIP -> "$lbl/$no.${pf.ext}"
                                    else -> "${lbl.replace(' ', '_')}_p$no.${pf.ext}"
                                }
                                if (pack.page(name, pf)) good++
                            }
                            if (good == 0) throw ChapterFailed("pages could not be converted")
                            if (req.mode == Mode.PER_CHAPTER) {
                                pack.close(if (format == Format.CBZ) comicInfo(series.title, ch, good) else null)
                                last = target!!.finish()
                                files++
                            }
                        }
                        ok++
                        val missing = fetched.pages.size - good
                        DownloadState.chapter(idx) {
                            it.copy(state = if (missing > 0) CState.PARTIAL else CState.DONE, failedPages = missing, error = if (missing > 0) "$missing / ${fetched.pages.size} pages missing" else null)
                        }
                    } catch (e: CancellationException) {
                        target?.abort()
                        throw e
                    } catch (e: Exception) {
                        target?.abort()
                        // In single-file mode a broken output stream is fatal; a chapter that simply had no usable pages is not.
                        if (req.mode != Mode.PER_CHAPTER && e !is ChapterFailed && e !== fetched.error) throw e
                        DownloadState.chapter(idx) { it.copy(state = CState.FAILED, error = reason(e)) }
                    } finally {
                        withContext(Dispatchers.IO) { File(work, idx.toString()).deleteRecursively() }
                    }
                    DownloadState.update { it.copy(doneChapters = it.doneChapters + 1) }
                }
            }
            if (ok == 0) throw IOException("None of the selected chapters could be downloaded")
            if (splitter != null) {
                withContext(Dispatchers.IO) { splitter.finish() }
                files = splitter.saved.size
                last = splitter.saved.lastOrNull()
            }
            val pack = single
            if (pack != null) {
                withContext(Dispatchers.IO) {
                    pack.close(if (format == Format.CBZ) comicInfo(series.title, null, 0) else null)
                    last = singleTarget!!.finish()
                }
                files = 1
                singleTarget = null
            }
            val saved = last
            val folderPath = saved?.path?.substringBeforeLast('/') ?: ""
            val uris = splitter?.saved?.mapNotNull { it.uri } ?: listOfNotNull(saved?.uri)
            DownloadState.finish(JobStatus.DONE) { it.copy(savedPath = if (req.mode == Mode.SINGLE) saved?.path else folderPath, files = uris) }
            Prefs.addHistory(HistoryItem(series.title, if (req.mode == Mode.SINGLE) saved?.path ?: folderPath else folderPath, saved?.uri, ok, System.currentTimeMillis(), files))
        } catch (e: CancellationException) {
            singleTarget?.abort()
            splitter?.abort()
            DownloadState.finish(JobStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            singleTarget?.abort()
            splitter?.abort()
            DownloadState.finish(JobStatus.ERROR) { it.copy(error = e.message) }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    private fun chapterTitle(ch: Chapter, lbl: String): String {
        val n = ch.number ?: return ch.title.ifBlank { lbl }
        return if (ch.title.isBlank() || ch.title.contains(n)) ch.title.ifBlank { "Chapter $n" } else "Chapter $n · ${ch.title}"
    }
}
