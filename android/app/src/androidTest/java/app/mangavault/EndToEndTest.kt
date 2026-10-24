package app.mangavault

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mangavault.data.Prefs
import app.mangavault.download.DownloadState
import app.mangavault.download.Downloader
import app.mangavault.download.Format
import app.mangavault.download.JobRequest
import app.mangavault.download.JobStatus
import app.mangavault.download.Mode
import app.mangavault.download.Storage
import app.mangavault.source.Sources
import app.mangavault.ui.Screen
import app.mangavault.ui.Tab
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Runs the real app on a device against a local fake manga site: parse a link, download chapters through the
 * real storage code, then open the produced CBZ files and count the images inside.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()

    private fun png(w: Int, h: Int, color: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private val throttled = java.util.concurrent.atomic.AtomicInteger()
    private val page = png(400, 600, Color.rgb(120, 90, 200))
    private val strip by lazy { png(320, 9000, Color.rgb(30, 160, 120)) } // long webtoon page
    private val logo = png(64, 64, Color.RED)

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html")
        .setBody("<html><head><title>Demo</title><meta property=\"og:title\" content=\"Demo Series\"></head><body>$body</body></html>")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                Regex("^/manga/demo/chapter-(\\d+)/$").find(path)?.let {
                    val n = it.groupValues[1].toInt()
                    if (n in 1..6) return html("<div class=\"reading-content\">" + (1..3).joinToString("") { p -> "<img data-src=\"/img/$n-$p.png\">" } + "</div>")
                }
                Regex("^/hot/demo/chapter-(\\d+)/$").find(path)?.let {
                    val n = it.groupValues[1].toInt()
                    if (n in 1..4) return html("<div class=\"reading-content\">" + (1..5).joinToString("") { p -> "<img src=\"/himg/$n-$p.png\">" } + "</div>")
                }
                Regex("^/tall/demo/chapter-(\\d+)/$").find(path)?.let {
                    val n = it.groupValues[1].toInt()
                    if (n in 1..10) return html("<div class=\"reading-content\">" + (1..4).joinToString("") { p -> "<img src=\"/strip/$n-$p.png\">" } + "</div>")
                }
                if (path.startsWith("/strip/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(strip))
                if (path == "/tall/demo") return html((10 downTo 1).joinToString("") { "<a href=\"/tall/demo/chapter-$it/\">Chapter $it</a>" })
                if (path.startsWith("/himg/")) {
                    // Hotlink protection: only the chapter page is an accepted Referer, and the host throttles now and then.
                    val ref = request.getHeader("Referer") ?: ""
                    if (!ref.contains("/hot/demo/chapter-")) return MockResponse().setResponseCode(403)
                    if (throttled.incrementAndGet() % 4 == 0) return MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(page))
                }
                Regex("^/js/read/(\\d)$").find(path)?.let {
                    // Pages exist only after JavaScript runs.
                    return html("<img src=\"/logo.png\"><div id=\"r\"></div><script>setTimeout(function(){for(var p=1;p<=3;p++){var i=new Image();" +
                        "i.src='/img/js-${it.groupValues[1]}-'+p+'.png';document.getElementById('r').appendChild(i);}},300)</script>")
                }
                return when {
                    // Only the three newest chapters are listed; 1..6 exist.
                    path == "/hot/demo" -> html((4 downTo 1).joinToString("") { "<a href=\"/hot/demo/chapter-$it/\">Chapter $it</a>" })
                    path == "/manga/demo" -> html((6 downTo 4).joinToString("") { "<a href=\"/manga/demo/chapter-$it/\">Chapter $it</a>" })
                    // Chapter list built by JavaScript.
                    path == "/js/series" -> html("<div id=\"l\"></div><script>setTimeout(function(){document.getElementById('l').innerHTML=" +
                        "[1,2,3].map(function(n){return '<a href=\"/js/read/'+n+'\">Chapter '+n+'</a>'}).join('')},300)</script>")
                    path == "/logo.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(logo))
                    path.startsWith("/img/") -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(page))
                    else -> MockResponse().setResponseCode(404).setBody("<title>404 Not Found</title>")
                }
            }
        }
        server.start()
        Prefs.setLang("en")
        Prefs.setTheme("dark")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun zipNames(uri: String): List<String> {
        val names = ArrayList<String>()
        ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { input ->
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name.endsWith(".png")) assertTrue("empty image ${e.name}", z.readBytes().size > 100)
                    names.add(e.name)
                }
            }
        }
        return names
    }

    private fun screenshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val out = Storage.open(ctx, "screens", "$name.png")
        out.stream.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.finish()
    }

    @Test
    fun downloadsAllChaptersIntoOneCbz() = runBlocking {
        val series = Sources.load(server.url("/manga/demo").toString(), "en", false)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), series.chapters.map { it.number })

        Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        val names = zipNames(Prefs.history.value.first().uri!!)
        assertEquals(18, names.count { it.endsWith(".png") })
        assertTrue(names.contains("Ch_001_p001.png") && names.contains("Ch_006_p003.png") && names.contains("ComicInfo.xml"))
    }

    @Test
    fun survivesHotlinkProtectionAndThrottling() = runBlocking {
        val series = Sources.load(server.url("/hot/demo").toString(), "en", false)
        assertEquals(listOf("1", "2", "3", "4"), series.chapters.map { it.number })
        Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        assertTrue(job.chapters.all { it.state == app.mangavault.download.CState.DONE })
        assertEquals(20, zipNames(Prefs.history.value.first().uri!!).count { it.endsWith(".png") })
    }

    @Test
    fun exportsOnePdfThatAndroidCanOpen() = runBlocking {
        val series = Sources.load(server.url("/manga/demo").toString(), "en", false)
        Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE, Format.PDF))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        val h = Prefs.history.value.first()
        assertTrue(h.path, h.path.endsWith("Demo Series.pdf"))
        val uri = Uri.parse(h.uri!!)
        val text = String(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF"))
        assertEquals(6, Regex("/Title <FEFF").findAll(text).count()) // one bookmark per chapter
        // The system PDF renderer must accept the file and see every page.
        ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r -> assertEquals(18, r.pageCount) }
        }
    }

    @Test
    fun pdfOfTenChaptersWithLongPagesHasEveryPage() = runBlocking {
        val series = Sources.load(server.url("/tall/demo").toString(), "en", false)
        assertEquals((1..10).map { it.toString() }, series.chapters.map { it.number })
        Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE, Format.PDF))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        assertTrue(job.chapters.all { it.state == app.mangavault.download.CState.DONE && it.failedPages == 0 })
        val uri = Uri.parse(Prefs.history.value.first().uri!!)
        ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r ->
                assertEquals(40, r.pageCount) // 10 chapters x 4 pages, none dropped
                r.openPage(39).use { p -> assertTrue("page is a long strip", p.height > p.width * 10) }
            }
        }
    }

    /** A detailed, hard-to-compress page: gradient plus noise, saved as maximum-quality JPEG (a typical "huge" scan). */
    private fun heavyJpeg(seed: Int): ByteArray {
        val w = 1600
        val h = 2400
        val rnd = java.util.Random(seed.toLong())
        val px = IntArray(w * h) { i -> val y = i / w; val n = rnd.nextInt(60); Color.rgb((y * 255 / h + n) % 256, (i % w * 255 / w + n) % 256, 120 + n) }
        val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    @Test
    fun compressesABigPdfWithoutLosingPages() = runBlocking {
        val src = java.io.File(ctx.cacheDir, "big.pdf")
        app.mangavault.download.PdfWriter(src.outputStream()).use { pdf ->
            for (i in 1..6) { val j = heavyJpeg(i); pdf.addJpeg(j, app.mangavault.download.Jpeg.info(j)!!) }
        }
        val req = app.mangavault.download.CompressRequest(Uri.fromFile(src), "big.pdf", src.length(), app.mangavault.download.FileKind.PDF,
            app.mangavault.download.Level.BALANCED, 0)
        app.mangavault.download.Compressor.run(ctx, req)
        val j = app.mangavault.download.CompressState.job.value!!
        assertEquals(j.error, JobStatus.DONE, j.status)
        assertTrue("no gain", !j.noGain)
        assertTrue("from ${src.length()} to ${j.outBytes}", j.outBytes * 3 < src.length())
        ctx.contentResolver.openFileDescriptor(Uri.parse(j.outUri!!), "r")!!.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r -> assertEquals(6, r.pageCount) }
        }
    }

    @Test
    fun compressesACbzToATargetSizeAndKeepsEveryImage() = runBlocking {
        val src = java.io.File(ctx.cacheDir, "big.cbz")
        java.util.zip.ZipOutputStream(src.outputStream()).use { z ->
            for (i in 1..5) { z.putNextEntry(java.util.zip.ZipEntry("00$i.jpg")); z.write(heavyJpeg(10 + i)); z.closeEntry() }
            z.putNextEntry(java.util.zip.ZipEntry("ComicInfo.xml")); z.write("<ComicInfo/>".toByteArray()); z.closeEntry()
        }
        val target = src.length() / 6
        val req = app.mangavault.download.CompressRequest(Uri.fromFile(src), "big.cbz", src.length(), app.mangavault.download.FileKind.ZIP,
            app.mangavault.download.Level.TARGET, target)
        app.mangavault.download.Compressor.run(ctx, req)
        val j = app.mangavault.download.CompressState.job.value!!
        assertEquals(j.error, JobStatus.DONE, j.status)
        assertTrue("target $target got ${j.outBytes}", j.outBytes < target * 1.3)
        val names = zipNames(j.outUri!!)
        assertEquals(5, names.count { it.endsWith(".jpg") })
        assertTrue(names.contains("ComicInfo.xml"))
    }

    @Test
    fun exportsOneZipWithAFolderPerChapter() = runBlocking {
        val series = Sources.load(server.url("/manga/demo").toString(), "en", false)
        Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE, Format.ZIP))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        val h = Prefs.history.value.first()
        assertTrue(h.path, h.path.endsWith("Demo Series.zip"))
        val names = zipNames(h.uri!!)
        assertEquals(18, names.count { it.endsWith(".png") })
        assertTrue(names.toString(), names.contains("Ch 001/001.png") && names.contains("Ch 006/003.png"))
    }

    @Test
    fun exportsOnePdfPerChapter() = runBlocking {
        val series = Sources.load(server.url("/manga/demo").toString(), "en", false)
        Downloader.run(ctx, JobRequest(series, series.chapters.take(2), Mode.PER_CHAPTER, Format.PDF))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        val h = Prefs.history.value.first()
        assertEquals(2, h.files)
        ctx.contentResolver.openFileDescriptor(Uri.parse(h.uri!!), "r")!!.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r -> assertEquals(3, r.pageCount) }
        }
    }

    @Test
    fun downloadsOneCbzPerChapter() = runBlocking {
        val series = Sources.load(server.url("/manga/demo").toString(), "en", false)
        Downloader.run(ctx, JobRequest(series, series.chapters.take(2), Mode.PER_CHAPTER))
        val job = DownloadState.job.value!!
        assertEquals(job.error, JobStatus.DONE, job.status)
        val h = Prefs.history.value.first()
        assertEquals(2, h.files)
        assertTrue(h.path, h.path.contains("MangaVault/Demo Series"))
        val names = zipNames(h.uri!!)
        assertEquals(listOf("001.png", "002.png", "003.png", "ComicInfo.xml"), names)
    }

    @Test
    fun browserModeHandlesJavaScriptSites() {
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                val series = Sources.load(server.url("/js/series").toString(), "en", false)
                assertEquals(listOf("1", "2", "3"), series.chapters.map { c -> c.number })
                Downloader.run(ctx, JobRequest(series, series.chapters, Mode.SINGLE))
                val job = DownloadState.job.value!!
                assertEquals(job.error, JobStatus.DONE, job.status)
                // 3 chapters x 3 pages; the small logo is filtered out
                assertEquals(9, zipNames(Prefs.history.value.first().uri!!).count { n -> n.endsWith(".png") })
            }
        }
    }

    @Test
    fun fullUiFlowWithForegroundService() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2500)
            screenshot("1-home")
            val link = server.url("/manga/demo").toString() // resolving the mock host is network I/O: keep it off the UI thread
            scenario.onActivity { a -> a.vm.url = link; a.vm.parse() }
            SystemClock.sleep(600)
            screenshot("2-finding")
            var screen: Screen? = null
            for (i in 0 until 60) {
                scenario.onActivity { a -> screen = a.vm.screen }
                if (screen == Screen.RESULT) break
                SystemClock.sleep(500)
            }
            assertEquals(Screen.RESULT, screen)
            SystemClock.sleep(1500)
            screenshot("3-chapters")
            scenario.onActivity { a -> a.beginDownload() }
            var status: JobStatus? = null
            for (i in 0 until 120) {
                status = DownloadState.job.value?.status
                if (status != null && status != JobStatus.RUNNING) break
                SystemClock.sleep(500)
            }
            assertEquals(DownloadState.job.value?.error, JobStatus.DONE, status)
            SystemClock.sleep(1500)
            screenshot("4-progress")
            scenario.onActivity { a -> a.vm.screen = Screen.MAIN; a.vm.tab = Tab.DOWNLOADS }
            SystemClock.sleep(1500)
            screenshot("5-downloads")
            scenario.onActivity { a -> a.vm.tab = Tab.SETTINGS }
            SystemClock.sleep(1500)
            screenshot("6-settings")
            scenario.onActivity { a -> a.vm.tab = Tab.HOME; Prefs.setLang("ar") }
            SystemClock.sleep(2000)
            screenshot("7-home-ar")
            Prefs.setLang("en")
        }
    }
}
