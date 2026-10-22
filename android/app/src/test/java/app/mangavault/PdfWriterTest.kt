package app.mangavault

import app.mangavault.download.Jpeg
import app.mangavault.download.PdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PdfWriterTest {
    private fun jpeg(w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_RGB): ByteArray {
        val img = BufferedImage(w, h, type)
        val g = img.createGraphics()
        g.color = java.awt.Color(90, 120, 220)
        g.fillRect(0, 0, w, h)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    @Test
    fun readsJpegSizeAndColourLayout() {
        val rgb = Jpeg.info(jpeg(320, 1800))!!
        assertEquals(320, rgb.width)
        assertEquals(1800, rgb.height)
        assertEquals(3, rgb.components)
        assertEquals(1, Jpeg.info(jpeg(50, 60, BufferedImage.TYPE_BYTE_GRAY))!!.components)
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
