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

/** Same UA as the hidden renderer, so a clearance cookie earned in the visible browser stays valid. */
const val BROWSER_UA = Http.UA

class RenderedImage(val src: String, val w: Int, val h: Int, val skip: Boolean)
class RenderResult(val html: String, val url: String, val images: List<RenderedImage>, val cookie: String, val snapshots: List<String> = emptyList())

/** Loads a page in Android's built-in browser engine, scrolls it so lazy images load, and returns what was rendered. */
object BrowserRenderer {
    private val mutex = Mutex()
    private val challenge = Regex("just a moment|attention required|checking your browser|verify you are human|ddos-guard", RegexOption.IGNORE_CASE)

    private const val JS_EXPAND = "(function(){var re=/^(show all|show more|load more|view all|view more|more chapters|see all|see more|all chapters|expand|load more chapters|عرض الكل|عرض المزيد|المزيد|تحميل المزيد|كل الفصول|اظهار المزيد|إظهار المزيد)\\b/i;" +
        "var els=[].slice.call(document.querySelectorAll('button,a,div[role=button],span,li'));" +
        "for(var i=0;i<els.length;i++){var e=els[i];var t=(e.textContent||'').trim();" +
        "if(t.length<40&&re.test(t)&&e.offsetParent!==null&&!e.disabled){e.click();return 'y';}}return 'n';})()"
    private const val JS_NEXT = "(function(){var re=/^(next|next page|older|older chapters|›|»|>|→|التالي|الصفحة التالية)\\s*[›»>→]?$/i;" +
        "var els=[].slice.call(document.querySelectorAll('a,button,li,span'));for(var i=0;i<els.length;i++){var e=els[i];" +
        "var t=(e.textContent||'').trim();var lab=(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('rel')||'')+' '+(e.getAttribute('title')||'')+' '+(typeof e.className==='string'?e.className:'');" +
        "if((re.test(t)||(t.length<3&&/\\bnext\\b/i.test(lab))||/^next$/i.test(e.getAttribute('rel')||''))&&e.offsetParent!==null&&!e.disabled&&!/disabled/i.test(lab)){e.click();return 'y';}}return 'n';})()"
    private const val JS_LINKS = "String(document.querySelectorAll('a[href]').length)"
    private const val JS_RANGES = "(function(){return String([].slice.call(document.querySelectorAll('a,button,li,span,div[role=tab],option'))" +
        ".filter(function(e){return /^\\s*\\d+\\s*[-\u2013~]\\s*\\d+\\s*$/.test(e.textContent||'')&&e.children.length===0;}).length);})()"
    private const val JS_RANGE_CLICK = "(function(i){var els=[].slice.call(document.querySelectorAll('a,button,li,span,div[role=tab],option'))" +
        ".filter(function(e){return /^\\s*\\d+\\s*[-\u2013~]\\s*\\d+\\s*$/.test(e.textContent||'')&&e.children.length===0;});" +
        "var e=els[i];if(!e)return 'n';if(e.tagName==='OPTION'){var s=e.parentElement;s.value=e.value;s.dispatchEvent(new Event('change',{bubbles:true}));}else{e.click();}return 'y';})"
    private const val JS_HTML = "document.documentElement.outerHTML"
    private const val JS_SCROLL = "(function(){window.scrollBy(0,Math.max(600,innerHeight*0.9));return String(document.documentElement.scrollHeight);})()"
    private const val JS_BOTTOM = "(function(){return String(innerHeight+scrollY>=document.documentElement.scrollHeight-4);})()"
    private const val JS_IMAGES = "JSON.stringify([].slice.call(document.images).map(function(i){return {s:i.currentSrc||i.src||i.getAttribute('data-src')||''," +
        "w:i.naturalWidth,h:i.naturalHeight,k:!!i.closest('header,footer,nav,aside,.sidebar,.related,.recommend,.comments,#comments,.ads,[class*=advert]')};}))"

    suspend fun render(url: String, expand: Boolean = false, scroll: Boolean = true, paginate: Boolean = false): RenderResult = mutex.withLock {
        val host = BrowserHost.container ?: throw SourceException(Err.BROWSER)
        try {
            withTimeout(if (paginate) 480_000L else 100_000L) { withContext(Dispatchers.Main) { run(host, url, expand, scroll, paginate) } }
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
    private suspend fun run(host: FrameLayout, url: String, expand: Boolean, scroll: Boolean, paginate: Boolean): RenderResult {
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
            // Still a human-verification page: the user has to pass it once in the visible browser.
            if (challenge.containsMatchIn(web.js("document.title") ?: "")) throw SourceException(Err.BLOCKED, url)
            if (expand) {
                // "Load more" lists add a batch per click: keep clicking until the page stops growing.
                var links = web.js(JS_LINKS)?.toIntOrNull() ?: 0
                var still = 0
                for (i in 0 until 150) {
                    if (web.js(JS_EXPAND) != "y") break
                    delay(700)
                    val now = web.js(JS_LINKS)?.toIntOrNull() ?: 0
                    if (now <= links) { if (++still >= 3) break } else still = 0
                    links = now
                }
            }
            if (scroll) {
                var last = ""
                for (i in 0 until 80) {
                    val h = web.js(JS_SCROLL) ?: ""
                    delay(150)
                    val bottom = web.js(JS_BOTTOM) == "true"
                    if (bottom && h == last) break
                    last = h
                }
                delay(600)
            }
            // Chapter lists split into pages or "1-100" tabs: open each one and keep a copy of what it shows.
            val snapshots = ArrayList<String>()
            if (paginate) {
                snapshots.add(web.js(JS_HTML) ?: "")
                val ranges = web.js(JS_RANGES)?.toIntOrNull() ?: 0
                for (i in 0 until minOf(ranges, 40)) {
                    if (web.js("$JS_RANGE_CLICK($i)") != "y") break
                    delay(1300)
                    snapshots.add(web.js(JS_HTML) ?: "")
                }
                var lastHtml = snapshots.lastOrNull() ?: ""
                for (i in 0 until 150) {
                    if (web.js(JS_NEXT) != "y") break
                    delay(1500)
                    val html = web.js(JS_HTML) ?: break
                    if (html == lastHtml) break
                    snapshots.add(html)
                    lastHtml = html
                }
            }
            val images = ArrayList<RenderedImage>()
            val arr = JSONArray(web.js(JS_IMAGES) ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                images.add(RenderedImage(o.optString("s"), o.optInt("w"), o.optInt("h"), o.optBoolean("k")))
            }
            val html = web.js("document.documentElement.outerHTML") ?: ""
            val finalUrl = web.url ?: url
            return RenderResult(html, finalUrl, images, CookieManager.getInstance().getCookie(finalUrl) ?: "", snapshots)
        } finally {
            host.removeView(web)
            web.stopLoading()
            web.destroy()
        }
    }
}
