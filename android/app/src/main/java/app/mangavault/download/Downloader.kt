package app.mangavault.download

import android.content.Context
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.net.Http
import app.mangavault.net.HttpStatusException
import app.mangavault.source.Chapter
import app.mangavault.source.Series
import app.mangavault.source.SourceException
import app.mangavault.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One downloaded chapter waiting on disk to be written into the archive. */
private class FetchedChapter(val index: Int, val pages: List<File>, val expected: Int, val error: String?)

object Downloader {
    private const val PAGE_PARALLEL = 6
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
        val n = ch.number ?: return "Ch ${(idx + 1).toString().padStart(width, '0')}"
        val frac = if (n.contains('.')) "." + n.substringAfter('.') else ""
        return "Ch ${n.substringBefore('.').padStart(3, '0')}$frac"
    }

    private fun newZip(out: OutputStream) = ZipOutputStream(out.buffered(128 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }

    private fun describe(e: Exception): String = when (e) {
        is SourceException -> e.code.name.lowercase().replace('_', ' ') + if (e.detail.isNotBlank()) " (${e.detail.take(60)})" else ""
        is HttpStatusException -> "site answered ${e.code}"
        is IOException -> e.message?.take(80) ?: "network error"
        else -> e.javaClass.simpleName
    }

    private suspend fun <T> retry(times: Int, block: suspend () -> T): T {
        var last: Exception? = null
        for (i in 1..times) {
            try { return block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                last = e
                // Back off harder when the site says we are too fast.
                delay(if (e is HttpStatusException && e.code == 429) 1500L * i else 350L * i)
            }
        }
        throw last ?: IOException("failed")
    }

    private suspend fun fetchPage(url: String, referer: String, headers: Map<String, String>, dir: File, n: Int): File {
        val r = Http.bytes(url, headers + mapOf("Referer" to referer, "Accept" to "image/avif,image/webp,image/*,*/*;q=0.8"))
        val looksImage = r.contentType.startsWith("image/") || extRe.containsMatchIn(url)
        if (!looksImage || r.contentType.startsWith("text/") || r.bytes.size < 64) throw IOException("not an image")
        val ext = extMap[r.contentType] ?: extRe.find(url)?.groupValues?.get(1)?.lowercase()?.replace("jpeg", "jpg") ?: "jpg"
        return File(dir, "%04d.%s".format(n, ext)).apply { writeBytes(r.bytes) }
    }

    /** Lists and downloads one chapter's pages into [dir]; never throws except on cancellation. */
    private suspend fun fetchChapter(series: Series, idx: Int, ch: Chapter, dir: File, referer: String, headers: Map<String, String>): FetchedChapter = coroutineScope {
        DownloadState.chapter(idx) { it.copy(state = CState.ACTIVE) }
        val urls = try {
            retry(3) { Sources.pages(series, ch) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val msg = describe(e)
            DownloadState.chapter(idx) { it.copy(state = CState.FAILED, error = msg) }
            return@coroutineScope FetchedChapter(idx, emptyList(), 0, msg)
        }
        dir.mkdirs()
        DownloadState.chapter(idx) { it.copy(total = urls.size) }
        val sem = Semaphore(PAGE_PARALLEL)
        val files = urls.mapIndexed { i, u ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val f = try { retry(3) { fetchPage(u, referer, headers, dir, i + 1) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    DownloadState.chapter(idx) { it.copy(done = it.done + 1, failedPages = it.failedPages + if (f == null) 1 else 0) }
                    f
                }
            }
        }.awaitAll()
        val good = files.filterNotNull()
        if (good.isEmpty()) {
            DownloadState.chapter(idx) { it.copy(state = CState.FAILED, error = "no page images could be downloaded") }
            FetchedChapter(idx, emptyList(), urls.size, "no pages")
        } else {
            FetchedChapter(idx, good, urls.size, null)
        }
    }

    private fun writePages(zip: ZipOutputStream, pages: List<File>, name: (Int, File) -> String) {
        pages.forEachIndexed { i, f ->
            zip.putNextEntry(ZipEntry(name(i, f)))
            f.inputStream().use { it.copyTo(zip, 64 * 1024) }
            zip.closeEntry()
        }
    }

    /**
     * Downloads [JobRequest.chapters]: up to [parallel] chapters at a time, pages saved to temp files, then
     * written to the archive in reading order as soon as each one (and all before it) is ready.
     */
    suspend fun run(ctx: Context, req: JobRequest, parallel: Int = Prefs.parallel.value) = coroutineScope {
        val series = req.series
        val selected = req.chapters
        val base = safeName(series.title)
        val referer = Sources.referer(series)
        val headers = series.session?.let { mapOf("Cookie" to it.cookie, "User-Agent" to it.userAgent) } ?: emptyMap()
        val width = maxOf(3, selected.size.toString().length)
        val tmp = File(ctx.cacheDir, "dl-${System.nanoTime()}")
        DownloadState.lastRequest = req
        DownloadState.start(series.title, selected, req.mode)

        var singleTarget: OutputTarget? = null
        var singleZip: ZipOutputStream? = null
        var last: SavedInfo? = null
        var ok = 0
        var files = 0
        val window = parallel.coerceIn(1, 6)
        val pending = arrayOfNulls<Deferred<FetchedChapter>>(selected.size)
        fun launch(i: Int) {
            pending[i] = async(Dispatchers.IO) { fetchChapter(series, i, selected[i], File(tmp, i.toString()), referer, headers) }
        }
        try {
            if (req.mode == Mode.SINGLE) {
                singleTarget = Storage.open(ctx, base, "$base.cbz")
                singleZip = newZip(singleTarget.stream)
            }
            for (i in 0 until minOf(window, selected.size)) launch(i)
            for (i in selected.indices) {
                val fetched = pending[i]!!.await()
                pending[i] = null
                if (i + window < selected.size) launch(i + window)
                val ch = selected[i]
                if (fetched.error == null) {
                    val lbl = label(ch, i, width)
                    val pw = maxOf(3, fetched.expected.toString().length)
                    try {
                        if (req.mode == Mode.SINGLE) {
                            writePages(singleZip!!, fetched.pages) { _, f -> "${lbl.replace(' ', '_')}_p${f.name.substringBefore('.').trimStart('0').padStart(pw, '0')}.${f.extension}" }
                        } else {
                            val target = Storage.open(ctx, base, "$base - $lbl.cbz")
                            try {
                                newZip(target.stream).use { z ->
                                    writePages(z, fetched.pages) { _, f -> "${f.name.substringBefore('.').trimStart('0').padStart(pw, '0')}.${f.extension}" }
                                    z.putNextEntry(ZipEntry("ComicInfo.xml"))
                                    z.write(comicInfo(series.title, ch, fetched.pages.size).toByteArray())
                                    z.closeEntry()
                                }
                                last = target.finish()
                                files++
                            } catch (e: Exception) {
                                target.abort()
                                throw e
                            }
                        }
                        ok++
                        val partial = fetched.pages.size < fetched.expected
                        DownloadState.chapter(i) { it.copy(state = if (partial) CState.PARTIAL else CState.DONE) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DownloadState.chapter(i) { it.copy(state = CState.FAILED, error = "could not save: ${describe(e)}") }
                    }
                }
                File(tmp, i.toString()).deleteRecursively()
                DownloadState.update { it.copy(doneChapters = i + 1) }
            }
            if (ok == 0) throw IOException("None of the selected chapters could be downloaded")
            if (req.mode == Mode.SINGLE) {
                val z = singleZip!!
                z.putNextEntry(ZipEntry("ComicInfo.xml"))
                z.write(comicInfo(series.title, null, 0).toByteArray())
                z.closeEntry()
                z.close() // flushes and closes the file before it is published
                last = singleTarget!!.finish()
                singleTarget = null
                files = 1
            }
            val saved = last
            val folderPath = saved?.path?.substringBeforeLast('/') ?: ""
            val shown = if (req.mode == Mode.SINGLE) saved?.path ?: folderPath else folderPath
            DownloadState.update { it.copy(status = JobStatus.DONE, savedPath = shown) }
            Prefs.addHistory(HistoryItem(series.title, shown, saved?.uri, ok, System.currentTimeMillis(), files))
        } catch (e: CancellationException) {
            pending.forEach { it?.cancel() }
            singleTarget?.abort()
            DownloadState.update { it.copy(status = JobStatus.CANCELLED) }
            throw e
        } catch (e: Exception) {
            pending.forEach { it?.cancel() }
            singleTarget?.abort()
            DownloadState.update { it.copy(status = JobStatus.ERROR, error = e.message) }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
