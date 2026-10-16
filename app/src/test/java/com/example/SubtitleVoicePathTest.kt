package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Guards the subtitle voice path against word-skipping:
 * parse -> dialogue text -> Hindi translation must preserve every word.
 */
class SubtitleVoicePathTest {

    private val sampleSrt = """
1
00:00:01,000 --> 00:00:04,500
[Rahul]: Hello friends, welcome to our project review meeting today.

2
00:00:05,000 --> 00:00:08,800
[Priya]: The development team has completed the new software module.

3
00:00:09,200 --> 00:00:13,500
[Narrator]: Look at this calm and clear river flowing in the lap of nature.
""".trimIndent()

    private fun words(s: String): List<String> =
        s.split(Regex("\\s+")).map { it.trim().filter { c -> c.isLetterOrDigit() }.lowercase() }
            .filter { it.isNotBlank() }

    @Test
    fun parse_preservesEveryDialogueWord() {
        val items = SrtParser.parse(sampleSrt)
        assertEquals(3, items.size)
        // Speaker tags extracted, dialogue intact.
        assertEquals("Rahul", items[0].speakerTag)
        for (item in items) {
            val displayWords = words(item.text)
            val spokenWords = words(item.cleanSpokenText)
            // cleanSpokenText = dialogue without the leading tag; every
            // dialogue word must survive (only the tag name may differ).
            for (w in spokenWords) {
                assertTrue("spoken word '$w' missing from display text", displayWords.contains(w))
            }
            assertTrue("dialogue became empty: ${item.text}", spokenWords.isNotEmpty())
        }
    }

    @Test
    fun parse_keepsMidSentenceBrackets() {
        val srt = "1\n00:00:01,000 --> 00:00:04,000\nHello [laughs] how are you doing today\n"
        val items = SrtParser.parse(srt)
        assertEquals(1, items.size)
        // Anchored tag regex must NOT chop words before a mid-text bracket.
        assertTrue(items[0].cleanSpokenText.contains("Hello"))
        assertTrue(items[0].cleanSpokenText.contains("how are you doing today"))
    }

    @Test
    fun offlineTranslation_preservesUnknownWords() {
        val line = "Rahul will deploy Kubernetes cluster tomorrow"
        val hindi = OfflineSubtitleEngine.translateSentenceToHindi(line)
        // Unknown/technical words must survive translation, never vanish.
        assertTrue("name dropped", hindi.contains("Rahul", ignoreCase = true) || hindi.contains("राहुल"))
        assertTrue("tech word dropped", hindi.contains("Kubernetes", ignoreCase = true))
        assertTrue("word dropped", hindi.contains("cluster", ignoreCase = true))
    }

    @Test
    fun offlineTranslation_fullPhraseCovered() {
        val hindi = OfflineSubtitleEngine.translateSentenceToHindi(
            "Good morning everyone, thank you very much"
        )
        assertTrue(hindi.contains("सुप्रभात"))
        assertTrue(hindi.contains("धन्यवाद"))
    }

    @Test
    fun transliteration_neverReturnsBlankForDevanagari() {
        val roman = DevanagariTransliteration.toPhoneticRoman("नमस्कार दोस्तों स्वागत है")
        assertTrue(roman.isNotBlank())
        assertEquals(4, roman.split(Regex("\\s+")).size)
    }
}
