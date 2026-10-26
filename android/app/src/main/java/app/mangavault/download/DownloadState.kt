package app.mangavault.download

import app.mangavault.source.Chapter
import app.mangavault.source.Series
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class Mode { SINGLE, PER_CHAPTER }
enum class CState { QUEUED, ACTIVE, DONE, PARTIAL, FAILED }
enum class JobStatus { RUNNING, DONE, ERROR, CANCELLED }

data class ChapterProgress(
    val chapter: Chapter,
    val state: CState = CState.QUEUED,
    val done: Int = 0,
    val total: Int = 0,
    val failedPages: Int = 0,
    val error: String? = null,
)

data class JobState(
    val title: String,
    val mode: Mode,
    val status: JobStatus,
    val chapters: List<ChapterProgress>,
    val doneChapters: Int = 0,
    val error: String? = null,
    val savedPath: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    private val finished get() = chapters.count { it.state == CState.DONE || it.state == CState.PARTIAL || it.state == CState.FAILED }
    val failed get() = chapters.count { it.state == CState.FAILED }
    val pagesDone get() = chapters.sumOf { it.done }

    /** Finished chapters plus the fraction of every chapter currently in flight (several run in parallel). */
    val percent: Int
        get() {
            if (status == JobStatus.DONE) return 100
            if (chapters.isEmpty()) return 0
            val partial = chapters.filter { it.state == CState.ACTIVE && it.total > 0 }.sumOf { it.done.toDouble() / it.total }
            return minOf(99, ((finished + partial) / chapters.size * 100).toInt())
        }

    val pagesPerSecond: Double
        get() {
            val secs = (System.currentTimeMillis() - startedAt) / 1000.0
            return if (secs < 1) 0.0 else pagesDone / secs
        }

    /** Seconds left, estimated from the average pages per chapter seen so far; null until there is data. */
    val etaSeconds: Long?
        get() {
            val known = chapters.filter { it.total > 0 }
            val speed = pagesPerSecond
            if (known.isEmpty() || speed <= 0.0) return null
            val avg = known.sumOf { it.total }.toDouble() / known.size
            val remaining = chapters.sumOf { c -> if (c.total > 0) c.total - c.done else if (c.state == CState.QUEUED) avg.toInt() else 0 }
            return (remaining / speed).toLong()
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

    @Volatile var lastRequest: JobRequest? = null

    fun chapter(idx: Int, f: (ChapterProgress) -> ChapterProgress) {
        job.update { j ->
            if (j == null) null else j.copy(chapters = j.chapters.toMutableList().also { it[idx] = f(it[idx]) })
        }
    }

    fun update(f: (JobState) -> JobState) { job.update { if (it == null) null else f(it) } }
}
