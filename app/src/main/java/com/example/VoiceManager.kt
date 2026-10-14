package com.example

import android.speech.tts.TextToSpeech
import android.util.Log

data class HindiVoicePersona(
    val id: String,
    val displayName: String,
    val gender: SpeakerGender,
    val description: String,
    val basePitch: Float,
    val baseSpeed: Float,
    val sampleText: String
)

data class SpokenVoiceParams(
    val pitch: Float,
    val speed: Float,
    val voiceItem: TtsVoiceItem?,
    val speakerName: String,
    val avatarColorHex: Long
)

/**
 * Manages Hindi character voices, TTS parameter modulation, and speaker-to-voice mappings.
 * Supports multi-character voice acting so different speakers in an SRT have unique voices.
 */
class VoiceManager {
    companion object {
        private const val TAG = "VoiceManager"

        /**
         * Robust voice gender detection across Google TTS, Samsung TTS, and system engines
         */
        fun determineVoiceGender(name: String, features: Set<String>? = null): SpeakerGender {
            val lower = name.lowercase()
            val feats = features?.joinToString(" ")?.lowercase() ?: ""
            val combined = "$lower $feats"
            return when {
                combined.contains("female") || combined.contains("#female") || combined.contains("fem") ||
                combined.contains("woman") || combined.contains("-hia-") || combined.contains("-hic-") ||
                combined.contains("-hid-") || combined.contains("-hig-") || combined.contains("-ena-") ||
                combined.contains("-enc-") || combined.contains("-bna-") || combined.contains("-mra-") ||
                combined.contains("-taa-") || combined.contains("-tea-") || combined.contains("-gua-") ||
                lower.endsWith("-a-local") || lower.endsWith("-a-network") || lower.endsWith("-c-local") ||
                lower.endsWith("-d-local") || lower.endsWith("-g-local") -> SpeakerGender.FEMALE

                combined.contains("male") || combined.contains("#male") || combined.contains("masc") ||
                combined.contains("man") || combined.contains("-hib-") || combined.contains("-hie-") ||
                combined.contains("-hif-") || combined.contains("-enb-") || combined.contains("-end-") ||
                combined.contains("-bnb-") || combined.contains("-mrb-") || combined.contains("-tab-") ||
                combined.contains("-teb-") || combined.contains("-gub-") ||
                lower.endsWith("-b-local") || lower.endsWith("-b-network") || lower.endsWith("-e-local") ||
                lower.endsWith("-f-local") || lower.endsWith("-i-local") -> SpeakerGender.MALE

                combined.contains("narrator") || combined.contains("story") -> SpeakerGender.NARRATOR
                else -> SpeakerGender.UNKNOWN
            }
        }

        val defaultPersonas = listOf(
            HindiVoicePersona(
                id = "narrator_deep",
                displayName = "Deep Narrator",
                gender = SpeakerGender.NARRATOR,
                description = "Authoritative, deep and calm narrative voice for story reading and video voiceover",
                basePitch = 0.85f,
                baseSpeed = 0.95f,
                sampleText = "नमस्ते, इस वीडियो में आपका स्वागत है।"
            )
        )
    }

    // Active speaker overrides: speakerId -> HindiVoicePersona
    private val speakerVoiceMap = mutableMapOf<String, HindiVoicePersona>()

    // Global modifiers
    var globalPitchModifier: Float = 1.0f
    var globalSpeedModifier: Float = 1.0f

    fun getVoiceParamsForSpeaker(
        speaker: SpeakerProfile?,
        availableVoices: List<TtsVoiceItem> = emptyList(),
        selectedGlobalVoice: TtsVoiceItem? = null,
        genderVoiceMode: GenderVoiceMode = GenderVoiceMode.FORCE_NARRATOR,
        selectedVoicePersona: HindiVoicePersona? = null
    ): SpokenVoiceParams {
        val narratorVoice = findBestMatchingDeepVoice(availableVoices)
            ?: findBestMatchingVoice(SpeakerGender.NARRATOR, availableVoices)
            ?: selectedGlobalVoice
        val persona = selectedVoicePersona ?: defaultPersonas.first()
        val basePitch = if (genderVoiceMode == GenderVoiceMode.SINGLE_DEEP_VOICE) {
            0.72f
        } else {
            persona.basePitch
        }
        val baseSpeed = if (genderVoiceMode == GenderVoiceMode.SINGLE_DEEP_VOICE) {
            0.92f
        } else {
            persona.baseSpeed
        }
        val pitch = (basePitch * globalPitchModifier).coerceIn(0.5f, 1.25f)
        val speed = (baseSpeed * globalSpeedModifier).coerceIn(0.6f, 1.5f)

        return SpokenVoiceParams(
            pitch = pitch,
            speed = speed,
            voiceItem = narratorVoice,
            speakerName = "Deep Narrator",
            avatarColorHex = 0xFF5D4037
        )
    }

