package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class TtsViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val database = AppDatabase.getInstance(context)
    private val dubbingProjectDao = database.dubbingProjectDao()
    private val dubbingProjectRepo = DubbingProjectRepository(dubbingProjectDao)
    private val srtSubtitleDao = database.srtSubtitleDao()
    private val srtSubtitleRepo = SrtSubtitleRepository(srtSubtitleDao)
    private val srtHistoryDao = database.srtHistoryDao()
    private val srtHistoryRepo = SrtHistoryRepository(context, srtHistoryDao)

    private val _uiState = MutableStateFlow(TtsUiState())
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isTtsInitInFlight = false
    private var ttsInitRetries = 0
    private val bgmAudioEngine = BgmAudioEngine(context)

    private var playbackTrackingJob: Job? = null
    private var speechSynthesisJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        initTts()
        registerNetworkRecovery()
        loadInitialData()
        startPlaybackClock()
    }

    private fun initTts() {
        if (isTtsInitInFlight) return
        isTtsInitInFlight = true
        try {
            try { tts?.stop() } catch (_: Exception) {}
            try { tts?.shutdown() } catch (_: Exception) {}
            tts = null
            isTtsInitialized = false
            _uiState.update { it.copy(voiceDiagnostics = "Starting TTS engine…") }
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            isTtsInitInFlight = false
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
            _uiState.update {
                it.copy(
                    speechState = SpeechState.ERROR,
                    errorMessage = "Failed to initialize TTS engine: ${e.message}",
                    statusMessage = "TTS Init Error",
                    voiceDiagnostics = "TTS engine could not be created: ${e.message}"
                )
            }
            scheduleTtsInitRetry()
        }
    }

    override fun onInit(status: Int) {
        isTtsInitInFlight = false
        if (status == TextToSpeech.SUCCESS) {
            ttsInitRetries = 0
            isTtsInitialized = true
            setupTtsEngine()
        } else {
            isTtsInitialized = false
            Log.w(TAG, "TTS init failed (status=$status); scheduling retry")
            _uiState.update {
                it.copy(
                    speechState = SpeechState.ERROR,
                    statusMessage = "TTS Initialization Failed",
                    errorMessage = "TTS engine could not be initialized on device",
                    voiceDiagnostics = "TTS init failed (status=$status). Engine is restarting…"
                )
            }
            scheduleTtsInitRetry()
        }
    }

    /** Offline devices often fail the first TTS init; retry with backoff. */
    private fun scheduleTtsInitRetry() {
        if (ttsInitRetries >= MAX_TTS_INIT_RETRIES) {
            _uiState.update {
                it.copy(
                    voiceDiagnostics = "TTS engine did not start after $MAX_TTS_INIT_RETRIES attempts. Install/select a TTS engine in Settings > Text-to-speech."
                )
            }
            return
        }
        ttsInitRetries++
        val attempt = ttsInitRetries
        viewModelScope.launch {
            delay(1000L * attempt)
            if (!isTtsInitialized) {
                Log.d(TAG, "Retrying TTS init (attempt $attempt)")
                initTts()
            }
        }
    }

    /** Rebuilds the engine from scratch (used when speak()/voice binding fails). */
    private fun restartTtsEngine() {
        ttsInitRetries = 0
        initTts()
    }

    private suspend fun awaitTtsReady(timeoutMs: Long = 6_000L): TextToSpeech? {
        var waited = 0L
        while (waited < timeoutMs) {
            val instance = tts
            if (instance != null && isTtsInitialized) return instance
            if (!isTtsInitInFlight && ttsInitRetries == 0) restartTtsEngine()
            delay(200)
            waited += 200
        }
        return if (isTtsInitialized) tts else null
    }

    /** Re-inits TTS when connectivity changes (e.g. engine started offline). */
    private fun registerNetworkRecovery() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    viewModelScope.launch {
                        if (!isTtsInitialized) {
                            ttsInitRetries = 0
                            initTts()
                        } else {
                            refreshVoiceData()
                        }
                    }
                }
            }
            networkCallback = callback
            cm?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Network recovery callback unavailable: ${e.message}")
        }
    }

    private fun setupTtsEngine() {
        val ttsInstance = tts ?: return
        try {
            // Route speech through media so it is audible even when the device
            // is in silent/notification mode (common reason for "no voice").
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                ttsInstance.setAudioAttributes(attributes)
            } catch (e: Exception) {
                Log.w(TAG, "TTS audio attributes not supported: ${e.message}")
            }
            // Voice language follows the selected app language (default Hindi).
            val language = _uiState.value.appLanguage
            val locale = ttsLocaleFor(language)
            val langResult = ttsInstance.setLanguage(locale)
            val isLangSupported = langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED

            // Fallback: if the selected voice data is missing on device, keep the
            // default locale so speech stays audible (romanized fallback in speak path).
            if (!isLangSupported) {
                try {
                    ttsInstance.language = Locale.getDefault()
                } catch (_: Exception) {
                }
            }

            val availableVoices = try {
                ttsInstance.voices?.map { voice ->
                    val gender = VoiceManager.determineVoiceGender(voice.name, voice.features)
                    TtsVoiceItem(
                        name = voice.name,
                        displayName = voice.name.substringAfterLast(":").replace("_", " "),
                        locale = voice.locale.displayName,
                        gender = gender,
                        isNetworkRequired = voice.isNetworkConnectionRequired,
                        rawVoice = voice
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            attachTtsListener()

            // Offline-first voice choice: a network-required voice stays silent
            // with internet off, so prefer an installed offline voice in the
            // selected language.
            val langCode = language.code
            val langVoices = availableVoices.filter { v ->
                v.locale.contains(langCode, ignoreCase = true) ||
                    v.rawVoice?.locale?.language.equals(langCode, ignoreCase = true)
            }
            val offlineLang = langVoices.filter { v ->
                !v.isNetworkRequired && v.rawVoice?.isNetworkConnectionRequired != true &&
                    v.rawVoice?.name?.lowercase()?.contains("network") != true
            }
            val offlineAny = availableVoices.filter { v ->
                !v.isNetworkRequired && v.rawVoice?.isNetworkConnectionRequired != true
            }
            val chosenVoice = offlineLang.firstOrNull()
                ?: langVoices.firstOrNull { v -> !v.isNetworkRequired }
                ?: langVoices.firstOrNull()
                ?: offlineAny.firstOrNull()
                ?: availableVoices.firstOrNull()

            // Apply the offline voice immediately so playback (and export)
            // never depends on a network voice.
            chosenVoice?.rawVoice?.let { voice ->
                val notInstalled = try {
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                } catch (_: Exception) { false }
                if (!notInstalled) {
                    try {
                        ttsInstance.voice = voice
                    } catch (_: Exception) {
                    }
                }
            }

            val offlineHindi = hasOfflineHindiVoice(availableVoices)
            val diagnostics = buildString {
                append("Engine: ")
                append(ttsInstance.defaultEngine ?: "system")
                append(" • voices: ")
                append(availableVoices.size)
                append(" • Hindi offline: ")
                append(if (offlineHindi) "yes" else "no")
                append(" • lang ok: ")
                append(if (isLangSupported) "yes" else "no (fallback)")
                chosenVoice?.let { append(" • voice: ${it.name}") }
            }

            _uiState.update {
                it.copy(
                    speechState = SpeechState.READY,
                    statusMessage = if (isLangSupported) "${language.nativeName} TTS Ready"
                    else "${language.nativeName} voice missing — fallback active (install ${language.englishName} voice data for best quality)",
                    isHindiSupported = if (language == AppLanguage.HINDI) isLangSupported else it.isHindiSupported,
                    isSpokenLangSupported = isLangSupported,
                    isHindiOfflineVoiceAvailable = offlineHindi,
                    ttsEngineLabel = ttsInstance.defaultEngine
                        ?: "System TTS",
                    voiceDiagnostics = diagnostics,
                    voices = availableVoices,
                    selectedVoice = chosenVoice
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring TTS: ${e.message}", e)
            _uiState.update {
                it.copy(voiceDiagnostics = "TTS configuration error: ${e.message}")
            }
        }
    }

    /** Locale for an app language from its ttsLocale tag (e.g. "hi-IN"). */
    private fun ttsLocaleFor(language: AppLanguage): Locale {
        return try {
            val tags = language.ttsLocale.split("-", "_")
            if (tags.size >= 2) Locale(tags[0], tags[1]) else Locale(language.code)
        } catch (_: Exception) {
            Locale(language.code)
        }
    }

    /**
     * Switches the live TTS engine to a newly selected language:
     * locale + matching offline voice, then rebuilds the spoken-text cache.
     */
    private fun applyTtsLanguage(language: AppLanguage) {
        val ttsInstance = tts ?: return
        if (!isTtsInitialized) return
        try {
            val locale = ttsLocaleFor(language)
            val langResult = ttsInstance.setLanguage(locale)
            val supported = langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (!supported) {
                try {
                    ttsInstance.language = Locale.getDefault()
                } catch (_: Exception) {
                }
            }
            val langCode = language.code
            val langVoices = _uiState.value.voices.filter { v ->
                v.locale.contains(langCode, ignoreCase = true) ||
                    v.rawVoice?.locale?.language.equals(langCode, ignoreCase = true)
            }
            val offlineLang = langVoices.filter { v ->
                !v.isNetworkRequired && v.rawVoice?.isNetworkConnectionRequired != true &&
                    v.rawVoice?.name?.lowercase()?.contains("network") != true
            }
            val chosen = if (_uiState.value.preferOfflineVoice) {
                offlineLang.firstOrNull()
                    ?: langVoices.firstOrNull { v -> !v.isNetworkRequired }
                    ?: langVoices.firstOrNull()
            } else {
                // Online mode: allow network voices for best quality.
                langVoices.firstOrNull() ?: _uiState.value.selectedVoice
            }
            chosen?.rawVoice?.let { voice ->
                val notInstalled = try {
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                } catch (_: Exception) { false }
                if (!notInstalled) {
                    try {
                        ttsInstance.voice = voice
                    } catch (_: Exception) {
                    }
                }
            }
            stopSpeaking()
            _uiState.update {
                it.copy(
                    isHindiSupported = if (language == AppLanguage.HINDI) supported else it.isHindiSupported,
                    isSpokenLangSupported = supported,
                    isHindiOfflineVoiceAvailable = hasOfflineHindiVoice(it.voices),
                    selectedVoice = chosen ?: it.selectedVoice,
                    statusMessage = if (supported) "${language.nativeName} TTS Ready"
                    else "${language.nativeName} voice missing — install ${language.englishName} voice data",
                    previewingPersonaId = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching TTS language: ${e.message}", e)
        }
    }

    /** (Re)attaches the live-speech progress listener (export swaps it temporarily). */
    private fun attachTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _uiState.update { it.copy(speechState = SpeechState.SPEAKING) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { speechCompletions.remove(it)?.complete(true) }
                _uiState.update { it.copy(speechState = SpeechState.READY) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { speechCompletions.remove(it)?.complete(false) }
                _uiState.update {
                    it.copy(
                        speechState = SpeechState.READY,
                        subtitleToast = "Voice engine error — offline voice data install karo (Settings > TTS)."
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { speechCompletions.remove(it)?.complete(false) }
                _uiState.update {
                    it.copy(
                        speechState = SpeechState.READY,
                        subtitleToast = "Voice engine error ($errorCode) — offline voice data install karo (Settings > TTS)."
                    )
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                utteranceId?.let { speechCompletions.remove(it)?.complete(false) }
                _uiState.update { it.copy(speechState = SpeechState.READY) }
            }
        })
    }

    // --- Reader speech plumbing: wait for the engine instead of guessing ---

    private val speechCompletions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private data class PreparedUtterance(
        val text: String,
        val pitch: Float,
        val speed: Float,
        val romanizedFallback: Boolean
    )

    /**
     * Shared cue -> speech preparation (text, pitch, rate). Returns null when
     * the cue must stay silent (muted, stale, blank).
     */
    /** Normal, soft narrator loudness (engines that support KEY_PARAM_VOLUME). */
    private fun softSpeechParams(): Bundle = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, NARRATOR_SOFT_VOLUME)
    }

    private fun prepareUtterance(
        cue: SrtItem,
        state: TtsUiState,
        readerMode: Boolean
    ): PreparedUtterance? {
        // Reader is an explicit user action: only a master mute silences it.
        if (state.isMasterMuted) return null
        if (!readerMode && (state.isDubbingMuted || !state.isAutoDubbingEnabled)) return null

        ensureOfflineVoiceApplied(state)

        var targetText = if (readerMode && state.isVerbatimSubtitleReading) {
            // Same-to-same: read the cue EXACTLY as written (full text, no
            // translation/paraphrase/speaker stripping).
            cue.text
        } else {
            state.speechTextCache[cue.index]
        }
        if (targetText.isNullOrBlank()) {
            val baseText = cue.cleanSpokenText.ifBlank { cue.text }
            targetText = if (state.appLanguage == AppLanguage.HINDI || state.isOfflineHindiVoiceInSubtitles) {
                if (isDevanagari(baseText)) baseText else OfflineSubtitleEngine.translateLineToHindi(baseText)
            } else {
                baseText
            }
        }

        // Audibility fallback: selected language voice is missing, or offline
        // mode has no installed voice for it (network voices go silent without
        // internet) -> romanize Devanagari and use the on-device default voice.
        // Online mode keeps native text (network voices are allowed there).
        val offlineVoiceMissing = state.preferOfflineVoice &&
            !hasInstalledOfflineVoiceFor(state, state.appLanguage.code)
        var romanized = false
        if ((!state.isSpokenLangSupported || offlineVoiceMissing) &&
            DevanagariTransliteration.hasDevanagari(targetText)
        ) {
            targetText = DevanagariTransliteration.toPhoneticRoman(targetText)
            romanized = targetText.isNotBlank()
        }
        if (targetText.isBlank()) return null

        // Staleness guard (live dubbing only).
        if (!readerMode && state.isVideoPlaying) {
            if (state.videoPositionMs > cue.endTimeMs + 1500L) return null
        }

        // Calm narrator: normal-ish pitch with slightly reduced volume so it
        // sounds soft instead of the old artificially deep 0.72 voice.
        val basePitch = if (state.isSingleDeepVoiceOnly) NARRATOR_SOFT_PITCH else pitchForCue(cue, state)

        return PreparedUtterance(
            text = targetText,
            pitch = basePitch,
            speed = rateForCue(cue, state, targetText),
            romanizedFallback = romanized
        )
    }

    /**
     * Prefers a real offline MALE / narrator voice, optionally for one language.
     * Female-but-only-offline-voice still wins over silence.
     */
    private fun pickDeepOfflineVoice(
        candidates: List<TtsVoiceItem>,
        langCode: String?
    ): TtsVoiceItem? {
        val offline = candidates.filter { item ->
            item.rawVoice?.let { isOfflineInstalledVoice(it) } == true
        }
        val languagePool = if (langCode != null) {
            offline.filter { item ->
                item.rawVoice?.let { voiceMatchesLanguage(it, langCode) } == true
            }
        } else {
            emptyList()
        }
        val pool = languagePool.ifEmpty { offline }
        return pool.firstOrNull { it.gender == SpeakerGender.MALE || it.gender == SpeakerGender.NARRATOR }
            ?: pool.firstOrNull {
                it.rawVoice?.let { raw ->
                    VoiceManager.determineVoiceGender(raw.name, raw.features) == SpeakerGender.MALE
                } == true
            }
            ?: pool.firstOrNull()
    }

    /**
     * Offline safety net: put the engine on a locale that actually has an
     * installed on-device voice. Without this, the engine can keep a
     * network-only Hindi voice selected and stay silent with internet off.
     */
    private fun ensureOfflineAudibleLocale(state: TtsUiState, romanizedFallback: Boolean) {
        val ttsInstance = tts ?: return
        try {
            // Online mode: the chosen (possibly network) voice is allowed, so
            // never force an offline voice here or Devanagari text would be
            // spoken by an unrelated offline locale (silent/garbled).
            if (!state.preferOfflineVoice) {
                if (!romanizedFallback) return
                // Romanized text needs a locale the engine can actually speak.
                val result = ttsInstance.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    ttsInstance.setLanguage(Locale.getDefault())
                }
                return
            }
            if (!romanizedFallback) {
                // Native text is speakable; make sure a deep voice matches it.
                val native = pickDeepOfflineVoice(state.voices, state.appLanguage.code)?.rawVoice
                if (native != null) {
                    try {
                        if (ttsInstance.voice?.name != native.name) ttsInstance.voice = native
                    } catch (e: Exception) {
                        Log.w(TAG, "Offline native voice apply failed: ${e.message}")
                    }
                    return
                }
                // No installed voice for the selected language: fall through to
                // the fallback chain instead of leaving a missing-data locale.
            }
            // Romanized fallback: Hindi offline deep voice first (it reads Roman
            // text fine), then device language, then English, then anything.
            val deviceLang = Locale.getDefault().language
            val fallback = pickDeepOfflineVoice(state.voices, state.appLanguage.code)?.rawVoice
                ?: pickDeepOfflineVoice(state.voices, deviceLang)?.rawVoice
                ?: pickDeepOfflineVoice(state.voices, "en")?.rawVoice
                ?: pickDeepOfflineVoice(state.voices, null)?.rawVoice
            if (fallback != null) {
                if (ttsInstance.voice?.name != fallback.name) {
                    ttsInstance.voice = fallback
                }
            } else {
                // No offline voice advertised at all: at least reset language so
                // the device default engine voice can speak the romanized text.
                val result = ttsInstance.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    ttsInstance.setLanguage(Locale.getDefault())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Offline locale fallback failed: ${e.message}")
        }
    }

    /** True only when the device has a real on-device (non-network) voice for this language. */
    private fun hasInstalledOfflineVoiceFor(state: TtsUiState, langCode: String): Boolean {
        return state.voices.any { item ->
            val raw = item.rawVoice ?: return@any false
            isOfflineInstalledVoice(raw) && voiceMatchesLanguage(raw, langCode)
        }
    }

    private fun voiceMatchesLanguage(raw: Voice, langCode: String): Boolean {
        // raw.locale.language is the reliable tag ("hi"); displayName is not.
        return raw.locale?.language.equals(langCode, ignoreCase = true) ||
            raw.name.lowercase().startsWith("${langCode.lowercase()}-")
    }

    /**
     * Reader path: speaks one cue and suspends until the engine reports done
     * (or a safety timeout). This is what makes continuous reading reliable —
     * the old fixed-delay timer skipped cues whenever speech ran slower.
     */
    private suspend fun speakCueAndAwait(cue: SrtItem): Boolean {
        val prepared = prepareUtterance(cue, _uiState.value, readerMode = true) ?: return false
        var attempt = 0
        while (attempt < 2) {
            // TTS init is async; give it a moment instead of silently skipping cues.
            val ttsInstance = awaitTtsReady(if (attempt == 0) 6_000L else 10_000L)
            if (ttsInstance == null) {
                attempt++
                if (attempt < 2) {
                    restartTtsEngine()
                    delay(500)
                }
                continue
            }
            ensureOfflineVoiceApplied(_uiState.value)
            ensureOfflineAudibleLocale(_uiState.value, prepared.romanizedFallback)
            val utteranceId = "reader_${cue.index}_${System.currentTimeMillis()}_$attempt"
            val done = CompletableDeferred<Boolean>()
            speechCompletions[utteranceId] = done
            var completed = false
            try {
                ttsInstance.setPitch(prepared.pitch)
                ttsInstance.setSpeechRate(prepared.speed)
                // QUEUE_FLUSH: reader owns the engine; no stale cue backlog.
                val result = ttsInstance.speak(
                    prepared.text,
                    TextToSpeech.QUEUE_FLUSH,
                    softSpeechParams(),
                    utteranceId
                )
                if (result == TextToSpeech.SUCCESS) {
                    // Timeout scales with text length so long lines are never cut off.
                    val timeoutMs = (prepared.text.length * 200L + 5_000L).coerceIn(8_000L, 90_000L)
                    completed = withTimeoutOrNull(timeoutMs) { done.await() } ?: false
                } else {
                    Log.w(TAG, "tts.speak() returned $result; restarting engine")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reader speech failed: ${e.message}", e)
            } finally {
                speechCompletions.remove(utteranceId)
            }
            if (completed) return true
            attempt++
            if (attempt < 2) {
                // Offline engines sometimes need a fresh instance before they
                // accept speech again (especially after data was installed).
                restartTtsEngine()
                delay(500)
            }
        }
        _uiState.update {
            it.copy(
                subtitleToast = "Voice start nahi hui — offline voice data install karo ya TTS engine restart karo.",
                voiceDiagnostics = "Reader could not start speech after 2 attempts. Check selected voice: ${
                    _uiState.value.selectedVoice?.name ?: "engine default"
                }"
            )
        }
        return false
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Seed sample subtitle if none present
            val defaultSrt = """
1
00:00:01,000 --> 00:00:04,500
[Narrator]: Welcome to the SRT Video Player with Hindi Subtitle narration.

2
00:00:05,000 --> 00:00:08,800
[Rahul]: नमस्कार दोस्तों! यह ऐप किसी भी वीडियो को हिंदी आवाज़ में डब कर सकता है।

3
00:00:09,200 --> 00:00:13,500
[Priya]: You can easily load external subtitles, translate them, and export dubbed videos.

4
00:00:14,000 --> 00:00:18,500
[Narrator]: Enjoy smooth synchronized playback with offline voice reading!
""".trimIndent()
            loadSrtText(defaultSrt, "welcome_demo.srt")

            // Load subtitle history from Room (reload previous .srt files quickly)
            launch {
                srtHistoryRepo.allHistory.collect { history ->
                    _uiState.update { it.copy(srtHistoryList = history) }
                }
            }

            // Load saved projects from Room
            dubbingProjectRepo.allProjects.collect { projects ->
                _uiState.update { it.copy(savedProjects = projects) }
            }
        }
        scanDeviceVideos()
    }

    private fun startPlaybackClock() {
        playbackTrackingJob?.cancel()
        playbackTrackingJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isVideoPlaying) {
                    val currentPos = _uiState.value.videoPositionMs + 200L
                    val maxDuration = _uiState.value.videoDurationMs.coerceAtLeast(1000L)
                    val nextPos = if (currentPos >= maxDuration) {
                        if (_uiState.value.isLoopModeEnabled) 0L else maxDuration
                    } else currentPos

                    updateVideoPosition(nextPos)
                    if (nextPos >= maxDuration && !_uiState.value.isLoopModeEnabled) {
                        _uiState.update { it.copy(isVideoPlaying = false) }
                    }
                }
                delay(200L)
            }
        }
    }

    fun updateVideoPosition(positionMs: Long) {
        val state = _uiState.value
        val effectivePosition = positionMs + state.soundTrackSyncDelayMs
        val parsed = state.parsedSubtitles
        // Story pre-roll: default 2s, so the narrator speaks each subtitle
        // before it appears on screen. 0 = same-to-same timing.
        val leadMs = state.dialogueLeadTimeMs.coerceIn(0L, 5000L)

        // Find active subtitle cue (lead window for voice, exact window for display).
        // Use index-based lookups: parsed.indexOf(cue) on a data class compares the
        // whole text and is very slow on multi-MB subtitle files.
        val activeIndex = parsed.indexOfFirst { cue ->
            effectivePosition in cue.startTimeMs..cue.endTimeMs
        }
        val activeCue = parsed.getOrNull(activeIndex)

        // Voice delivery: speak cues IN ORDER, starting each one `leadMs`
        // before its subtitle appears. Looking only at the next unspoken cue
        // (instead of indexOfFirst over the lead window) is what makes the
        // pre-roll work for back-to-back subtitles too.
        var voiceIndex = -1
        if (state.isOfflineSubtitleMode && !state.isSubtitleReaderPlaying && state.isVideoPlaying) {
            var idx = (state.lastSpokenSubtitleIndex + 1).coerceAtLeast(0)
            while (idx < parsed.size && effectivePosition > parsed[idx].endTimeMs + 1500L) {
                idx++
            }
            val candidate = parsed.getOrNull(idx)
            if (candidate != null && effectivePosition >= candidate.startTimeMs - leadMs) {
                voiceIndex = idx
            }
        }
        val voiceCue = parsed.getOrNull(voiceIndex)

        _uiState.update {
            it.copy(
                videoPositionMs = positionMs,
                activeSubtitle = activeCue,
                lastSpokenSubtitleIndex = if (voiceCue != null) voiceIndex else it.lastSpokenSubtitleIndex
            )
        }

        if (voiceCue != null) {
            speakSubtitleCue(voiceCue)
        }
    }

    fun speakSubtitleCue(cue: SrtItem) {
        speechSynthesisJob?.cancel()
        speechSynthesisJob = viewModelScope.launch {
            try {
                // Wait briefly for TTS init so the first cue is not silently lost.
                val ttsInstance = awaitTtsReady(3_000L)
                if (ttsInstance == null) {
                    _uiState.update { it.copy(statusMessage = "TTS engine loading — voice will start automatically") }
                    return@launch
                }
                val prepared = prepareUtterance(
                    cue,
                    _uiState.value,
                    readerMode = _uiState.value.isSubtitleReaderPlaying
                ) ?: return@launch

                ensureOfflineVoiceApplied(_uiState.value)
                ensureOfflineAudibleLocale(_uiState.value, prepared.romanizedFallback)
                ttsInstance.setPitch(prepared.pitch)
                ttsInstance.setSpeechRate(prepared.speed)

                val utteranceId = "cue_${cue.index}_${System.currentTimeMillis()}"
                // Story narration: QUEUE_ADD lets every line finish naturally
                // (no mid-sentence cut), while the 2s pre-roll keeps the voice
                // ahead of the on-screen subtitle.
                ttsInstance.speak(prepared.text, TextToSpeech.QUEUE_ADD, softSpeechParams(), utteranceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakSubtitleCue: ${e.message}", e)
            }
        }
    }

    private fun isDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    /** Precomputes spoken text per cue so live speech has zero translation lag. */
    private fun buildSpeechTextCache(items: List<SrtItem>): Map<Int, String> {
        val hindiMode = _uiState.value.appLanguage == AppLanguage.HINDI ||
            _uiState.value.isOfflineHindiVoiceInSubtitles
        val map = LinkedHashMap<Int, String>(items.size)
        for (item in items) {
            // Story narration reads dialogue only: speaker tags like
            // "[Rahul]:" are stripped so the narrator never announces them.
            val baseText = item.cleanSpokenText.ifBlank { item.text }
            val spoken = if (!hindiMode) {
                // Multi-language mode: speak subtitles in the selected language as-is.
                baseText
            } else if (isDevanagari(baseText)) {
                baseText
            } else {
                try {
                    OfflineSubtitleEngine.translateLineToHindi(baseText)
                } catch (_: Exception) {
                    baseText
                }
            }
            if (spoken.isNotBlank()) map[item.index] = spoken
        }
        return map
    }

    /**
     * Voice engine routing for the speak path.
     * Offline ON: guarantees an installed offline voice (never silent offline).
     * Offline OFF (online): allows the best matching voice even if it needs
     * network, for higher quality when internet is available.
     */
    private fun ensureOfflineVoiceApplied(state: TtsUiState = _uiState.value) {
        try {
            val ttsInstance = tts ?: return
            if (!state.preferOfflineVoice) {
                // Online mode: pick best language voice, network allowed.
                val langCode = state.appLanguage.code
                val langVoices = state.voices.filter { v ->
                    v.rawVoice?.let { voiceMatchesLanguage(it, langCode) } == true
                }
                val best = langVoices.firstOrNull()
                    ?: state.selectedVoice?.takeIf { v ->
                        v.rawVoice?.let { voiceMatchesLanguage(it, langCode) } == true
                    }
                val raw = best?.rawVoice
                if (raw != null && ttsInstance.voice?.name != raw.name) {
                    try {
                        ttsInstance.voice = raw
                    } catch (_: Exception) {
                    }
                }
                return
            }
            // Offline mode must land on a REAL installed on-device voice.
            // Otherwise a network voice stays selected and speech is silent
            // with internet off (this was why the offline reader looked dead).
            // Always prefer a deep male/narrator voice for the Hindi narrator.
            val target = pickDeepOfflineVoice(state.voices, state.appLanguage.code)
                ?: pickDeepOfflineVoice(state.voices, null)
            val targetVoice = target?.rawVoice ?: return
            // Skip redundant setVoice() calls (they can blip ongoing speech).
            if (tts?.voice?.name != targetVoice.name) {
                try {
                    ttsInstance.voice = targetVoice
                } catch (e: Exception) {
                    Log.w(TAG, "Offline voice apply failed: ${e.message}")
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Real, installed, non-network voice — the only kind that works offline. */
    private fun isOfflineInstalledVoice(voice: Voice): Boolean {
        return try {
            if (voice.isNetworkConnectionRequired) return false
            val name = voice.name.lowercase()
            if (name.contains("network") || name.contains("-online")) return false
            voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        } catch (_: Exception) {
            false
        }
    }

    private fun hasOfflineHindiVoice(voices: List<TtsVoiceItem>): Boolean {
        return voices.any { item ->
            item.rawVoice?.let { raw ->
                isOfflineInstalledVoice(raw) &&
                    voiceMatchesLanguage(raw, AppLanguage.HINDI.code)
            } == true
        }
    }

    /** Re-enumerates engine voices. Call after returning from the TTS installer. */
    fun refreshVoiceData() {
        if (!isTtsInitialized) return
        setupTtsEngine()
    }

    fun refreshVoiceDataWithFeedback() {
        refreshVoiceData()
        val available = _uiState.value.isHindiOfflineVoiceAvailable
        val msg = if (available) {
            "Hindi offline voice mil gayi — ab Devanagari saaf sunai dega."
        } else {
            "Hindi offline voice abhi bhi nahi mili — installer me Hindi download karke Refresh dabao."
        }
        _uiState.update { it.copy(subtitleToast = msg, statusMessage = msg) }
    }

    fun showHindiVoiceSetupDialog(show: Boolean) {
        _uiState.update { it.copy(showHindiVoiceSetupDialog = show) }
    }

    /** Engine's own TTS settings screen (voice data / engine choice). */
    fun openTtsSettings() {
        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        _uiState.update {
            it.copy(subtitleToast = "Settings nahi khul payi — manually Settings > Text-to-speech kholo.")
        }
    }

    /** Opens Google Speech Services listing when the engine itself is missing/outdated. */
    fun openGoogleTtsStore() {
        val packageName = tts?.defaultEngine ?: "com.google.android.tts"
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: Exception) {
            try {
                context.startActivity(web)
            } catch (_: Exception) {
                _uiState.update { it.copy(subtitleToast = "Play Store nahi khul paya.") }
            }
        }
    }

    /**
     * Opens the system TTS voice-data installer so the user can download the
     * Hindi (Devanagari) offline voice. Falls back to TTS settings, then to
     * general settings when an OEM has no installer activity.
     */
    fun openTtsInstallData() {
        val candidates = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                val msg = "Hindi offline voice download karo (voice data > Hindi), phir app me wapas aao."
                _uiState.update { it.copy(subtitleToast = msg, statusMessage = msg) }
                return
            } catch (_: Exception) {
                // Try the next intent.
            }
        }
        _uiState.update {
            it.copy(subtitleToast = "TTS settings nahi khul payi — Settings > Text-to-speech > Install voice data.")
        }
    }

    // --- Subtitle Loading & Translation ---

    /**
     * Reads a picked .srt/.vtt/.ass file off the main thread and loads it.
     * Large files (4 MB+) must never be read/parsed on Main or the UI freezes
     * and the picker looks like it "does nothing".
     */
    fun loadSrtFromUri(uri: android.net.Uri, fileName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSubtitles = true, statusMessage = "Loading subtitle…") }
            val (text, error) = try {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    // Some providers report 0 size; only reject clearly huge files.
                    val declaredSize = try {
                        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    } catch (_: Exception) {
                        -1L
                    }
                    if (declaredSize > MAX_SUBTITLE_BYTES) {
                        return@withContext Pair(null, "File too large (${declaredSize / (1024 * 1024)} MB). Max ${MAX_SUBTITLE_BYTES / (1024 * 1024)} MB.")
                    }
                    val stream = resolver.openInputStream(uri)
                        ?: return@withContext Pair(null, "Could not open this file. Copy it to local storage and try again.")
                    val bytes = stream.use { it.readBytes() }
                    if (bytes.isEmpty()) {
                        return@withContext Pair(null, "File is empty.")
                    }
                    if (bytes.size > MAX_SUBTITLE_BYTES) {
                        return@withContext Pair(null, "File too large (${bytes.size / (1024 * 1024)} MB). Max ${MAX_SUBTITLE_BYTES / (1024 * 1024)} MB.")
                    }
                    Pair(decodeSubtitleBytes(bytes), null)
                }
            } catch (e: Exception) {
                Pair(null, "Failed to read subtitle: ${e.message}")
            }

            if (text == null) {
                val msg = error ?: "Failed to read subtitle"
                _uiState.update {
                    it.copy(isLoadingSubtitles = false, statusMessage = msg, subtitleToast = msg)
                }
                return@launch
            }
            loadSrtText(text, fileName)
        }
    }

    fun consumeSubtitleToast() {
        _uiState.update { it.copy(subtitleToast = null) }
    }

    /** UTF-8 first, BOM-aware UTF-16, then Windows-1252 for legacy files. */
    private fun decodeSubtitleBytes(bytes: ByteArray): String {
        fun tryDecode(charset: java.nio.charset.Charset): String? = try {
            val decoder = charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFE && b1 == 0xFF) {
                return (tryDecode(Charsets.UTF_16BE) ?: "").removePrefix("\uFEFF")
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return (tryDecode(Charsets.UTF_16LE) ?: "").removePrefix("\uFEFF")
            }
        }
        tryDecode(Charsets.UTF_8)?.let { return it.removePrefix("\uFEFF") }
        return String(bytes, java.nio.charset.Charset.forName("windows-1252"))
    }

    fun loadSrtText(rawText: String, fileName: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingSubtitles = true) }
                val format = SubtitleFormat.fromFileName(fileName)
                // Parsing a 4 MB file with regexes is heavy -> keep it off Main.
                val (parsedItems, langDetection) = withContext(Dispatchers.Default) {
                    val items = SrtParser.parse(rawText, fileName = fileName)
                    Pair(items, SrtLanguageDetector.detectLanguage(rawText))
                }
                if (parsedItems.isEmpty()) {
                    val msg = "No subtitles found in this file. Format check karo (.srt / .vtt / .ass)."
                    _uiState.update {
                        it.copy(
                            isLoadingSubtitles = false,
                            statusMessage = msg,
                            subtitleToast = msg
                        )
                    }
                    return@launch
                }
                // Precompute spoken Hindi only for reasonably sized files; huge
                // files fall back to on-demand translation in the speak path.
                val speechCache = try {
                    if (parsedItems.size <= SPEECH_CACHE_MAX_CUES) {
                        withContext(Dispatchers.Default) { buildSpeechTextCache(parsedItems) }
                    } else {
                        emptyMap()
                    }
                } catch (_: Exception) {
                    emptyMap()
                }

                _uiState.update {
                    it.copy(
                        rawSrtText = rawText,
                        currentSrtFileName = fileName ?: "subtitles.${format.defaultExtension}",
                        detectedSubtitleFormat = format,
                        parsedSubtitles = parsedItems,
                        speechTextCache = speechCache,
                        activeSubtitle = parsedItems.firstOrNull(),
                        lastSpokenSubtitleIndex = -1,
                        srtLanguageDetection = langDetection,
                        originalSrtBackup = rawText,
                        isTranslatedWithGemini = false,
                        showLanguageWarningBanner = !langDetection.isHindi && it.autoTranslateNonHindiOnLoad,
                        isLoadingSubtitles = false,
                        statusMessage = "Loaded ${parsedItems.size} subtitles",
                        subtitleToast = "Loaded ${parsedItems.size} subtitles: ${fileName ?: "subtitles"}"
                    )
                }

                // If non-Hindi and auto translate enabled, prompt or translate
                if (!langDetection.isHindi && _uiState.value.autoTranslateNonHindiOnLoad) {
                    _uiState.update { it.copy(showAutoTranslatePromptDialog = true) }
                }

                // Log to subtitle history (Room) for quick reload later.
                try {
                    val snap = _uiState.value
                    srtHistoryRepo.recordHistory(
                        fileName = snap.currentSrtFileName ?: fileName ?: "subtitles.srt",
                        sourceType = SrtHistorySource.IMPORTED,
                        rawContent = rawText,
                        cueCount = parsedItems.size,
                        durationMs = parsedItems.maxOfOrNull { it.endTimeMs } ?: 0L,
                        languageCode = snap.appLanguage.code,
                        formatName = format.name,
                        videoTitle = snap.videoTitle,
                        videoUri = snap.videoUri
                    )
                } catch (_: Exception) {
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse SRT: ${e.message}", e)
                val msg = "Failed to parse subtitle: ${e.message}"
                _uiState.update {
                    it.copy(
                        isLoadingSubtitles = false,
                        statusMessage = msg,
                        subtitleToast = msg
                    )
                }
            }
        }
    }

    fun translateCurrentSrtToHindi(force: Boolean = false) {
        val currentSrt = _uiState.value.rawSrtText
        if (currentSrt.isBlank()) return

        _uiState.update {
            it.copy(
                isTranslatingSrt = true,
                translationProgressMessage = "Translating subtitles to Hindi...",
                translationError = null,
                showAutoTranslatePromptDialog = false
            )
        }

        viewModelScope.launch {
            try {
                val customApiKey = _uiState.value.customGeminiApiKey.ifBlank { null }
                val result = GeminiTranslator.translateSrtToHindi(currentSrt, customApiKey)

                when (result) {
                    is TranslationResult.Success -> {
                        val translatedSrt = result.translatedSrt
                        val newParsed = SrtParser.parse(translatedSrt)
                        val newCache = try {
                            withContext(Dispatchers.Default) { buildSpeechTextCache(newParsed) }
                        } catch (_: Exception) { emptyMap() }
                        _uiState.update {
                            it.copy(
                                rawSrtText = translatedSrt,
                                parsedSubtitles = newParsed,
                                speechTextCache = newCache,
                                lastSpokenSubtitleIndex = -1,
                                isTranslatingSrt = false,
                                isTranslatedWithGemini = true,
                                translationProgressMessage = "Translation complete!"
                            )
                        }
                    }
                    is TranslationResult.Error -> {
                        // Seamless Fallback to high quality Offline Subtitle Engine
                        Log.w(TAG, "Gemini translation failed: ${result.message}. Using Offline Subtitle Engine fallback.")
                        val offlineHindiSrt = translateSrtOffline(currentSrt)
                        val newParsed = SrtParser.parse(offlineHindiSrt)
                        val newCache = try {
                            withContext(Dispatchers.Default) { buildSpeechTextCache(newParsed) }
                        } catch (_: Exception) { emptyMap() }
                        _uiState.update {
                            it.copy(
                                rawSrtText = offlineHindiSrt,
                                parsedSubtitles = newParsed,
                                speechTextCache = newCache,
                                lastSpokenSubtitleIndex = -1,
                                isTranslatingSrt = false,
                                isTranslatedWithGemini = false,
                                translationProgressMessage = "Offline Hindi Translation applied!"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to offline
                val offlineHindiSrt = translateSrtOffline(currentSrt)
                val newParsed = SrtParser.parse(offlineHindiSrt)
                val newCache = try {
                    withContext(Dispatchers.Default) { buildSpeechTextCache(newParsed) }
                } catch (_: Exception) { emptyMap() }
                _uiState.update {
                    it.copy(
                        rawSrtText = offlineHindiSrt,
                        parsedSubtitles = newParsed,
                        speechTextCache = newCache,
                        lastSpokenSubtitleIndex = -1,
                        isTranslatingSrt = false,
                        translationError = null
                    )
                }
            }
        }
    }

    private fun translateSrtOffline(rawSrt: String): String {
        val parsed = SrtParser.parse(rawSrt)
        val translated = parsed.map { item ->
            val hindiText = OfflineSubtitleEngine.translateLineToHindi(item.text)
            item.copy(text = hindiText)
        }
        return SrtParser.exportToSrt(translated)
    }

    fun revertToOriginalSrt() {
        val backup = _uiState.value.originalSrtBackup ?: return
        val parsed = SrtParser.parse(backup)
        viewModelScope.launch {
            val newCache = try {
                withContext(Dispatchers.Default) { buildSpeechTextCache(parsed) }
            } catch (_: Exception) { emptyMap() }
            _uiState.update {
                it.copy(
                    rawSrtText = backup,
                    parsedSubtitles = parsed,
                    speechTextCache = newCache,
                    lastSpokenSubtitleIndex = -1,
                    isTranslatedWithGemini = false,
                    translationError = null
                )
            }
        }
    }

    fun dismissTranslationError() {
        _uiState.update { it.copy(translationError = null) }
    }

    fun setShowApiKeyDialog(show: Boolean) {
        _uiState.update { it.copy(showApiKeyDialog = show) }
    }

    fun setAutoTranslateNonHindiOnLoad(enabled: Boolean) {
        _uiState.update { it.copy(autoTranslateNonHindiOnLoad = enabled) }
    }

    fun setCustomGeminiApiKey(apiKey: String) {
        _uiState.update { it.copy(customGeminiApiKey = apiKey.trim(), showApiKeyDialog = false) }
    }

    // --- Audio Mixer & Sound Controls ---

    fun applyMixPreset(
        videoVol: Float,
        dubVol: Float,
        bgmVol: Float,
        videoMuted: Boolean,
        dubMuted: Boolean,
        bgmEnabled: Boolean,
        presetName: String
    ) {
        _uiState.update {
            it.copy(
                videoVolume = videoVol,
                dubbingVolume = dubVol,
                bgmVolume = bgmVol,
                isVideoMuted = videoMuted,
                isDubbingMuted = dubMuted,
                isBgmEnabled = bgmEnabled
            )
        }
    }

    fun showVolumeMixerDialog(show: Boolean) {
        _uiState.update { it.copy(showVolumeMixerDialog = show) }
    }

    fun setCrossfadeBalance(balance: Float) {
        _uiState.update { it.copy(crossfadeBalance = balance.coerceIn(0f, 1f)) }
    }

    fun setVideoVolume(vol: Float) {
        _uiState.update { it.copy(videoVolume = vol.coerceIn(0f, 1f), isVideoMuted = vol == 0f) }
    }

    fun toggleVideoMute() {
        _uiState.update { it.copy(isVideoMuted = !it.isVideoMuted) }
    }

    fun toggleOriginalSolo() {
        _uiState.update {
            val nextSolo = !it.isOriginalSolo
            it.copy(
                isOriginalSolo = nextSolo,
                isDubbingMuted = if (nextSolo) true else it.isDubbingMuted,
                isVideoMuted = false
            )
        }
    }

    fun setDubbingVolume(vol: Float) {
        _uiState.update { it.copy(dubbingVolume = vol.coerceIn(0f, 1f), isDubbingMuted = vol == 0f) }
    }

    fun testDubbingVoice() {
        val testText = "यह हिंदी आवाज़ की परीक्षण ध्वनि है। सबटाइटल्स साफ सुनाई दे रहे हैं।"
        tts?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test_voice")
    }

    fun toggleDubbingMute() {
        _uiState.update { it.copy(isDubbingMuted = !it.isDubbingMuted) }
    }

    fun toggleDubbingSolo() {
        _uiState.update {
            val nextSolo = !it.isDubbingSolo
            it.copy(
                isDubbingSolo = nextSolo,
                isVideoMuted = if (nextSolo) true else it.isVideoMuted,
                isDubbingMuted = false
            )
        }
    }

    fun toggleMasterMute() {
        _uiState.update { it.copy(isMasterMuted = !it.isMasterMuted) }
    }

    fun setMasterVolume(vol: Float) {
        _uiState.update { it.copy(masterVolume = vol.coerceIn(0f, 1f), isMasterMuted = vol == 0f) }
    }

    fun togglePlayPause() {
        val wasPlaying = _uiState.value.isVideoPlaying
        _uiState.update { it.copy(isVideoPlaying = !it.isVideoPlaying) }
        // Pause: stop voice immediately so queued dialogues don't keep
        // speaking while the video is paused.
        if (wasPlaying) {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
        }
    }

    fun seekTo(positionMs: Long) {
        // Seek: flush stale queued speeches so voice restarts cleanly
        // at the new position instead of speaking old timeline.
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        val clamped = positionMs.coerceIn(0L, _uiState.value.videoDurationMs.coerceAtLeast(1000L))
        // Re-arm narration from the cue at the new position: mark everything
        // that already finished as spoken so the current/next cue speaks.
        val lastFinished = _uiState.value.parsedSubtitles.indexOfLast { it.endTimeMs < clamped - 150L }
        // pendingSeekMs bridges Compose state -> ExoPlayer.seekTo() in the surface view.
        _uiState.update { it.copy(pendingSeekMs = clamped, lastSpokenSubtitleIndex = lastFinished) }
        updateVideoPosition(clamped)
    }

    fun consumePendingSeek() {
        _uiState.update { it.copy(pendingSeekMs = null) }
    }

    /**
     * Periodic correction from the real ExoPlayer position. The in-app clock is
     * simulated, so snap state back when drift exceeds ~1s and adopt real duration.
     */
    fun syncExoProgress(exoPositionMs: Long, exoDurationMs: Long) {
        val st = _uiState.value
        if (exoDurationMs > 0 && exoDurationMs != st.videoDurationMs) {
            _uiState.update { it.copy(videoDurationMs = exoDurationMs) }
        }
        if (!st.isVideoPlaying) return
        // Tight sync: the simulated clock can drift; keep the subtitle/voice
        // trigger within ~120ms of the real player position.
        if (kotlin.math.abs(exoPositionMs - st.videoPositionMs) > 120L) {
            updateVideoPosition(exoPositionMs.coerceAtLeast(0L))
        }
    }

    // --- Subtitle Synchronization ---
    // Single effective offset, mirrored in both fields: the sync panel/slider
    // UI reads subtitleOffsetMs, the speak path reads soundTrackSyncDelayMs.

    fun setSubtitleOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-10000L, 10000L)
        _uiState.update { it.copy(soundTrackSyncDelayMs = clamped, subtitleOffsetMs = clamped) }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        _uiState.update {
            val next = (it.soundTrackSyncDelayMs + deltaMs).coerceIn(-10000L, 10000L)
            it.copy(soundTrackSyncDelayMs = next, subtitleOffsetMs = next)
        }
    }

    fun alignSubtitleToCurrentPosition() {
        val active = _uiState.value.activeSubtitle ?: return
        val currentVideoPos = _uiState.value.videoPositionMs
        val diff = currentVideoPos - active.startTimeMs
        setSubtitleOffset(diff)
    }

    fun toggleSameToSameTimeDelivery(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isSameToSameTimeDelivery = enabled,
                isFastSubtitleSpeechEnabled = enabled,
                // 1:1 delivery starts exactly with the cue; normal story
                // narration starts 2s before the subtitle appears.
                dialogueLeadTimeMs = if (enabled) 0L else 2000L,
                lastSpokenSubtitleIndex = -1
            )
        }
    }

    fun lockSameToSameSync() {
        // Explicit 1:1 lock: zero offset and zero pre-roll so the voice starts
        // on the exact subtitle timestamp.
        _uiState.update {
            it.copy(
                soundTrackSyncDelayMs = 0L,
                subtitleOffsetMs = 0L,
                dialogueLeadTimeMs = 0L,
                isSameToSameTimeDelivery = true,
                isPerfectTimingEnabled = true,
                lastSpokenSubtitleIndex = -1
            )
        }
    }

    // --- Subtitle Appearance & Dialogs ---

    fun setSubtitleFontSize(size: Int) {
        _uiState.update { it.copy(subtitleFontSize = size) }
    }

    fun setSubtitleTheme(theme: SubtitleTheme) {
        _uiState.update { it.copy(subtitleTheme = theme) }
    }

    fun setSubtitleBackgroundOpacity(opacity: Float) {
        _uiState.update { it.copy(subtitleBackgroundOpacity = opacity) }
    }

    fun setSubtitleAlignment(alignment: SubtitleAlignment) {
        _uiState.update { it.copy(subtitleAlignment = alignment) }
    }

    fun toggleShowSubtitlesOnVideo() {
        _uiState.update { it.copy(showSubtitlesOnVideo = !it.showSubtitlesOnVideo) }
    }

    fun resetSubtitleAppearanceDefaults() {
        _uiState.update {
            it.copy(
                subtitleFontSize = 18,
                subtitleTheme = SubtitleTheme.MX_YELLOW,
                subtitleBackgroundOpacity = 0.75f,
                subtitleAlignment = SubtitleAlignment.CENTER,
                showSubtitlesOnVideo = true
            )
        }
    }

    // --- Gestures & Controls ---

    fun setBrightnessLevel(level: Float) {
        _uiState.update { it.copy(brightnessLevel = level.coerceIn(0.1f, 1.0f)) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _uiState.update { it.copy(aspectRatioMode = mode) }
    }

    fun setVideoOrientationLock(lock: VideoOrientationLock) {
        _uiState.update { it.copy(orientationLock = lock) }
    }

    fun setFullscreen(full: Boolean) {
        _uiState.update { it.copy(isFullscreen = full) }
    }

    /** Enter SRT-style fullscreen: widescreen lock + fullscreen layout. */
    fun enterFullscreen() {
        _uiState.update { it.copy(isFullscreen = true, orientationLock = VideoOrientationLock.LANDSCAPE) }
    }

    /** Exit fullscreen and return to sensor-based auto-rotate. */
    fun exitFullscreen() {
        _uiState.update { it.copy(isFullscreen = false, orientationLock = VideoOrientationLock.AUTO) }
    }

    fun toggleFullscreen() {
        if (_uiState.value.isFullscreen) exitFullscreen() else enterFullscreen()
    }

    fun toggleScreenLock() {
        _uiState.update { it.copy(isScreenLocked = !it.isScreenLocked) }
    }

    fun toggleLoopMode() {
        _uiState.update { it.copy(isLoopModeEnabled = !it.isLoopModeEnabled) }
    }

    fun selectVoicePersona(persona: HindiVoicePersona?) {
        _uiState.update { it.copy(selectedVoicePersona = persona) }
    }

    fun setSpeechSpeed(progress: Int) {
        _uiState.update { it.copy(speedProgress = progress) }
        tts?.setSpeechRate(0.5f + (progress / 100f) * 1.5f)
    }

    fun setSpeechPitch(progress: Int) {
        _uiState.update { it.copy(pitchProgress = progress) }
        tts?.setPitch(0.5f + (progress / 100f) * 1.5f)
    }

    fun setVoice(voiceItem: TtsVoiceItem) {
        _uiState.update { it.copy(selectedVoice = voiceItem) }
        voiceItem.rawVoice?.let { tts?.voice = it }
    }

    fun previewPersona(persona: HindiVoicePersona) {
        _uiState.update { it.copy(previewingPersonaId = persona.id, selectedVoicePersona = persona) }
        tts?.setPitch(persona.basePitch)
        tts?.setSpeechRate(persona.baseSpeed)
        speakText(persona.sampleText)
    }

    fun testVoiceSample(voiceItem: TtsVoiceItem) {
        setVoice(voiceItem)
        speakText("नमस्ते! यह आवाज़ का नमूना है।")
    }

    fun stopSpeaking() {
        tts?.stop()
        _uiState.update { it.copy(speechState = SpeechState.READY, previewingPersonaId = null) }
    }

    fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sample_${System.currentTimeMillis()}")
    }

    // --- Multi-language Subtitle Reader (standalone, works with/without video) ---

    private var subtitleReaderJob: Job? = null
    private var readerStopRequested = false

    fun showOfflineSubtitleReaderDialog(show: Boolean) {
        _uiState.update { it.copy(showOfflineSubtitleReaderDialog = show) }
    }

    fun setSubtitleReaderDelay(seconds: Float) {
        _uiState.update { it.copy(subtitleReaderDelaySeconds = seconds.coerceIn(0f, 5f)) }
    }

    fun toggleSubtitleReaderContinuous() {
        _uiState.update { it.copy(isSubtitleReaderContinuous = !it.isSubtitleReaderContinuous) }
    }

    fun setSubtitleReaderContinuous(continuous: Boolean) {
        _uiState.update { it.copy(isSubtitleReaderContinuous = continuous) }
    }

    /** Same-to-same: read every cue exactly as written, in full. */
    fun toggleVerbatimSubtitleReading() {
        _uiState.update { it.copy(isVerbatimSubtitleReading = !it.isVerbatimSubtitleReading) }
    }

    fun setVerbatimSubtitleReading(enabled: Boolean) {
        _uiState.update { it.copy(isVerbatimSubtitleReading = enabled) }
    }

    /** One-tap: enable same-to-same and start reading from the first cue. */
    fun startVerbatimReader() {
        setVerbatimSubtitleReading(true)
        _uiState.update { it.copy(isSubtitleReaderContinuous = true) }
        startSubtitleReader(_uiState.value.readingSubtitleIndex.coerceAtLeast(0))
    }

    /** Starts reading loaded subtitles aloud in the selected reader language. */
    fun startSubtitleReader(fromIndex: Int = 0) {
        val cues = _uiState.value.parsedSubtitles
        if (cues.isEmpty()) {
            val msg = "Pehle Add Subtitles se .srt file jodo"
            _uiState.update { it.copy(statusMessage = msg, subtitleToast = msg) }
            return
        }
        stopSubtitleReader(keepStatus = true)
        readerStopRequested = false
        val startIdx = fromIndex.coerceIn(0, cues.size - 1)
        _uiState.update {
            it.copy(
                isSubtitleReaderPlaying = true,
                readingSubtitleIndex = startIdx,
                subtitleToast = "Reader shuru — dialogue ${startIdx + 1}/${cues.size}"
            )
        }
        subtitleReaderJob = viewModelScope.launch {
            var spokenAny = false
            try {
                var idx = startIdx
                while (isActive) {
                    val items = _uiState.value.parsedSubtitles
                    if (idx < 0 || idx >= items.size) break
                    _uiState.update { it.copy(readingSubtitleIndex = idx) }
                    val cue = items[idx]
                    // Wait for actual speech completion (not a guessed timer),
                    // so slow/long dialogues are never skipped.
                    if (speakCueAndAwait(cue)) spokenAny = true
                    if (!_uiState.value.isSubtitleReaderContinuous) break
                    val gapMs = (_uiState.value.subtitleReaderDelaySeconds * 1000L).toLong()
                    if (gapMs > 0) delay(gapMs)
                    idx++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Subtitle reader failed: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isSubtitleReaderPlaying = false) }
                if (!spokenAny && !readerStopRequested) {
                    _uiState.update {
                        it.copy(
                            subtitleToast = "Reader ne kuch nahi bola — offline voice data install karo ya Voice Profiles se doosri awaaz chuno."
                        )
                    }
                }
            }
        }
    }

    fun stopSubtitleReader(keepStatus: Boolean = false) {
        readerStopRequested = true
        subtitleReaderJob?.cancel()
        subtitleReaderJob = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _uiState.update { it.copy(isSubtitleReaderPlaying = false) }
        if (!keepStatus) {
            _uiState.update { it.copy(statusMessage = "Subtitle reader stopped") }
        }
    }

    fun toggleSubtitleReader() {
        if (_uiState.value.isSubtitleReaderPlaying) stopSubtitleReader()
        else startSubtitleReader(_uiState.value.readingSubtitleIndex.coerceAtLeast(0))
    }

    /**
     * Speaks one line through the exact same offline path as the reader, so the
     * user can verify audio works with internet off. Reports the outcome.
     */
    fun testVoiceOutput() {
        viewModelScope.launch {
            _uiState.update { it.copy(subtitleToast = "Voice test chal raha hai…") }
            val sample = _uiState.value.parsedSubtitles.firstOrNull { it.text.isNotBlank() }
                ?: SrtItem(
                    index = 0,
                    startTimeMs = 0L,
                    endTimeMs = 8_000L,
                    text = "नमस्ते! यह ऑफ़लाइन आवाज़ का टेस्ट है।"
                )
            val ok = speakCueAndAwait(sample)
            val voiceName = tts?.voice?.name?.substringAfterLast(':') ?: "default"
            _uiState.update {
                it.copy(
                    subtitleToast = if (ok) {
                        "Voice OK ($voiceName) — offline audio chal raha hai."
                    } else {
                        "Voice fail — Settings > Text-to-speech me offline voice data install karo."
                    }
                )
            }
        }
    }

    fun setGenderVoiceMode(mode: GenderVoiceMode) {
        _uiState.update { it.copy(genderVoiceMode = mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        val prev = _uiState.value.appLanguage
        if (prev == language) return
        // Explicit language choice wins: Hindi mode dubs in Hindi,
        // any other language speaks subtitles in that language.
        _uiState.update {
            it.copy(
                appLanguage = language,
                isOfflineHindiVoiceInSubtitles = (language == AppLanguage.HINDI),
                lastSpokenSubtitleIndex = -1
            )
        }
        applyTtsLanguage(language)
        viewModelScope.launch {
            val items = _uiState.value.parsedSubtitles
            val newCache = try {
                withContext(Dispatchers.Default) { buildSpeechTextCache(items) }
            } catch (_: Exception) { emptyMap() }
            _uiState.update { it.copy(speechTextCache = newCache, lastSpokenSubtitleIndex = -1) }
        }
    }

    fun setAppThemeMode(themeMode: AppThemeMode) {
        _uiState.update { it.copy(appThemeMode = themeMode) }
    }

    // --- Project Persistence ---

    fun saveCurrentProject(title: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val project = DubbingProject(
                title = title.ifBlank { "Dubbing Project" },
                videoUri = state.videoUri,
                videoTitle = state.videoTitle,
                rawSrtText = state.rawSrtText,
                subtitleCount = state.parsedSubtitles.size,
                languageCode = state.appLanguage.code,
                activeAudioTrack = state.activeAudioTrack.name,
                audioPresetMode = state.audioPresetMode.name,
                isBgmEnabled = state.isBgmEnabled,
                bgmVolume = state.bgmVolume,
                bgmStyle = state.bgmStyle.name,
                subtitleFontSize = state.subtitleFontSize,
                subtitleTheme = state.subtitleTheme.name,
                subtitleBackgroundOpacity = state.subtitleBackgroundOpacity,
                subtitleAlignment = state.subtitleAlignment.name,
                speedProgress = state.speedProgress,
                pitchProgress = state.pitchProgress,
                selectedVoiceName = state.selectedVoice?.name
            )
            dubbingProjectRepo.saveProject(project)
            _uiState.update { it.copy(showSaveProjectDialog = false) }
        }
    }

    fun loadProject(project: DubbingProject) {
        viewModelScope.launch {
            val voice = _uiState.value.voices.firstOrNull { it.name == project.selectedVoiceName }
            _uiState.update {
                it.copy(
                    currentProjectId = project.id,
                    currentProjectTitle = project.title,
                    videoUri = project.videoUri,
                    videoTitle = project.videoTitle,
                    bgmVolume = project.bgmVolume,
                    speedProgress = project.speedProgress,
                    pitchProgress = project.pitchProgress,
                    selectedVoice = voice,
                    showProjectsDialog = false
                )
            }
            if (project.rawSrtText.isNotBlank()) {
                loadSrtText(project.rawSrtText, "project_${project.id}.srt")
            }
        }
    }

    fun deleteProject(project: DubbingProject) {
        viewModelScope.launch {
            dubbingProjectRepo.deleteProject(project)
            _uiState.update { it.copy(lastRemovedProject = project) }
        }
    }

    fun restoreLastRemovedProject() {
        val last = _uiState.value.lastRemovedProject ?: return
        viewModelScope.launch {
            dubbingProjectRepo.saveProject(last)
            _uiState.update { it.copy(lastRemovedProject = null) }
        }
    }

    fun restoreSampleProjects() {
        viewModelScope.launch {
            val sample = DubbingProject(
                title = "Office Meeting & Hindi Narration",
                videoUri = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
                videoTitle = "Office Presentation",
                rawSrtText = _uiState.value.rawSrtText,
                subtitleCount = _uiState.value.parsedSubtitles.size
            )
            dubbingProjectRepo.saveProject(sample)
        }
    }

    // --- Device Video Scanning ---

    fun scanDeviceVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningVideos = true, videoScanError = null) }
            try {
                val videos = LocalMediaScanner.queryDeviceVideos(context)
                _uiState.update {
                    it.copy(
                        deviceVideos = videos,
                        isScanningVideos = false,
                        hasVideoPermission = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanningVideos = false,
                        videoScanError = e.message
                    )
                }
            }
        }
    }

    fun selectDeviceVideo(video: MxVideoItem) {
        // New video: clear old queued voice before starting fresh.
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _uiState.update {
            it.copy(
                videoUri = video.uriString,
                videoTitle = video.title,
                videoDurationMs = video.durationMs.coerceAtLeast(1000L),
                videoPositionMs = 0L,
                activeSubtitle = null,
                lastSpokenSubtitleIndex = -1,
                isVideoPlaying = true,
                currentTab = AppTab.VIDEO_PLAYER
            )
        }
        if (!video.srtPreset.isNullOrBlank()) {
            loadSrtText(video.srtPreset, "${video.title}.srt")
        }
    }

    fun setAppTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setSelectedBrowserFolder(folder: String?) {
        _uiState.update { it.copy(selectedBrowserFolder = folder) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleFolderGridView() {
        _uiState.update { it.copy(isFolderGridView = !it.isFolderGridView) }
    }

    fun toggleSearchingVideos() {
        _uiState.update {
            val next = !it.isSearchingVideos
            it.copy(isSearchingVideos = next, searchQuery = if (!next) "" else it.searchQuery)
        }
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.values()
        val currentIdx = modes.indexOf(_uiState.value.aspectRatioMode)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setAspectRatioMode(nextMode)
    }

    fun cycleDecoderMode() {
        val modes = DecoderMode.values()
        val currentIdx = modes.indexOf(_uiState.value.decoderMode)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setDecoderMode(nextMode)
    }

    fun setDecoderMode(mode: DecoderMode) {
        _uiState.update { it.copy(decoderMode = mode, showDecoderDialog = false) }
    }

    fun cycleRepeatMode() {
        val modes = MxRepeatMode.values()
        val currentIdx = modes.indexOf(_uiState.value.repeatMode)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setRepeatMode(nextMode)
    }

    fun setRepeatMode(mode: MxRepeatMode) {
        _uiState.update { it.copy(repeatMode = mode) }
    }

    fun playNextVideo() {
        val videos = _uiState.value.deviceVideos
        if (videos.isEmpty()) return
        val currentUri = _uiState.value.videoUri
        val currentIndex = videos.indexOfFirst { it.uriString == currentUri }
        val nextIndex = if (currentIndex != -1 && currentIndex + 1 < videos.size) currentIndex + 1 else 0
        selectDeviceVideo(videos[nextIndex])
    }

    fun playPrevVideo() {
        val videos = _uiState.value.deviceVideos
        if (videos.isEmpty()) return
        val currentUri = _uiState.value.videoUri
        val currentIndex = videos.indexOfFirst { it.uriString == currentUri }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else videos.size - 1
        selectDeviceVideo(videos[prevIndex])
    }

    fun setVolumeBoostPercent(percent: Int) {
        _uiState.update { it.copy(volumeBoostPercent = percent) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        _uiState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = minutes?.let { m -> m * 60L },
                sleepTimerTargetEpochMs = minutes?.let { m -> System.currentTimeMillis() + (m * 60L * 1000L) },
                showSleepTimerDialog = false
            )
        }
    }

    fun showDecoderDialog(show: Boolean) {
        _uiState.update { it.copy(showDecoderDialog = show) }
    }

    fun showVideoInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showVideoInfoDialog = show) }
    }

    fun showSleepTimerDialog(show: Boolean) {
        _uiState.update { it.copy(showSleepTimerDialog = show) }
    }

    fun showAudioBoostDialog(show: Boolean) {
        _uiState.update { it.copy(showAudioBoostDialog = show) }
    }

    fun showOrientationDialog(show: Boolean) {
        _uiState.update { it.copy(showOrientationDialog = show) }
    }

    fun showSpeedMenu(show: Boolean) {
        _uiState.update { it.copy(showSpeedMenu = show) }
    }

    fun showSubtitleSyncSlider(show: Boolean) {
        _uiState.update { it.copy(showSubtitleSyncSlider = show) }
    }

    fun showGestureVisualizer(show: Boolean) {
        _uiState.update { it.copy(showGestureOverlay = show) }
    }

    fun showFriendlyHelpDialog(show: Boolean) {
        _uiState.update { it.copy(showFriendlyHelpDialog = show) }
    }

    fun showThemeDialog(show: Boolean) {
        _uiState.update { it.copy(showThemeDialog = show) }
    }

    fun showSubtitleAppearanceDialog(show: Boolean) {
        _uiState.update { it.copy(showSubtitleAppearanceDialog = show) }
    }

    fun showVoiceProfilesDialog(show: Boolean) {
        _uiState.update { it.copy(showVoiceProfilesDialog = show) }
    }

    fun showProjectsDialog(show: Boolean) {
        _uiState.update { it.copy(showProjectsDialog = show) }
    }

    fun showSaveProjectDialog(show: Boolean) {
        _uiState.update { it.copy(showSaveProjectDialog = show) }
    }

    fun showSubtitleSyncDialog(show: Boolean) {
        _uiState.update { it.copy(showSubtitleSyncDialog = show) }
    }

    fun showSrtHistoryDialog(show: Boolean) {
        _uiState.update { it.copy(showSrtHistoryDialog = show) }
    }

    /** Reload a subtitle file from history. */
    fun loadHistoryEntry(entry: SrtHistoryEntity) {
        viewModelScope.launch {
            try {
                srtHistoryRepo.touchHistory(entry.id)
            } catch (_: Exception) {
            }
            _uiState.update { it.copy(showSrtHistoryDialog = false) }
            val content = try {
                srtHistoryRepo.fullContentFor(entry)
            } catch (_: Exception) {
                null
            }
            if (content.isNullOrBlank()) {
                _uiState.update {
                    it.copy(statusMessage = "History file mil nahi rahi — dobara import karo.")
                }
                return@launch
            }
            loadSrtText(content, entry.fileName)
        }
    }

    fun deleteHistoryEntry(entry: SrtHistoryEntity) {
        viewModelScope.launch {
            try {
                val path = entry.filePathOrUri
                if (!path.isNullOrBlank() && !path.startsWith("content://")) {
                    File(path).takeIf { it.exists() }?.delete()
                }
                srtHistoryRepo.deleteHistory(entry.id)
            } catch (e: Exception) {
                Log.e(TAG, "Delete history failed: ${e.message}", e)
            }
        }
    }

    fun clearSrtHistory() {
        viewModelScope.launch {
            try {
                srtHistoryRepo.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Clear history failed: ${e.message}", e)
            }
        }
    }

    // --- Subtitle file Save / Share ---

    private fun currentSubtitleContent(): String {
        val st = _uiState.value
        return try {
            SubtitleExportHelper.getFormattedSubtitles(
                st.parsedSubtitles, st.rawSrtText, st.detectedSubtitleFormat
            )
        } catch (_: Exception) {
            st.rawSrtText
        }
    }

    /** Save loaded subtitles as .srt into Downloads (MediaStore, no permission needed). */
    fun saveLoadedSubtitlesToDownloads() {
        val st = _uiState.value
        val content = currentSubtitleContent()
        if (content.isBlank()) {
            _uiState.update { it.copy(statusMessage = "No subtitles loaded — pehle Add Subtitle karo") }
            return
        }
        viewModelScope.launch {
            val (ok, msg) = try {
                withContext(Dispatchers.IO) {
                    SubtitleExportHelper.saveToDownloads(
                        context,
                        st.currentSrtFileName ?: "subtitles.srt",
                        content
                    )
                }
            } catch (e: Exception) {
                Pair(false, "Save failed: ${e.message}")
            }
            _uiState.update { it.copy(statusMessage = msg) }
            if (ok) {
                try {
                    srtHistoryRepo.recordHistory(
                        fileName = st.currentSrtFileName ?: "subtitles.srt",
                        sourceType = SrtHistorySource.EXPORTED,
                        rawContent = content,
                        cueCount = st.parsedSubtitles.size,
                        languageCode = st.appLanguage.code,
                        formatName = st.detectedSubtitleFormat.name
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Share loaded subtitles via system share sheet. */
    fun shareLoadedSubtitles() {
        val st = _uiState.value
        val content = currentSubtitleContent()
        if (content.isBlank()) {
            _uiState.update { it.copy(statusMessage = "No subtitles loaded — pehle Add Subtitle karo") }
            return
        }
        try {
            SubtitleExportHelper.shareSubtitles(
                context,
                st.videoTitle,
                content,
                st.currentSrtFileName ?: "subtitles.srt"
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Share failed: ${e.message}") }
        }
    }

    fun showLanguageSelectorDialog(show: Boolean) {
        _uiState.update { it.copy(showLanguageSelectorDialog = show) }
    }

    fun showGenderDialog(show: Boolean) {
        _uiState.update { it.copy(showGenderDialog = show) }
    }

    // --- Sound track / timing / zoom dialogs (all wired to real effects) ---

    fun showVideoSoundTrackDialog(show: Boolean) {
        _uiState.update { it.copy(showVideoSoundTrackDialog = show) }
    }

    /** Real audio routing: Hindi dub / original / dual-mix. */
    fun setActiveAudioTrackMode(mode: AudioTrackMode) {
        _uiState.update {
            it.copy(
                activeAudioTrack = mode,
                isVideoMuted = mode == AudioTrackMode.HINDI_TTS_DUB,
                isAutoDubbingEnabled = mode != AudioTrackMode.ORIGINAL_VIDEO,
                isDubbingMuted = false
            )
        }
    }

    fun selectVideoSoundTrack(item: VideoSoundTrackItem) {
        _uiState.update { it.copy(selectedVideoSoundTrackId = item.id) }
    }

    fun setSoundTrackSyncDelayMs(delayMs: Long) {
        val clamped = delayMs.coerceIn(-5000L, 5000L)
        _uiState.update { it.copy(soundTrackSyncDelayMs = clamped, subtitleOffsetMs = clamped) }
    }

    fun toggleVocalClarityBoost() {
        _uiState.update { it.copy(isVocalClarityBoostEnabled = !it.isVocalClarityBoostEnabled) }
    }

    /**
     * Screen toggle: offline voice (no internet needed) vs online voice
     * (best quality, needs internet). Takes effect on the next dialogue.
     */
    fun togglePreferOfflineVoice() {
        val next = !_uiState.value.preferOfflineVoice
        _uiState.update { it.copy(preferOfflineVoice = next) }
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        ensureOfflineVoiceApplied(_uiState.value)
        val msg = if (next) "Offline voice ON — bina internet ke awaaz"
        else "Online voice ON — best quality (internet chahiye)"
        _uiState.update {
            it.copy(statusMessage = msg, subtitleToast = msg)
        }
    }

    fun setPreferOfflineVoice(offline: Boolean) {
        if (_uiState.value.preferOfflineVoice == offline) return
        togglePreferOfflineVoice()
    }

    fun testSoundTrack() {
        val lang = _uiState.value.appLanguage
        speakText(if (lang == AppLanguage.HINDI) "यह ऑडियो ट्रैक परीक्षण है।" else "This is an audio track test.")
    }

    fun showPerfectTimingDialog(show: Boolean) {
        _uiState.update { it.copy(showPerfectTimingDialog = show) }
    }

    /** Story pre-roll (0–5000ms). 2000ms = voice starts 2s before subtitle. */
    fun setDialogueLeadTimeMs(leadMs: Long) {
        _uiState.update { it.copy(dialogueLeadTimeMs = leadMs.coerceIn(0L, 5000L)) }
    }

    fun togglePerfectTiming(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isPerfectTimingEnabled = enabled,
                dialogueLeadTimeMs = if (enabled) 2000L else it.dialogueLeadTimeMs
            )
        }
    }

    fun toggleDynamicRateAutoFit(enabled: Boolean) {
        _uiState.update { it.copy(isDynamicRateAutoFitEnabled = enabled) }
    }

    fun calibrateDialogueTiming() {
        _uiState.update {
            it.copy(
                soundTrackSyncDelayMs = 0L,
                subtitleOffsetMs = 0L,
                dialogueLeadTimeMs = 2000L,
                isPerfectTimingEnabled = true,
                lastSpokenSubtitleIndex = -1
            )
        }
    }

    fun showZoomControlsDialog(show: Boolean) {
        _uiState.update { it.copy(showZoomControlsDialog = show) }
    }

    fun setVideoZoomScale(scale: Float) {
        _uiState.update { it.copy(videoZoomScale = scale.coerceIn(1.0f, 5.0f)) }
    }

    fun resetVideoZoom() {
        _uiState.update {
            it.copy(
                videoZoomScale = 1.0f,
                videoPanOffsetX = 0f,
                videoPanOffsetY = 0f,
                aspectRatioMode = AspectRatioMode.FIT
            )
        }
    }


    /** Persona pitch for a cue (live speech and file export share this). */
    private fun pitchForCue(cue: SrtItem, state: TtsUiState): Float {
        if (state.genderVoiceMode != GenderVoiceMode.AUTO_MULTI_SPEAKER) {
            return when (state.genderVoiceMode) {
                GenderVoiceMode.SINGLE_DEEP_VOICE -> 0.9f
                GenderVoiceMode.FORCE_MALE -> 0.85f
                GenderVoiceMode.FORCE_FEMALE -> 1.28f
                GenderVoiceMode.FORCE_CHILD -> 1.48f
                GenderVoiceMode.FORCE_NARRATOR -> 0.95f
                else -> 1.0f
            }
        }
        return try {
            when (SpeakerDetector.analyzeLine(cue.text).speaker.gender) {
                SpeakerGender.FEMALE -> 1.28f
                SpeakerGender.MALE -> 0.85f
                SpeakerGender.CHILD -> 1.45f
                SpeakerGender.NARRATOR -> 0.95f
                else -> 1.0f
            }
        } catch (_: Exception) {
            1.0f
        }
    }

    private fun speedForState(state: TtsUiState): Float {
        val mult = if (state.isFastSubtitleSpeechEnabled) state.fastSubtitleSpeechMultiplier else 1.0f
        return (state.speedProgress / 50f).coerceIn(0.5f, 2.0f) * mult
    }

    /**
     * Per-cue speech rate, fitted to the cue's own on-screen duration. Fitting
     * to the cue (not cue + pre-roll) keeps the 2s story lead stable: each line
     * finishes before the next subtitle starts, so the queue never drifts.
     */
    private fun rateForCue(cue: SrtItem, state: TtsUiState, speechText: String): Float {
        val base = speedForState(state)
        val fitted = if (!state.isDynamicRateAutoFitEnabled) {
            base
        } else {
            val windowMs = (cue.endTimeMs - cue.startTimeMs).coerceAtLeast(1500L)
            val neededCps = speechText.length / (windowMs / 1000f)
            base * (neededCps / 14f).coerceIn(0.9f, 1.5f)
        }
        val isHindiLine = speechText.any { it in '\u0900'..'\u097F' }
        return if (isHindiLine) {
            fitted.coerceAtLeast(state.hindiFullSpeedMultiplier.coerceIn(0.5f, 2.0f))
                .coerceIn(0.5f, 2.0f)
        } else {
            fitted.coerceIn(0.5f, 2.0f)
        }
    }

    // --- Video Export with Hindi dubbing ---

    private var exportJob: Job? = null

    fun showVideoExportDialog(show: Boolean) {
        _uiState.update { it.copy(showVideoExportDialog = show) }
    }

    fun dismissExportDialog() {
        if (_uiState.value.isExportingVideo) return
        _uiState.update { it.copy(showVideoExportDialog = false, videoExportError = null) }
    }

    fun cancelVideoExport() {
        exportJob?.cancel()
        exportJob = null
        ExportForegroundService.stop(context)
        attachTtsListener()
        _uiState.update {
            it.copy(isExportingVideo = false, videoExportStatus = "Export cancelled")
        }
    }

    fun shareExportedVideo() {
        val path = _uiState.value.exportedVideoFilePath ?: return
        try {
            VideoExportManager.shareExportedVideo(context, File(path))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}", e)
        }
    }

    fun startVideoExport() {
        val st = _uiState.value
        if (st.isExportingVideo) return
        if (st.videoUri.isNullOrBlank()) {
            _uiState.update {
                it.copy(showVideoExportDialog = true, videoExportError = "No video selected. Pehle koi video kholo.")
            }
            return
        }
        if (st.parsedSubtitles.isEmpty()) {
            _uiState.update {
                it.copy(showVideoExportDialog = true, videoExportError = "No subtitles loaded. Pehle Add Subtitle se .srt file jodo.")
            }
            return
        }
        exportJob?.cancel()
        stopSpeaking()
        _uiState.update {
            it.copy(
                showVideoExportDialog = true,
                isExportingVideo = true,
                videoExportProgress = 0f,
                videoExportStatus = "Starting export...",
                videoExportError = null,
                exportedVideoFilePath = null,
                exportedSrtFilePath = null
            )
        }
        ExportForegroundService.start(context)
        exportJob = viewModelScope.launch {
            try {
                val snap = _uiState.value
                val config = VideoExportConfig(
                    includeHindiDubbing = true,
                    // Export always mixes the original video sound with the dub.
                    // Playback mute is a preview preference, not an export one;
                    // only an explicitly zero volume removes the original track.
                    includeOriginalAudio = snap.videoVolume > 0.01f,
                    originalAudioVolume = snap.videoVolume.coerceIn(0f, 1f),
                    voiceSpeechVolume = if (snap.isDubbingMuted) 0f else snap.dubbingVolume.coerceIn(0f, 1f),
                    includeTuneBgm = snap.isBgmEnabled,
                    bgmVolume = snap.bgmVolume.coerceIn(0f, 1f),
                    isDuckingEnabled = true,
                    duckingRatio = 0.6f,
                    subtitleFontSizeSp = snap.subtitleFontSize,
                    outputFileName = snap.videoTitle
                )
                val result = VideoExportManager.exportVideoWithHindiSubtitles(
                    context = context,
                    videoUriString = snap.videoUri,
                    videoTitle = snap.videoTitle,
                    rawSrtContent = snap.rawSrtText,
                    parsedCues = snap.parsedSubtitles,
                    config = config,
                    targetLanguageCode = snap.appLanguage.code,
                    targetLanguageLabel = snap.appLanguage.englishName,
                    synthesizer = { cue, text, outFile -> synthesizeCueToFile(cue, text, outFile) },
                    onProgress = { p, msg ->
                        _uiState.update { it.copy(videoExportProgress = p.coerceIn(0f, 1f), videoExportStatus = msg) }
                        ExportForegroundService.update(context, p, msg)
                    }
                )
                attachTtsListener()
                ExportForegroundService.stop(context)
                if (result.success) {
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            videoExportProgress = 1f,
                            videoExportStatus = "Export complete!",
                            exportedVideoFilePath = result.outputVideoFile?.absolutePath,
                            exportedSrtFilePath = result.outputSrtFile?.absolutePath,
                            videoExportError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            videoExportStatus = "Export failed",
                            videoExportError = result.errorMessage ?: "Unknown export failure"
                        )
                    }
                }
            } catch (e: CancellationException) {
                attachTtsListener()
                ExportForegroundService.stop(context)
                _uiState.update { it.copy(isExportingVideo = false, videoExportStatus = "Export cancelled") }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export crashed: ${e.message}", e)
                attachTtsListener()
                ExportForegroundService.stop(context)
                _uiState.update {
                    it.copy(isExportingVideo = false, videoExportStatus = "Export failed", videoExportError = e.message)
                }
            }
        }
    }

    /** Real device TTS -> WAV file for one subtitle cue (offline voice, no network). */
    private suspend fun synthesizeCueToFile(cue: SrtItem, text: String, outFile: File): Boolean {
        val ttsInstance = tts ?: return false
        if (!isTtsInitialized || text.isBlank()) return false
        return try {
            val state = _uiState.value
            ensureOfflineVoiceApplied(state)
            var speechText = text
            var romanized = false
            val offlineVoiceMissing = state.preferOfflineVoice &&
                !hasInstalledOfflineVoiceFor(state, state.appLanguage.code)
            if ((!state.isSpokenLangSupported || offlineVoiceMissing) &&
                DevanagariTransliteration.hasDevanagari(speechText)
            ) {
                speechText = DevanagariTransliteration.toPhoneticRoman(speechText)
                romanized = speechText.isNotBlank()
            }
            ensureOfflineAudibleLocale(state, romanized)
            val exportPitch = if (state.isSingleDeepVoiceOnly) NARRATOR_SOFT_PITCH else pitchForCue(cue, state)
            ttsInstance.setPitch(exportPitch)
            ttsInstance.setSpeechRate(rateForCue(cue, state, speechText).coerceIn(0.5f, 2.0f))
            if (outFile.exists()) outFile.delete()
            suspendCancellableCoroutine { cont ->
                val exportUtteranceId = "export_${cue.index}_${System.currentTimeMillis()}"
                ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == exportUtteranceId && cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == exportUtteranceId && cont.isActive) cont.resume(false)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == exportUtteranceId && cont.isActive) cont.resume(false)
                    }
                })
                val rc = try {
                    ttsInstance.synthesizeToFile(speechText, softSpeechParams(), outFile, exportUtteranceId)
                } catch (_: Exception) {
                    TextToSpeech.ERROR
                }
                if (rc != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
                cont.invokeOnCancellation {
                    try {
                        ttsInstance.stop()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }


    override fun onCleared() {
        super.onCleared()
        playbackTrackingJob?.cancel()
        speechSynthesisJob?.cancel()
        subtitleReaderJob?.cancel()
        ExportForegroundService.stop(context)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        networkCallback = null
        tts?.stop()
        tts?.shutdown()
        bgmAudioEngine.stop()
    }

    companion object {
        private const val TAG = "TtsViewModel"
        private const val MAX_SUBTITLE_BYTES = 40L * 1024 * 1024
        private const val SPEECH_CACHE_MAX_CUES = 20_000
        private const val NARRATOR_SOFT_PITCH = 0.95f
        private const val NARRATOR_SOFT_VOLUME = 0.85f
        private const val MAX_TTS_INIT_RETRIES = 5
    }
}
