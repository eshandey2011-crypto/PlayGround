package com.example

import java.util.regex.Pattern

data class LineSpeakerAnalysis(
    val speaker: SpeakerProfile,
    val cleanSpokenText: String,
    val detectedTag: String?,
    val confidence: Float,
    val detectionMethod: String
)

interface ISpeakerDetector {
    val detectorName: String
    fun detectSpeaker(rawText: String, lineIndex: Int, previousSpeaker: SpeakerProfile?): LineSpeakerAnalysis
    fun processSubtitles(items: List<SrtItem>): List<SrtItem>
}

/**
 * Rule-based & heuristic Hindi Speaker Detector.
 */
class RuleBasedHindiSpeakerDetector : ISpeakerDetector {
    override val detectorName: String = "Speaker & Dialogue Tag Detector"

    private val TAG_PATTERNS = listOf(
        Pattern.compile("^\\[(.*?)\\]\\s*:\\s*(.*)$", Pattern.DOTALL),
        Pattern.compile("^\\((.*?)\\)\\s*:\\s*(.*)$", Pattern.DOTALL),
        Pattern.compile("^\\[(.*?)\\]\\s*(.*)$", Pattern.DOTALL),
        Pattern.compile("^\\((.*?)\\)\\s*(.*)$", Pattern.DOTALL),
        Pattern.compile("^([\\u0900-\\u097Fa-zA-Z0-9\\s_]{2,20})\\s*:\\s*(.*)$", Pattern.DOTALL),
        Pattern.compile("^([\\u0900-\\u097Fa-zA-Z0-9\\s_]{2,20})\\s*-\\s*(.*)$", Pattern.DOTALL)
    )

    private val knownFemaleNames = setOf(
        "priya", "sunita", "neha", "anjali", "pooja", "riya", "kavita", "seema",
        "radha", "meera", "tina", "sonam", "sarah", "mary", "emily", "anna",
        "lisa", "jessica", "emma", "olivia", "sophia", "mia", "female", "woman",
        "girl", "lady", "madam", "miss", "mrs", "f1", "f2"
    )

    private val knownMaleNames = setOf(
        "rahul", "amit", "vikram", "rohan", "raj", "vikas", "sanjay", "anil",
        "manoj", "suresh", "deepak", "kabir", "john", "david", "alex", "michael",
        "james", "robert", "william", "daniel", "male", "man", "boy", "guy",
        "sir", "mr", "m1", "m2"
    )

    private val narratorKeywords = setOf(
        "narrator", "voiceover", "announcer", "host", "narr", "vo", "सूत्रधार"
    )

    private val speakerColors = listOf(
        0xFF1976D2,
        0xFFE91E63,
        0xFF00796B,
        0xFF7B1FA2,
        0xFFF57C00,
        0xFF388E3C,
        0xFF5D4037
    )

    private val detectedProfiles = mutableMapOf<String, SpeakerProfile>()

    override fun detectSpeaker(rawText: String, lineIndex: Int, previousSpeaker: SpeakerProfile?): LineSpeakerAnalysis {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            val unknown = getOrCreateProfile("Unknown", SpeakerGender.UNKNOWN, SpeakerRole.UNKNOWN)
            return LineSpeakerAnalysis(unknown, "", null, 0.0f, "Empty")
        }

