package app.mangavault.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class HistoryItem(val title: String, val path: String, val uri: String?, val chapters: Int, val time: Long, val files: Int)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _lang = MutableStateFlow("en")
    private val _theme = MutableStateFlow("dark")
    private val _browser = MutableStateFlow(false)
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val lang: StateFlow<String> = _lang
    val theme: StateFlow<String> = _theme
    val browserDefault: StateFlow<Boolean> = _browser
    val history: StateFlow<List<HistoryItem>> = _history

    fun init(c: Context) {
        sp = c.getSharedPreferences("mangavault", Context.MODE_PRIVATE)
        _lang.value = sp.getString("lang", null) ?: if (Locale.getDefault().language == "ar") "ar" else "en"
        _theme.value = sp.getString("theme", "dark") ?: "dark"
        _browser.value = sp.getBoolean("browser", false)
        _history.value = try {
            val arr = JSONArray(sp.getString("history", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryItem(o.getString("title"), o.getString("path"), o.optString("uri").ifBlank { null }, o.optInt("chapters"), o.optLong("time"), o.optInt("files"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setLang(v: String) { _lang.value = v; sp.edit().putString("lang", v).apply() }
    fun setTheme(v: String) { _theme.value = v; sp.edit().putString("theme", v).apply() }
    fun setBrowser(v: Boolean) { _browser.value = v; sp.edit().putBoolean("browser", v).apply() }

    fun addHistory(item: HistoryItem) {
        val list = (listOf(item) + _history.value).take(50)
        _history.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("title", it.title).put("path", it.path).put("uri", it.uri ?: "").put("chapters", it.chapters).put("time", it.time).put("files", it.files))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() { _history.value = emptyList(); sp.edit().remove("history").apply() }
}
