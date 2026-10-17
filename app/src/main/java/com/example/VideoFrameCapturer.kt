package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoFrameCapturer {
    fun findTextureView(view: View?): TextureView? {
        if (view == null) return null
        if (view is TextureView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val found = findTextureView(child)
                if (found != null) return found
            }
        }
        return null
    }

    fun captureCurrentFrame(playerView: PlayerView?): Bitmap? {
        if (playerView == null) return null
        val textureView = findTextureView(playerView) ?: return null
        return try {
            if (textureView.isAvailable) {
                textureView.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun captureFrameWithFallback(
        playerView: PlayerView?,
        context: Context,
        videoUri: String?,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val fromTexture = withContext(Dispatchers.Main) {
            captureCurrentFrame(playerView)
        }
        if (fromTexture != null) {
            return@withContext fromTexture
        }
        if (!videoUri.isNullOrBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                val parsedUri = Uri.parse(videoUri)
                if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
                    retriever.setDataSource(videoUri, HashMap<String, String>())
                } else {
                    retriever.setDataSource(context, parsedUri)
                }
                val timeUs = positionMs.coerceAtLeast(0L) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                retriever.release()
                return@withContext frame
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        null
    }

    suspend fun saveBitmapToPublicPictures(
        context: Context,
        bitmap: Bitmap,
        titlePrefix: String = "VideoFrame"
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "${titlePrefix}_$timeStamp.jpg"
        val mimeType = "image/jpeg"
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VideoFrames")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = context.contentResolver.insert(collection, values)
                if (itemUri != null) {
                    try {
                        context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)) {
                                throw Exception("Bitmap compression failed")
                            }
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(itemUri, values, null, null)
                        itemUri
                    } catch (e: Exception) {
                        e.printStackTrace()
                        try {
                            context.contentResolver.delete(itemUri, null, null)
                        } catch (_: Exception) {}
                        null
                    }
                } else {
                    null
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "VideoFrames")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val imageFile = File(targetDir, fileName)
                FileOutputStream(imageFile).use { outStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)) {
                        throw Exception("Bitmap compression failed")
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: Uri.fromFile(imageFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
