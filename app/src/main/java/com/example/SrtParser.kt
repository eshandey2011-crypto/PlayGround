package com.example

import java.util.regex.Pattern

enum class SrtDetectedLanguage(
    val displayName: String,
    val scriptName: String,
    val adviceHindiTts: String,
    val languageCode: String = "und",
    val flagEmoji: String = "🌐"
) {
    HINDI_DEVANAGARI(
        "Hindi (Devanagari)",
        "Devanagari Script",
        "This subtitle is in Devanagari script. TTS engine will voice it smoothly.",
        "hi",
        "🇮🇳"
    ),
    ENGLISH_LATIN(
        "English (Latin)",
        "Latin / Roman Script",
        "This subtitle is in English. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "en",
        "🇺🇸"
    ),
    SPANISH(
        "Spanish",
        "Latin Script",
        "This subtitle is in Spanish. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "es",
        "🇪🇸"
    ),
    FRENCH(
        "French",
        "Latin Script",
        "This subtitle is in French. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "fr",
        "🇫🇷"
    ),
    GERMAN(
        "German",
        "Latin Script",
        "This subtitle is in German. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "de",
        "🇩🇪"
    ),
    JAPANESE(
        "Japanese",
        "Kanji / Kana Script",
        "This subtitle is in Japanese. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "ja",
        "🇯🇵"
    ),
    CHINESE(
        "Chinese",
        "Hanzi Script",
        "This subtitle is in Chinese. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "zh",
        "🇨🇳"
    ),
    KOREAN(
        "Korean",
        "Hangul Script",
        "This subtitle is in Korean. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "ko",
        "🇰🇷"
    ),
    ARABIC(
        "Arabic",
        "Arabic Script",
        "This subtitle is in Arabic. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "ar",
        "🇸🇦"
    ),
    RUSSIAN(
        "Russian",
        "Cyrillic Script",
        "This subtitle is in Cyrillic script. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "ru",
        "🇷🇺"
    ),
    BENGALI(
        "Bengali",
        "Bengali Script",
        "This subtitle is in Bengali script. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "bn",
        "🇧🇩"
    ),
    TAMIL(
        "Tamil",
        "Tamil Script",
        "This subtitle is in Tamil script. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "ta",
        "🇮🇳"
    ),
    TELUGU(
        "Telugu",
        "Telugu Script",
        "This subtitle is in Telugu script. Auto-translation to Hindi is recommended for synced Hindi dubbing.",
        "te",
        "🇮🇳"
    ),
    MIXED_HINGLISH(
        "Hinglish / Mixed Script",
        "Devanagari + Latin Mixed",
        "This subtitle contains both Devanagari and Roman scripts.",
        "hi",
        "🇮🇳"
    ),
    OTHER_LANGUAGE(
        "Other Language (Non-Hindi)",
        "Non-Hindi Script",
        "This subtitle is in an alternative language. Auto-translation to Hindi is recommended.",
        "und",
        "🌐"
    ),
    EMPTY(
        "Empty Subtitle",
        "No Script",
        "No dialogue text found in subtitle.",
        "und",
        "📄"
    );

    val code: String get() = languageCode
}

data class SrtLanguageDetection(
    val isHindiCompliant: Boolean,
    val detectedLanguage: SrtDetectedLanguage,
    val hindiPercentage: Int,
    val devanagariCount: Int,
    val latinCount: Int,
    val otherScriptCount: Int,
    val totalSubtitles: Int,
    val sampleSnippet: String,
    val confidencePercent: Int = 95,
    val previewHindiTranslation: String = ""
) {
    val isHindi: Boolean get() = isHindiCompliant
}

object SrtParser {
    private val TIME_PATTERN = Pattern.compile(
        "(\\d{1,4}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,4}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
    )
    private val SPEAKER_TAG_REGEX = Regex("^(\\[[^\\]]+\\]:?|\\([^\\)]+\\):?|[A-Za-z0-9_\\s\\u0900-\\u097F]{2,25}:)\\s*")
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val VTT_VOICE_REGEX = Regex("<v(?:\\.[a-zA-Z0-9_-]+)*\\s+([^>]+)>", RegexOption.IGNORE_CASE)
    private val ASS_OVERRIDE_REGEX = Regex("\\{[^}]*\\}")
    private val ASS_DRAWING_REGEX = Regex("\\{\\\\p[1-9]\\}.*?(\\{\\\\p0\\}|\$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    data class AssStyle(
        val name: String,
        val fontName: String = "Arial",
        val fontSize: Float = 22f,
        val primaryColorArgb: Long = 0xFFFFFFFF,
        val outlineColorArgb: Long = 0xFF000000,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val alignment: Int = 2
    )

