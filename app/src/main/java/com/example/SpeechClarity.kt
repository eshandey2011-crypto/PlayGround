package com.example

/**
 * Central tuning for clear, word-by-word subtitle speech.
 *
 * Two clarity rules:
 * 1. Never rush: speech rate stays near 1.0x. Fast "chipmunk" delivery is
 *    what makes offline voices garbled and hard to follow.
 * 2. Terminal pause: every spoken line ends with a pause mark (।/.)
 *    so the engine separates words and sentences instead of mumbling.
 */
object SpeechClarity {
    /** Phone-average English/Hindi syllable rate the engine handles clearly. */
    const val CLEAR_CHARS_PER_SECOND = 14f

    /** Steady base rate for clear speech (upper-clamped, no garble). */
    fun baseRate(speedProgress: Int, fastEnabled: Boolean, fastMultiplier: Float): Float {
        val mult = if (fastEnabled) fastMultiplier else 1.0f
        return (speedProgress / 50f).coerceIn(0.5f, 1.25f) * mult
    }

    /**
     * Bounded per-cue auto-fit. Bends the rate only slightly so long
     * dialogues fit short windows — never into unclear fast speech.
     */
    fun autofitRate(base: Float, textLen: Int, windowMs: Long, enabled: Boolean): Float {
        val safeBase = base.coerceIn(0.5f, 1.5f)
        if (!enabled) return safeBase
        val window = windowMs.coerceAtLeast(800L)
        val neededCps = textLen / (window / 1000f)
        return (safeBase * (neededCps / CLEAR_CHARS_PER_SECOND).coerceIn(0.9f, 1.2f))
            .coerceIn(0.5f, 1.5f)
    }

    /** Appends a terminal pause mark when the line has none. */
    fun withTerminalPause(text: String): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return t
        val last = t.last()
        if (last == '।' || last == '.' || last == '!' || last == '?' || last == '॥') return t
        return if (DevanagariTransliteration.hasDevanagari(t)) "$t।" else "$t."
    }

    /**
     * Finish-first policy: while the previous cue is still being spoken AND
     * its window hasn't ended, a new cue must NOT flush it mid-sentence.
     * The stale cue is skipped (marked spoken) so it never speaks late —
     * spoken lines always complete fully, matching the video moment.
     *
     * @return true when the new cue may interrupt and speak now.
     */
    fun mayInterruptSpeech(
        isSpeaking: Boolean,
        prevCueEndMs: Long?,
        effectivePositionMs: Long,
        finishGraceMs: Long = 300L
    ): Boolean {
        if (!isSpeaking) return true
        if (prevCueEndMs == null) return true
        return effectivePositionMs > prevCueEndMs + finishGraceMs
    }

    /** A queued line stays speakable this long after its window ends. */
    const val QUEUE_STALE_GRACE_MS = 2500L

    /** Hard cap so the queue can never grow without bound. */
    const val QUEUE_MAX_SIZE = 20

    /**
     * Drops queue heads whose moment has long passed (they would sound
     * wrong spoken late). Returns how many were dropped.
     */
    fun dropStaleHeads(
        queue: MutableList<SrtItem>,
        effectivePositionMs: Long,
        graceMs: Long = QUEUE_STALE_GRACE_MS
    ): Int {
        var dropped = 0
        while (true) {
            val head = queue.firstOrNull() ?: break
            if (effectivePositionMs <= head.endTimeMs + graceMs) break
            queue.removeAt(0)
            dropped++
        }
        return dropped
    }
}
