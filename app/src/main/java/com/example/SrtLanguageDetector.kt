package com.example

import java.util.Locale

/**
 * Utility for automatically detecting the source language of SRT subtitle files.
 * Accurately analyzes character distributions, Unicode script blocks, and lexical tokens
 * to determine whether subtitles need to be auto-translated to Hindi before playback.
 */
object SrtLanguageDetector {
    private val SPANISH_WORDS = setOf(
        "el", "la", "de", "que", "y", "en", "un", "una", "los", "las", "del", "por",
        "con", "para", "como", "está", "pero", "más", "este", "esta", "hola", "gracias"
    )

    private val FRENCH_WORDS = setOf(
        "le", "la", "les", "de", "et", "un", "une", "dans", "pour", "avec", "sur",
        "qui", "est", "pas", "ce", "cette", "merci", "bonjour", "oui", "non"
    )

    private val GERMAN_WORDS = setOf(
        "der", "die", "das", "und", "in", "den", "von", "zu", "mit", "sich", "des",
        "auf", "für", "ist", "im", "dem", "nicht", "eine", "einer", "danke", "hallo"
    )

    private val ENGLISH_WORDS = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for",
        "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by",
        "from", "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all",
        "would", "there", "their", "what", "so", "up", "out", "if", "about", "who", "get",
        "which", "go", "me", "when", "make", "can", "like", "time", "no", "just", "him",
        "know", "take", "people", "into", "year", "your", "good", "some", "could", "them",
        "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us"
    )

    fun detectLanguage(content: String, parsedItems: List<SrtItem> = emptyList()): SrtLanguageDetection =
        detect(content, parsedItems)

    fun detect(content: String, parsedItems: List<SrtItem> = emptyList()): SrtLanguageDetection {
        val dialogueText = if (parsedItems.isNotEmpty()) {
            parsedItems.joinToString(" ") { it.text }
        } else {
            val parsed = SrtParser.parse(content)
            if (parsed.isNotEmpty()) parsed.joinToString(" ") { it.text } else content
        }

        val totalSubtitlesCount = if (parsedItems.isNotEmpty()) parsedItems.size else SrtParser.parse(content).size

        if (dialogueText.isBlank()) {
            return SrtLanguageDetection(
                isHindiCompliant = false,
                detectedLanguage = SrtDetectedLanguage.EMPTY,
                hindiPercentage = 0,
                devanagariCount = 0,
                latinCount = 0,
                otherScriptCount = 0,
                totalSubtitles = totalSubtitlesCount,
                sampleSnippet = "",
                confidencePercent = 0,
                previewHindiTranslation = ""
            )
        }

        var devanagariCount = 0
        var latinCount = 0
        var cyrillicCount = 0
        var arabicCount = 0
        var cjkCount = 0
        var kanaCount = 0
        var hangulCount = 0
        var bengaliCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var otherCount = 0

        for (ch in dialogueText) {
            when {
                ch in '\u0900'..'\u097F' || ch in '\uA8E0'..'\uA8FF' -> devanagariCount++
                (ch in 'a'..'z') || (ch in 'A'..'Z') -> latinCount++
                ch in '\u0400'..'\u04FF' -> cyrillicCount++
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> arabicCount++
                ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' -> kanaCount++
                ch in '\uAC00'..'\uD7AF' || ch in '\u1100'..'\u11FF' -> hangulCount++
                ch in '\u4E00'..'\u9FFF' -> cjkCount++
                ch in '\u0980'..'\u09FF' -> bengaliCount++
                ch in '\u0B80'..'\u0BFF' -> tamilCount++
                ch in '\u0C00'..'\u0C7F' -> teluguCount++
                ch.isLetter() -> otherCount++
            }
        }

        val totalAlphabetic = devanagariCount + latinCount + cyrillicCount + arabicCount +
                cjkCount + kanaCount + hangulCount + bengaliCount + tamilCount + teluguCount + otherCount
        val hindiPercentage = if (totalAlphabetic > 0) {
            (devanagariCount * 100) / totalAlphabetic
        } else {
            0
        }

        val sampleSnippet = dialogueText.take(120).replace("\n", " ").trim()

        val (detectedLang, confidence) = classifyLanguage(
            totalAlphabetic = totalAlphabetic,
            devanagariCount = devanagariCount,
            latinCount = latinCount,
            cyrillicCount = cyrillicCount,
            arabicCount = arabicCount,
            cjkCount = cjkCount,
            kanaCount = kanaCount,
            hangulCount = hangulCount,
            bengaliCount = bengaliCount,
            tamilCount = tamilCount,
            teluguCount = teluguCount,
            dialogueText = dialogueText,
            hindiPercentage = hindiPercentage
        )

        val isCompliant = detectedLang == SrtDetectedLanguage.HINDI_DEVANAGARI
        val firstCueText = parsedItems.firstOrNull()?.text ?: sampleSnippet.take(60)
        val previewHindi = if (isCompliant) {
            firstCueText
        } else {
            OfflineSubtitleEngine.translateSentenceToHindi(firstCueText)
        }

        return SrtLanguageDetection(
            isHindiCompliant = isCompliant,
            detectedLanguage = detectedLang,
            hindiPercentage = hindiPercentage,
            devanagariCount = devanagariCount,
            latinCount = latinCount,
            otherScriptCount = totalAlphabetic - (devanagariCount + latinCount),
            totalSubtitles = totalSubtitlesCount,
            sampleSnippet = sampleSnippet,
            confidencePercent = confidence,
            previewHindiTranslation = previewHindi
        )
    }

    private fun classifyLanguage(
        totalAlphabetic: Int,
        devanagariCount: Int,
        latinCount: Int,
        cyrillicCount: Int,
        arabicCount: Int,
        cjkCount: Int,
        kanaCount: Int,
        hangulCount: Int,
        bengaliCount: Int,
        tamilCount: Int,
        teluguCount: Int,
        dialogueText: String,
        hindiPercentage: Int
    ): Pair<SrtDetectedLanguage, Int> {
        if (totalAlphabetic == 0) return SrtDetectedLanguage.EMPTY to 0

        if (hindiPercentage >= 70 || (devanagariCount > 0 && latinCount == 0 && totalAlphabetic == devanagariCount)) {
            val conf = (hindiPercentage).coerceIn(70, 100)
            return SrtDetectedLanguage.HINDI_DEVANAGARI to conf
        }

        if (hindiPercentage in 15..69) {
            return SrtDetectedLanguage.MIXED_HINGLISH to 85
        }

        if (cyrillicCount > latinCount && cyrillicCount > devanagariCount) {
            val conf = ((cyrillicCount * 100) / totalAlphabetic).coerceIn(60, 99)
            return SrtDetectedLanguage.RUSSIAN to conf
        }

        if (arabicCount > latinCount && arabicCount > devanagariCount) {
            val conf = ((arabicCount * 100) / totalAlphabetic).coerceIn(60, 99)
            return SrtDetectedLanguage.ARABIC to conf
        }

        if (hangulCount > 0 && hangulCount >= cjkCount) {
            return SrtDetectedLanguage.KOREAN to 95
        }

        if (kanaCount > 0) {
            return SrtDetectedLanguage.JAPANESE to 95
        }

        if (cjkCount > 0 && cjkCount > latinCount) {
            return SrtDetectedLanguage.CHINESE to 90
        }

        if (bengaliCount > latinCount && bengaliCount > 0) {
            return SrtDetectedLanguage.BENGALI to 92
        }

        if (tamilCount > latinCount && tamilCount > 0) {
            return SrtDetectedLanguage.TAMIL to 92
        }

        if (teluguCount > latinCount && teluguCount > 0) {
            return SrtDetectedLanguage.TELUGU to 92
        }

        if (latinCount >= devanagariCount) {
            val words = dialogueText.lowercase(Locale.ROOT)
                .split(Regex("[^a-z ]+"))
                .filter { it.length in 2..15 }

            var enScore = 0
            var esScore = 0
            var frScore = 0
            var deScore = 0

            for (w in words) {
                if (ENGLISH_WORDS.contains(w)) enScore++
                if (SPANISH_WORDS.contains(w)) esScore++
                if (FRENCH_WORDS.contains(w)) frScore++
                if (GERMAN_WORDS.contains(w)) deScore++
            }

            val maxScore = maxOf(enScore, esScore, frScore, deScore)
            val latinConfidence = if (words.isNotEmpty()) {
                ((maxScore * 100) / words.size.coerceAtLeast(1) * 3).coerceIn(65, 98)
            } else 80

            return when {
                maxScore == 0 || enScore >= maxScore -> SrtDetectedLanguage.ENGLISH_LATIN to latinConfidence
                esScore > enScore && esScore >= frScore && esScore >= deScore -> SrtDetectedLanguage.SPANISH to latinConfidence
                frScore > enScore && frScore >= esScore && frScore >= deScore -> SrtDetectedLanguage.FRENCH to latinConfidence
                deScore > enScore -> SrtDetectedLanguage.GERMAN to latinConfidence
                else -> SrtDetectedLanguage.ENGLISH_LATIN to latinConfidence
            }
        }

        return SrtDetectedLanguage.OTHER_LANGUAGE to 60
    }

    fun shouldPromptForAutoTranslation(detection: SrtLanguageDetection?): Boolean {
        if (detection == null) return false
        if (detection.detectedLanguage == SrtDetectedLanguage.EMPTY) return false
        return !detection.isHindiCompliant
    }
}