    fun parseAssColor(raw: String?, defaultColor: Long = 0xFFFFFFFF): Long {
        if (raw.isNullOrBlank()) return defaultColor
        val trimmed = raw.trim().removeSurrounding("&H", "&").removePrefix("&h").removePrefix("&H")
        return try {
            if (trimmed.startsWith("#")) {
                val hex = trimmed.substring(1)
                if (hex.length == 6) {
                    0xFF000000L or hex.toLong(16)
                } else if (hex.length == 8) {
                    hex.toLong(16)
                } else defaultColor
            } else {
                val cleanHex = trimmed.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                when (cleanHex.length) {
                    6 -> {
                        val b = cleanHex.substring(0, 2).toInt(16)
                        val g = cleanHex.substring(2, 4).toInt(16)
                        val r = cleanHex.substring(4, 6).toInt(16)
                        0xFF000000L or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
                    }
                    8 -> {
                        val aAss = cleanHex.substring(0, 2).toInt(16)
                        val alpha = (255 - aAss).coerceIn(0, 255)
                        val b = cleanHex.substring(2, 4).toInt(16)
                        val g = cleanHex.substring(4, 6).toInt(16)
                        val r = cleanHex.substring(6, 8).toInt(16)
                        ((alpha.toLong() and 0xFF) shl 24) or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
                    }
                    else -> {
                        val dec = raw.trim().toLongOrNull()
                        if (dec != null) {
                            val b = ((dec shr 16) and 0xFF).toInt()
                            val g = ((dec shr 8) and 0xFF).toInt()
                            val r = (dec and 0xFF).toInt()
                            0xFF000000L or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
                        } else {
                            defaultColor
                        }
                    }
                }
            }
        } catch (e: Exception) {
            defaultColor
        }
    }

    private val defaultDetector: ISpeakerDetector = RuleBasedHindiSpeakerDetector()

    fun parse(content: String, detector: ISpeakerDetector = defaultDetector, fileName: String? = null): List<SrtItem> {
        if (content.isBlank()) return emptyList()
        val format = detectFormat(content, fileName)
        val items = when (format) {
            SubtitleFormat.VTT -> parseVtt(content, detector)
            SubtitleFormat.ASS, SubtitleFormat.SSA -> parseAss(content, detector)
            SubtitleFormat.SRT -> parseSrt(content, detector)
        }
        if (items.isNotEmpty()) return items

        return if (format == SubtitleFormat.ASS || format == SubtitleFormat.SSA) {
            val srtFallback = parseSrt(content, detector)
            if (srtFallback.isNotEmpty()) srtFallback else parseVtt(content, detector)
        } else if (format == SubtitleFormat.VTT) {
            val srtFallback = parseSrt(content, detector)
            if (srtFallback.isNotEmpty()) srtFallback else parseAss(content, detector)
        } else {
            val assFallback = parseAss(content, detector)
            if (assFallback.isNotEmpty()) assFallback else parseVtt(content, detector)
        }
    }

    fun detectFormat(content: String, fileName: String? = null): SubtitleFormat {
        val cleanName = fileName?.substringBefore('?')?.substringBefore('#')
        val ext = cleanName?.substringAfterLast('.', "")?.lowercase()
        if (ext == "vtt") return SubtitleFormat.VTT
        if (ext == "ass") return SubtitleFormat.ASS
        if (ext == "ssa") return SubtitleFormat.SSA
        if (ext == "srt") return SubtitleFormat.SRT

        val normalized = content.trimStart().removePrefix("\uFEFF").trimStart()
        if (normalized.startsWith("WEBVTT", ignoreCase = true)) {
            return SubtitleFormat.VTT
        }
        val lowerContent = normalized.lowercase()
        if (lowerContent.contains("[events]") ||
            lowerContent.contains("[script info]") ||
            lowerContent.contains("scripttype:") ||
            lowerContent.contains("[v4+ styles]") ||
            lowerContent.contains("[v4 styles]") ||
            Regex("^dialogue\\s*:", RegexOption.MULTILINE).containsMatchIn(lowerContent)
        ) {
            return if (lowerContent.contains("[v4+ styles]") ||
                lowerContent.contains("v4.00+") ||
                ext == "ass"
            ) SubtitleFormat.ASS else SubtitleFormat.SSA
        }
        return SubtitleFormat.SRT
    }

    fun parseSrt(content: String, detector: ISpeakerDetector = defaultDetector): List<SrtItem> {
        val items = mutableListOf<SrtItem>()
        if (content.isBlank()) return items
        val normalized = content.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split(Regex("\n{2,}"))
        var generatedIndex = 1

        for (rawBlock in blocks) {
            val block = rawBlock.trim()
            if (block.isEmpty()) continue
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLineIndex = -1
            var startTimeMs = -1L
            var endTimeMs = -1L

            for (i in lines.indices) {
                val matcher = TIME_PATTERN.matcher(lines[i])
                if (matcher.find()) {
                    timeLineIndex = i
                    startTimeMs = parseTimestamp(
                        matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)
                    )
                    endTimeMs = parseTimestamp(
                        matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8)
                    )
                    break
                }
            }

