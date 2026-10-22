package app.mangavault.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class SavedInfo(val uri: String?, val path: String)

class OutputTarget(val stream: OutputStream, private val onFinish: () -> SavedInfo, private val onAbort: () -> Unit) {
    fun finish(): SavedInfo = onFinish()
    fun abort() = onAbort()
}

/** Creates files in Download/MangaVault/<series>/ (MediaStore on Android 10+, plain files before). */
object Storage {
    fun open(ctx: Context, folder: String, name: String): OutputTarget {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val rel = "${Environment.DIRECTORY_DOWNLOADS}/MangaVault/$folder"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, rel)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Cannot create file")
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open file")
            return OutputTarget(
                out,
                {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    SavedInfo(uri.toString(), "$rel/$name")
                },
                {
                    try { out.close() } catch (_: Exception) { }
                    try { resolver.delete(uri, null, null) } catch (_: Exception) { }
                },
            )
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MangaVault/$folder")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create folder")
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, name.substringBeforeLast('.') + " (${n++})." + name.substringAfterLast('.', "cbz"))
        val out = FileOutputStream(file)
        return OutputTarget(
            out,
            {
                MediaScannerConnection.scanFile(ctx, arrayOf(file.path), null, null)
                SavedInfo(null, file.path)
            },
            {
                try { out.close() } catch (_: Exception) { }
                file.delete()
            },
        )
    }
}
