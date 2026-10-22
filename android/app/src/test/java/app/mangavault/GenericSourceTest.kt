package app.mangavault

import app.mangavault.source.Chapter
import app.mangavault.source.GenericSource
import app.mangavault.source.Sources
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Chapter discovery against a fake manga site that hides older chapters in different ways. */
class GenericSourceTest {
    private val server = MockWebServer()

    private fun html(body: String, title: String = "Demo") =
        MockResponse().setHeader("Content-Type", "text/html").setBody("<html><head><title>$title</title><meta property=\"og:title\" content=\"Demo Series\"></head><body>$body</body></html>")

    private fun links(prefix: String, range: IntRange) = range.reversed().joinToString("") { "<a href=\"$prefix/chapter-$it/\">Chapter $it</a>" }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                Regex("^/(gap|paged)/demo/chapter-(\\d+)/$").find(path)?.let {
                    val n = it.groupValues[2].toInt()
                    if (n in 1..30) {
                        return html("<div class=\"reading-content\">" + (1..3).joinToString("") { p -> "<img data-src=\"/img/$n-$p.jpg\" src=\"data:image/gif;base64,R0\">" } + "</div>")
                    }
                }
                return when (path) {
                    // Only the 10 newest chapters are shown; 1..20 exist on the site.
                    "/gap/demo" -> html("<h1>Demo</h1>" + links("/gap/demo", 21..30))
                    // Chapter list split over three pages with ?page=N.
                    "/paged/demo" -> html(links("/paged/demo", 21..30) + "<a href=\"/paged/demo?page=2\">2</a>")
                    "/paged/demo?page=2" -> html(links("/paged/demo", 11..20) + "<a href=\"/paged/demo?page=3\">3</a>")
                    "/paged/demo?page=3" -> html(links("/paged/demo", 1..10))
                    else -> MockResponse().setResponseCode(404).setBody("<title>404 Not Found</title>")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun numbers(chapters: List<Chapter>) = chapters.map { it.number }

    @Test
    fun hiddenOlderChaptersAreRebuiltFromTheUrlPattern() = runBlocking {
        val s = GenericSource.getSeries(server.url("/gap/demo").toString(), browser = false)
        assertEquals((1..30).map { it.toString() }, numbers(s.chapters))
        assertEquals("Demo Series", s.title)
    }

    @Test
    fun paginatedListsAreFollowed() = runBlocking {
        val s = GenericSource.getSeries(server.url("/paged/demo").toString(), browser = false)
        assertEquals((1..30).map { it.toString() }, numbers(s.chapters))
    }

    @Test
    fun aPastedChapterLinkResolvesToTheSeries() = runBlocking {
        val s = GenericSource.getSeries(server.url("/paged/demo/chapter-5/").toString(), browser = false)
        assertEquals(30, s.chapters.size)
    }

    @Test
    fun pagesAreFoundInReadingOrder() = runBlocking {
        val s = GenericSource.getSeries(server.url("/paged/demo").toString(), browser = false)
        val pages = GenericSource.getPages(s.chapters[4], null, false)
        assertEquals(listOf("5-1", "5-2", "5-3"), pages.map { it.substringAfterLast('/').substringBefore('.') })
    }

    @Test
    fun linksAreExtractedFromSharedText() {
        assertEquals("https://example.com/manga/x", Sources.normalize("Read this: https://example.com/manga/x now!"))
        assertEquals("https://example.com/manga/x", Sources.normalize("example.com/manga/x"))
    }
}
