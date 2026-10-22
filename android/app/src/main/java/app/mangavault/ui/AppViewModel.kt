package app.mangavault.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mangavault.data.Prefs
import app.mangavault.download.CState
import app.mangavault.download.DownloadService
import app.mangavault.download.DownloadState
import app.mangavault.download.JobHolder
import app.mangavault.download.JobRequest
import app.mangavault.download.JobStatus
import app.mangavault.download.Format
import app.mangavault.download.Mode
import app.mangavault.source.Chapter
import app.mangavault.source.Err
import app.mangavault.source.Series
import app.mangavault.source.SourceException
import app.mangavault.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { MAIN, RESULT, PROGRESS }
enum class Tab { HOME, DOWNLOADS, SETTINGS }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var browserMode by mutableStateOf(Prefs.browserDefault.value)
    var loading by mutableStateOf(false)
    var errorKey by mutableStateOf<String?>(null)
    var errorDetail by mutableStateOf("")
    var series by mutableStateOf<Series?>(null)
    var selected by mutableStateOf(setOf<String>())
    var mode by mutableStateOf(Mode.SINGLE)
    var format by mutableStateOf(Format.values().firstOrNull { it.name == Prefs.format.value } ?: Format.CBZ)
    var screen by mutableStateOf(Screen.MAIN)
    var tab by mutableStateOf(Tab.HOME)
    var verifyUrl by mutableStateOf<String?>(null)
    var showVerify by mutableStateOf(false)
    private var parseJob: Job? = null

    fun parse() {
        if (loading) return
        errorKey = null
        verifyUrl = null
        loading = true
        parseJob = viewModelScope.launch {
            try {
                // HTML parsing is CPU work; keep it off the UI thread (network already runs on IO).
                val s = withContext(Dispatchers.Default) { Sources.load(url, Prefs.lang.value, browserMode || Prefs.browserDefault.value) }
                series = s
                selected = s.chapters.map { it.id }.toSet()
                screen = Screen.RESULT
            } catch (e: SourceException) {
                errorKey = "err_${e.code.name}"
                errorDetail = if (e.code == Err.HTTP) e.detail else ""
                if (e.code == Err.BLOCKED) verifyUrl = e.detail.ifBlank { url }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorKey = "err_NETWORK"
            } finally {
                loading = false
            }
        }
    }

    fun pickFormat(f: Format) { format = f; Prefs.setFormat(f.name) }

    fun cancelParse() { parseJob?.cancel(); loading = false }

    /** Called after the user passed the site's check in the visible browser. */
    fun verified() {
        showVerify = false
        browserMode = true
        parse()
    }

    fun selectFirst(n: Int) { selected = series?.chapters?.take(n)?.map { it.id }?.toSet() ?: emptySet() }
    fun selectLast(n: Int) { selected = series?.chapters?.takeLast(n)?.map { it.id }?.toSet() ?: emptySet() }

    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun selectAll() { selected = series?.chapters?.map { it.id }?.toSet() ?: emptySet() }
    fun selectNone() { selected = emptySet() }

    fun selectRange(from: Double?, to: Double?) {
        val s = series ?: return
        selected = s.chapters.mapIndexedNotNull { i, c ->
            val n = c.number?.toDoubleOrNull() ?: (i + 1).toDouble()
            if ((from == null || n >= from) && (to == null || n <= to)) c.id else null
        }.toSet()
    }

    val isRunning: Boolean get() = DownloadState.job.value?.status == JobStatus.RUNNING

    /** Returns false when it could not start (nothing selected / another job running). */
    fun startDownload(): Boolean {
        val s = series ?: return false
        if (isRunning) { screen = Screen.PROGRESS; return true }
        val chosen: List<Chapter> = s.chapters.filter { it.id in selected }
        if (chosen.isEmpty()) return false
        JobHolder.pending = JobRequest(s, chosen, mode, format)
        DownloadState.start(s.title, chosen, mode, format)
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        screen = Screen.PROGRESS
        return true
    }

    /** Re-runs only the chapters that failed (or everything that did not finish, after an error/cancel). */
    fun retryFailed() {
        val s = series ?: return
        if (isRunning) return
        val j = DownloadState.job.value ?: return
        val again = j.chapters.filter { it.state == CState.FAILED || it.state == CState.QUEUED || it.state == CState.ACTIVE }.map { it.chapter }
        val chosen = again.ifEmpty { j.chapters.map { it.chapter } }
        JobHolder.pending = JobRequest(s, chosen, j.mode, j.format)
        DownloadState.start(s.title, chosen, j.mode, j.format)
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        screen = Screen.PROGRESS
    }

    /** Back to an empty home screen for the next series. */
    fun newDownload() {
        screen = Screen.MAIN
        tab = Tab.HOME
        url = ""
        errorKey = null
    }

    fun cancelDownload() {
        val app = getApplication<Application>()
        app.startService(Intent(app, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL))
    }

    fun back() {
        when (screen) {
            Screen.PROGRESS -> screen = if (series != null) Screen.RESULT else Screen.MAIN
            Screen.RESULT -> screen = Screen.MAIN
            Screen.MAIN -> Unit
        }
    }
}

fun chapterLabel(c: Chapter, word: String): String {
    val n = c.number ?: return c.title.ifBlank { "—" }
    if (c.title.isNotBlank() && Regex("(?<!\\d\\.)(?<!\\d)" + Regex.escape(n) + "(?!\\d)").containsMatchIn(c.title)) return c.title
    return "$word $n" + if (c.title.isNotBlank()) " · ${c.title}" else ""
}
