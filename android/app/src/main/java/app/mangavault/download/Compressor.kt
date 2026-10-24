package app.mangavault.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Compression presets. [width] is the pixel width pages are rendered/scaled to (phone screens are 720–1440 px wide,
 * so anything wider is invisible on a phone), [quality] the encoder quality.
 */
enum class Level(val width: Int, val quality: Int) {
    HIGH(1440, 85),
    BALANCED(1080, 75),
    SMALL(800, 62),
    TARGET(1080, 80),
}

enum class FileKind { PDF, ZIP }

/**
 * [webp]: encode pages as WebP instead of JPEG (about a third smaller at the same look). A PDF cannot hold WebP, so a
 * PDF compressed with WebP is saved as a CBZ, which every comic reader opens.
 */
class CompressRequest(
    val uri: Uri, val name: String, val size: Long, val kind: FileKind, val level: Level, val targetBytes: Long, val webp: Boolean = false,
)

/** Expected output sizes for the presets, measured on sample pages before compressing. */
class Estimate(val high: Long, val balanced: Long, val small: Long) {
    /** 0 excellent · 1 very good · 2 good · 3 low · 4 very low, for a target size. */
    fun grade(target: Long): Int = when {
        target >= high -> 0
        target >= balanced -> 1
        target >= small -> 2
        target * 2.2 >= small -> 3
        else -> 4
    }
}

data class CompressJob(
    val name: String,
    val status: JobStatus,
    val inBytes: Long,
    val outBytes: Long = 0,
    /** Pages (PDF) or images (CBZ/ZIP) done / known. */
    val done: Int = 0,
    val total: Int = 0,
    /** 0..1 */
    val fraction: Float = 0f,
    val error: String? = null,
    /** True when the result would not be smaller than the original (nothing is saved then). */
    val noGain: Boolean = false,
    val outUri: String? = null,
    val outPath: String? = null,
    val mime: String = "application/pdf",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    /** Final size if the rest of the file shrinks like the part done so far. */
    val projected: Long get() = if (fraction > 0.01f) (outBytes / fraction).toLong() else 0
}

object CompressState {
    val job = MutableStateFlow<CompressJob?>(null)
    @Volatile var pending: CompressRequest? = null
    fun update(f: (CompressJob) -> CompressJob) { job.update { if (it == null) null else f(it) } }
}

/** Shrinks a PDF / CBZ / ZIP of comic pages by re-rendering every page at phone resolution with an efficient codec. */
object Compressor {
    private val imageExt = Regex("\\.(jpe?g|png|webp|gif|bmp|avif|heic)$", RegexOption.IGNORE_CASE)

