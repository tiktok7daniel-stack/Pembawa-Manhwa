package app.mangavault.download

import android.os.SystemClock
import app.mangavault.source.Chapter
import app.mangavault.source.Series
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** SINGLE: one file · PER_CHAPTER: one file per chapter · SPLIT: files of at most [JobRequest.splitBytes]. */
enum class Mode { SINGLE, PER_CHAPTER, SPLIT }

/** Output file type. */
enum class Format(val ext: String) { CBZ("cbz"), PDF("pdf"), ZIP("zip") }
enum class CState { QUEUED, ACTIVE, DONE, PARTIAL, FAILED }
enum class JobStatus { RUNNING, DONE, ERROR, CANCELLED }

data class ChapterProgress(
    val chapter: Chapter, val state: CState = CState.QUEUED, val done: Int = 0, val total: Int = 0, val failedPages: Int = 0,
    /** Short reason shown under a failed chapter, e.g. "HTTP 403". */
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
    /** Image bytes received so far. */
    val bytes: Long = 0,
    val pagesDone: Int = 0,
    /** Pages known so far (grows as each chapter's page list is read). */
    val pagesTotal: Int = 0,
    /** [SystemClock.elapsedRealtime] at start / end. */
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val format: Format = Format.CBZ,
    /** Every file written (content URIs), e.g. all parts of a split download. */
    val files: List<String> = emptyList(),
) {
    /** 0..1 across all chapters; several chapters can be in flight at once. */
    val fraction: Float
        get() {
            if (status == JobStatus.DONE) return 1f
            if (chapters.isEmpty()) return 0f
            var sum = 0f
            for (c in chapters) {
                sum += when (c.state) {
                    CState.DONE, CState.PARTIAL, CState.FAILED -> 1f
                    CState.ACTIVE -> if (c.total > 0) 0.98f * c.done / c.total else 0f
                    CState.QUEUED -> 0f
                }
            }
            return minOf(0.99f, sum / chapters.size)
        }

    val percent: Int get() = if (status == JobStatus.DONE) 100 else (fraction * 100).toInt()
}

class JobRequest(val series: Series, val chapters: List<Chapter>, val mode: Mode, val format: Format = Format.CBZ, val splitBytes: Long = 10L shl 20,
    /** Auto mode: chapters per folder (0 = one folder for everything). */
    val batchSize: Int = 0)

/** Hands the (large) job request from the UI to the foreground service. */
object JobHolder {
    @Volatile var pending: JobRequest? = null
}

object DownloadState {
    val job = MutableStateFlow<JobState?>(null)

    fun start(title: String, chapters: List<Chapter>, mode: Mode, format: Format = Format.CBZ) {
        job.value = JobState(title, mode, JobStatus.RUNNING, chapters.map { ChapterProgress(it) }, startedAt = SystemClock.elapsedRealtime(), format = format)
    }

    fun chapter(idx: Int, f: (ChapterProgress) -> ChapterProgress) {
        job.update { j ->
            if (j == null || idx !in j.chapters.indices) j else j.copy(chapters = j.chapters.toMutableList().also { it[idx] = f(it[idx]) })
        }
    }

    /** One page finished (successfully or not) for chapter [idx]. */
    fun page(idx: Int, bytes: Long, failed: Boolean) {
        job.update { j ->
            if (j == null || idx !in j.chapters.indices) j
            else j.copy(
                bytes = j.bytes + bytes,
                pagesDone = j.pagesDone + 1,
                chapters = j.chapters.toMutableList().also { val c = it[idx]; it[idx] = c.copy(done = c.done + 1, failedPages = c.failedPages + if (failed) 1 else 0) },
            )
        }
    }

    /** A page that failed earlier came through on a later attempt. */
    fun recover(idx: Int, bytes: Long) {
        job.update { j ->
            if (j == null || idx !in j.chapters.indices) j
            else j.copy(bytes = j.bytes + bytes, chapters = j.chapters.toMutableList().also { val c = it[idx]; it[idx] = c.copy(failedPages = maxOf(0, c.failedPages - 1)) })
        }
    }

    fun update(f: (JobState) -> JobState) { job.update { if (it == null) null else f(it) } }

    fun finish(status: JobStatus, f: (JobState) -> JobState = { it }) {
        job.update { if (it == null) null else f(it).copy(status = status, finishedAt = SystemClock.elapsedRealtime()) }
    }
}
