package app.mangavault.source

import app.mangavault.net.Http
import app.mangavault.net.HttpStatusException
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.net.URLDecoder

/** Works on most reader sites: finds chapter links and page images, falling back to a real WebView for JS-built pages. */
object GenericSource {
    private val IC = RegexOption.IGNORE_CASE
    private val chHint = Regex("chapter|chap\\b|\\bch[-_. ]?\\d|episode|\\bep[-_. ]?\\d|capitulo|الفصل|فصل|الحلقة|حلقة", IC)
    private val num = Regex("(?:chapter|chap|ch|episode|ep|capitulo|الفصل|فصل|الحلقة|حلقة)[\\s\\-_.:#/]*?(\\d+(?:[.\\-]\\d+)?)", IC)
    private val imgExt = Regex("\\.(jpe?g|png|webp|avif|gif)(\\?|#|$)", IC)
    private val badImg = Regex("logo|avatar|banner|icon|sprite|emoji|favicon|loading|placeholder|spinner|/ads?/|gravatar|button|badge|social", IC)
    private val reader = listOf(
        ".reading-content", "#readerarea", ".chapter-content", ".chapter-container", "#chapter-reader", ".page-break",
        ".reader-area", "#reader", ".reader", ".entry-content", "#chapter_body", ".viewer", "#viewer", "article",
    )
    private val lazyAttrs = listOf("data-src", "data-lazy-src", "data-original", "data-cfsrc", "data-url", "src")
    private const val NEXT = "a[rel=next], .pagination a.next, .page-numbers.next, a.next.page-numbers, li.next a"
    private val scriptImg = Regex("https?:\\\\?/\\\\?/[^\"'\\s\\\\]+(?:\\\\/[^\"'\\s\\\\]+)*\\.(?:jpe?g|png|webp|avif)", IC)

    private class Raw(val href: String, val text: String, val number: String?)
    private class Found(val doc: Document, val title: String, val cover: String?, val description: String, var list: List<Raw>)

    private fun host(u: String) = try { URL(u).host.removePrefix("www.") } catch (_: Exception) { "" }
    private fun sameSite(a: String, b: String) = host(a) == host(b)
    private fun blocked(code: Int) = code in setOf(401, 403, 429, 503) || code >= 500

    private fun chapterNumber(text: String, href: String): String? {
        val decoded = try { URLDecoder.decode(href, "UTF-8") } catch (_: Exception) { href }
        val m = num.find(text) ?: num.find(decoded) ?: return null
        return m.groupValues[1].replace('-', '.')
    }

