package app.mangavault.download

import android.content.Context
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
private class Fetched(val pages: List<PageFile?>, val error: Exception?)

/**
 * Downloads chapters as a pipeline:
 *  - several chapters are fetched at the same time (their page lists are read, then every page is requested in parallel),
 *  - all page requests share one limit, so the connection is always full but the site is not flooded,
 *  - pages land in a temp folder and are packed into CBZ files strictly in chapter order.
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

    private fun newZip(out: java.io.OutputStream) = ZipOutputStream(out.buffered(256 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }

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
        h["Accept"] = "image/avif,image/webp,image/*,*/*;q=0.8"
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
        }
        // Nothing usable from the plain page read: open the chapter in the real browser engine and take what it shows.
        if (pages.none { it != null } && !series.forceBrowser && !Sources.isMangaDex(series)) {
            try {
                val again = Sources.pages(series, ch, browser = true)
                DownloadState.chapter(idx) { it.copy(done = 0, total = again.size, failedPages = 0) }
                DownloadState.update { it.copy(pagesTotal = it.pagesTotal + again.size) }
                val r = downloadAll(idx, again, dir, referers, headers, pageGate, "b")
                pages = r.first; pageError = r.second ?: pageError
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

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this, 128 * 1024) }
        closeEntry()
    }

    suspend fun run(ctx: Context, req: JobRequest) {
        val series = req.series
        val selected = req.chapters
        val base = safeName(series.title)
        val referer = Sources.referer(series)
        val headers = series.session?.let { mapOf("Cookie" to it.cookie, "User-Agent" to it.userAgent) } ?: emptyMap()
        val width = maxOf(3, selected.size.toString().length)
        val (pageParallel, chaptersAhead) = Prefs.concurrency()
        val pageGate = Semaphore(pageParallel)
        val chapterGate = Semaphore(chaptersAhead)
        val work = File(ctx.cacheDir, "dl-${System.currentTimeMillis()}")
        if (DownloadState.job.value?.status != JobStatus.RUNNING || DownloadState.job.value?.chapters?.size != selected.size) {
            DownloadState.start(series.title, selected, req.mode)
        }

        var singleTarget: OutputTarget? = null
        var singleZip: ZipOutputStream? = null
        var last: SavedInfo? = null
        var ok = 0
        var files = 0
        try {
            if (req.mode == Mode.SINGLE) {
                singleTarget = withContext(Dispatchers.IO) { Storage.open(ctx, base, "$base.cbz") }
                singleZip = newZip(singleTarget.stream)
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
                        val good = fetched.pages.count { it != null }
                        if (good == 0) throw IOException("no pages could be downloaded")
                        val lbl = label(ch, idx, width)
                        val pw = maxOf(3, fetched.pages.size.toString().length)
                        withContext(Dispatchers.IO) {
                            val zip = if (req.mode == Mode.SINGLE) singleZip!! else {
                                target = Storage.open(ctx, base, "$base - $lbl.cbz")
                                newZip(target!!.stream)
                            }
                            fetched.pages.forEachIndexed { n, pf ->
                                if (pf == null) return@forEachIndexed
                                val no = (n + 1).toString().padStart(pw, '0')
                                val name = if (req.mode == Mode.SINGLE) "${lbl.replace(' ', '_')}_p$no.${pf.ext}" else "$no.${pf.ext}"
                                zip.putFile(name, pf.file)
                            }
                            if (req.mode == Mode.PER_CHAPTER) {
                                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                                zip.write(comicInfo(series.title, ch, good).toByteArray())
                                zip.closeEntry()
                                zip.close() // flushes and closes the file before it is published
                                last = target!!.finish()
                                files++
                            }
                        }
                        ok++
                        val partial = good < fetched.pages.size
                        DownloadState.chapter(idx) { it.copy(state = if (partial) CState.PARTIAL else CState.DONE) }
                    } catch (e: CancellationException) {
                        target?.abort()
                        throw e
                    } catch (e: Exception) {
                        target?.abort()
                        // In single-file mode a broken archive stream is fatal; a chapter that simply had no pages is not.
                        if (req.mode == Mode.SINGLE && fetched.error == null && fetched.pages.any { it != null }) throw e
                        DownloadState.chapter(idx) { it.copy(state = CState.FAILED, error = reason(e)) }
                    } finally {
                        withContext(Dispatchers.IO) { File(work, idx.toString()).deleteRecursively() }
                    }
                    DownloadState.update { it.copy(doneChapters = it.doneChapters + 1) }
                }
            }
            if (ok == 0) throw IOException("None of the selected chapters could be downloaded")
            if (req.mode == Mode.SINGLE) {
                withContext(Dispatchers.IO) {
                    singleZip!!.putNextEntry(ZipEntry("ComicInfo.xml"))
                    singleZip!!.write(comicInfo(series.title, null, 0).toByteArray())
                    singleZip!!.closeEntry()
                    singleZip!!.close()
                    last = singleTarget!!.finish()
                }
                files = 1
                singleTarget = null
            }
            val saved = last
            val folderPath = saved?.path?.substringBeforeLast('/') ?: ""
            DownloadState.finish(JobStatus.DONE) { it.copy(savedPath = if (req.mode == Mode.SINGLE) saved?.path else folderPath) }
            Prefs.addHistory(HistoryItem(series.title, if (req.mode == Mode.SINGLE) saved?.path ?: folderPath else folderPath, saved?.uri, ok, System.currentTimeMillis(), files))
        } catch (e: CancellationException) {
            singleTarget?.abort()
            DownloadState.finish(JobStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            singleTarget?.abort()
            DownloadState.finish(JobStatus.ERROR) { it.copy(error = e.message) }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }
}
