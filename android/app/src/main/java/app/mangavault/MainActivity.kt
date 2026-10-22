package app.mangavault

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Thin native shell around the MangaVault web app: stores the server address, keeps navigation
 * inside that server, and hands file downloads to the system DownloadManager so they land in
 * Downloads/MangaVault/<series>/.
 */
class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private var web: WebView? = null
    private var loadedServer: String? = null
    private var pendingDownload: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("mangavault", MODE_PRIVATE)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent?) {
        val server = prefs.getString(KEY_SERVER, null)
        if (server == null || intent?.action == ACTION_CHANGE_SERVER) showSetup(server) else showWeb(server)
    }

    // ---------- setup screen ----------

    private fun showSetup(current: String?) {
        web?.destroy()
        web = null
        loadedServer = null

        val pad = dp(24)
        val title = TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val desc = TextView(this).apply {
            text = getString(R.string.setup_desc)
            setTextColor(Color.parseColor("#9AA0BD"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }
        val input = EditText(this).apply {
            hint = getString(R.string.setup_hint)
            setText(current ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7090"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val save = Button(this).apply {
            text = getString(R.string.setup_save)
            setOnClickListener {
                val url = normalize(input.text.toString())
                if (url == null) {
                    Toast.makeText(this@MainActivity, R.string.setup_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString(KEY_SERVER, url).apply()
                    showWeb(url)
                }
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#090A12"))
            addView(title)
            addView(desc)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(column)
    }

    /** "192.168.1.5:3000" -> http://..., "example.com" -> https://..., returns null if unusable. */
    private fun normalize(raw: String): String? {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return null
        if (!s.contains("://")) {
            val local = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$").matches(s) || s.substringBefore(':').endsWith(".local")
            s = (if (local) "http://" else "https://") + s
        }
        val uri = Uri.parse(s)
        return if (URLUtil.isNetworkUrl(s) && !uri.host.isNullOrEmpty()) s else null
    }

    // ---------- web screen ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWeb(server: String) {
        if (web != null && loadedServer == server) return
        web?.destroy()
        loadedServer = server

        val view = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090A12"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    when {
                        uri.scheme == "mv" -> {
                            if (uri.host == "retry") v.loadUrl(server) else showSetup(server)
                            return true
                        }
                        uri.host == Uri.parse(server).host -> return false
                        else -> {
                            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                            return true
                        }
                    }
                }

                override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) v.loadDataWithBaseURL(null, errorPage(), "text/html", "utf-8", null)
                }
            }
            setDownloadListener { url, _, contentDisposition, mime, _ -> startDownload(url, contentDisposition, mime) }
            loadUrl(server)
        }
        web = view
        setContentView(FrameLayout(this).apply { addView(view) })
    }

    private fun errorPage(): String {
        val css = "body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;" +
            "background:#090a12;color:#f3f4fb;font-family:sans-serif;text-align:center;padding:24px}" +
            "p{color:#9aa0bd}a{display:inline-block;margin:6px;padding:12px 22px;border-radius:14px;background:#7c6cff;color:#fff;text-decoration:none}"
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>$css</style></head><body>" +
            "<h2>${getString(R.string.error_title)}</h2><p>${getString(R.string.error_desc)}</p>" +
            "<div><a href='mv://retry'>${getString(R.string.retry)}</a><a href='mv://change'>${getString(R.string.change_server)}</a></div></body></html>"
    }

    // ---------- downloads ----------

    private fun startDownload(url: String, contentDisposition: String?, mime: String?) {
        val action = {
            try {
                val name = fileName(url, contentDisposition, mime)
                val folder = sanitize(name.substringBeforeLast('.'))
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MangaVault/$folder/$name")
                if (!mime.isNullOrEmpty()) request.setMimeType(mime)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, getString(R.string.download_started, name), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = action
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
        } else {
            action()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pendingDownload?.invoke()
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            pendingDownload = null
        }
    }

    /** Prefers the RFC 5987 filename* (keeps Arabic titles intact) over the ASCII fallback. */
    private fun fileName(url: String, contentDisposition: String?, mime: String?): String {
        val star = contentDisposition?.let { Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val raw = if (star != null) Uri.decode(star) else URLUtil.guessFileName(url, contentDisposition, mime)
        return sanitize(raw).ifEmpty { "manga.cbz" }
    }

    private fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), " ").replace(Regex("\\s+"), " ").trim().take(100)

    // ---------- misc ----------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_SERVER = "server"
        private const val ACTION_CHANGE_SERVER = "app.mangavault.CHANGE_SERVER"
        private const val REQ_STORAGE = 42
    }
}
