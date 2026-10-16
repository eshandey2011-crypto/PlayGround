package com.example

/**
 * Offline subtitle↔voice matching ("khoj" result).
 *
 * True lip-sync forced alignment needs speech-to-text on the video audio,
 * which is impossible fully offline without heavy models. Instead we
 * MEASURE the device voice once (synthesize a short sample, read the WAV
 * duration) and then MATCH every cue:
 *
 *  - estimate how long the cue will take to speak,
 *  - start speaking early enough that the voice COMPLETES just before
 *    the subtitle is displayed — voice first, text after.
 */
object VoiceMatch {

    /** Fallback speech pace when calibration hasn't run yet (ms per char). */
    const val DEFAULT_MS_PER_CHAR = 90f

    /** Fastest/slowest believable pace; clamps bad measurements. */
    const val MIN_MS_PER_CHAR = 30f
    const val MAX_MS_PER_CHAR = 300f

    /** Widest allowed speak-ahead window (ms). */
    const val MAX_LEAD_MS = 2500L

    /** Voice finishes this much before the subtitle appears (ms). */
    const val END_MARGIN_MS = 200L

    /** Estimated speech time for [charCount] chars at [speechRate]. */
    fun estimateSpeechMs(charCount: Int, msPerChar: Float, speechRate: Float): Long {
        if (charCount <= 0) return 0L
        val pace = msPerChar.coerceIn(MIN_MS_PER_CHAR, MAX_MS_PER_CHAR)
        val rate = speechRate.coerceIn(0.5f, 2.5f)
        return ((charCount * pace) / rate).toLong().coerceAtLeast(200L)
    }

    /**
     * Speak-ahead lead so the voice COMPLETES before the subtitle appears:
     * lead = estimated speech time + small margin (voice first, text after),
     * never below the user's base lead, capped to [MAX_LEAD_MS].
     * Very long dialogues start at the cap and may overlap display slightly.
     */
    fun matchLeadMs(estSpeechMs: Long, baseLeadMs: Long): Long {
        val base = baseLeadMs.coerceAtLeast(0L)
        val need = (estSpeechMs.coerceAtLeast(0L) + END_MARGIN_MS).coerceAtLeast(base)
        return need.coerceIn(base, MAX_LEAD_MS)
    }

    /** ms-per-char from a measured calibration synthesis. */
    fun msPerCharFromCalibration(measuredMs: Long, sampleChars: Int): Float {
        if (measuredMs <= 0L || sampleChars <= 0) return DEFAULT_MS_PER_CHAR
        return (measuredMs.toFloat() / sampleChars).coerceIn(MIN_MS_PER_CHAR, MAX_MS_PER_CHAR)
    }
}
