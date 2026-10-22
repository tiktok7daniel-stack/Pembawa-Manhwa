package app.mangavault.download

import android.content.Context
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.net.Http
import app.mangavault.source.Chapter
import app.mangavault.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class Page(val bytes: ByteArray, val ext: String)

object Downloader {
    private const val CHUNK = 6
    private val extMap = mapOf("image/jpeg" to "jpg", "image/jpg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/avif" to "avif", "image/gif" to "gif")

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

    private fun newZip(out: java.io.OutputStream) = ZipOutputStream(out.buffered(64 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }

    private suspend fun <T> retry(times: Int, block: suspend () -> T): T {
        var last: Exception? = null
        for (i in 1..times) {
            try { return block() } catch (e: CancellationException) { throw e } catch (e: Exception) { last = e; delay(400L * i) }
        }
        throw last ?: IOException("failed")
    }

    private suspend fun fetchPage(url: String, referer: String, headers: Map<String, String>): Page {
        val r = Http.bytes(url, headers + mapOf("Referer" to referer, "Accept" to "image/*,*/*;q=0.8"))
        val looksImage = r.contentType.startsWith("image/") || Regex("\\.(jpe?g|png|webp|avif|gif)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
        if (!looksImage || r.contentType.startsWith("text/") || r.bytes.size < 64) throw IOException("Not an image")
        val ext = extMap[r.contentType]
            ?: Regex("\\.(jpe?g|png|webp|avif|gif)(\\?|#|$)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase()?.replace("jpeg", "jpg")
            ?: "jpg"
        return Page(r.bytes, ext)
    }

    private suspend fun fetchChunk(urls: List<String>, referer: String, headers: Map<String, String>): List<Page?> = coroutineScope {
        urls.map { u -> async { try { retry(3) { fetchPage(u, referer, headers) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null } } }.awaitAll()
    }

    suspend fun run(ctx: Context, req: JobRequest) {
        val series = req.series
        val selected = req.chapters
        val base = safeName(series.title)
        val referer = Sources.referer(series)
        val headers = series.session?.let { mapOf("Cookie" to it.cookie, "User-Agent" to it.userAgent) } ?: emptyMap()
        val width = maxOf(3, selected.size.toString().length)
        DownloadState.start(series.title, selected, req.mode)

        var singleTarget: OutputTarget? = null
        var singleZip: ZipOutputStream? = null
        var last: SavedInfo? = null
        var ok = 0
        var files = 0
        try {
            if (req.mode == Mode.SINGLE) {
                singleTarget = Storage.open(ctx, base, "$base.cbz")
                singleZip = newZip(singleTarget.stream)
            }
            for ((idx, ch) in selected.withIndex()) {
                currentCoroutineContext().ensureActive()
                DownloadState.chapter(idx) { it.copy(state = CState.ACTIVE) }
                var target: OutputTarget? = null
                try {
                    val urls = retry(3) { Sources.pages(series, ch) }
                    DownloadState.chapter(idx) { it.copy(total = urls.size) }
                    val lbl = label(ch, idx, width)
                    val zip: ZipOutputStream
                    if (req.mode == Mode.SINGLE) {
                        zip = singleZip!!
                    } else {
                        target = Storage.open(ctx, base, "$base - $lbl.cbz")
                        zip = newZip(target.stream)
                    }
                    val pw = maxOf(3, urls.size.toString().length)
                    var pageNo = 0
                    var good = 0
                    for (chunk in urls.chunked(CHUNK)) {
                        currentCoroutineContext().ensureActive()
                        val pages = fetchChunk(chunk, referer, headers)
                        for (p in pages) {
                            pageNo++
                            if (p != null) {
                                val name = if (req.mode == Mode.SINGLE) "${lbl.replace(' ', '_')}_p${pageNo.toString().padStart(pw, '0')}.${p.ext}"
                                else "${pageNo.toString().padStart(pw, '0')}.${p.ext}"
                                zip.putNextEntry(ZipEntry(name))
                                zip.write(p.bytes)
                                zip.closeEntry()
                                good++
                            }
                            val failed = if (p == null) 1 else 0
                            DownloadState.chapter(idx) { it.copy(done = it.done + 1, failedPages = it.failedPages + failed) }
                        }
                    }
                    if (good == 0) throw IOException("no pages could be downloaded")
                    if (req.mode == Mode.PER_CHAPTER) {
                        zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                        zip.write(comicInfo(series.title, ch, good).toByteArray())
                        zip.closeEntry()
                        zip.close() // flushes and closes the file before it is published
                        last = target!!.finish()
                        files++
                    }
                    ok++
                    val partial = good < urls.size
                    DownloadState.chapter(idx) { it.copy(state = if (partial) CState.PARTIAL else CState.DONE) }
                } catch (e: CancellationException) {
                    target?.abort()
                    throw e
                } catch (e: Exception) {
                    target?.abort()
                    DownloadState.chapter(idx) { it.copy(state = CState.FAILED) }
                }
                DownloadState.update { it.copy(doneChapters = idx + 1) }
            }
            if (ok == 0) throw IOException("None of the selected chapters could be downloaded")
            if (req.mode == Mode.SINGLE) {
                singleZip!!.putNextEntry(ZipEntry("ComicInfo.xml"))
                singleZip.write(comicInfo(series.title, null, 0).toByteArray())
                singleZip.closeEntry()
                singleZip.close()
                last = singleTarget!!.finish()
                files = 1
                singleTarget = null
            }
            val saved = last
            val folderPath = saved?.path?.substringBeforeLast('/') ?: ""
            DownloadState.update { it.copy(status = JobStatus.DONE, savedPath = if (req.mode == Mode.SINGLE) saved?.path else folderPath) }
            Prefs.addHistory(HistoryItem(series.title, if (req.mode == Mode.SINGLE) saved?.path ?: folderPath else folderPath, saved?.uri, ok, System.currentTimeMillis(), files))
        } catch (e: CancellationException) {
            singleTarget?.abort()
            DownloadState.update { it.copy(status = JobStatus.CANCELLED) }
            throw e
        } catch (e: Exception) {
            singleTarget?.abort()
            DownloadState.update { it.copy(status = JobStatus.ERROR, error = e.message) }
        }
    }
}
