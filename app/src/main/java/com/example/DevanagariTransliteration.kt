package com.example

/**
 * Robust Devanagari-to-Romanized phonetic transliterator for Text-To-Speech fallback.
 * Ensures offline subtitle reading sound ALWAYS works audibly, even on devices/emulators
 * where native Hindi TTS voice data is not downloaded or supported.
 */
object DevanagariTransliteration {
    private val independentVowels = mapOf(
        '\u0905' to "a",   // अ
        '\u0906' to "aa",  // आ
        '\u0907' to "i",   // इ
        '\u0908' to "ee",  // ई
        '\u0909' to "u",   // उ
        '\u090A' to "oo",  // ऊ
        '\u090B' to "ri",  // ऋ
        '\u090F' to "e",   // ए
        '\u0910' to "ai",  // ऐ
        '\u0913' to "o",   // ओ
        '\u0914' to "au"   // औ
    )

    private val matras = mapOf(
        '\u093E' to "aa",  // ा
        '\u093F' to "i",   // ि
        '\u0940' to "ee",  // ी
        '\u0941' to "u",   // ु
        '\u0942' to "oo",  // ू
        '\u0943' to "ri",  // ृ
        '\u0947' to "e",   // े
        '\u0948' to "ai",  // ै
        '\u094B' to "o",   // ो
        '\u094C' to "au"   // ौ
    )

    private val consonants = mapOf(
        '\u0915' to "k",   // क
        '\u0916' to "kh",  // ख
        '\u0917' to "g",   // ग
        '\u0918' to "gh",  // घ
        '\u0919' to "ng",  // ङ
        '\u091A' to "ch",  // च
        '\u091B' to "chh", // छ
        '\u091C' to "j",   // ज
        '\u091D' to "jh",  // झ
        '\u091E' to "ny",  // ञ
        '\u091F' to "t",   // ट
        '\u0920' to "th",  // ठ
        '\u0921' to "d",   // ड
        '\u0922' to "dh",  // ढ
        '\u0923' to "n",   // ण
        '\u0924' to "t",   // त
        '\u0925' to "th",  // थ
        '\u0926' to "d",   // द
        '\u0927' to "dh",  // ध
        '\u0928' to "n",   // न
        '\u092A' to "p",   // प
        '\u092B' to "ph",  // फ
        '\u092C' to "b",   // ब
        '\u092D' to "bh",  // भ
        '\u092E' to "m",   // म
        '\u092F' to "y",   // य
        '\u0930' to "r",   // र
        '\u0932' to "l",   // ल
        '\u0933' to "l",   // ळ
        '\u0935' to "v",   // व
        '\u0936' to "sh",  // श
        '\u0937' to "sh",  // ष
        '\u0938' to "s",   // स
        '\u0939' to "h",   // ह
        '\u0958' to "q",   // क़
        '\u0959' to "kh",  // ख़
        '\u095A' to "gh",  // ग़
        '\u095B' to "z",   // ज़
        '\u095C' to "r",   // ड़
        '\u095D' to "rh",  // ढ़
        '\u095E' to "f",   // फ़
        '\u095F' to "y"    // य़
    )

    private val hindiDigits = mapOf(
        '\u0966' to '0', '\u0967' to '1', '\u0968' to '2', '\u0969' to '3', '\u096A' to '4',
        '\u096B' to '5', '\u096C' to '6', '\u096D' to '7', '\u096E' to '8', '\u096F' to '9'
    )

    private const val VIRAMA = '\u094D'      // ् (Halant)
    private const val ANUSVARA = '\u0902'    // ं
    private const val CANDRABINDU = '\u0901' // ँ
    private const val VISARGA = '\u0903'     // ः
    private const val NUKTA = '\u093C'       // ़
    private const val DANDA = '\u0964'       // ।
    private const val DOUBLE_DANDA = '\u0965'// ॥

    /**
     * Checks if text contains any Devanagari characters.
     */
    fun hasDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    /**
     * Transliterates a Devanagari Hindi string to natural, phonetic Roman script.
     * Preserves punctuation, whitespace, and existing Latin text.
     */
    fun toPhoneticRoman(text: String): String {
        if (!hasDevanagari(text)) return text
        val sb = StringBuilder()
        val len = text.length
        var i = 0

        while (i < len) {
            val ch = text[i]

            // 1. Digits
            if (hindiDigits.containsKey(ch)) {
                sb.append(hindiDigits[ch])
                i++
                continue
            }

            // 2. Danda / Punctuation
            if (ch == DANDA || ch == DOUBLE_DANDA) {
                sb.append(". ")
                i++
                continue
            }

            // 3. Independent Vowels
            if (independentVowels.containsKey(ch)) {
                sb.append(independentVowels[ch])
                i++
                continue
            }

            // 4. Consonants
            if (consonants.containsKey(ch)) {
                val consonantStr = consonants[ch] ?: ""
                sb.append(consonantStr)

                // Check next character for Halant (Virama), Matra, Anusvara, or end of syllable
                var nextIdx = i + 1
                // Skip Nukta if combined
                if (nextIdx < len && text[nextIdx] == NUKTA) {
                    nextIdx++
                }

                if (nextIdx < len) {
                    val nextCh = text[nextIdx]
                    when {
                        nextCh == VIRAMA -> {
                            // Halant: suppress inherent 'a'
                            i = nextIdx + 1
                            continue
                        }
                        matras.containsKey(nextCh) -> {
                            // Matra: replace inherent 'a' with the matra vowel
                            sb.append(matras[nextCh])
                            i = nextIdx + 1
                            continue
                        }
                        nextCh == ANUSVARA || nextCh == CANDRABINDU -> {
                            sb.append("an")
                            i = nextIdx + 1
                            continue
                        }
                        nextCh == VISARGA -> {
                            sb.append("ah")
                            i = nextIdx + 1
                            continue
                        }
                        consonants.containsKey(nextCh) || independentVowels.containsKey(nextCh) -> {
                            sb.append("a")
                            i = nextIdx
                            continue
                        }
                        else -> {
                            // End of word or whitespace/punctuation
                            i = nextIdx
                            continue
                        }
                    }
                } else {
                    // Word-final consonant: omit inherent 'a'
                    i++
                    continue
                }
            }

            // 5. Standalone Matras / Modifiers (if any)
            if (matras.containsKey(ch)) {
                sb.append(matras[ch])
                i++
                continue
            }
            if (ch == ANUSVARA || ch == CANDRABINDU) {
                sb.append("n")
                i++
                continue
            }
            if (ch == VISARGA) {
                sb.append("h")
                i++
                continue
            }
            if (ch == VIRAMA || ch == NUKTA) {
                i++
                continue
            }

            // 6. Non-Devanagari characters (Latin, numbers, spaces, emojis)
            sb.append(ch)
            i++
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
