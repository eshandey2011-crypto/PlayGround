package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Logic tests for the subtitle pipeline: parsing, offline Hindi
 * translation, Devanagari fallback and SRT export round-trip.
 */
class SrtPlayerLogicTest {

    private val sampleSrt = """
1
00:00:01,000 --> 00:00:04,500
[Narrator]: Welcome to the SRT Video Player.

2
00:00:05,000 --> 00:00:08,800
[Rahul]: Good morning everyone!

3
00:00:09,200 --> 00:00:13,500
[Priya]: Thank you very much.
""".trimIndent()

    @Test
    fun `parseSrt reads cues with timings and text`() {
        val items = SrtParser.parseSrt(sampleSrt)
        assertEquals(3, items.size)
        assertEquals(1000L, items[0].startTimeMs)
        assertEquals(4500L, items[0].endTimeMs)
        assertTrue(items[0].text.contains("Welcome"))
        assertEquals("Rahul", items[1].speakerTag)
        assertEquals("Good morning everyone!", items[1].cleanSpokenText)
    }

    @Test
    fun `parseSrt dedupes colliding cue numbers into unique indices`() {
        val dup = """
1
00:00:01,000 --> 00:00:02,000
First line.

1
00:00:03,000 --> 00:00:04,000
Second line.

1
00:00:05,000 --> 00:00:06,000
Third line.
""".trimIndent()
        val items = SrtParser.parseSrt(dup)
        assertEquals(3, items.size)
        val indices = items.map { it.index }
        assertEquals(3, indices.toSet().size)
        // Texts must stay attached to their own cue, not collide.
        assertTrue(items.any { it.text.contains("First") })
        assertTrue(items.any { it.text.contains("Second") })
        assertTrue(items.any { it.text.contains("Third") })
    }

    @Test
    fun `parseSrt tolerates garbage without crashing`() {
        assertTrue(SrtParser.parseSrt("").isEmpty())
        assertTrue(SrtParser.parseSrt("just some random text\nno timestamps here").isEmpty())
        // Timestamp but no text -> skipped, no crash.
        assertTrue(SrtParser.parseSrt("7\n00:00:01,000 --> 00:00:02,000\n").isEmpty())
    }

    @Test
    fun `parseVtt reads cues skipping header`() {
        val vtt = """
WEBVTT

00:01.000 --> 00:04.000
Hello team!

NOTE this is a comment

00:05.000 --> 00:08.000
<v Priya>Good evening.</v>
""".trimIndent()
        val items = SrtParser.parseVtt(vtt)
        assertEquals(2, items.size)
        assertEquals(1000L, items[0].startTimeMs)
        assertEquals(4000L, items[0].endTimeMs)
    }

    @Test
    fun `offline translation converts known English phrases to Hindi`() {
        val hindi = OfflineSubtitleEngine.translateSentenceToHindi("Good morning everyone")
        assertTrue(hindi.contains("सुप्रभात"))
        // Devanagari input passes through untouched.
        val dev = "नमस्ते दोस्तों"
        assertEquals(dev, OfflineSubtitleEngine.translateSentenceToHindi(dev))
        // Speaker prefix is translated, dialogue still present.
        val tagged = OfflineSubtitleEngine.translateSentenceToHindi("[Narrator]: Thank you")
        assertTrue(tagged.contains("सूत्रधार"))
        assertTrue(tagged.contains("धन्यवाद"))
    }

    @Test
    fun `transliteration fallback produces pronounceable non-empty roman text`() {
        val roman = DevanagariTransliteration.toPhoneticRoman("नमस्ते दोस्तों")
        assertTrue(roman.isNotBlank())
        assertFalse(DevanagariTransliteration.hasDevanagari(roman))
        // Latin text passes through unchanged.
        assertEquals("Hello world", DevanagariTransliteration.toPhoneticRoman("Hello world"))
    }

    @Test
    fun `export round-trip preserves cues`() {
        val items = SrtParser.parseSrt(sampleSrt)
        val exported = SrtParser.exportToSrt(items)
        assertTrue(exported.isNotBlank())
        val reparsed = SrtParser.parseSrt(exported)
        assertEquals(items.size, reparsed.size)
        assertEquals(items[0].startTimeMs, reparsed[0].startTimeMs)
        assertEquals(items[2].endTimeMs, reparsed[2].endTimeMs)
    }

    @Test
    fun `detectFormat distinguishes srt vtt and ass`() {
        assertEquals(SubtitleFormat.VTT, SrtParser.detectFormat("WEBVTT\n\n00:01 --> 00:02\nHi"))
        assertEquals(
            SubtitleFormat.ASS,
            SrtParser.detectFormat("[Script Info]\n[V4+ Styles]\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,Hi")
        )
        assertEquals(SubtitleFormat.SRT, SrtParser.detectFormat(sampleSrt))
    }

    @Test
    fun `clarity base rate stays in clear range`() {
        // Default slider (50) with fast mode on must not rush.
        assertEquals(1.0f, SpeechClarity.baseRate(50, true, 1.0f))
        // Even maxed sliders stay bounded.
        assertTrue(SpeechClarity.baseRate(100, true, 1.0f) <= 1.25f)
        assertTrue(SpeechClarity.baseRate(0, false, 1.0f) >= 0.5f)
    }