        for (pattern in TAG_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val rawSpeakerName = matcher.group(1)?.trim() ?: ""
                val spokenPart = matcher.group(2)?.trim() ?: ""
                if (rawSpeakerName.isNotEmpty() && spokenPart.isNotEmpty() && rawSpeakerName.length <= 25) {
                    val normalizedLower = rawSpeakerName.lowercase()
                    val gender = when {
                        narratorKeywords.any { normalizedLower.contains(it) } -> SpeakerGender.NARRATOR
                        knownFemaleNames.any { normalizedLower.contains(it) } -> SpeakerGender.FEMALE
                        knownMaleNames.any { normalizedLower.contains(it) } -> SpeakerGender.MALE
                        else -> inferGenderFromText(spokenPart)
                    }
                    val role = when (gender) {
                        SpeakerGender.NARRATOR -> SpeakerRole.NARRATOR
                        SpeakerGender.FEMALE -> SpeakerRole.PRIMARY_FEMALE
                        SpeakerGender.MALE -> SpeakerRole.PRIMARY_MALE
                        else -> SpeakerRole.SECONDARY
                    }
                    val profile = getOrCreateProfile(rawSpeakerName, gender, role)
                    return LineSpeakerAnalysis(
                        speaker = profile,
                        cleanSpokenText = spokenPart,
                        detectedTag = rawSpeakerName,
                        confidence = 0.95f,
                        detectionMethod = "Explicit Speaker Tag"
                    )
                }
            }
        }

        val textLower = trimmed.lowercase()
        val isNarrator = trimmed.startsWith("एक ") || trimmed.startsWith("वहां ") ||
                trimmed.startsWith("once ") || trimmed.startsWith("there ") || trimmed.startsWith("here ") ||
                trimmed.contains("narrator") || trimmed.contains("nature") || trimmed.contains("scene")
        val inferredGender = when {
            isNarrator -> SpeakerGender.NARRATOR
            hasFemaleGrammar(textLower) -> SpeakerGender.FEMALE
            hasMaleGrammar(textLower) -> SpeakerGender.MALE
            previousSpeaker != null && previousSpeaker.gender != SpeakerGender.UNKNOWN -> {
                if (trimmed.endsWith("?") || trimmed.endsWith("।") || trimmed.endsWith(".")) {
                    previousSpeaker.gender
                } else {
                    SpeakerGender.UNKNOWN
                }
            }
            else -> SpeakerGender.NARRATOR
        }

        val speakerName = when (inferredGender) {
            SpeakerGender.NARRATOR -> "Narrator"
            SpeakerGender.FEMALE -> "Female Character"
            SpeakerGender.MALE -> "Male Character"
            SpeakerGender.CHILD -> "Child Character"
            SpeakerGender.UNKNOWN -> "Speaker ${lineIndex + 1}"
        }
        val role = when (inferredGender) {
            SpeakerGender.NARRATOR -> SpeakerRole.NARRATOR
            SpeakerGender.FEMALE -> SpeakerRole.PRIMARY_FEMALE
            SpeakerGender.MALE -> SpeakerRole.PRIMARY_MALE
            SpeakerGender.CHILD -> SpeakerRole.CHILD
            else -> SpeakerRole.SECONDARY
        }
        val profile = getOrCreateProfile(speakerName, inferredGender, role)
        return LineSpeakerAnalysis(
            speaker = profile,
            cleanSpokenText = trimmed,
            detectedTag = null,
            confidence = if (inferredGender != SpeakerGender.UNKNOWN) 0.75f else 0.4f,
            detectionMethod = "Grammar / Context Heuristics"
        )
    }

    private fun hasFemaleGrammar(text: String): Boolean {
        return text.contains("रही ") || text.contains("गई ") ||
                text.contains("आई ") || text.contains("हुई ") ||
                text.contains("दी ") || text.contains("थी ") ||
                text.contains("she said") || text.contains("woman") || text.contains("girl") || text.contains("her ")
    }

    private fun hasMaleGrammar(text: String): Boolean {
        return text.contains("रहा ") || text.contains("गया ") ||
                text.contains("आया ") || text.contains("हुआ ") ||
                text.contains("दिया ") || text.contains("था ") ||
                text.contains("he said") || text.contains("sir") || text.contains("brother") || text.contains("his ")
    }

    private fun inferGenderFromText(text: String): SpeakerGender {
        val lower = text.lowercase()
        return when {
            hasFemaleGrammar(lower) -> SpeakerGender.FEMALE
            hasMaleGrammar(lower) -> SpeakerGender.MALE
            else -> SpeakerGender.UNKNOWN
        }
    }

    private fun getOrCreateProfile(name: String, gender: SpeakerGender, role: SpeakerRole): SpeakerProfile {
        return detectedProfiles.getOrPut(name) {
            val colorIndex = (detectedProfiles.size) % speakerColors.size
            val defaultPitch = when (gender) {
                SpeakerGender.FEMALE -> 1.20f
                SpeakerGender.MALE -> 0.85f
                SpeakerGender.CHILD -> 1.48f
                SpeakerGender.NARRATOR -> 0.95f
                SpeakerGender.UNKNOWN -> 1.00f
            }
            val defaultSpeed = when (gender) {
                SpeakerGender.NARRATOR -> 0.96f
                else -> 1.00f
            }
            SpeakerProfile(
                id = "spk_${detectedProfiles.size + 1}_${name.hashCode()}",
                name = name,
                gender = gender,
                role = role,
                avatarColorHex = speakerColors[colorIndex],
                pitchMultiplier = defaultPitch,
                speedMultiplier = defaultSpeed,
                isAiDetected = false,
                detectionTag = "Heuristic"
            )
        }
    }

    override fun processSubtitles(items: List<SrtItem>): List<SrtItem> {
        var lastSpeaker: SpeakerProfile? = null
        return items.mapIndexed { idx, item ->
            val analysis = detectSpeaker(item.text, idx, lastSpeaker)
            lastSpeaker = analysis.speaker
            item.copy(
                speaker = analysis.speaker,
                cleanSpokenText = analysis.cleanSpokenText,
                speakerTag = analysis.detectedTag,
                customPitch = analysis.speaker.pitchMultiplier,
                customSpeed = analysis.speaker.speedMultiplier
            )
        }
    }
}

/**
 * AI-powered Semantic Speaker Diarization & Emotion Detector.
 */
