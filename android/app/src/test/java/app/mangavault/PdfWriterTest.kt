package app.mangavault

import app.mangavault.download.Jpeg
import app.mangavault.download.PdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PdfWriterTest {
    /** Just enough of a JPEG for the writer: SOI, a JFIF segment, a baseline frame header, some data, EOI. */
    private fun jpeg(w: Int, h: Int, comps: Int = 3, sof: Int = 0xC0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, 16, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
        val len = 8 + comps * 3
        out.write(byteArrayOf(0xFF.toByte(), sof.toByte(), 0, len.toByte(), 8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), comps.toByte()))
        repeat(comps) { out.write(byteArrayOf((it + 1).toByte(), 0x11, 0)) }
        out.write(ByteArray(200) { 0x55 })
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    @Test
    fun readsJpegSizeAndColourLayout() {
        val rgb = Jpeg.info(jpeg(320, 1800))!!
        assertEquals(320, rgb.width)
        assertEquals(1800, rgb.height)
        assertEquals(3, rgb.components)
        assertEquals(1, Jpeg.info(jpeg(50, 60, comps = 1))!!.components)
        assertNotNull(Jpeg.info(jpeg(50, 60, sof = 0xC2))) // progressive
        assertNull(Jpeg.info(jpeg(50, 60, sof = 0xC3))) // lossless: not shown by PDF viewers
        assertNull(Jpeg.info(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun writesAValidPdfWithBookmarksAndCorrectXref() {
        val out = ByteArrayOutputStream()
        val pdf = PdfWriter(out)
        val a = jpeg(400, 600)
        val tall = jpeg(300, 30000) // long webtoon strip: page must be scaled under the 14400 limit
        pdf.bookmark("Chapter 1")
        pdf.addJpeg(a, Jpeg.info(a)!!)
        pdf.addJpeg(a, Jpeg.info(a)!!)
        pdf.bookmark("الفصل 2")
        pdf.addJpeg(tall, Jpeg.info(tall)!!)
        pdf.close()

        val bytes = out.toByteArray()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertEquals(3, Regex("/Type /Page\\b").findAll(text).count())
        assertTrue(text.contains("/Count 3"))
        assertEquals(2, Regex("/Title <FEFF").findAll(text).count())

        // Every xref entry must point exactly at "N 0 obj".
        val startxref = Regex("startxref\\n(\\d+)").find(text)!!.groupValues[1].toInt()
        assertTrue(text.startsWith("xref", startxref))
        val lines = text.substring(startxref).lines()
        val size = lines[1].split(' ')[1].toInt()
        for (id in 1 until size) {
            val off = lines[2 + id].substring(0, 10).toInt()
            assertTrue("object $id at $off", text.startsWith("$id 0 obj", off))
        }
        val box = Regex("/MediaBox \\[0 0 ([\\d.]+) ([\\d.]+)]").findAll(text).map { it.groupValues[2].toDouble() }.toList()
        assertTrue(box.all { it <= 14400.0 })
        assertNotNull(box.firstOrNull())
    }
}
