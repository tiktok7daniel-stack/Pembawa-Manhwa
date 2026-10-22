package app.mangavault.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpStatusException(val code: Int) : IOException("HTTP $code")

class TextResult(val body: String, val url: String)
class BytesResult(val bytes: ByteArray, val contentType: String)

object Http {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** Keeps cookies a site sets on the series page for its chapter and image requests. */
    private object MemoryCookies : CookieJar {
        private val store = HashMap<String, MutableList<Cookie>>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { c -> list.removeAll { it.name == c.name }; list.add(c) }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.filterKeys { url.host == it || url.host.endsWith(".$it") }.values.flatten().filter { it.expiresAt > now && it.matches(url) }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookies)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun request(url: String, headers: Map<String, String>, method: String, body: RequestBody?): Request {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "*/*")
        headers.forEach { (k, v) -> b.header(k, v) }
        val payload = if (method == "GET") null else body ?: ByteArray(0).toRequestBody()
        return b.method(method, payload).build()
    }

    suspend fun text(url: String, headers: Map<String, String> = emptyMap(), method: String = "GET", body: RequestBody? = null): TextResult =
        withContext(Dispatchers.IO) {
            val h = mapOf("Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8", "Accept-Language" to "en-US,en;q=0.9,ar;q=0.8") + headers
            client.newCall(request(url, h, method, body)).execute().use { r ->
                if (!r.isSuccessful) throw HttpStatusException(r.code)
                TextResult(r.body?.string() ?: "", r.request.url.toString())
            }
        }

    suspend fun bytes(url: String, headers: Map<String, String> = emptyMap()): BytesResult =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers, "GET", null)).execute().use { r ->
                if (!r.isSuccessful) throw HttpStatusException(r.code)
                BytesResult(r.body?.bytes() ?: ByteArray(0), r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() ?: "")
            }
        }
}
