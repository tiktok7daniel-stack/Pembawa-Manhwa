package app.mangavault.source

import app.mangavault.net.HttpStatusException
import java.io.IOException
import java.net.URL

object Sources {
    /** Accepts a bare link or any shared text that contains one ("Read X at https://..."). */
    fun normalize(raw: String): String {
        val text = raw.trim()
        val found = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).find(text)?.value
        var s = (found ?: text.takeIf { !it.contains(' ') && it.contains('.') } ?: throw SourceException(Err.BAD_URL))
            .trimEnd('.', ',', ')', ']', '!', '?', '"', '\'')
        if (!s.contains("://")) s = "https://$s"
        val u = try { URL(s) } catch (_: Exception) { throw SourceException(Err.BAD_URL) }
        if (u.protocol != "http" && u.protocol != "https" || u.host.isNullOrBlank() || !(u.host.contains('.') || u.host == "localhost")) throw SourceException(Err.BAD_URL)
        return s
    }

    private inline fun <T> mapErrors(block: () -> T): T {
        try {
            return block()
        } catch (e: SourceException) { throw e
        } catch (e: HttpStatusException) { throw SourceException(Err.HTTP, e.code.toString())
        } catch (e: IOException) { throw SourceException(Err.NETWORK)
        } catch (e: IllegalArgumentException) { throw SourceException(Err.BAD_URL) }
    }

    suspend fun load(raw: String, lang: String, browser: Boolean): Series {
        val url = normalize(raw)
        return mapErrors {
            if (MangaDexSource.matches(url)) MangaDexSource.getSeries(url, lang).copy(sourceUrl = url)
            else GenericSource.getSeries(url, browser).copy(sourceUrl = url)
        }
    }

    /** [browser] = read the chapter in the real browser engine (slower, but sees exactly what a reader sees). */
    suspend fun pages(series: Series, chapter: Chapter, browser: Boolean = series.forceBrowser): List<String> = mapErrors {
        if (series.sourceName == "MangaDex") MangaDexSource.getPages(chapter)
        else GenericSource.getPages(chapter, series.session, browser)
    }

    fun isMangaDex(series: Series) = series.sourceName == "MangaDex"

    fun referer(series: Series): String =
        if (series.sourceName == "MangaDex") "https://mangadex.org/"
        else try { URL(series.sourceUrl).let { "${it.protocol}://${it.host}/" } } catch (_: Exception) { "" }
}