            if (timeLineIndex != -1 && startTimeMs >= 0 && endTimeMs >= startTimeMs) {
                val parsedIndex = if (timeLineIndex > 0) {
                    lines[0].toIntOrNull() ?: generatedIndex
                } else {
                    generatedIndex
                }
                val textLines = lines.subList(timeLineIndex + 1, lines.size)
                val cleanText = textLines.joinToString(" ")
                    .replace(HTML_TAG_REGEX, "")
                    .unescapeHtmlEntities()
                    .trim()

                if (cleanText.isNotEmpty()) {
                    val speakerTagMatch = SPEAKER_TAG_REGEX.find(cleanText)
                    val speakerTag = speakerTagMatch?.value?.trim()
                        ?.removeSuffix(":")
                        ?.trim()
                        ?.removeSurrounding("[", "]")
                        ?.removeSurrounding("(", ")")
                        ?.removeSuffix(":")
                        ?.trim()
                    val cleanSpoken = if (speakerTagMatch != null) {
                        cleanText.substring(speakerTagMatch.range.last + 1).trim()
                    } else {
                        cleanText
                    }

                    items.add(
                        SrtItem(
                            index = parsedIndex,
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs,
                            text = cleanText,
                            cleanSpokenText = cleanSpoken,
                            speakerTag = speakerTag
                        )
                    )
                    generatedIndex++
                }
            }
        }
        val sorted = items.sortedBy { it.startTimeMs }
        return detector.processSubtitles(sorted)
    }

    fun parseVtt(content: String, detector: ISpeakerDetector = defaultDetector): List<SrtItem> {
        val items = mutableListOf<SrtItem>()
        if (content.isBlank()) return items
        val normalized = content.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val rawBlocks = normalized.split(Regex("\n{2,}"))
        var generatedIndex = 1

        for (rawBlock in rawBlocks) {
            val block = rawBlock.trim()
            if (block.isEmpty()) continue
            if (block.startsWith("WEBVTT", ignoreCase = true) && !block.contains("-->")) {
                continue
            }
            if (block.startsWith("NOTE", ignoreCase = true) ||
                block.startsWith("STYLE", ignoreCase = true) ||
                block.startsWith("REGION", ignoreCase = true)
            ) {
                continue
            }

            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLineIndex = -1
            var startTimeMs = -1L
            var endTimeMs = -1L

            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains("-->")) {
                    val arrowIdx = line.indexOf("-->")
                    val startToken = line.substring(0, arrowIdx).trim()
                    val afterArrow = line.substring(arrowIdx + 3).trim()
                    val endToken = afterArrow.split(Regex("\\s+"))[0].trim()
                    val startMs = parseVttTimestamp(startToken)
                    val endMs = parseVttTimestamp(endToken)
                    if (startMs >= 0 && endMs >= startMs) {
                        timeLineIndex = i
                        startTimeMs = startMs
                        endTimeMs = endMs
                        break
                    }
                }
            }

            if (timeLineIndex != -1 && startTimeMs >= 0 && endTimeMs >= startTimeMs) {
                val parsedIndex = if (timeLineIndex > 0) {
                    lines[0].toIntOrNull() ?: generatedIndex
                } else {
                    generatedIndex
                }
                val textLines = lines.subList(timeLineIndex + 1, lines.size)
                var rawPayload = textLines.joinToString(" ").trim()
                var extractedSpeakerTag: String? = null
                val voiceMatch = VTT_VOICE_REGEX.find(rawPayload)
                if (voiceMatch != null) {
                    val voiceName = voiceMatch.groupValues[1].trim()
                    extractedSpeakerTag = voiceName
                    val contentWithoutVoiceTag = rawPayload
                        .replace(VTT_VOICE_REGEX, "")
                        .replace("</v>", "", ignoreCase = true)
                        .trim()
                    val hasExistingSpeaker = contentWithoutVoiceTag.startsWith("[") ||
                            contentWithoutVoiceTag.startsWith("(") ||
                            contentWithoutVoiceTag.contains(Regex("^[^:]+:\\s*"))
                    rawPayload = if (hasExistingSpeaker) contentWithoutVoiceTag else "[$voiceName]: $contentWithoutVoiceTag"
                }

                val cleanText = rawPayload
                    .replace(HTML_TAG_REGEX, "")
                    .unescapeHtmlEntities()
                    .trim()

                if (cleanText.isNotEmpty()) {
                    val speakerMatch = SPEAKER_TAG_REGEX.find(cleanText)
                    val speakerTag = extractedSpeakerTag ?: speakerMatch?.value?.trim()
                        ?.removeSuffix(":")
                        ?.trim()
                        ?.removeSurrounding("[", "]")
                        ?.removeSurrounding("(", ")")
                        ?.removeSuffix(":")
                        ?.trim()
                    val cleanSpoken = if (speakerMatch != null) {
                        cleanText.substring(speakerMatch.range.last + 1).trim()
                    } else {
                        cleanText
                    }

                    items.add(
                        SrtItem(
                            index = parsedIndex,
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs,
                            text = cleanText,
                            cleanSpokenText = cleanSpoken,
                            speakerTag = speakerTag
                        )
                    )
                    generatedIndex++
                }
            }
        }
        val sorted = items.sortedBy { it.startTimeMs }
        return detector.processSubtitles(sorted)
    }

    fun parseAss(content: String, detector: ISpeakerDetector = defaultDetector): List<SrtItem> {
        val items = mutableListOf<SrtItem>()
        if (content.isBlank()) return items
        val normalized = content.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.lines()
        var currentSection = ""
        val stylesMap = mutableMapOf<String, AssStyle>()
        var styleFormatCols = listOf<String>()
        var startCol = 1
        var endCol = 2
        var styleCol = 3
        var nameCol = 4
        var textCol = 9
        var numColumns = 10
        var generatedIndex = 1

        val sectionRegex = Regex("^\\[([^\\]]+)\\]$")
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";")) continue

            val sectionMatch = sectionRegex.find(line)
            if (sectionMatch != null) {
                currentSection = sectionMatch.groupValues[1].trim().lowercase()
                continue
            }

            if (currentSection == "v4+ styles" || currentSection == "v4 styles" || currentSection == "styles") {
                if (line.startsWith("Format:", ignoreCase = true)) {
                    styleFormatCols = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                    continue
                }
                if (line.startsWith("Style:", ignoreCase = true)) {
                    val payload = line.substringAfter(':').trim()
                    val parts = payload.split(',')
                    val nameIdx = styleFormatCols.indexOf("name").takeIf { it >= 0 } ?: 0
                    val fontIdx = styleFormatCols.indexOf("fontname").takeIf { it >= 0 } ?: 1
                    val sizeIdx = styleFormatCols.indexOf("fontsize").takeIf { it >= 0 } ?: 2
                    val primColorIdx = styleFormatCols.indexOfFirst { it.contains("primary") }.takeIf { it >= 0 } ?: 3
                    val outColorIdx = styleFormatCols.indexOfFirst { it.contains("outline") }.takeIf { it >= 0 } ?: 5
                    val boldIdx = styleFormatCols.indexOf("bold").takeIf { it >= 0 } ?: 7
                    val italicIdx = styleFormatCols.indexOf("italic").takeIf { it >= 0 } ?: 8
                    val alignIdx = styleFormatCols.indexOf("alignment").takeIf { it >= 0 } ?: 18

                    val styleName = parts.getOrNull(nameIdx)?.trim().orEmpty()
                    if (styleName.isNotBlank()) {
                        val fontName = parts.getOrNull(fontIdx)?.trim() ?: "Arial"
                        val fontSize = parts.getOrNull(sizeIdx)?.trim()?.toFloatOrNull() ?: 22f
                        val primColor = parseAssColor(parts.getOrNull(primColorIdx), 0xFFFFFFFF)
                        val outColor = parseAssColor(parts.getOrNull(outColorIdx), 0xFF000000)
                        val isBold = parts.getOrNull(boldIdx)?.trim() == "-1" || parts.getOrNull(boldIdx)?.trim() == "1"
                        val isItalic = parts.getOrNull(italicIdx)?.trim() == "-1" || parts.getOrNull(italicIdx)?.trim() == "1"
                        val alignment = parts.getOrNull(alignIdx)?.trim()?.toIntOrNull() ?: 2

                        stylesMap[styleName.lowercase()] = AssStyle(
                            name = styleName,
                            fontName = fontName,
                            fontSize = fontSize,
                            primaryColorArgb = primColor,
                            outlineColorArgb = outColor,
                            isBold = isBold,
                            isItalic = isItalic,
                            alignment = alignment
                        )
                    }
                    continue
                }
            }

            val isDialogueLine = line.startsWith("Dialogue", ignoreCase = true) && line.contains(":")
            val isFormatLine = line.startsWith("Format", ignoreCase = true) && line.contains(":")

            if (currentSection == "events" || currentSection.isEmpty() || isDialogueLine || isFormatLine) {
                if (isFormatLine) {
                    val cols = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                    numColumns = cols.size
                    startCol = cols.indexOf("start").takeIf { it >= 0 } ?: 1
                    endCol = cols.indexOf("end").takeIf { it >= 0 } ?: 2
                    styleCol = cols.indexOf("style").takeIf { it >= 0 } ?: 3
                    nameCol = cols.indexOfFirst { it == "name" || it == "actor" }.takeIf { it >= 0 } ?: 4
                    textCol = cols.indexOf("text").takeIf { it >= 0 } ?: cols.lastIndex
                    continue
                }
                if (isDialogueLine) {
                    val payload = line.substringAfter(':').trim()
                    val limit = maxOf(textCol + 1, numColumns)
                    val parts = payload.split(',', limit = limit)
                    if (parts.size <= maxOf(startCol, endCol)) continue

                    val startToken = parts.getOrNull(startCol)?.trim() ?: continue
                    val endToken = parts.getOrNull(endCol)?.trim() ?: continue
                    val startTimeMs = parseAssTimestamp(startToken)
                    val endTimeMs = parseAssTimestamp(endToken)
                    if (startTimeMs < 0 || endTimeMs < startTimeMs) continue

                    val rawStyle = parts.getOrNull(styleCol)?.trim().orEmpty()
                    val rawName = parts.getOrNull(nameCol)?.trim().orEmpty()
                    val rawText = parts.getOrNull(textCol)?.trim().orEmpty()

                    var cleanText = rawText.replace(ASS_DRAWING_REGEX, "")
                    cleanText = cleanText
                        .replace(ASS_OVERRIDE_REGEX, "")
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .replace("\\h", " ")
                        .replace(HTML_TAG_REGEX, "")
                        .unescapeHtmlEntities()
                        .trim()

                    val matchedStyle = stylesMap[rawStyle.lowercase()]
                    var speakerTag: String? = null
                    val speakerColor: Long? = matchedStyle?.primaryColorArgb

                    if (rawName.isNotBlank() && !rawName.equals("Default", ignoreCase = true)) {
                        speakerTag = rawName
                        val hasExistingSpeaker = cleanText.startsWith("[") || cleanText.startsWith("(") ||
                                cleanText.contains(Regex("^[^:]+:\\s*"))
                        if (!hasExistingSpeaker) {
                            cleanText = "[$rawName]: $cleanText"
                        }
                    } else if (rawStyle.isNotBlank() && isCharacterStyle(rawStyle)) {
                        speakerTag = rawStyle
                        val hasExistingSpeaker = cleanText.startsWith("[") || cleanText.startsWith("(") ||
                                cleanText.contains(Regex("^[^:]+:\\s*"))
                        if (!hasExistingSpeaker) {
                            cleanText = "[$rawStyle]: $cleanText"
                        }
                    }

                    if (cleanText.isNotEmpty()) {
                        val speakerMatch = SPEAKER_TAG_REGEX.find(cleanText)
                        val resolvedSpeakerTag = speakerTag ?: speakerMatch?.value?.trim()
                            ?.removeSuffix(":")
                            ?.trim()
                            ?.removeSurrounding("[", "]")
                            ?.removeSurrounding("(", ")")
                            ?.removeSuffix(":")
                            ?.trim()
                        val rawDialogue = if (speakerMatch != null) {
                            cleanText.substring(speakerMatch.range.last + 1).trim()
                        } else {
                            cleanText
                        }

                        val cleanSpoken = rawDialogue
                            .replace("\n", " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()

                        val speakerProfile = if (resolvedSpeakerTag != null) {
                            SpeakerProfile(
                                id = "spk_${resolvedSpeakerTag.lowercase().filter { it.isLetterOrDigit() }}",
                                name = resolvedSpeakerTag,
                                avatarColorHex = speakerColor ?: 0xFF00E5FF,
                                isAiDetected = false,
                                detectionTag = "ASS"
                            )
                        } else null

                        items.add(
                            SrtItem(
                                index = generatedIndex++,
                                startTimeMs = startTimeMs,
                                endTimeMs = endTimeMs,
                                text = cleanText,
                                cleanSpokenText = cleanSpoken,
                                speakerTag = resolvedSpeakerTag,
                                speaker = speakerProfile
                            )
                        )
                    }
                }
            }
        }
        val sorted = items.sortedBy { it.startTimeMs }
        return detector.processSubtitles(sorted)
    }

    private fun isCharacterStyle(style: String): Boolean {
        val lower = style.lowercase()
        return lower !in setOf(
            "default", "defaultstyle", "main", "top", "bottom", "title",
            "sign", "signs", "alt", "standard", "normal", "italics", "subtitle"
        )
    }

    fun parseVttTimestamp(token: String): Long {
        val parts = token.trim().replace(',', '.').split(':')
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split('.')
                val seconds = secParts[0].toLongOrNull() ?: 0L
                val fracStr = secParts.getOrNull(1) ?: "0"
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split('.')
                val seconds = secParts[0].toLongOrNull() ?: 0L
                val fracStr = secParts.getOrNull(1) ?: "0"
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                (minutes * 60 + seconds) * 1000 + millis
            }
            else -> 0L
        }
    }

    fun parseAssTimestamp(token: String): Long {
        val parts = token.trim().split(':')
        if (parts.size < 2) return 0L
        val hours = if (parts.size >= 3) parts[0].toLongOrNull() ?: 0L else 0L
        val minutes = if (parts.size >= 3) parts[1].toLongOrNull() ?: 0L else parts[0].toLongOrNull() ?: 0L
        val secStr = if (parts.size >= 3) parts[2] else parts[1]
        val secParts = secStr.replace(',', '.').split('.')
        val seconds = secParts[0].toLongOrNull() ?: 0L
        val fracStr = secParts.getOrNull(1) ?: "0"
        val millis = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    }

    fun exportToSrt(items: List<SrtItem>): String {
        val sb = StringBuilder()
        for ((idx, item) in items.withIndex()) {
            sb.append(idx + 1).append("\n")
            sb.append(formatSrtTimestamp(item.startTimeMs))
                .append(" --> ")
                .append(formatSrtTimestamp(item.endTimeMs))
                .append("\n")
            sb.append(item.text).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    fun exportToVtt(items: List<SrtItem>): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        for ((idx, item) in items.withIndex()) {
            sb.append(idx + 1).append("\n")
            sb.append(formatVttTimestamp(item.startTimeMs))
                .append(" --> ")
                .append(formatVttTimestamp(item.endTimeMs))
                .append("\n")
            val speakerTag = item.speakerTag ?: item.speaker?.name
            val dialogue = if (speakerTag != null) {
                val candidate = item.cleanSpokenText.ifBlank { item.text }
                candidate.replace(Regex("^[\\[\\(][^\\]\\)]+[\\]\\)]:?\\s*"), "").trim()
            } else {
                item.text
            }
            if (speakerTag != null && !dialogue.contains("<v")) {
                sb.append("<v ").append(speakerTag).append(">")
                    .append(dialogue)
                    .append("</v>\n\n")
            } else {
                sb.append(item.text).append("\n\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun exportToAss(items: List<SrtItem>, title: String = "Exported Subtitles"): String {
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        sb.append("Title: ").append(title).append("\n")
        sb.append("ScriptType: v4.00+\n")
        sb.append("WrapStyle: 0\n")
        sb.append("ScaledBorderAndShadow: yes\n")
        sb.append("PlayResX: 1920\nPlayResY: 1080\n\n")
        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        sb.append("Style: Default,Arial,22,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n")

        val palette = listOf(
            "&H0000FFFF",
            "&H00FF00FF",
            "&H00FFFF00",
            "&H0000FF00",
            "&H000080FF",
            "&H00FF8000"
        )
        val distinctSpeakers = items.mapNotNull { it.speakerTag ?: it.speaker?.name }
            .filter { it.isNotBlank() && !it.equals("Default", ignoreCase = true) }
            .distinct()

        for ((idx, spk) in distinctSpeakers.withIndex()) {
            val color = palette[idx % palette.size]
            val safeName = spk.replace(',', '_')
            sb.append("Style: ").append(safeName).append(",Arial,22,")
                .append(color).append(",&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n")
        }

        sb.append("\n[Events]\n")
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for (item in items) {
            val speaker = item.speakerTag ?: item.speaker?.name ?: ""
            val dialogue = if (speaker.isNotBlank()) {
                val candidate = item.cleanSpokenText.ifBlank { item.text }
                candidate.replace(Regex("^[\\[\\(][^\\]\\)]+[\\]\\)]:?\\s*"), "").trim()
            } else {
                item.text
            }
            val styleName = if (speaker.isNotBlank() && distinctSpeakers.contains(speaker)) speaker.replace(',', '_') else "Default"
            val escapedDialogue = dialogue.replace("\r", "").replace("\n", "\\N")
            sb.append("Dialogue: 0,")
                .append(formatAssTimestamp(item.startTimeMs)).append(",")
                .append(formatAssTimestamp(item.endTimeMs)).append(",")
                .append(styleName).append(",")
                .append(speaker).append(",")
                .append("0,0,0,,")
                .append(escapedDialogue)
                .append("\n")
        }
        return sb.toString().trimEnd()
    }

    fun exportAs(items: List<SrtItem>, format: SubtitleFormat): String {
        return when (format) {
            SubtitleFormat.SRT -> exportToSrt(items)
            SubtitleFormat.VTT -> exportToVtt(items)
            SubtitleFormat.ASS, SubtitleFormat.SSA -> exportToAss(items)
        }
    }

    fun exportToPlainText(items: List<SrtItem>): String {
        val sb = StringBuilder()
        for (item in items) {
            val speaker = item.speakerTag ?: item.speaker?.name
            val text = item.cleanSpokenText.ifBlank { item.text }
            if (speaker != null && speaker.isNotBlank()) {
                sb.append("[$speaker]: ")
            }
            sb.append(text).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    fun exportToPlainText(content: String): String {
        val items = parse(content)
        if (items.isNotEmpty()) {
            return exportToPlainText(items)
        }
        return content
    }

    fun convertTo(content: String, targetFormat: SubtitleFormat): String {
        val items = parse(content)
        return exportAs(items, targetFormat)
    }

    fun formatSrtTimestamp(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }

    fun formatVttTimestamp(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }

    fun formatAssTimestamp(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val cs = (ms % 1000) / 10
        return String.format(java.util.Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun String.unescapeHtmlEntities(): String {
        return this.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    private fun parseTimestamp(hStr: String?, mStr: String?, sStr: String?, msStr: String?): Long {
        val hours = hStr?.toLongOrNull() ?: 0L
        val minutes = mStr?.toLongOrNull() ?: 0L
        val seconds = sStr?.toLongOrNull() ?: 0L
        var millis = msStr?.toLongOrNull() ?: 0L
        if (msStr != null && msStr.length == 1) millis *= 100
        else if (msStr != null && msStr.length == 2) millis *= 10
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    }

    val sampleHindiOfficeSrt = """
1
00:00:00,800 --> 00:00:04,500
[राहुल]: नमस्ते टीम, आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है।

2
00:00:05,200 --> 00:00:09,800
[प्रिया]: धन्यवाद राहुल, डेवलपमेंट टीम ने नया सॉफ्टवेयर मॉड्यूल पूरा कर लिया है।

3
00:00:10,500 --> 00:00:15,200
[विक्रम]: क्लाइंट का फीडबैक बहुत सकारात्मक है; हमें यूजर इंटरफेस को सरल रखना होगा।

4
00:00:16,000 --> 00:00:20,800
[सुनीता]: मैं इस सप्ताह के अंत तक डिज़ाइन और टेस्टिंग रिपोर्ट जमा कर दूंगी।

5
00:00:21,500 --> 00:00:26,000
(सूत्रधार): यह उत्पाद अगले महीने के पहले दिन लॉन्च किया जाएगा।
""".trimIndent()

    val sampleOfficeSrt = """
1
00:00:00,800 --> 00:00:04,500
[Rahul]: Hello team, welcome to our project review meeting today.

2
00:00:05,200 --> 00:00:09,800
[Priya]: Thank you Rahul, the development team has completed the new software module.

3
00:00:10,500 --> 00:00:15,200
[Vikram]: Client feedback is very positive; we need to keep the user interface simple.

4
00:00:16,000 --> 00:00:20,800
[Sunita]: I will submit the design and testing report by the end of this week.

5
00:00:21,500 --> 00:00:26,000
(Narrator): This product will be launched on the first day of next month.
""".trimIndent()

    val sampleNatureSrt = """
1
00:00:01,000 --> 00:00:04,500
(Narrator): Look at this calm and clear river flowing in the lap of nature.

2
00:00:05,000 --> 00:00:09,200
[Rohan]: Among the lush green forests, the morning sun brings fresh energy.

3
00:00:10,000 --> 00:00:14,800
[Priya]: The blanket of snow on the mountain peaks reflects the beauty of nature.

4
00:00:15,500 --> 00:00:19,500
[Rohan]: The chirping of birds makes the atmosphere even sweeter.

5
00:00:20,500 --> 00:00:25,000
(Narrator): Protecting the environment is the moral duty of us all.
""".trimIndent()

    val sampleStorySrt = """
1
00:00:00,800 --> 00:00:04,200
(Narrator): In a small village lived a courageous boy named Vikram.

2
00:00:05,000 --> 00:00:08,600
[Vikram]: I will play near the edge of the forest with my friends every day.

3
00:00:09,500 --> 00:00:14,000
[Sunita]: Hey Vikram, look! An injured bird has fallen in the bushes!

4
00:00:14,800 --> 00:00:18,500
[Vikram]: Do not fear Sunita, I will go and help it right now.

5
00:00:19,500 --> 00:00:24,000
(Narrator): True kindness, courage, and friendship make every challenge easier.
""".trimIndent()

    val sampleEnglishSrt = """
1
00:00:00,800 --> 00:00:04,500
[Narrator]: Hello team, welcome to our annual product review meeting.

2
00:00:05,200 --> 00:00:09,800
[Sarah]: First, we will discuss the primary project milestones and progress reports.

3
00:00:10,500 --> 00:00:15,200
[David]: The engineering team has successfully finished all critical modules.

4
00:00:16,000 --> 00:00:20,800
[Sarah]: According to client feedback, we should simplify the user navigation.

5
00:00:21,500 --> 00:00:26,000
[Narrator]: By next Friday, we will be ready for the final production release.
""".trimIndent()

    val sampleBengaliSrt = """
1
00:00:00,800 --> 00:00:04,500
[রাহুল]: নমস্কার সবাইকে, আজকের প্রজেক্ট মিটিংয়ে আপনাদের স্বাগতম।

2
00:00:05,200 --> 00:00:09,800
[প্রিয়া]: ধন্যবাদ রাহুল, ডেভেলপমেন্ট টিম সফলভাবে নতুন মডিউল তৈরি করেছে।

3
00:00:10,500 --> 00:00:15,200
[বিক্রম]: ক্লায়েন্টের প্রতিক্রিয়া খুব ইতিবাচক; আমাদের ইন্টারফেস সহজ রাখতে হবে।
""".trimIndent()

    val sampleMarathiSrt = """
1
00:00:00,800 --> 00:00:04,500
[राहुल]: नमस्कार टीम, आजच्या प्रकल्प आढावा बैठकीत सर्वांचे स्वागत आहे.

2
00:00:05,200 --> 00:00:09,800
[प्रिया]: धन्यवाद राहुल, विकास संघाने नवीन सॉफ्टवेअर मॉड्यूल पूर्ण केले आहे.

3
00:00:10,500 --> 00:00:15,200
[विक्रम]: ग्राहकांचा प्रतिसाद खूप सकारात्मक आहे; आम्हाला इंटरफेस साधा ठेवावा लागेल.
""".trimIndent()

    val sampleVtt = """
WEBVTT
Kind: captions
Language: en

NOTE Dialogue sample for WebVTT format testing

00:00:00.800 --> 00:00:04.500 position:50% align:middle
<v Rahul>Hello team, welcome to our project review meeting today.</v>

00:00:05.200 --> 00:00:09.800
<v Priya>Thank you Rahul, the development team has completed the new software module.</v>

00:00:10.500 --> 00:00:15.200
<v Vikram>Client feedback is very positive; we need to keep the user interface simple.</v>

00:00:16.000 --> 00:00:20.800
<v Sunita>I will submit the design and testing report by the end of this week.</v>

00:00:21.500 --> 00:00:26.000
<v Narrator>This product will be launched on the first day of next month.</v>
""".trimIndent()

    val sampleHindiVtt = """
WEBVTT
Language: hi

00:00:00.800 --> 00:00:04.500
<v राहुल>नमस्ते टीम, आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है।</v>

00:00:05.200 --> 00:00:09.800
<v प्रिया>धन्यवाद राहुल, डेवलपमेंट टीम ने नया सॉफ्टवेयर मॉड्यूल पूरा कर लिया है।</v>

00:00:10.500 --> 00:00:15.200
<v विक्रम>क्लाइंट का फीडबैक बहुत सकारात्मक है; हमें यूजर इंटरफेस को सरल रखना होगा।</v>

00:00:16.000 --> 00:00:20.800
<v सुनीता>मैं इस सप्ताह के अंत तक डिज़ाइन और टेस्टिंग रिपोर्ट जमा कर दूंगी।</v>

00:00:21.500 --> 00:00:26.000
<v सूत्रधार>यह उत्पाद अगले महीने के पहले दिन लॉन्च किया जाएगा।</v>
""".trimIndent()

    val sampleAss = """
[Script Info]
Title: Project Review Sample
ScriptType: v4.00+
WrapStyle: 0
ScaledBorderAndShadow: yes
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,22,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
Style: Rahul,Arial,22,&H0000FFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
Style: Priya,Arial,22,&H00FF00FF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.80,0:00:04.50,Rahul,Rahul,0,0,0,,{\pos(960,1000)}Hello team, welcome to our project review meeting today.
Dialogue: 0,0:00:05.20,0:00:09.80,Priya,Priya,0,0,0,,{\pos(960,1000)}Thank you Rahul, the development team has completed the new software module.
Dialogue: 0,0:00:10.50,0:00:15.20,Default,Vikram,0,0,0,,{\pos(960,1000)}Client feedback is very positive; we need to keep the user interface simple.
Dialogue: 0,0:00:16.00,0:00:20.80,Default,Sunita,0,0,0,,{\pos(960,1000)}I will submit the design and testing report by the end of this week.
Dialogue: 0,0:00:21.50,0:00:26.00,Default,Narrator,0,0,0,,{\pos(960,1000)}This product will be launched on the first day of next month.
""".trimIndent()

    val sampleHindiAss = """
[Script Info]
Title: Hindi Project Review Sample
ScriptType: v4.00+
WrapStyle: 0
ScaledBorderAndShadow: yes
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,22,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
Style: Rahul,Arial,22,&H0000FFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
Style: Priya,Arial,22,&H00FF00FF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.80,0:00:04.50,Default,राहुल,0,0,0,,{\pos(960,1000)}नमस्ते टीम, आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है।
Dialogue: 0,0:00:05.20,0:00:09.80,Default,प्रिया,0,0,0,,{\pos(960,1000)}धन्यवाद राहुल, डेवलपमेंट टीम ने नया सॉफ्टवेयर मॉड्यूल पूरा कर लिया है।
Dialogue: 0,0:00:10.50,0:00:15.20,Default,विक्रम,0,0,0,,{\pos(960,1000)}क्लाइंट का फीडबैक बहुत सकारात्मक है; हमें यूजर इंटरफेस को सरल रखना होगा।
Dialogue: 0,0:00:16.00,0:00:20.80,Default,सुनीता,0,0,0,,{\pos(960,1000)}मैं इस सप्ताह के अंत तक डिज़ाइन और टेस्टिंग रिपोर्ट जमा कर दूंगी।
Dialogue: 0,0:00:21.50,0:00:26.00,Default,सूत्रधार,0,0,0,,{\pos(960,1000)}यह उत्पाद अगले महीने के पहले दिन लॉन्च किया जाएगा।
""".trimIndent()

    fun detectLanguage(content: String, parsedItems: List<SrtItem> = emptyList()): SrtLanguageDetection {
        return SrtLanguageDetector.detect(content, parsedItems)
    }

    val sampleDemoVideos = emptyList<VideoSource>()
}
