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

    private val pageParam = Regex("[?&](page|p|pg|paged|chapter_page|cp|start)=(\\d+)", IC)
    private val pagePath = Regex("/page/\\d+/?$", IC)
    private val rangeText = Regex("^\\s*\\d+\\s*[-–~]\\s*\\d+\\s*$")
    private val notFound = Regex("404|not found|page not found|غير موجود|الصفحة غير", IC)

    /** Links to further pages of the same chapter list: ?page=2, /page/2, "Next", "1-100" range tabs/selects. */
    private fun listPages(doc: Document, base: String): List<String> {
        val out = LinkedHashSet<String>()
        val basePath = try { URL(base).path.trimEnd('/') } catch (_: Exception) { return emptyList() }
        doc.selectFirst(NEXT)?.absUrl("href")?.let { if (it.isNotBlank()) out.add(it) }
        for (el in doc.select("a[href], option[value]")) {
            val isOption = el.tagName() == "option"
            val link = if (isOption) el.absUrl("value") else el.absUrl("href")
            if (!link.startsWith("http") || !sameSite(link, base)) continue
            val path = try { URL(link).path.trimEnd('/') } catch (_: Exception) { continue }
            if (!path.startsWith(basePath)) continue
            val paged = pageParam.containsMatchIn(link) || pagePath.containsMatchIn(path)
            val range = rangeText.matches(el.text())
            if (paged || (range && path != basePath) || (isOption && range)) out.add(link.substringBefore('#'))
        }
        return out.toList()
    }

    /** Follows every page of a paginated chapter list (breadth-first, capped). */
    private suspend fun crawlListPages(first: Found, firstUrl: String, headers: Map<String, String>): List<Raw> {
        var list = first.list
        val visited = mutableSetOf(firstUrl.substringBefore('#'))
        val queue = ArrayDeque(listPages(first.doc, firstUrl))
        var fetched = 0
        while (queue.isNotEmpty() && fetched < 60) {
            val next = queue.removeFirst()
            if (!visited.add(next)) continue
            val p = try { Http.text(next, headers) } catch (_: Exception) { continue }
            fetched++
            val more = readSeries(p.body, p.url)
            list = list + more.list
            listPages(more.doc, firstUrl).forEach { if (it !in visited) queue.addLast(it) }
        }
        return list
    }

    private fun wholeNumber(r: Raw): Int? = r.number?.toDoubleOrNull()?.takeIf { it == Math.floor(it) && it >= 0 }?.toInt()

    /** True when the list visibly starts after chapter 1, i.e. older chapters are hidden somewhere. */
    private fun looksIncomplete(list: List<Raw>): Boolean {
        val nums = list.mapNotNull { wholeNumber(it) }
        return nums.size >= 2 && nums.min() > 1
    }

    private suspend fun exists(url: String, headers: Map<String, String>): Boolean = try {
        val r = Http.text(url, headers)
        val samePath = URL(r.url).path.trimEnd('/') == URL(url).path.trimEnd('/')
        samePath && !notFound.containsMatchIn(Jsoup.parse(r.body).title())
    } catch (_: Exception) { false }

    /**
     * Chapter links usually follow one pattern (".../chapter-150/"). When the site only lists some of them,
     * rebuild the missing ones from that pattern, after checking that a few of them really exist.
     */
    private suspend fun fillGaps(list: List<Raw>, headers: Map<String, String>): List<Raw> {
        val numbered = list.filter { wholeNumber(it) != null }
        if (numbered.size < 2) return list
        fun template(r: Raw): String? {
            val n = wholeNumber(r).toString()
            val hits = Regex("(?<!\\d)" + n + "(?!\\d)").findAll(r.href).toList()
            if (hits.size != 1) return null
            val m = hits[0].range
            return r.href.substring(0, m.first) + "{n}" + r.href.substring(m.last + 1)
        }
        val best = numbered.mapNotNull { template(it) }.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return list
        if (best.value < 2 || best.value < numbered.size * 0.8) return list
        val have = numbered.mapNotNull { wholeNumber(it) }.toSet()
        val missing = (1 until have.max()).filter { it !in have }
        if (missing.isEmpty() || missing.size > 5000) return list
        val probes = listOf(missing.first(), missing[missing.size / 2], missing.last()).distinct()
        for (n in probes) if (!exists(best.key.replace("{n}", n.toString()), headers)) return list
        return list + missing.map { Raw(best.key.replace("{n}", it.toString()), "", it.toString()) }
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
                f.list = list
                f.list = crawlListPages(f, r.url, emptyMap())
                if (looksIncomplete(f.list)) f.list = fillGaps(f.list, emptyMap())
                found = f
            } catch (e: HttpStatusException) {
                if (!blocked(e.code)) throw e
            }
        }

        // Too little found, blocked, or older chapters still hidden: use the real (WebView) browser, which also
        // clicks "Next" / range tabs and collects every list it sees.
        val current = found
        if (current == null || current.list.size < 2 || looksIncomplete(current.list)) {
            val r = try {
                BrowserRenderer.render(url, expand = true, paginate = true)
            } catch (e: SourceException) {
                // Keep a usable plain-fetch result rather than failing the whole parse.
                if (current != null && current.list.size >= 2) null else throw e
            }
            if (r != null) {
                var merged = current?.list ?: emptyList()
                var title: Found? = null
                for (html in r.snapshots + r.html) {
                    val snap = readSeries(html, r.url)
                    if (title == null) title = snap
                    merged = merged + snap.list
                }
                val headers = mapOf("Cookie" to r.cookie, "User-Agent" to Http.UA)
                if (looksIncomplete(merged)) merged = fillGaps(merged, headers)
                val base = current ?: title!!
                if (current == null || merged.size > current.list.size) {
                    base.list = merged
                    found = base
                    session = Session(r.cookie, Http.UA)
                    rendered = true
                }
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
