package com.example

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust local video scanner that discovers all videos on the user's phone
 * using Android MediaStore and handles Android 14+, 13, and legacy storage permissions.
 */
object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private val thumbnailCache = ConcurrentHashMap<String, Bitmap>()

    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasVideoPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val fullAccess = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                val partialAccess = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
                fullAccess || partialAccess
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    suspend fun queryDeviceVideos(context: Context): List<MxVideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<MxVideoItem>()
        if (!hasVideoPermission(context)) {
            Log.d(TAG, "Storage/Media permission not granted yet, showing library sample videos.")
            return@withContext getDefaultSampleVideos()
        }
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.Video.Media.RELATIVE_PATH)
        } else {
            projection.add(MediaStore.Video.Media.DATA)
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collectionUri,
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                } else -1
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val rawName = displayName ?: title ?: "Video_$id"
                    val cleanTitle = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
                    val durationMs = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dateModifiedSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    var folder = "Videos"
                    if (bucketCol != -1) {
                        val bucket = cursor.getString(bucketCol)
                        if (!bucket.isNullOrBlank()) folder = bucket
                    }
                    if (folder == "Videos" && pathCol != -1) {
                        val path = cursor.getString(pathCol)
                        if (!path.isNullOrBlank()) {
                            val cleanPath = path.trimEnd('/')
                            folder = cleanPath.substringAfterLast('/', cleanPath)
                        }
                    }
                    if (folder.isBlank()) folder = "Device Videos"

                    val resolution = formatResolution(width, height)
                    val durationText = formatDuration(durationMs)
                    val sizeText = formatFileSize(sizeBytes)
                    val dateText = formatTimestamp(dateModifiedSec * 1000)
                    val isNew = (System.currentTimeMillis() - (dateModifiedSec * 1000)) < (48L * 3600 * 1000)

                    videoList.add(
                        MxVideoItem(
                            id = "device_vid_$id",
                            title = cleanTitle,
                            folderName = folder,
                            uriString = contentUri.toString(),
                            durationText = durationText,
                            sizeText = sizeText,
                            resolution = resolution,
                            srtPreset = null,
                            isHindiDubbed = false,
                            isNew = isNew,
                            dateAdded = dateText,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            isFromDevice = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore videos: ${e.message}", e)
        }
        if (videoList.isEmpty()) {
            return@withContext getDefaultSampleVideos()
        }
        videoList
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSec = durationMs / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return if (hr > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hr, min, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", min, sec)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    private fun formatResolution(width: Int, height: Int): String {
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        return when {
            maxDim >= 3840 || minDim >= 2160 -> "4K UHD"
            maxDim >= 2560 || minDim >= 1440 -> "2K QHD"
            maxDim >= 1920 || minDim >= 1080 -> "1080p FHD"
            maxDim >= 1280 || minDim >= 720 -> "720p HD"
            maxDim >= 854 || minDim >= 480 -> "480p SD"
            maxDim > 0 && minDim > 0 -> "${minDim}p"
            else -> "HD"
        }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Recently"
        val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffDays = diffHours / 24
        return when {
            diffHours < 1 -> "Just now"
            diffHours < 24 -> "$diffHours hrs ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7L -> "$diffDays days ago"
            else -> SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    suspend fun loadVideoThumbnail(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        val cached = thumbnailCache[uriString]
        if (cached != null && !cached.isRecycled) return@withContext cached
        try {
            val uri = Uri.parse(uriString)
            var bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(240, 160), null)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            if (bitmap == null) {
                var retriever: android.media.MediaMetadataRetriever? = null
                try {
                    val uri = Uri.parse(uriString)
                    retriever = android.media.MediaMetadataRetriever()
                    if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                        retriever.setDataSource(context, uri)
                    } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                        retriever.setDataSource(uriString, HashMap<String, String>())
                    } else {
                        // Plain path / unsupported scheme: skip retriever entirely.
                        try { retriever.release() } catch (_: Exception) {}
                        retriever = null
                    }
                    if (retriever != null) {
                        bitmap = retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    }
                } catch (_: Exception) {
                } finally {
                    try { retriever?.release() } catch (_: Exception) {}
                }
            }
            if (bitmap != null) {
                thumbnailCache[uriString] = bitmap
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun getDefaultSampleVideos(): List<MxVideoItem> = listOf(
        MxVideoItem(
            id = "sample_sintel",
            title = "Sintel Fantasy Quest",
            folderName = "Movies",
            uriString = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
            durationText = "00:52",
            sizeText = "5.2 MB",
            resolution = "720p HD",
            srtPreset = "1\n00:00:01,000 --> 00:00:04,500\n[Rohan]: Looking for someone?\n\n2\n00:00:05,000 --> 00:00:09,000\n[Priya]: Just an old dragon friend across the mountain.\n\n3\n00:00:10,000 --> 00:00:14,000\n[Narrator]: The journey begins in the winter snow.",
            isHindiDubbed = true,
            isNew = true,
            dateAdded = "Today",
            durationMs = 52_000L,
            sizeBytes = 5_452_595L,
            isFromDevice = false
        ),
        MxVideoItem(
            id = "sample_bigbuck",
            title = "Big Buck Bunny 4K Cinema",
            folderName = "Movies",
            uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            durationText = "09:56",
            sizeText = "158 MB",
            resolution = "1080p FHD",
            srtPreset = "1\n00:00:02,000 --> 00:00:06,000\n[Bunny]: Good morning forest!\n\n2\n00:00:07,000 --> 00:00:11,500\n[Frank]: Hey, watch where you are hopping!\n\n3\n00:00:12,000 --> 00:00:17,000\n[Narrator]: A peaceful morning turns into an epic comedy showdown.",
            isHindiDubbed = true,
            isNew = true,
            dateAdded = "Today",
            durationMs = 596_000L,
            sizeBytes = 158_000_000L,
            isFromDevice = false
        ),
        MxVideoItem(
            id = "sample_tears",
            title = "Tears of Steel (Sci-Fi VFX)",
            folderName = "Downloads",
            uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            durationText = "12:14",
            sizeText = "220 MB",
            resolution = "4K UHD",
            srtPreset = "1\n00:00:03,000 --> 00:00:07,000\n[Commander]: Systems online. Prepare quantum drive.\n\n2\n00:00:08,000 --> 00:00:12,500\n[Engineer]: All thrusters configured for jump.\n\n3\n00:00:13,000 --> 00:00:18,000\n[Narrator]: The battle above the Earth has reached critical juncture.",
            isHindiDubbed = false,
            isNew = false,
            dateAdded = "Yesterday",
            durationMs = 734_000L,
            sizeBytes = 220_000_000L,
            isFromDevice = false
        ),
        MxVideoItem(
            id = "sample_elephants",
            title = "Elephant's Dream Studio Tour",
            folderName = "Animation",
            uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            durationText = "10:53",
            sizeText = "180 MB",
            resolution = "1080p FHD",
            srtPreset = "1\n00:00:01,500 --> 00:00:05,000\n[Proog]: Welcome to the machine, Emo.\n\n2\n00:00:06,000 --> 00:00:10,000\n[Emo]: It seems endlessly complex and alive.",
            isHindiDubbed = false,
            isNew = false,
            dateAdded = "3 days ago",
            durationMs = 653_000L,
            sizeBytes = 180_000_000L,
            isFromDevice = false
        ),
        MxVideoItem(
            id = "sample_camera_recording",
            title = "Holiday Trip 2026 Raw 60fps",
            folderName = "Camera",
            uriString = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
            durationText = "03:22",
            sizeText = "48 MB",
            resolution = "1080p FHD",
            srtPreset = null,
            isHindiDubbed = false,
            isNew = true,
            dateAdded = "Just now",
            durationMs = 202_000L,
            sizeBytes = 48_000_000L,
            isFromDevice = false
        )
    )
}
