package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Offline subtitle<->voice matching math (VoiceMatch is pure JVM).
 * Rule: voice COMPLETES before the subtitle is displayed (voice first,
 * text after).
 */
class VoiceMatchTest {

    @Test
    fun shortCue_voiceCompletesBeforeDisplay() {
        // 20 chars at 90ms/char, rate 1.0 -> 1800ms speech.
        val est = VoiceMatch.estimateSpeechMs(20, 90f, 1.0f)
        assertEquals(1800L, est)
        // Lead = speech + 200ms margin -> voice ends 200ms before display.
        assertEquals(2000L, VoiceMatch.matchLeadMs(est, 1000L))
    }

    @Test
    fun tinyCue_respectsBaseLead() {
        // 5 chars -> ~450ms speech + margin = 650 < base 1000 -> base wins.
        val est = VoiceMatch.estimateSpeechMs(5, 90f, 1.0f)
        assertEquals(1000L, VoiceMatch.matchLeadMs(est, 1000L))
    }

    @Test
    fun longCue_cappedAtMaxLead() {
        // 60 chars -> 5400ms speech + margin -> capped at 2500 max.
        val est = VoiceMatch.estimateSpeechMs(60, 90f, 1.0f)
        assertEquals(5400L, est)
        assertEquals(2500L, VoiceMatch.matchLeadMs(est, 1000L))
    }

    @Test
    fun calibration_clampsPace() {
        assertEquals(90f, VoiceMatch.msPerCharFromCalibration(1800L, 20), 0.01f)
        // Absurd measurement -> clamped, never breaks matching.
        assertEquals(300f, VoiceMatch.msPerCharFromCalibration(90000L, 10), 0.01f)
        assertEquals(90f, VoiceMatch.msPerCharFromCalibration(0L, 0), 0.01f)
    }

    @Test
    fun estimate_handlesRate() {
        // Faster user rate -> shorter estimate.
        val slow = VoiceMatch.estimateSpeechMs(20, 90f, 1.0f)
        val fast = VoiceMatch.estimateSpeechMs(20, 90f, 1.5f)
        assertTrue(fast < slow)
    }
}
