package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Utility helper for copying subtitle text to clipboard,
 * saving / downloading .srt and other subtitle formats to device storage,
 * and sharing subtitle files across apps.
 */
object SubtitleExportHelper {
    private const val TAG = "SubtitleExportHelper"

    fun sanitizeFileName(title: String?, fallback: String = "Subtitles", extension: String = "srt"): String {
        val base = if (title.isNullOrBlank()) fallback else title.trim()
        val clean = base.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        val withoutExt = if (clean.endsWith(".srt", ignoreCase = true) ||
            clean.endsWith(".vtt", ignoreCase = true) ||
            clean.endsWith(".ass", ignoreCase = true) ||
            clean.endsWith(".txt", ignoreCase = true)) {
            clean.substringBeforeLast('.')
        } else {
            clean
        }
        val safeBase = withoutExt.ifBlank { fallback }
        val ext = extension.trimStart('.').lowercase()
        return "$safeBase.$ext"
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Subtitles"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy subtitles to clipboard", e)
            false
        }
    }

    fun saveToDownloads(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "application/x-subrip"
    ): Pair<Boolean, String> {
        try {
            val cleanName = sanitizeFileName(fileName, "Subtitles", fileName.substringAfterLast('.', "srt"))
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Pair(false, "Could not access Downloads storage")
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } ?: return Pair(false, "Failed to open output stream")
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return Pair(true, "Saved to Downloads: $cleanName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val targetFile = File(downloadsDir, cleanName)
                targetFile.writeText(content, Charsets.UTF_8)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                return Pair(true, "Saved to: ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to Downloads", e)
            return Pair(false, "Error downloading subtitle: ${e.localizedMessage}")
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write subtitle to uri $uri", e)
            false
        }
    }

    fun shareSubtitles(context: Context, title: String, content: String, fileName: String = "subtitles.srt") {
        try {
            val cacheFile = File(context.cacheDir, sanitizeFileName(fileName, "Subtitles", "srt"))
            cacheFile.writeText(content, Charsets.UTF_8)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "$title Subtitles (.srt)")
                putExtra(Intent.EXTRA_TEXT, content)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(shareIntent, "Share Subtitles ($title)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share subtitles", e)
        }
    }

    fun getFormattedSubtitles(
        items: List<SrtItem>,
        rawFallback: String,
        format: SubtitleFormat
    ): String {
        if (items.isNotEmpty()) {
            return SrtParser.exportAs(items, format)
        }
        if (rawFallback.isNotBlank()) {
            return SrtParser.convertTo(rawFallback, format)
        }
        return ""
    }

    fun getPlainTranscript(items: List<SrtItem>, rawFallback: String): String {
        if (items.isNotEmpty()) {
            return SrtParser.exportToPlainText(items)
        }
        return SrtParser.exportToPlainText(rawFallback)
    }
}
