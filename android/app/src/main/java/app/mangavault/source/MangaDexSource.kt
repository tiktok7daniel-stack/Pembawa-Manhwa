package app.mangavault.source

import app.mangavault.net.Http
import org.json.JSONObject
import java.net.URL

object MangaDexSource {
    private const val API = "https://api.mangadex.org"
    private val idRe = Regex("/title/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})", RegexOption.IGNORE_CASE)

    fun matches(url: String): Boolean {
        val host = try { URL(url).host } catch (_: Exception) { return false }
        return Regex("(^|\\.)mangadex\\.org$", RegexOption.IGNORE_CASE).containsMatchIn(host) && idRe.containsMatchIn(url)
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    suspend fun getSeries(url: String, lang: String): Series {
        val id = idRe.find(url)!!.groupValues[1]
        val data = JSONObject(Http.text("$API/manga/$id?includes[]=cover_art").body).getJSONObject("data")
        val attrs = data.getJSONObject("attributes")
        val titles = attrs.getJSONObject("title")
        val title = titles.str("en").ifBlank { titles.str(lang) }.ifBlank { titles.keys().asSequence().firstOrNull()?.let { titles.str(it) } ?: "Manga" }
        val desc = attrs.optJSONObject("description")?.let { d -> d.str("en").ifBlank { d.str(lang) } } ?: ""

        var coverFile: String? = null
        val rels = data.optJSONArray("relationships")
        if (rels != null) for (i in 0 until rels.length()) {
            val r = rels.getJSONObject(i)
            if (r.optString("type") == "cover_art") coverFile = r.optJSONObject("attributes")?.str("fileName")?.ifBlank { null }
        }

        val byNumber = LinkedHashMap<String, Chapter>()
        var offset = 0
        while (offset < 10_000) {
            val q = "limit=500&offset=$offset&translatedLanguage[]=$lang&order[chapter]=asc" +
                "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic"
            val feed = JSONObject(Http.text("$API/manga/$id/feed?$q").body)
            val list = feed.getJSONArray("data")
            for (i in 0 until list.length()) {
                val c = list.getJSONObject(i)
                val a = c.getJSONObject("attributes")
                if (a.optInt("pages", 0) == 0 || a.str("externalUrl").isNotBlank()) continue
                val number = a.str("chapter").ifBlank { null }
                val key = number ?: "oneshot-${c.getString("id")}"
                if (!byNumber.containsKey(key)) byNumber[key] = Chapter(c.getString("id"), number, a.str("title"), "https://mangadex.org/chapter/${c.getString("id")}")
            }
            offset += 500
            if (offset >= feed.optInt("total", 0)) break
        }
        if (byNumber.isEmpty()) throw SourceException(Err.NO_CHAPTERS, lang)
        return Series(title, desc, coverFile?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }, byNumber.values.toList(), sourceName = "MangaDex")
    }

    suspend fun getPages(chapter: Chapter): List<String> {
        val d = JSONObject(Http.text("$API/at-home/server/${chapter.id}").body)
        val base = d.getString("baseUrl")
        val ch = d.getJSONObject("chapter")
        val hash = ch.getString("hash")
        val files = ch.getJSONArray("data")
        return (0 until files.length()).map { "$base/data/$hash/${files.getString(it)}" }
    }
}