    private fun extractChapters(doc: Document, base: String): List<Raw> {
        val seen = LinkedHashMap<String, Raw>()
        val baseClean = base.substringBefore('#')
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (!href.startsWith("http") || !sameSite(href, base)) continue
            val clean = href.substringBefore('#')
            if (clean == baseClean) continue
            val text = a.text().replace(Regex("\\s+"), " ").trim()
            val path = try { URLDecoder.decode(URL(href).path, "UTF-8") } catch (_: Exception) { "" }
            if (!chHint.containsMatchIn(text) && !chHint.containsMatchIn(path)) continue
            if (!seen.containsKey(clean)) seen[clean] = Raw(clean, text, chapterNumber(text, clean))
        }
        return seen.values.toList()
    }

    private fun finish(list: List<Raw>): List<Chapter> {
        val withNum = list.count { it.number != null }
        val out: List<Raw> = if (withNum >= maxOf(1.0, list.size * 0.6)) {
            val byNum = LinkedHashMap<String, Raw>()
            for (c in list) { val n = c.number; if (n != null && !byNum.containsKey(n)) byNum[n] = c }
            byNum.values.sortedBy { it.number?.toDoubleOrNull() ?: 0.0 }
        } else if (list.size > 1) list.reversed() else list // sites list newest first
        return out.mapIndexed { i, c -> Chapter(i.toString(), c.number, c.text.take(120), c.href) }
    }

    private fun readSeries(html: String, url: String): Found {
        val doc = Jsoup.parse(html, url)
        val title = (doc.selectFirst("meta[property=og:title]")?.attr("content")?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()?.ifBlank { null } ?: doc.title().ifBlank { null } ?: "Manga")
            .replace(Regex("\\s+"), " ").trim().take(150)
        val cover = doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifBlank { null }
        val desc = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.take(600) ?: ""
        return Found(doc, title, cover, desc, extractChapters(doc, url))
    }

    /** A pasted chapter link (".../series/chapter-12") is tried as its parent series page first. */
    private fun seriesCandidates(url: String): List<String> {
        val u = try { URL(url) } catch (_: Exception) { return listOf(url) }
        val segs = u.path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (segs.size >= 2 && chHint.containsMatchIn(segs.last())) {
            return listOf("${u.protocol}://${u.authority}/" + segs.dropLast(1).joinToString("/"), url)
        }
        return listOf(url)
    }

    suspend fun getSeries(url: String, browser: Boolean): Series {
        var last: Exception? = null
        for (candidate in seriesCandidates(url)) {
            try {
                return loadSeries(candidate, browser)
            } catch (e: SourceException) {
                if (e.code != Err.NO_CHAPTERS) throw e
                last = e
            } catch (e: HttpStatusException) {
                if (e.code != 404) throw e
                last = e
            }
        }
        throw last ?: SourceException(Err.NO_CHAPTERS)
    }

    private suspend fun loadSeries(url: String, browser: Boolean): Series {
        var found: Found? = null
        var session: Session? = null
        var rendered = false

        if (!browser) {
            try {
                val r = Http.text(url)
                val f = readSeries(r.body, r.url)
                var list = f.list
                // Madara (WordPress) themes load the chapter list through ajax.
                if (Regex("wp-manga|madara", IC).containsMatchIn(r.body) && list.size < 3) {
                    try {
                        val ajax = URL(URL(r.url.trimEnd('/') + "/"), "ajax/chapters/").toString()
                        val a = Http.text(ajax, mapOf("X-Requested-With" to "XMLHttpRequest"), "POST", FormBody.Builder().build())
                        val extra = extractChapters(Jsoup.parse(a.body, r.url), r.url)
                        if (extra.size > list.size) list = extra
                    } catch (_: Exception) { /* keep what the page showed */ }
                }
                // Paginated chapter lists.
                var next = f.doc.selectFirst(NEXT)?.absUrl("href")
                val visited = mutableSetOf(r.url)
                var guard = 0
                while (guard++ < 30) {
                    val n = next?.takeIf { it.isNotBlank() } ?: break
                    if (n in visited || !sameSite(n, r.url)) break
                    visited += n
                    val p = try { Http.text(n) } catch (_: Exception) { break }
                    val more = readSeries(p.body, p.url)
                    list = list + more.list
                    next = more.doc.selectFirst(NEXT)?.absUrl("href")
                }
                f.list = list
                found = f
            } catch (e: HttpStatusException) {
                if (!blocked(e.code)) throw e
            }
        }

        // Too little found, or blocked: load it in a real (WebView) browser so JavaScript-built pages work.
        val current = found
        if (current == null || current.list.size < 2) {
            val r = BrowserRenderer.render(url, expand = true)
            val second = readSeries(r.html, r.url)
            if (current == null || second.list.size > current.list.size) {
                found = second
                session = Session(r.cookie, Http.UA)
                rendered = true
            }
        }
        val result = found ?: throw SourceException(Err.NO_CHAPTERS)
        if (result.list.isEmpty()) throw SourceException(Err.NO_CHAPTERS)
        return Series(result.title, result.description, result.cover, finish(result.list), sourceName = host(url),
            session = session, forceBrowser = browser || rendered)
    }

    private fun pageImages(html: String, url: String): List<String> {
        val doc = Jsoup.parse(html, url)
        var scope: org.jsoup.select.Elements? = null
        for (sel in reader) {
            val el = doc.select(sel)
            if (el.select("img").size >= 2) { scope = el; break }
        }
        val urls = LinkedHashSet<String>()
        fun push(u: String?) { if (!u.isNullOrBlank() && u.startsWith("http") && !badImg.containsMatchIn(u)) urls.add(u) }

        val nodes = (scope ?: doc.select("body")).select("img, noscript")
        for (el in nodes) {
            if (el.tagName() == "noscript") {
                Jsoup.parse(el.html(), url).select("img").forEach { push(it.absUrl("src")) }
                continue
            }
            for (attr in lazyAttrs) {
                val v = el.attr(attr)
                if (v.isNotBlank() && !v.startsWith("data:")) { push(el.absUrl(attr)); break }
            }
        }
        if (urls.size < 2) {
            // Reader scripts that embed page lists as JSON.
            for (s in doc.select("script")) {
                for (m in scriptImg.findAll(s.data())) push(m.value.replace("\\/", "/"))
            }
        }
        return if (scope != null) urls.toList() else urls.filter { imgExt.containsMatchIn(it) }
    }

    suspend fun getPages(chapter: Chapter, session: Session?, forceBrowser: Boolean): List<String> {
        if (!forceBrowser) {
            try {
                val headers = session?.let { mapOf("Cookie" to it.cookie, "User-Agent" to it.userAgent) } ?: emptyMap()
                val r = Http.text(chapter.url, headers)
                val urls = pageImages(r.body, r.url)
                if (urls.size >= 2) return urls
            } catch (e: HttpStatusException) {
                if (!blocked(e.code)) throw e
            }
        }
        val r = BrowserRenderer.render(chapter.url, scroll = true)
        val urls = LinkedHashSet<String>()
        for (i in r.images) {
            if (i.src.isBlank() || i.skip || i.w < 300 || i.h < 200 || !i.src.startsWith("http")) continue
            urls.add(i.src)
        }
        if (urls.isEmpty()) urls.addAll(pageImages(r.html, r.url))
        if (urls.isEmpty()) throw SourceException(Err.NO_PAGES)
        return urls.toList()
    }
}
