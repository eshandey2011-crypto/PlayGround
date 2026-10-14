package com.example

/**
 * Readiness of the on-device (100% offline) subtitle reader voice.
 *
 * Pure decision logic (no Android objects) so it is unit-testable;
 * [TtsViewModel] feeds it live engine data.
 */
object OfflineVoiceCheck {

    /** Mirrors TextToSpeech language-availability constants without the import. */
    const val AVAIL_OK = 0
    const val AVAIL_MISSING_DATA = -1
    const val AVAIL_NOT_SUPPORTED = -2

    data class Status(
        val ready: Boolean,
        val hasEngine: Boolean,
        val hasOfflineVoice: Boolean,
        val offlineVoiceName: String? = null,
        val title: String,
        val guidance: String
    )

    fun decide(
        languageNativeName: String,
        languageEnglishName: String,
        hasEngine: Boolean,
        langAvailability: Int,
        offlineVoiceName: String?
    ): Status {
        if (!hasEngine) {
            return Status(
                ready = false,
                hasEngine = false,
                hasOfflineVoice = false,
                title = "No voice engine",
                guidance = "Phone me koi TTS engine nahi hai. Google Speech Services install karo, phir voice 100% offline chalegi."
            )
        }
        if (langAvailability == AVAIL_MISSING_DATA) {
            return Status(
                ready = false,
                hasEngine = true,
                hasOfflineVoice = false,
                title = "$languageNativeName voice data missing",
                guidance = "$languageEnglishName voice data download karna hoga (ek baar internet se). Uske baad subtitle voice hamesha offline chalegi."
            )
        }
        if (langAvailability == AVAIL_NOT_SUPPORTED) {
            return Status(
                ready = false,
                hasEngine = true,
                hasOfflineVoice = false,
                title = "$languageNativeName not supported",
                guidance = "Ye engine $languageEnglishName nahi bol sakta. Google Speech Services update karo ya dusri bhasha chuno."
            )
        }
        if (offlineVoiceName == null) {
            return Status(
                ready = false,
                hasEngine = true,
                hasOfflineVoice = false,
                title = "Only online voice found",
                guidance = "Sirf internet-wali voice mili — internet band par awaaz nahi aayegi. Neeche Install dabakar offline voice data download karo."
            )
        }
        return Status(
            ready = true,
            hasEngine = true,
            hasOfflineVoice = true,
            offlineVoiceName = offlineVoiceName,
            title = "$languageNativeName offline voice ready",
            guidance = "100% offline taiyaar — internet band ho tab bhi subtitle voice chalegi."
        )
    }
}
