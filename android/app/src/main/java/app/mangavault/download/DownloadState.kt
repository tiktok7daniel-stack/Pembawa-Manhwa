package app.mangavault.download

import app.mangavault.source.Chapter
import app.mangavault.source.Series
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class Mode { SINGLE, PER_CHAPTER }
enum class CState { QUEUED, ACTIVE, DONE, PARTIAL, FAILED }
enum class JobStatus { RUNNING, DONE, ERROR, CANCELLED }

data class ChapterProgress(val chapter: Chapter, val state: CState = CState.QUEUED, val done: Int = 0, val total: Int = 0, val failedPages: Int = 0)

data class JobState(
    val title: String,
    val mode: Mode,
    val status: JobStatus,
    val chapters: List<ChapterProgress>,
    val doneChapters: Int = 0,
    val error: String? = null,
    val savedPath: String? = null,
) {
    val percent: Int
        get() {
            if (status == JobStatus.DONE) return 100
            if (chapters.isEmpty()) return 0
            val active = chapters.firstOrNull { it.state == CState.ACTIVE }
            val partial = if (active != null && active.total > 0) active.done.toFloat() / active.total else 0f
            return minOf(99, ((doneChapters + partial) / chapters.size * 100).toInt())
        }
}

class JobRequest(val series: Series, val chapters: List<Chapter>, val mode: Mode)

/** Hands the (large) job request from the UI to the foreground service. */
object JobHolder {
    @Volatile var pending: JobRequest? = null
}

object DownloadState {
    val job = MutableStateFlow<JobState?>(null)

    fun start(title: String, chapters: List<Chapter>, mode: Mode) {
        job.value = JobState(title, mode, JobStatus.RUNNING, chapters.map { ChapterProgress(it) })
    }

    fun chapter(idx: Int, f: (ChapterProgress) -> ChapterProgress) {
        job.update { j ->
            if (j == null) null else j.copy(chapters = j.chapters.toMutableList().also { it[idx] = f(it[idx]) })
        }
    }

    fun update(f: (JobState) -> JobState) { job.update { if (it == null) null else f(it) } }
}
