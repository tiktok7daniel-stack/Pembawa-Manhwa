package app.mangavault.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    val client: OkHttpClient = OkHttpClient.Builder()
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
            client.newCall(request(url, headers, method, body)).execute().use { r ->
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