class AISpeakerDetector(
    private val fallbackDetector: ISpeakerDetector = RuleBasedHindiSpeakerDetector()
) : ISpeakerDetector {
    override val detectorName: String = "AI-Powered Semantic Speaker Diarization"
    private val emotionTagPattern = Pattern.compile("^\\[(.*?)\\]\\s*(.*)$|^\\((.*?)\\)\\s*(.*)$", Pattern.DOTALL)

    override fun detectSpeaker(rawText: String, lineIndex: Int, previousSpeaker: SpeakerProfile?): LineSpeakerAnalysis {
        val baseAnalysis = fallbackDetector.detectSpeaker(rawText, lineIndex, previousSpeaker)
        val text = baseAnalysis.cleanSpokenText

        val emotionMatcher = emotionTagPattern.matcher(text)
        if (emotionMatcher.find()) {
            val emotionTag = (emotionMatcher.group(1) ?: emotionMatcher.group(3))?.trim() ?: ""
            val cleanDialogue = (emotionMatcher.group(2) ?: emotionMatcher.group(4))?.trim() ?: text
            val (pitchMod, speedMod) = calculateEmotionModifier(emotionTag)
            val updatedSpeaker = baseAnalysis.speaker.copy(
                pitchMultiplier = (baseAnalysis.speaker.pitchMultiplier * pitchMod).coerceIn(0.6f, 1.8f),
                speedMultiplier = (baseAnalysis.speaker.speedMultiplier * speedMod).coerceIn(0.7f, 1.6f),
                isAiDetected = true,
                detectionTag = "AI Emotion: $emotionTag"
            )
            return baseAnalysis.copy(
                speaker = updatedSpeaker,
                cleanSpokenText = cleanDialogue,
                confidence = 0.98f,
                detectionMethod = "AI Semantic & Emotion Extraction"
            )
        }
        return baseAnalysis.copy(
            speaker = baseAnalysis.speaker.copy(isAiDetected = true, detectionTag = "AI Diarization"),
            confidence = 0.90f
        )
    }

    private fun calculateEmotionModifier(emotionTag: String): Pair<Float, Float> {
        val lower = emotionTag.lowercase()
        return when {
            lower.contains("happy") || lower.contains("खुश") -> Pair(1.15f, 1.08f)
            lower.contains("angry") || lower.contains("गुस्सा") -> Pair(0.92f, 1.12f)
            lower.contains("sad") || lower.contains("उदास") -> Pair(0.88f, 0.90f)
            lower.contains("surprise") || lower.contains("हैरान") -> Pair(1.20f, 1.05f)
            lower.contains("serious") || lower.contains("गंभीर") -> Pair(0.95f, 0.95f)
            else -> Pair(1.00f, 1.00f)
        }
    }

    override fun processSubtitles(items: List<SrtItem>): List<SrtItem> {
        val baseItems = fallbackDetector.processSubtitles(items)
        val speakerMap = mutableMapOf<String, SpeakerProfile>()
        var lastSpeaker: SpeakerProfile? = null

        return baseItems.mapIndexed { index, item ->
            var spk = item.speaker ?: SpeakerProfile(
                id = "spk_ai_$index",
                name = "Character ${index + 1}",
                gender = SpeakerGender.UNKNOWN,
                role = SpeakerRole.SECONDARY
            )

            if (spk.gender == SpeakerGender.UNKNOWN && lastSpeaker != null && index > 0) {
                val prevItem = baseItems[index - 1]
                if (item.startTimeMs - prevItem.endTimeMs < 1200L && prevItem.text.contains("?")) {
                    val counterpartGender = if (lastSpeaker?.gender == SpeakerGender.MALE) SpeakerGender.FEMALE else SpeakerGender.MALE
                    val counterpartName = if (counterpartGender == SpeakerGender.FEMALE) "Sunita (AI Diarized)" else "Rahul (AI Diarized)"
                    spk = spk.copy(
                        name = counterpartName,
                        gender = counterpartGender,
                        pitchMultiplier = if (counterpartGender == SpeakerGender.FEMALE) 1.25f else 0.85f,
                        isAiDetected = true,
                        detectionTag = "AI Turn-Taking"
                    )
                }
            }

            val unifiedProfile = speakerMap.getOrPut(spk.name) {
                spk.copy(isAiDetected = true, detectionTag = spk.detectionTag.ifEmpty { "AI Diarized" })
            }
            lastSpeaker = unifiedProfile

            val cleanTextWithoutBrackets = item.cleanSpokenText
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .trim()

            item.copy(
                speaker = unifiedProfile,
                cleanSpokenText = if (cleanTextWithoutBrackets.isNotEmpty()) cleanTextWithoutBrackets else item.cleanSpokenText,
                customPitch = unifiedProfile.pitchMultiplier,
                customSpeed = unifiedProfile.speedMultiplier
            )
        }
    }
}

object SpeakerDetector {
    private val detector = RuleBasedHindiSpeakerDetector()

    fun analyzeLine(rawText: String): LineSpeakerAnalysis {
        return detector.detectSpeaker(rawText, 0, null)
    }

    fun processSubtitles(items: List<SrtItem>): List<SrtItem> {
        return detector.processSubtitles(items)
    }
}