    @Test
    fun `clarity autofit never reaches garble speed`() {
        // Very long text in a tiny window: still capped.
        val rushed = SpeechClarity.autofitRate(1.0f, 500, 800L, true)
        assertTrue("rate $rushed too fast", rushed <= 1.5f)
        // Disabled autofit returns the steady base.
        assertEquals(1.0f, SpeechClarity.autofitRate(1.0f, 500, 800L, false))
        // Normal cue keeps base rate (no slowdown).
        assertEquals(1.0f, SpeechClarity.autofitRate(1.0f, 20, 4000L, true), 0.11f)
    }

    @Test
    fun `clarity terminal pause is added once`() {
        assertEquals("नमस्ते दोस्तों।", SpeechClarity.withTerminalPause("नमस्ते दोस्तों"))
        assertEquals("Hello team.", SpeechClarity.withTerminalPause("Hello team"))
        // Existing pause marks are kept as-is (no doubling).
        assertEquals("नमस्ते।", SpeechClarity.withTerminalPause("नमस्ते।"))
        assertEquals("Hi!", SpeechClarity.withTerminalPause("Hi!"))
        assertEquals("", SpeechClarity.withTerminalPause("   "))
    }

    @Test
    fun `offline check reports no engine`() {
        val s = OfflineVoiceCheck.decide("हिन्दी", "Hindi", false, 0, null)
        assertFalse(s.ready)
        assertFalse(s.hasEngine)
    }

    @Test
    fun `offline check reports missing voice data`() {
        val s = OfflineVoiceCheck.decide(
            "हिन्दी", "Hindi", true,
            OfflineVoiceCheck.AVAIL_MISSING_DATA, null
        )
        assertFalse(s.ready)
        assertTrue(s.hasEngine)
        assertFalse(s.hasOfflineVoice)
    }

    @Test
    fun `offline check reports online-only voice as not ready`() {
        val s = OfflineVoiceCheck.decide(
            "हिन्दी", "Hindi", true,
            OfflineVoiceCheck.AVAIL_OK, null
        )
        assertFalse(s.ready)
        assertTrue(s.hasEngine)
    }

    @Test
    fun `offline check ready only with installed offline voice`() {
        val s = OfflineVoiceCheck.decide(
            "हिन्दी", "Hindi", true,
            OfflineVoiceCheck.AVAIL_OK, "hi-IN-language"
        )
        assertTrue(s.ready)
        assertTrue(s.hasOfflineVoice)
        assertEquals("hi-IN-language", s.offlineVoiceName)
    }

    @Test
    fun `offline check reports unsupported language`() {
        val s = OfflineVoiceCheck.decide(
            "हिन्दी", "Hindi", true,
            OfflineVoiceCheck.AVAIL_NOT_SUPPORTED, null
        )
        assertFalse(s.ready)
    }

    @Test
    fun `swipe seek scales with video length`() {
        // 30s clip: full-width swipe covers only 25% (7.5s), not 90s.
        assertEquals(7500L, swipeSeekDeltaMs(1.0f, 30_000L))
        assertEquals(-3750L, swipeSeekDeltaMs(-0.5f, 30_000L))
        // 2h movie: capped at 90s span.
        assertEquals(90000L, swipeSeekDeltaMs(1.0f, 7_200_000L))
        assertEquals(45000L, swipeSeekDeltaMs(0.5f, 7_200_000L))
        // Tiny drag = tiny jump.
        assertEquals(750L, swipeSeekDeltaMs(0.1f, 30_000L))
        // No duration = no jump.
        assertEquals(0L, swipeSeekDeltaMs(1.0f, 0L))
    }

    @Test
    fun `finish-first policy never cuts running speech mid-window`() {
        // Speaking + prev cue window still open -> hold, don't interrupt.
        assertFalse(SpeechClarity.mayInterruptSpeech(true, 5000L, 4000L))
        // Speaking but prev window ended (beyond grace) -> next may speak.
        assertTrue(SpeechClarity.mayInterruptSpeech(true, 5000L, 5400L))
        // Not speaking -> always speak.
        assertTrue(SpeechClarity.mayInterruptSpeech(false, 5000L, 4000L))
        // No previous cue tracked -> speak.
        assertTrue(SpeechClarity.mayInterruptSpeech(true, null, 4000L))
    }

    @Test
    fun `speech queue drops only long-stale heads`() {
        fun cue(idx: Int, start: Long, end: Long) = SrtItem(idx, start, end, "Line $idx")
        val queue = mutableListOf(cue(1, 1000L, 2000L), cue(2, 3000L, 4000L), cue(3, 5000L, 9000L))
        // At 4500ms: cue 1 exactly at grace edge -> kept (inclusive boundary).
        assertEquals(0, SpeechClarity.dropStaleHeads(queue, 4500L))
        // At 4501ms: cue 1 long past (stale), cue 2 within grace -> keep.
        val dropped = SpeechClarity.dropStaleHeads(queue, 4501L)
        assertEquals(1, dropped)
        assertEquals(listOf(2, 3), queue.map { it.index })
        // Far future: everything stale.
        assertEquals(2, SpeechClarity.dropStaleHeads(queue, 20000L))
        assertTrue(queue.isEmpty())
    }
}
