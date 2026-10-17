package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class SyncMetrics(
    val currentPositionMs: Long = 0L,
    val activeSubtitleIndex: Int = -1,
    val activeSpeaker: SpeakerProfile? = null,
    val syncOffsetMs: Long = 0L,
    val estimatedSpeechDurationMs: Long = 0L,
    val recommendedRateMultiplier: Float = 1.0f,
    val isSpeaking: Boolean = false,
    val driftMs: Long = 0L,
    val leadTimeMs: Long = 120L,
    val isPerfectTimingActive: Boolean = true,
    val syncQuality: String = "Perfect Sync"
)

/**
 * Precision Synchronization Engine for Video, Subtitles, and Hindi TTS Dubbing.
 */
class SyncEngine(
    private val voiceManager: VoiceManager = VoiceManager()
) {
    private val _syncState = MutableStateFlow(SyncMetrics())
    val syncState: StateFlow<SyncMetrics> = _syncState.asStateFlow()

    private var currentSubtitles: List<SrtItem> = emptyList()
    private var lastSpokenIndex = -1

    fun updateSubtitles(subtitles: List<SrtItem>) {
        currentSubtitles = subtitles
        lastSpokenIndex = -1
    }

    fun resetSpokenState() {
        lastSpokenIndex = -1
        _syncState.update { it.copy(activeSubtitleIndex = -1, isSpeaking = false) }
    }

    fun allowRetriggerSubtitle(subtitleIndex: Int? = null) {
        if (subtitleIndex == null || subtitleIndex == lastSpokenIndex) {
            lastSpokenIndex = -1
        }
    }

    fun markSubtitleSpoken(subtitleIndex: Int) {
        lastSpokenIndex = subtitleIndex
        _syncState.update { it.copy(activeSubtitleIndex = subtitleIndex) }
    }

    fun findActiveSubtitle(positionMs: Long, offsetMs: Long): SrtItem? {
        if (currentSubtitles.isEmpty()) return null
        val effectiveTime = positionMs + offsetMs
        return currentSubtitles.firstOrNull { effectiveTime in it.startTimeMs..it.endTimeMs }
    }

    fun findUpcomingSubtitle(positionMs: Long, offsetMs: Long, windowMs: Long = 350L): SrtItem? {
        val effectiveTime = positionMs + offsetMs
        return currentSubtitles.firstOrNull { item ->
            item.startTimeMs in effectiveTime..(effectiveTime + windowMs)
        }
    }

    fun checkSubtitleTrigger(
        positionMs: Long,
        offsetMs: Long,
        isAutoDubbingEnabled: Boolean,
        availableVoices: List<TtsVoiceItem> = emptyList(),
        selectedGlobalVoice: TtsVoiceItem? = null,
        genderVoiceMode: GenderVoiceMode = GenderVoiceMode.AUTO_MULTI_SPEAKER,
        selectedVoicePersona: HindiVoicePersona? = null,
        leadTimeMs: Long = 0L,
        isPerfectTimingEnabled: Boolean = true,
        isDynamicRateAutoFitEnabled: Boolean = true,
        isSameToSameTimeMode: Boolean = true
    ): Pair<SrtItem, SpokenVoiceParams>? {
        if (!isAutoDubbingEnabled || currentSubtitles.isEmpty()) return null
        val effectiveTime = positionMs + offsetMs

        val compensationLeadTime = if (isSameToSameTimeMode) {
            0L
        } else if (isPerfectTimingEnabled) {
            leadTimeMs.coerceIn(0L, 500L)
        } else {
            0L
        }

        val match = currentSubtitles.firstOrNull { item ->
            val itemIdx = currentSubtitles.indexOf(item)
            val prevItem = if (itemIdx > 0) currentSubtitles[itemIdx - 1] else null
            val effectiveLead = if (prevItem != null && prevItem.endTimeMs > (item.startTimeMs - compensationLeadTime)) {
                (item.startTimeMs - prevItem.endTimeMs).coerceAtLeast(0L).coerceAtMost(compensationLeadTime)
            } else {
                compensationLeadTime
            }
            val triggerStartTime = (item.startTimeMs - effectiveLead).coerceAtLeast(0L)
            effectiveTime >= triggerStartTime && effectiveTime <= item.endTimeMs && item.index != lastSpokenIndex
        }

        if (match != null) {
            lastSpokenIndex = match.index

            val words = match.cleanSpokenText.split(Regex("\\s+")).filter { it.isNotBlank() }
            val wordCount = words.size
            val estimatedSpokenMs = (wordCount * 250L).coerceAtLeast(500L)
            val slotDurationMs = match.durationMs
            val matchIdx = currentSubtitles.indexOf(match)
            val nextItem = if (matchIdx >= 0 && matchIdx + 1 < currentSubtitles.size) currentSubtitles[matchIdx + 1] else null
            val timeUntilNext = if (nextItem != null && nextItem.startTimeMs > match.startTimeMs) {
                nextItem.startTimeMs - match.startTimeMs
            } else {
                slotDurationMs
            }
            val maxAllowedDuration = if (timeUntilNext in 300L..(slotDurationMs * 2)) {
                minOf(slotDurationMs, timeUntilNext)
            } else {
                slotDurationMs
            }

            val recommendedRate = if (isDynamicRateAutoFitEnabled && maxAllowedDuration > 0 && estimatedSpokenMs > maxAllowedDuration) {
                (estimatedSpokenMs.toFloat() / maxAllowedDuration.toFloat()).coerceIn(1.0f, 2.2f)
            } else {
                1.0f
            }

            val voiceParams = voiceManager.getVoiceParamsForSpeaker(
                speaker = match.speaker,
                availableVoices = availableVoices,
                selectedGlobalVoice = selectedGlobalVoice,
                genderVoiceMode = genderVoiceMode,
                selectedVoicePersona = selectedVoicePersona
            )

            val adjustedParams = voiceParams.copy(
                speed = (voiceParams.speed * recommendedRate).coerceIn(0.5f, 2.2f)
            )

            val drift = abs(effectiveTime - match.startTimeMs)
            val quality = when {
                drift <= 20L -> "1:1 Same-to-Same Time"
                drift <= 50L -> "Perfect Sync"
                drift <= 120L -> "Good Sync"
                else -> "Offset Active (${drift}ms)"
            }

            _syncState.update {
                it.copy(
                    currentPositionMs = positionMs,
                    activeSubtitleIndex = match.index,
                    activeSpeaker = match.speaker,
                    syncOffsetMs = offsetMs,
                    estimatedSpeechDurationMs = estimatedSpokenMs,
                    recommendedRateMultiplier = recommendedRate,
                    isSpeaking = true,
                    driftMs = drift,
                    leadTimeMs = compensationLeadTime,
                    isPerfectTimingActive = isPerfectTimingEnabled,
                    syncQuality = quality
                )
            }
            return Pair(match, adjustedParams)
        }
        return null
    }

    fun shouldStopCurrentSpeech(positionMs: Long, offsetMs: Long, toleranceMs: Long = 80L): Boolean {
        if (lastSpokenIndex == -1) return false
        val effectiveTime = positionMs + offsetMs
        val spokenItem = currentSubtitles.firstOrNull { it.index == lastSpokenIndex } ?: return false
        return effectiveTime > (spokenItem.endTimeMs + toleranceMs)
    }

    fun getLastSpokenSubtitle(): SrtItem? = currentSubtitles.firstOrNull { it.index == lastSpokenIndex }

    fun onSpeechStart(autoDuckingEnabled: Boolean = false) {
        _syncState.update { it.copy(isSpeaking = true) }
    }

    fun onSpeechEnd() {
        _syncState.update { it.copy(isSpeaking = false) }
    }

    fun onSeek(newPositionMs: Long) {
        lastSpokenIndex = -1
        _syncState.update {
            it.copy(
                currentPositionMs = newPositionMs,
                activeSubtitleIndex = -1,
                isSpeaking = false
            )
        }
    }
}
