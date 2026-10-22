package app.mangavault.source

data class Chapter(val id: String, val number: String?, val title: String, val url: String)

data class Session(val cookie: String, val userAgent: String)

data class Series(
    val title: String,
    val description: String,
    val cover: String?,
    val chapters: List<Chapter>,
    val sourceUrl: String = "",
    val sourceName: String = "",
    val session: Session? = null,
    val forceBrowser: Boolean = false,
)

enum class Err { BAD_URL, NO_CHAPTERS, NO_PAGES, NETWORK, HTTP, BROWSER, BLOCKED }

class SourceException(val code: Err, val detail: String = "") : Exception("$code $detail")
