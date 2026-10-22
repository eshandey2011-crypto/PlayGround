package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeSrtParseTest {

    private fun buildLargeSrt(targetBytes: Int): Pair<String, Int> {
        val sb = StringBuilder(targetBytes + 1024)
        var index = 1
        var ms = 0L
        while (sb.length < targetBytes) {
            val start = ms
            val end = ms + 2_000
            sb.append(index).append('\n')
            sb.append(formatTime(start)).append(" --> ").append(formatTime(end)).append('\n')
            sb.append("यह एक बड़े सबटाइटल फ़ाइल की पंक्ति है जिसे पूरा पढ़कर सुनाया जाना चाहिए — line ").append(index).append('\n')
            sb.append("This is an English continuation line for the same cue number ").append(index).append('\n')
            sb.append('\n')
            index++
            ms = end + 100
        }
        return Pair(sb.toString(), index - 1)
    }

    private fun formatTime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val milli = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    @Test
    fun parsesFourMegabyteSrtCompletely() {
        val (content, expectedCues) = buildLargeSrt(4 * 1024 * 1024)
        assertTrue("fixture should be at least 4 MB", content.length >= 4 * 1024 * 1024)

        val cues = SrtParser.parse(content, fileName = "large.srt")

        assertEquals(expectedCues, cues.size)
        assertEquals(1, cues.first().index)
        assertTrue(cues.first().text.contains("बड़े"))
        assertEquals(expectedCues, cues.last().index)
    }
}