    /** Reads the first bytes: "%PDF" or a zip signature. */
    fun sniff(ctx: Context, uri: Uri): FileKind? = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val b = ByteArray(4)
            val n = s.read(b)
            when {
                n >= 4 && b[0] == '%'.code.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'D'.code.toByte() && b[3] == 'F'.code.toByte() -> FileKind.PDF
                n >= 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() -> FileKind.ZIP
                else -> null
            }
        }
    } catch (_: Exception) { null }

    /** Encodes an opaque page bitmap. WebP (libwebp, built into Android) beats JPEG by roughly a third at equal quality. */
    @Suppress("DEPRECATION")
    private fun encode(bmp: Bitmap, q: Int, webp: Boolean): ByteArray {
        bmp.setHasAlpha(false) // pages are flattened on white: no alpha channel to store
        val out = ByteArrayOutputStream()
        val fmt = when {
            !webp -> Bitmap.CompressFormat.JPEG
            Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.WEBP
        }
        bmp.compress(fmt, q.coerceIn(1, 99), out)
        return out.toByteArray()
    }

    /**
     * Picks quality and width per page. Presets use fixed settings. A target size gives each page a byte budget (the
     * caller recomputes it from what is still left, so overshoot on one page is paid back on the next) and steps quality,
     * then width, down only as far as that page needs — or back up when there is room.
     */
    private class Tuner(val level: Level) {
        var quality = level.quality
        var width = level.width
        private val adaptive get() = level == Level.TARGET

        private fun lower(): Boolean {
            when {
                quality > 50 -> quality -= 8
                quality > 24 -> quality -= 6
                quality > 12 -> quality -= 4
                width > 360 -> width = maxOf(360, (width * 0.82).toInt())
                else -> return false
            }
            return true
        }

        fun <T> fit(budget: Long, encode: (width: Int, quality: Int) -> Pair<T, Long>): T {
            var (best, size) = encode(width, quality)
            if (!adaptive) return best
            // Plenty of room: win quality (then resolution) back, one step.
            if (size < budget * 0.5) {
                val (w2, q2) = if (width < level.width) minOf(level.width, (width * 1.2).toInt()) to quality else width to minOf(90, quality + 8)
                if (w2 != width || q2 != quality) {
                    val up = encode(w2, q2)
                    if (up.second <= budget) { width = w2; quality = q2; best = up.first; size = up.second }
                }
            }
            while (size > budget && lower()) {
                val r = encode(width, quality)
                best = r.first; size = r.second
            }
            return best
        }
    }

    suspend fun run(ctx: Context, req: CompressRequest) {
        val base = Downloader.safeName(req.name.substringBeforeLast('.'))
        val ext = when {
            req.kind == FileKind.PDF && req.webp -> "cbz"
            req.kind == FileKind.PDF -> "pdf"
            else -> req.name.substringAfterLast('.', "zip").lowercase().takeIf { it == "cbz" || it == "zip" } ?: "zip"
        }
        val mime = when (ext) { "pdf" -> "application/pdf"; "cbz" -> "application/vnd.comicbook+zip"; else -> "application/zip" }
        CompressState.job.value = CompressJob(req.name, JobStatus.RUNNING, req.size, mime = mime, startedAt = SystemClock.elapsedRealtime())
        var target: OutputTarget? = null
        try {
            val t = withContext(Dispatchers.IO) { Storage.open(ctx, "Compressed", "$base (compressed).$ext", mime) }
            target = t
            val counting = CountingOut(t.stream)
            val tuner = Tuner(req.level)
            withContext(Dispatchers.IO) {
                when {
                    req.kind == FileKind.PDF && req.webp -> pdfToCbz(ctx, req, counting, tuner)
                    req.kind == FileKind.PDF -> pdf(ctx, req, counting, tuner)
                    else -> zip(ctx, req, counting, tuner)
                }
            }
            val out = counting.count
            if (out >= req.size) {
                // Never hand back a "compressed" file that is bigger than the original.
                t.abort()
                target = null
                CompressState.update { it.copy(status = JobStatus.DONE, outBytes = out, fraction = 1f, noGain = true, finishedAt = SystemClock.elapsedRealtime()) }
                return
            }
            val saved = withContext(Dispatchers.IO) { t.finish() }
            target = null
            CompressState.update {
                it.copy(status = JobStatus.DONE, outBytes = out, fraction = 1f, outUri = saved.uri, outPath = saved.path, finishedAt = SystemClock.elapsedRealtime())
            }
        } catch (e: CancellationException) {
            target?.abort()
            CompressState.update { it.copy(status = JobStatus.CANCELLED, finishedAt = SystemClock.elapsedRealtime()) }
            throw e
        } catch (e: Throwable) {
            target?.abort()
            val msg = when (e) {
                is SecurityException -> "password-protected or locked file"
                is OutOfMemoryError -> "not enough memory for this page"
                else -> e.message ?: e.javaClass.simpleName
            }
            CompressState.update { it.copy(status = JobStatus.ERROR, error = msg, finishedAt = SystemClock.elapsedRealtime()) }
        }
    }

    private class CountingOut(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        @Volatile var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private class CountingIn(input: InputStream) : FilterInputStream(input) {
        @Volatile var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    /** Budget for the next unit: what is left of the target, shared by what is left to do. */
    private fun budget(req: CompressRequest, written: Long, share: Double): Long =
        if (req.targetBytes <= 0) Long.MAX_VALUE else (((req.targetBytes * 0.985) - written) * share).toLong().coerceAtLeast(1500)

    // ---------------------------------------------------------------- PDF

    private inline fun <T> openPdf(ctx: Context, uri: Uri, block: (PdfRenderer) -> T): T {
        val fd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open file")
        return fd.use { PdfRenderer(fd).use(block) }
    }

    /** Renders every page (band by band, so long strips never need one huge bitmap) and writes a new PDF (JPEG). */
    private suspend fun pdf(ctx: Context, req: CompressRequest, out: CountingOut, tuner: Tuner) {
        openPdf(ctx, req.uri) { r ->
            val total = r.pageCount
            if (total == 0) throw IOException("the PDF has no pages")
            CompressState.update { it.copy(total = total) }
            val writer = PdfWriter(out)
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val budget = budget(req, out.count + 400L * (total - i), 1.0 / (total - i))
                r.openPage(i).use { page ->
                    val slices = tuner.fit(budget) { w, q ->
                        val s = renderPage(page, w, q, false)
                        s to s.sumOf { it.first.size.toLong() }
                    }
                    writer.addStack(slices)
                }
                val written = out.count
                CompressState.update { it.copy(done = i + 1, fraction = (i + 1f) / total, outBytes = written) }
            }
            writer.close()
        }
    }

    /** PDF in, CBZ of WebP pages out: the smallest result at a given look, readable in any comic reader. */
    private suspend fun pdfToCbz(ctx: Context, req: CompressRequest, out: CountingOut, tuner: Tuner) {
        openPdf(ctx, req.uri) { r ->
            val total = r.pageCount
            if (total == 0) throw IOException("the PDF has no pages")
            CompressState.update { it.copy(total = total) }
            val zout = ZipOutputStream(out.buffered(256 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }
            val pw = maxOf(4, total.toString().length)
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val budget = budget(req, out.count + 120L * (total - i), 1.0 / (total - i))
                r.openPage(i).use { page ->
                    val slices = tuner.fit(budget) { w, q ->
                        val s = renderPage(page, w, q, true)
                        s to s.sumOf { it.first.size.toLong() }
                    }
                    val no = (i + 1).toString().padStart(pw, '0')
                    if (slices.size == 1) put(zout, "$no.webp", slices[0].first)
                    else slices.forEachIndexed { k, s -> put(zout, "${no}_${('a' + k)}.webp", s.first) }
                }
                zout.flush()
                val written = out.count
                CompressState.update { it.copy(done = i + 1, fraction = (i + 1f) / total, outBytes = written) }
            }
            zout.close()
        }
    }

    private fun renderPage(page: PdfRenderer.Page, maxWidth: Int, quality: Int, webp: Boolean): List<Pair<ByteArray, JpegInfo>> {
        // Page size is in points (1/72 in). Never render wider than ~2.2x the point size: no detail exists beyond that.
        val width = minOf(maxWidth, (page.width * 2.2).toInt()).coerceAtLeast(200)
        val scale = width.toFloat() / page.width
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val band = 2048
        val out = ArrayList<Pair<ByteArray, JpegInfo>>()
        var y = 0
        while (y < height) {
            val h = minOf(band, height - y)
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            try {
                bmp.eraseColor(Color.WHITE)
                val m = Matrix().apply { setScale(scale, scale); postTranslate(0f, -y.toFloat()) }
                page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                out.add(encode(bmp, quality, webp) to JpegInfo(width, h, 3))
            } finally {
                bmp.recycle()
            }
            y += h
        }
        return out
    }

    // ---------------------------------------------------------------- CBZ / ZIP

    /** Re-encodes every image entry; other entries (ComicInfo.xml…) are copied untouched. Progress follows bytes read. */
    private suspend fun zip(ctx: Context, req: CompressRequest, out: CountingOut, tuner: Tuner) {
        val raw = ctx.contentResolver.openInputStream(req.uri) ?: throw IOException("cannot open file")
        val counting = CountingIn(raw.buffered(256 * 1024))
        val ext = if (req.webp) "webp" else "jpg"
        ZipInputStream(counting).use { zin ->
            val zout = ZipOutputStream(out.buffered(256 * 1024)).apply { setLevel(Deflater.NO_COMPRESSION) }
            var images = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val bytes = zin.readBytes()
                if (imageExt.containsMatchIn(e.name)) {
                    val left = (req.size - (counting.count - bytes.size)).coerceAtLeast(bytes.size.toLong())
                    zout.flush()
                    val budget = budget(req, out.count, bytes.size.toDouble() / left)
                    val parts = try {
                        tuner.fit(budget) { w, q -> val p = reencode(bytes, w, q, req.webp); p to (p?.sumOf { it.size.toLong() } ?: 0L) }
                    } catch (_: Throwable) { null }
                    val keepOriginal = parts.isNullOrEmpty() || parts.sumOf { it.size.toLong() } >= bytes.size
                    if (keepOriginal) {
                        put(zout, e.name, bytes) // never lose a page, never enlarge one
                    } else {
                        val stem = e.name.substringBeforeLast('.')
                        if (parts!!.size == 1) put(zout, "$stem.$ext", parts[0])
                        else parts.forEachIndexed { i, p -> put(zout, "${stem}_${('a' + i)}.$ext", p) }
                    }
                    images++
                } else {
                    put(zout, e.name, bytes)
                }
                zout.flush()
                val read = counting.count
                val written = out.count
                CompressState.update {
                    it.copy(done = images, total = images, fraction = if (req.size > 0) (read.toFloat() / req.size).coerceIn(0f, 0.99f) else 0f, outBytes = written)
                }
            }
            zout.close()
        }
    }

    private fun put(z: ZipOutputStream, name: String, bytes: ByteArray) {
        z.putNextEntry(ZipEntry(name))
        z.write(bytes)
        z.closeEntry()
    }

    /** Scales an image to [maxWidth] and encodes it; very tall results are split so no single bitmap gets huge. */
    @Suppress("DEPRECATION")
    private fun reencode(bytes: ByteArray, maxWidth: Int, quality: Int, webp: Boolean): List<ByteArray>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val scale = minOf(1f, maxWidth.toFloat() / w)
        val outW = (w * scale).toInt().coerceAtLeast(1)
        var sample = 1
        while (w / (sample * 2) >= outW) sample *= 2
        val dec = try { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) } catch (_: Throwable) { null }
        // Bands of at most 8000 output pixels (WebP allows 16383); one band = one image.
        val bandSrc = ((8000 / scale).toInt()).coerceAtLeast(1)
        val parts = ArrayList<ByteArray>()
        var y = 0
        while (y < h) {
            val bottom = minOf(h, y + bandSrc)
            val src = if (dec != null) {
                dec.decodeRegion(Rect(0, y, w, bottom), BitmapFactory.Options().apply { inSampleSize = sample })
            } else if (y == 0 && bottom == h) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } else null
            if (src == null) { dec?.recycle(); return null }
            val targetH = ((bottom - y) * scale).toInt().coerceAtLeast(1)
            val scaled = if (src.width == outW && src.height == targetH) src else Bitmap.createScaledBitmap(src, outW, targetH, true)
            val flat = Bitmap.createBitmap(outW, targetH, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
            parts.add(encode(flat, quality, webp))
            flat.recycle()
            if (scaled !== src) scaled.recycle()
            src.recycle()
            y = bottom
        }
        dec?.recycle()
        return parts
    }

    // ---------------------------------------------------------------- estimate

    /**
     * Measures a few pages spread through the file at each preset and scales up, so the user sees the expected size
     * (and, for a target, the quality it implies) before spending minutes on a big file.
     */
    fun estimate(ctx: Context, uri: Uri, kind: FileKind, size: Long, webp: Boolean): Estimate? = try {
        val presets = listOf(Level.HIGH, Level.BALANCED, Level.SMALL)
        if (kind == FileKind.PDF) {
            openPdf(ctx, uri) { r ->
                val n = r.pageCount
                if (n == 0) return@openPdf null
                val picks = (0 until minOf(5, n)).map { it * (n - 1) / maxOf(1, minOf(5, n) - 1) }.distinct()
                val sums = LongArray(3)
                for (i in picks) r.openPage(i).use { page ->
                    presets.forEachIndexed { k, lv -> sums[k] += renderPage(page, lv.width, lv.quality, webp).sumOf { it.first.size.toLong() } }
                }
                val per = sums.map { it.toDouble() / picks.size * n * 1.01 + 2000 }
                Estimate(per[0].toLong(), per[1].toLong(), per[2].toLong())
            }
        } else {
            val raw = ctx.contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(raw.buffered(256 * 1024)).use { zin ->
                var orig = 0L
                val sums = LongArray(3)
                var seen = 0
                while (seen < 6) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory || !imageExt.containsMatchIn(e.name)) continue
                    val bytes = zin.readBytes()
                    val enc = presets.map { lv -> reencode(bytes, lv.width, lv.quality, webp)?.sumOf { it.size.toLong() } ?: bytes.size.toLong() }
                    orig += bytes.size
                    enc.forEachIndexed { k, v -> sums[k] += minOf(v, bytes.size.toLong()) }
                    seen++
                }
                if (orig == 0L) null else {
                    val r = sums.map { (it.toDouble() / orig * size).toLong() }
                    Estimate(r[0], r[1], r[2])
                }
            }
        }
    } catch (_: Throwable) { null }
}
