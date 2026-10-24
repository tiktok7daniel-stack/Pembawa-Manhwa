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
import app.mangavault.download.CompressJob
import app.mangavault.download.CompressRequest
import app.mangavault.download.CompressService
import app.mangavault.download.CompressState
import app.mangavault.download.Compressor
import app.mangavault.download.FileKind
import app.mangavault.download.Format
import app.mangavault.download.Level
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
enum class Tab { HOME, DOWNLOADS, TOOLS, SETTINGS }

/** A file chosen for compression. */
class PickedFile(val uri: android.net.Uri, val name: String, val size: Long, val kind: FileKind)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var browserMode by mutableStateOf(Prefs.browserDefault.value)
    var loading by mutableStateOf(false)
    var errorKey by mutableStateOf<String?>(null)
    var errorDetail by mutableStateOf("")
    var series by mutableStateOf<Series?>(null)
    var selected by mutableStateOf(setOf<String>())
    var mode by mutableStateOf(Mode.SINGLE)
    /** Largest file allowed in "split by size" mode, in MB (20 fits a Claude project upload). */
    var splitMb by mutableStateOf("20")
    private val splitBytes: Long get() = (splitMb.toLongOrNull() ?: 20L).coerceIn(1, 4000) * 1048576L
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

    // ---------------------------------------------------------------- compression

    var picked by mutableStateOf<PickedFile?>(null)
    var pickError by mutableStateOf(false)
    var level by mutableStateOf(Level.BALANCED)
    var targetMb by mutableStateOf("20")
    /** Encode pages as WebP (PDF input is then saved as CBZ). */
    var webp by mutableStateOf(false)
    var estimate by mutableStateOf<app.mangavault.download.Estimate?>(null)
    var estimating by mutableStateOf(false)
    private var estimateJob: Job? = null

    fun chooseWebp(v: Boolean) {
        if (v == webp) return
        webp = v
        refreshEstimate()
    }

    /** Samples a few pages of the chosen file at each preset so the expected sizes show before compressing. */
    private fun refreshEstimate() {
        val f = picked ?: return
        val app = getApplication<Application>()
        estimateJob?.cancel()
        estimate = null
        estimating = true
        val useWebp = webp
        estimateJob = viewModelScope.launch {
            val e = withContext(Dispatchers.Default) { Compressor.estimate(app, f.uri, f.kind, f.size, useWebp) }
            estimate = e
            estimating = false
        }
    }

    fun langNow(): String = Prefs.lang.value

    /** Reads name and size of the chosen document and checks it really is a PDF or a zip (CBZ). */
    fun onPicked(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                var size = -1L
                try {
                    app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val s = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                        }
                    }
                } catch (_: Exception) { }
                if (size < 0) size = try { app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0 } catch (_: Exception) { 0 }
                val kind = Compressor.sniff(app, uri)
                if (kind == null) null else PickedFile(uri, name, size, kind)
            }
            pickError = f == null
            if (f != null) {
                picked = f
                webp = f.kind == FileKind.ZIP
                refreshEstimate()
            }
        }
    }

    /** False when a target size is needed but missing. */
    fun startCompress(): Boolean {
        val f = picked ?: return false
        if (CompressState.job.value?.status == JobStatus.RUNNING) return true
        val target = if (level == Level.TARGET) (targetMb.toLongOrNull() ?: 0) * 1048576L else 0
        if (level == Level.TARGET && target <= 0) return false
        CompressState.pending = CompressRequest(f.uri, f.name, f.size, f.kind, level, target, webp)
        CompressState.job.value = CompressJob(f.name, JobStatus.RUNNING, f.size)
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, CompressService::class.java))
        return true
    }

    fun cancelCompress() {
        val app = getApplication<Application>()
        app.startService(Intent(app, CompressService::class.java).setAction(CompressService.ACTION_CANCEL))
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
        JobHolder.pending = JobRequest(s, chosen, mode, format, splitBytes)
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
        val again = j.chapters.filter { it.state != CState.DONE }.map { it.chapter }
        val chosen = again.ifEmpty { j.chapters.map { it.chapter } }
        JobHolder.pending = JobRequest(s, chosen, j.mode, j.format, splitBytes)
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
