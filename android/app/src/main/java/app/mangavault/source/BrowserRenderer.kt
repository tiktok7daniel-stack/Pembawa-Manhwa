package app.mangavault.source

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import app.mangavault.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume

/** A full-size, nearly transparent container owned by the activity; WebViews live here so they really render. */
object BrowserHost {
    @Volatile var container: FrameLayout? = null
}

class RenderedImage(val src: String, val w: Int, val h: Int, val skip: Boolean)
class RenderResult(val html: String, val url: String, val images: List<RenderedImage>, val cookie: String)

/** Loads a page in Android's built-in browser engine, scrolls it so lazy images load, and returns what was rendered. */
object BrowserRenderer {
    private val mutex = Mutex()
    private val challenge = Regex("just a moment|attention required|checking your browser|verify you are human|ddos-guard", RegexOption.IGNORE_CASE)

    private const val JS_EXPAND = "(function(){var re=/show all|load more|view all|more chapters|see all|عرض الكل|المزيد|تحميل المزيد|كل الفصول/i;" +
        "var els=[].slice.call(document.querySelectorAll('button,a,div[role=button],span'));" +
        "for(var i=0;i<els.length;i++){var e=els[i];var t=(e.textContent||'').trim();" +
        "if(t.length<40&&re.test(t)&&e.offsetParent!==null){e.click();return 'y';}}return 'n';})()"
    private const val JS_SCROLL = "(function(){window.scrollBy(0,Math.max(600,innerHeight*0.9));return String(document.documentElement.scrollHeight);})()"
    private const val JS_BOTTOM = "(function(){return String(innerHeight+scrollY>=document.documentElement.scrollHeight-4);})()"
    private const val JS_IMAGES = "JSON.stringify([].slice.call(document.images).map(function(i){return {s:i.currentSrc||i.src||i.getAttribute('data-src')||''," +
        "w:i.naturalWidth,h:i.naturalHeight,k:!!i.closest('header,footer,nav,aside,.sidebar,.related,.recommend,.comments,#comments,.ads,[class*=advert]')};}))"

    suspend fun render(url: String, expand: Boolean = false, scroll: Boolean = true): RenderResult = mutex.withLock {
        val host = BrowserHost.container ?: throw SourceException(Err.BROWSER)
        try {
            withTimeout(100_000L) { withContext(Dispatchers.Main) { run(host, url, expand, scroll) } }
        } catch (e: TimeoutCancellationException) {
            throw SourceException(Err.BROWSER, "timeout")
        }
    }

    private fun decode(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try { JSONTokener(raw).nextValue()?.toString() } catch (_: Exception) { null }
    }

    private suspend fun WebView.js(script: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { raw -> if (cont.isActive) cont.resume(decode(raw)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(host: FrameLayout, url: String, expand: Boolean, scroll: Boolean): RenderResult {
        val web = WebView(host.context)
        val loaded = CompletableDeferred<Unit>()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = Http.UA
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { loaded.complete(Unit) }
        }
        host.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            web.loadUrl(url)
            withTimeoutOrNull(30_000) { loaded.await() }
            delay(1200)
            for (i in 0 until 15) { // wait out simple "checking your browser" screens
                if (challenge.containsMatchIn(web.js("document.title") ?: "")) delay(1000) else break
            }
            if (expand) {
                for (i in 0 until 6) {
                    if (web.js(JS_EXPAND) != "y") break
                    delay(900)
                }
            }
            if (scroll) {
                var last = ""
                for (i in 0 until 80) {
                    val h = web.js(JS_SCROLL) ?: ""
                    delay(250)
                    val bottom = web.js(JS_BOTTOM) == "true"
                    if (bottom && h == last) break
                    last = h
                }
                delay(800)
            }
            val images = ArrayList<RenderedImage>()
            val arr = JSONArray(web.js(JS_IMAGES) ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                images.add(RenderedImage(o.optString("s"), o.optInt("w"), o.optInt("h"), o.optBoolean("k")))
            }
            val html = web.js("document.documentElement.outerHTML") ?: ""
            val finalUrl = web.url ?: url
            return RenderResult(html, finalUrl, images, CookieManager.getInstance().getCookie(finalUrl) ?: "")
        } finally {
            host.removeView(web)
            web.stopLoading()
            web.destroy()
        }
    }
}
