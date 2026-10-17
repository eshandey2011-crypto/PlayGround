package com.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class ParsedSrtResult(
    val file: SrtFileEntity,
    val cues: List<SrtCueEntity>
)

data class TimelineSyncState(
    val fileId: Long = 0L,
    val currentPositionMs: Long = 0L,
    val offsetMs: Long = 0L,
    val effectivePositionMs: Long = 0L,
    val activeCue: SrtCueEntity? = null,
    val activeCueIndex: Int = -1,
    val nextCue: SrtCueEntity? = null,
    val previousCue: SrtCueEntity? = null,
    val progressInCue: Float = 0f,
    val remainingMsInCue: Long = 0L,
    val timeUntilNextCueMs: Long? = null,
    val isCueActive: Boolean = false
)

class SrtTimelineSyncService(
    private val repository: SrtSubtitleRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _syncState = MutableStateFlow(TimelineSyncState())
    val syncState: StateFlow<TimelineSyncState> = _syncState.asStateFlow()

    @Volatile
    private var boundFileId: Long = 0L
    @Volatile
    private var cachedCues: List<SrtCueEntity> = emptyList()
    @Volatile
    private var currentOffsetMs: Long = 0L

    companion object {
        fun parseSrt(
            content: String,
            title: String = "Untitled Subtitle",
            videoUri: String? = null,
            videoTitle: String? = null,
            languageCode: String = "en",
            defaultOffsetMs: Long = 0L
        ): ParsedSrtResult {
            if (content.isBlank()) {
                val file = SrtFileEntity(
                    title = title,
                    videoUri = videoUri,
                    videoTitle = videoTitle,
                    languageCode = languageCode,
                    rawContent = content,
                    totalCues = 0,
                    durationMs = 0L,
                    offsetMs = defaultOffsetMs
                )
                return ParsedSrtResult(file, emptyList())
            }

            val parsedItems = SrtParser.parse(content)
            val cues = parsedItems.map { item ->
                SrtCueEntity(
                    fileId = 0L,
                    cueIndex = item.index,
                    startTimeMs = item.startTimeMs,
                    endTimeMs = item.endTimeMs,
                    text = item.text,
                    cleanSpokenText = item.cleanSpokenText,
                    speakerTag = item.speakerTag
                )
            }

            val maxEndTimeMs = cues.maxOfOrNull { it.endTimeMs } ?: 0L
            val fileEntity = SrtFileEntity(
                title = title,
                videoUri = videoUri,
                videoTitle = videoTitle,
                languageCode = languageCode,
                rawContent = content,
                totalCues = cues.size,
                durationMs = maxEndTimeMs,
                offsetMs = defaultOffsetMs
            )
            return ParsedSrtResult(fileEntity, cues)
        }

        fun formatMsToSrtTime(ms: Long): String {
            val nonNeg = ms.coerceAtLeast(0L)
            val hours = nonNeg / 3600000L
            val minutes = (nonNeg % 3600000L) / 60000L
            val seconds = (nonNeg % 60000L) / 1000L
            val millis = nonNeg % 1000L
            return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }

        fun exportToSrt(cues: List<SrtCueEntity>): String {
            val sb = StringBuilder()
            cues.sortedBy { it.startTimeMs }.forEachIndexed { index, cue ->
                sb.append(index + 1).append("\n")
                sb.append(formatMsToSrtTime(cue.startTimeMs))
                    .append(" --> ")
                    .append(formatMsToSrtTime(cue.endTimeMs))
                    .append("\n")
                sb.append(cue.text.ifBlank { cue.cleanSpokenText }).append("\n\n")
            }
            return sb.toString().trimEnd()
        }
    }

    suspend fun parseAndStore(
        content: String,
        title: String,
        videoUri: String? = null,
        videoTitle: String? = null,
        languageCode: String = "en",
        offsetMs: Long = 0L
    ): Long {
        val parsed = parseSrt(
            content = content,
            title = title,
            videoUri = videoUri,
            videoTitle = videoTitle,
            languageCode = languageCode,
            defaultOffsetMs = offsetMs
        )
        val fileId = repository.saveFileAndCues(parsed.file, parsed.cues)
        bindFile(fileId)
        return fileId
    }

    suspend fun storeParsed(result: ParsedSrtResult): Long {
        val fileId = repository.saveFileAndCues(result.file, result.cues)
        bindFile(fileId)
        return fileId
    }

    suspend fun loadFileWithCues(fileId: Long): SrtFileWithCues? {
        return repository.getFileWithCuesDirect(fileId)
    }

    suspend fun updateTimelineOffset(fileId: Long, offsetMs: Long) {
        currentOffsetMs = offsetMs
        repository.updateOffset(fileId, offsetMs)
        _syncState.update { it.copy(offsetMs = offsetMs) }
    }

    suspend fun deleteSubtitleFile(fileId: Long) {
        repository.deleteFile(fileId)
        if (boundFileId == fileId) {
            boundFileId = 0L
            cachedCues = emptyList()
            _syncState.value = TimelineSyncState()
        }
    }

    suspend fun bindFile(fileId: Long) {
        val fileWithCues = repository.getFileWithCuesDirect(fileId)
        if (fileWithCues != null) {
            boundFileId = fileId
            currentOffsetMs = fileWithCues.file.offsetMs
            cachedCues = fileWithCues.cues.sortedBy { it.startTimeMs }
            _syncState.update {
                it.copy(
                    fileId = fileId,
                    offsetMs = currentOffsetMs
                )
            }
        }
    }

    fun setCues(fileId: Long, cues: List<SrtCueEntity>, offsetMs: Long = 0L) {
        boundFileId = fileId
        currentOffsetMs = offsetMs
        cachedCues = cues.sortedBy { it.startTimeMs }
        _syncState.update {
            it.copy(
                fileId = fileId,
                offsetMs = offsetMs
            )
        }
    }

    fun setOffset(offsetMs: Long) {
        currentOffsetMs = offsetMs
        _syncState.update { it.copy(offsetMs = offsetMs) }
        scope.launch {
            if (boundFileId > 0L) {
                repository.updateOffset(boundFileId, offsetMs)
            }
        }
    }

    fun syncToPlaybackPosition(positionMs: Long): TimelineSyncState {
        val effectiveMs = positionMs + currentOffsetMs
        var activeCue: SrtCueEntity? = null
        var nextCue: SrtCueEntity? = null
        var prevCue: SrtCueEntity? = null

        val cues = cachedCues
        if (cues.isNotEmpty()) {
            for (i in cues.indices) {
                val cue = cues[i]
                if (effectiveMs in cue.startTimeMs..cue.endTimeMs) {
                    activeCue = cue
                    if (i + 1 < cues.size) {
                        nextCue = cues[i + 1]
                    }
                    if (i - 1 >= 0) {
                        prevCue = cues[i - 1]
                    }
                    break
                } else if (effectiveMs < cue.startTimeMs) {
                    nextCue = cue
                    if (i - 1 >= 0) {
                        prevCue = cues[i - 1]
                    }
                    break
                } else {
                    prevCue = cue
                }
            }
        }

        val progressInCue: Float
        val remainingMsInCue: Long
        if (activeCue != null) {
            val cueDuration = activeCue.durationMs
            val elapsedInCue = (effectiveMs - activeCue.startTimeMs).coerceAtLeast(0L)
            progressInCue = if (cueDuration > 0) {
                (elapsedInCue.toFloat() / cueDuration.toFloat()).coerceIn(0f, 1f)
            } else {
                1.0f
            }
            remainingMsInCue = (activeCue.endTimeMs - effectiveMs).coerceAtLeast(0L)
        } else {
            progressInCue = 0f
            remainingMsInCue = 0L
        }

        val timeUntilNextCueMs = nextCue?.let { (it.startTimeMs - effectiveMs).coerceAtLeast(0L) }
        val newState = TimelineSyncState(
            fileId = boundFileId,
            currentPositionMs = positionMs,
            offsetMs = currentOffsetMs,
            effectivePositionMs = effectiveMs,
            activeCue = activeCue,
            activeCueIndex = activeCue?.cueIndex ?: -1,
            nextCue = nextCue,
            previousCue = prevCue,
            progressInCue = progressInCue,
            remainingMsInCue = remainingMsInCue,
            timeUntilNextCueMs = timeUntilNextCueMs,
            isCueActive = activeCue != null
        )
        _syncState.value = newState
        return newState
    }

    suspend fun queryActiveCueFromDatabase(positionMs: Long, offsetMs: Long = currentOffsetMs): SrtCueEntity? {
        if (boundFileId <= 0L) return null
        return repository.findActiveCue(boundFileId, positionMs + offsetMs)
    }

    suspend fun queryNextCueFromDatabase(positionMs: Long, offsetMs: Long = currentOffsetMs): SrtCueEntity? {
        if (boundFileId <= 0L) return null
        return repository.findNextCue(boundFileId, positionMs + offsetMs)
    }

    fun seekToCue(cueIndex: Int): Long? {
        val targetCue = cachedCues.firstOrNull { it.cueIndex == cueIndex } ?: return null
        return (targetCue.startTimeMs - currentOffsetMs).coerceAtLeast(0L)
    }

    fun seekToNextCue(): Long? {
        val next = _syncState.value.nextCue ?: return null
        return (next.startTimeMs - currentOffsetMs).coerceAtLeast(0L)
    }

    fun seekToPreviousCue(): Long? {
        val prev = _syncState.value.previousCue ?: return null
        return (prev.startTimeMs - currentOffsetMs).coerceAtLeast(0L)
    }

    fun findCueAt(positionMs: Long): SrtCueEntity? {
        val effective = positionMs + currentOffsetMs
        return cachedCues.firstOrNull { effective in it.startTimeMs..it.endTimeMs }
    }
}
