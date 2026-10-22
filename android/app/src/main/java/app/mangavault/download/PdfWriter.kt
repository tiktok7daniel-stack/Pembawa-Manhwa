package app.mangavault.download

import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale

/** Size and colour layout of a JPEG, read from its frame header without decoding it. */
class JpegInfo(val width: Int, val height: Int, val components: Int)

object Jpeg {
    /** Returns null for anything a PDF viewer could not show as-is (not a JPEG, 12-bit, lossless, arithmetic-coded). */
    fun info(b: ByteArray): JpegInfo? {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return null
        var i = 2
        while (i + 9 < b.size) {
            if ((b[i].toInt() and 0xFF) != 0xFF) { i++; continue }
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (marker != 0xC0 && marker != 0xC1 && marker != 0xC2) return null // only baseline / extended / progressive DCT
                val precision = b[i + 4].toInt() and 0xFF
                val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                val comps = b[i + 9].toInt() and 0xFF
                if (precision != 8 || w <= 0 || h <= 0 || comps !in setOf(1, 3, 4)) return null
                return JpegInfo(w, h, comps)
            }
            if (marker == 0xDA || len < 2) return null
            i += 2 + len
        }
        return null
    }
}

/**
 * Minimal streaming PDF writer for image books: every page is one JPEG shown at full size, embedded as-is
 * (no re-encoding, so no quality loss), plus an optional bookmark per chapter. Only offsets are kept in memory,
 * so a book of thousands of pages costs almost nothing.
 */
class PdfWriter(out: OutputStream) : Closeable {
    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private val os = Counting(out.buffered(256 * 1024))
    private val offsets = HashMap<Int, Long>()
    private val pages = ArrayList<Int>()
    private val marks = ArrayList<Pair<String, Int>>()
    private var pendingMark: String? = null
    private var next = 4 // 1 catalog, 2 page tree, 3 outline root
    private var closed = false

    val pageCount: Int get() = pages.size

    init {
        os.write("%PDF-1.4\n%".toByteArray(Charsets.ISO_8859_1))
        os.write(byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
    }

    private fun w(s: String) = os.write(s.toByteArray(Charsets.ISO_8859_1))

    private fun begin(id: Int) {
        offsets[id] = os.count
        w("$id 0 obj\n")
    }

    private fun num(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /** The next page added starts a chapter with this title in the PDF's bookmarks. */
    fun bookmark(title: String) { pendingMark = title }

    fun addJpeg(bytes: ByteArray, info: JpegInfo) {
        val img = next++
        val content = next++
        val page = next++
        val cs = when (info.components) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK /Decode [1 0 1 0 1 0 1 0]"; else -> "/DeviceRGB" }
        begin(img)
        w("<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} /ColorSpace $cs " +
            "/BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.size} >>\nstream\n")
        os.write(bytes)
        w("\nendstream\nendobj\n")

        // 96 dpi; very long webtoon strips are scaled to stay under the 14400-unit page limit many viewers enforce.
        val scale = minOf(0.75, 14400.0 / info.height, 14400.0 / info.width)
        val pw = num(info.width * scale)
        val ph = num(info.height * scale)
        val draw = "q $pw 0 0 $ph 0 0 cm /Im0 Do Q"
        begin(content)
        w("<< /Length ${draw.length} >>\nstream\n$draw\nendstream\nendobj\n")
        begin(page)
        w("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] /Resources << /XObject << /Im0 $img 0 R >> >> /Contents $content 0 R >>\nendobj\n")
        pages.add(page)
        pendingMark?.let { marks.add(it to page) }
        pendingMark = null
    }

    private fun pdfText(s: String): String {
        val sb = StringBuilder("<FEFF")
        for (c in s) sb.append(String.format(Locale.US, "%04X", c.code))
        return sb.append('>').toString()
    }

    override fun close() {
        if (closed) return
        closed = true
        val markIds = marks.map { next++ }
        marks.forEachIndexed { i, (title, page) ->
            begin(markIds[i])
            val prev = if (i > 0) " /Prev ${markIds[i - 1]} 0 R" else ""
            val nxt = if (i < marks.size - 1) " /Next ${markIds[i + 1]} 0 R" else ""
            w("<< /Title ${pdfText(title)} /Parent 3 0 R$prev$nxt /Dest [$page 0 R /Fit] >>\nendobj\n")
        }
        begin(3)
        if (markIds.isEmpty()) w("<< /Type /Outlines /Count 0 >>\nendobj\n")
        else w("<< /Type /Outlines /First ${markIds.first()} 0 R /Last ${markIds.last()} 0 R /Count ${markIds.size} >>\nendobj\n")
        begin(2)
        w("<< /Type /Pages /Kids [${pages.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>\nendobj\n")
        begin(1)
        w("<< /Type /Catalog /Pages 2 0 R /Outlines 3 0 R" + (if (markIds.isNotEmpty()) " /PageMode /UseOutlines" else "") + " >>\nendobj\n")
        val xref = os.count
        val size = next
        val sb = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (id in 1 until size) sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets[id] ?: 0L))
        sb.append("trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        w(sb.toString())
        os.flush()
        os.close()
    }
}