    fun applyVoiceToTts(
        tts: TextToSpeech?,
        params: SpokenVoiceParams,
        targetLanguageCode: String? = null
    ) {
        if (tts == null) return
        try {
            tts.setPitch(params.pitch)
            tts.setSpeechRate(params.speed)
            val rawVoice = params.voiceItem?.rawVoice
            if (rawVoice != null) {
                if (targetLanguageCode != null && !rawVoice.locale.language.equals(targetLanguageCode, ignoreCase = true)) {
                    Log.w(TAG, "Skipping voice ${rawVoice.name} (${rawVoice.locale}) - does not match target language $targetLanguageCode")
                    return
                }
                val isOnline = try {
                    rawVoice.isNetworkConnectionRequired ||
                        rawVoice.name.lowercase().contains("network") ||
                        rawVoice.name.lowercase().contains("-online")
                } catch (e: Exception) { false }
                if (isOnline) {
                    Log.w(TAG, "Skipping online voice ${rawVoice.name} - enforcing offline on-device speech.")
                    return
                }
                val isNotInstalled = try {
                    rawVoice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                } catch (e: Exception) { false }
                if (!isNotInstalled) {
                    try {
                        tts.voice = rawVoice
                    } catch (e: Exception) {
                        Log.w(TAG, "Voice assignment failed, continuing with pitch/speed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying voice parameters to TTS: ${e.message}")
        }
    }

    fun assignVoiceToSpeaker(speakerId: String, persona: HindiVoicePersona) {
        speakerVoiceMap[speakerId] = persona
    }

    fun getAssignedPersona(speakerId: String): HindiVoicePersona? {
        return speakerVoiceMap[speakerId]
    }

    fun exportMappingsJson(): String {
        val pairs = speakerVoiceMap.map { "\"${it.key}\":\"${it.value.id}\"" }
        return "{" + pairs.joinToString(",") + "}"
    }

    fun importMappingsJson(json: String) {
        speakerVoiceMap.clear()
        if (json.isBlank() || json == "{}") return
        try {
            val cleaned = json.trim().removeSurrounding("{", "}").trim()
            if (cleaned.isEmpty()) return
            val entries = cleaned.split(",")
            for (entry in entries) {
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val speakerId = parts[0].trim().replace("\"", "")
                    val personaId = parts[1].trim().replace("\"", "")
                    val persona = defaultPersonas.find { it.id == personaId }
                    if (persona != null) {
                        speakerVoiceMap[speakerId] = persona
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing speaker mappings JSON: ${e.message}")
        }
    }

    fun findBestMatchingVoice(gender: SpeakerGender, availableVoices: List<TtsVoiceItem>): TtsVoiceItem? {
        if (availableVoices.isEmpty()) return null
        val offlineOnly = availableVoices.filter {
            !it.isNetworkRequired &&
                it.rawVoice?.isNetworkConnectionRequired != true &&
                it.rawVoice?.name?.lowercase()?.contains("network") != true &&
                it.rawVoice?.name?.lowercase()?.contains("-online") != true
        }
        val pool = if (offlineOnly.isNotEmpty()) offlineOnly else availableVoices
        return findBestMatchingDeepVoice(pool) ?: pool.firstOrNull {
            it.gender == SpeakerGender.NARRATOR ||
            it.gender == SpeakerGender.MALE ||
            determineVoiceGender(it.name) == SpeakerGender.NARRATOR
        } ?: pool.firstOrNull()
    }

    fun findBestMatchingDeepVoice(availableVoices: List<TtsVoiceItem>): TtsVoiceItem? {
        if (availableVoices.isEmpty()) return null
        val offlineOnly = availableVoices.filter {
            !it.isNetworkRequired &&
                it.rawVoice?.isNetworkConnectionRequired != true &&
                it.rawVoice?.name?.lowercase()?.contains("network") != true &&
                it.rawVoice?.name?.lowercase()?.contains("-online") != true
        }
        val pool = if (offlineOnly.isNotEmpty()) offlineOnly else availableVoices
        return pool.firstOrNull { voice ->
            val lower = voice.name.lowercase()
            val dLower = voice.displayName.lowercase()
            (voice.gender == SpeakerGender.MALE || voice.gender == SpeakerGender.NARRATOR) &&
                (lower.contains("-b-") || lower.contains("-e-") || lower.contains("-f-") ||
                 lower.contains("male") || dLower.contains("male") || dLower.contains("narrator") || dLower.contains("deep"))
        } ?: pool.firstOrNull {
            it.gender == SpeakerGender.MALE || it.gender == SpeakerGender.NARRATOR || determineVoiceGender(it.name, it.rawVoice?.features) == SpeakerGender.MALE
        } ?: pool.firstOrNull()
    }
}
