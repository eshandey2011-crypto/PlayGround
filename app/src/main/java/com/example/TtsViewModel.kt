package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlin.coroutines.resume
import java.io.File
import java.util.Locale
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
    private val srtHistoryRepo = SrtHistoryRepository(srtHistoryDao)

    private val _uiState = MutableStateFlow(TtsUiState())
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var lastTtsRetryMs = 0L
    private val bgmAudioEngine = BgmAudioEngine(context)

    private var playbackTrackingJob: Job? = null
    private var speechSynthesisJob: Job? = null
    /**
     * Ordered speech queue: every subtitle line is read fully, exactly
     * once, in order — dense dialogues wait their turn instead of cutting
     * each other off mid-sentence. Main-thread only (see pumpSpeechQueue).
     */
    private val speechQueue = ArrayDeque<SrtItem>()
    @Volatile private var speechInFlight = false

    init {
        initTts()
        loadInitialData()
        startPlaybackClock()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
            _uiState.update {
                it.copy(
                    speechState = SpeechState.ERROR,
                    errorMessage = "Failed to initialize TTS engine: ${e.message}",
                    statusMessage = "TTS Init Error"
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            setupTtsEngine()
        } else {
            isTtsInitialized = false
            _uiState.update {
                it.copy(
                    speechState = SpeechState.ERROR,
                    statusMessage = "TTS Initialization Failed",
                    errorMessage = "TTS engine could not be initialized on device"
                )
            }
        }
    }

    private fun setupTtsEngine() {
        val ttsInstance = tts ?: return
        try {
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

            // Offline-first voice choice. A network-required voice stays
            // silent with internet off, so ONLY an installed offline voice in
            // the selected language is ever assigned. Otherwise the engine
            // default is kept (best chance of audible speech).
            val langCode = language.code
            val offlineLangVoice = pickOfflineVoice(availableVoices, langCode)
            val offlineAnyVoice = availableVoices.firstOrNull { isOfflineVoiceItem(it) }
            // Displayed selection prefers the language voice even when it is
            // network-based (user can install it), but the ENGINE only gets
            // an offline voice — never a network one.
            val langVoices = availableVoices.filter { v ->
                v.locale.contains(langCode, ignoreCase = true) ||
                    v.rawVoice?.locale?.language.equals(langCode, ignoreCase = true)
            }
            val chosenVoice = offlineLangVoice
                ?: langVoices.firstOrNull()
                ?: offlineAnyVoice
                ?: availableVoices.firstOrNull()
            val engineHasTts = try {
                ttsInstance.engines?.isNotEmpty() == true
            } catch (_: Exception) {
                true
            }

            val offlineApplied = applyOfflineVoiceOrKeepDefault(
                ttsInstance, offlineLangVoice, langCode
            )
            val effectivelySupported = isLangSupported && (offlineApplied || offlineLangVoice != null)

            _uiState.update {
                it.copy(
                    speechState = SpeechState.READY,
                    statusMessage = when {
                        !engineHasTts -> "No TTS engine on device — install Google Speech Services for voice"
                        effectivelySupported -> "${language.nativeName} TTS Ready (offline voice)"
                        isLangSupported -> "${language.nativeName} voice needs download — online only for now (install ${language.englishName} voice data)"
                        else -> "${language.nativeName} voice missing — fallback active (install ${language.englishName} voice data for best quality)"
                    },
                    isHindiSupported = if (language == AppLanguage.HINDI) effectivelySupported else it.isHindiSupported,
                    isSpokenLangSupported = effectivelySupported,
                    voices = availableVoices,
                    selectedVoice = chosenVoice
                )
            }
            refreshOfflineVoiceStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring TTS: ${e.message}", e)
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

    /** True only for installed on-device voices (audible with internet off). */
    private fun isOfflineVoiceItem(v: TtsVoiceItem): Boolean {
        return try {
            !v.isNetworkRequired &&
                v.rawVoice?.isNetworkConnectionRequired != true &&
                v.rawVoice?.name?.lowercase()?.contains("network") != true &&
                v.rawVoice?.name?.lowercase()?.contains("-online") != true &&
                v.rawVoice?.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        } catch (_: Exception) {
            false
        }
    }

    /** Best installed offline voice for a language, or null when none exists. */
    private fun pickOfflineVoice(voices: List<TtsVoiceItem>, langCode: String): TtsVoiceItem? {
        val langVoices = voices.filter { v ->
            v.locale.contains(langCode, ignoreCase = true) ||
                v.rawVoice?.locale?.language.equals(langCode, ignoreCase = true)
        }
        return langVoices.firstOrNull { isOfflineVoiceItem(it) }
    }

    /**
     * Applies a voice to the engine only when it is installed on-device AND
     * matches the target language. Never assigns network/not-installed
     * voices — that is the #1 cause of silence with internet off.
     * @return true when a matching offline voice was applied.
     */
    private fun applyOfflineVoiceOrKeepDefault(
        ttsInstance: TextToSpeech,
        candidate: TtsVoiceItem?,
        langCode: String
    ): Boolean {
        val raw = candidate?.rawVoice ?: return false
        if (!isOfflineVoiceItem(candidate)) return false
        if (!raw.locale.language.equals(langCode, ignoreCase = true)) return false
        return try {
            ttsInstance.voice = raw
            true
        } catch (_: Exception) {
            false
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
            val offlineLangVoice = pickOfflineVoice(_uiState.value.voices, langCode)
            val langVoices = _uiState.value.voices.filter { v ->
                v.locale.contains(langCode, ignoreCase = true) ||
                    v.rawVoice?.locale?.language.equals(langCode, ignoreCase = true)
            }
            val chosen = offlineLangVoice ?: langVoices.firstOrNull()
            val offlineApplied = applyOfflineVoiceOrKeepDefault(
                ttsInstance, offlineLangVoice, langCode
            )
            val effectivelySupported = supported && (offlineApplied || offlineLangVoice != null)
            stopSpeaking()
            _uiState.update {
                it.copy(
                    isHindiSupported = if (language == AppLanguage.HINDI) effectivelySupported else it.isHindiSupported,
                    isSpokenLangSupported = effectivelySupported,
                    selectedVoice = chosen ?: it.selectedVoice,
                    statusMessage = if (effectivelySupported) "${language.nativeName} TTS Ready (offline voice)"
                    else "${language.nativeName} voice missing — install ${language.englishName} voice data",
                    previewingPersonaId = null
                )
            }
            refreshOfflineVoiceStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error switching TTS language: ${e.message}", e)
        }
    }

    /** (Re)attaches the live-speech progress listener (export swaps it temporarily). */
    private fun attachTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speechInFlight = true
                _uiState.update { it.copy(speechState = SpeechState.SPEAKING) }
            }

            override fun onDone(utteranceId: String?) {
                // Binder thread: hop to Main before touching the queue.
                viewModelScope.launch { onSpeechEnded() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModelScope.launch { onSpeechEnded() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                viewModelScope.launch { onSpeechEnded() }
            }
        })
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
        // Lead-time pre-roll: start speaking slightly BEFORE the cue appears
        // so voice lands on time instead of lagging behind video.
        val leadMs = state.dialogueLeadTimeMs.coerceIn(0L, 2000L)

        // Find active subtitle cue (lead window for voice, exact window for display)
        val activeCue = parsed.firstOrNull { cue ->
            effectivePosition in cue.startTimeMs..cue.endTimeMs
        }
        val upcomingCue = parsed.firstOrNull { cue ->
            effectivePosition in (cue.startTimeMs - leadMs)..cue.endTimeMs
        }

        val activeIndex = if (activeCue != null) parsed.indexOf(activeCue) else -1
        val upcomingIndex = if (upcomingCue != null) parsed.indexOf(upcomingCue) else -1

        _uiState.update {
            it.copy(
                videoPositionMs = positionMs,
                activeSubtitle = activeCue,
                lastSpokenSubtitleIndex = if (upcomingCue != null) upcomingIndex else it.lastSpokenSubtitleIndex
            )
        }

        // Ordered speech queue: every new line joins the queue and is read
        // fully, exactly once, in order. A running line is never cut
        // mid-sentence for the next one — dense dialogues wait their turn.
        // Stale lines (moment long passed) are dropped, never spoken late.
        if (state.isOfflineSubtitleMode && state.isVideoPlaying && upcomingCue != null && upcomingIndex != state.lastSpokenSubtitleIndex) {
            _uiState.update { it.copy(lastSpokenSubtitleIndex = upcomingIndex) }
            val prevEnd = parsed.getOrNull(upcomingIndex - 1)?.endTimeMs
                ?: parsed.getOrNull(state.lastSpokenSubtitleIndex)?.endTimeMs
            val speaking = state.speechState == SpeechState.SPEAKING || speechInFlight
            val queueBusy = speechQueue.isNotEmpty()
            if (!speaking && !queueBusy) {
                // Engine truly idle: speak right away.
                speakSubtitleCue(upcomingCue)
            } else if (!queueBusy && SpeechClarity.mayInterruptSpeech(speaking, prevEnd, effectivePosition)) {
                // Previous line's moment passed: take over for the new line.
                speechQueue.clear()
                try {
                    tts?.stop()
                } catch (_: Exception) {
                }
                speechInFlight = false
                speakSubtitleCue(upcomingCue)
            } else if (speechQueue.none { it.index == upcomingCue.index }) {
                // Engine busy: wait turn behind the running line.
                // A non-empty queue is never flushed — queued lines are kept.
                if (speechQueue.size >= SpeechClarity.QUEUE_MAX_SIZE) {
                    speechQueue.removeFirst()
                }
                speechQueue.addLast(upcomingCue)
            }
        }
        // Keep draining: a freshly idle engine picks up the next line.
        if (speechQueue.isNotEmpty()) {
            SpeechClarity.dropStaleHeads(speechQueue, effectivePosition)
            pumpSpeechQueue()
        }
    }

    /**
     * Speaks the next queued line when the engine is idle. Called from the
     * playback clock and from utterance-end callbacks (via onSpeechEnded).
     */
    private fun pumpSpeechQueue() {
        if (speechInFlight) return
        if (_uiState.value.speechState == SpeechState.SPEAKING) return
        val next = speechQueue.firstOrNull() ?: return
        speechQueue.removeFirst()
        speakSubtitleCue(next)
    }

    /** Engine finished / stopped / failed one utterance: advance the queue. */
    private fun onSpeechEnded() {
        speechInFlight = false
        _uiState.update { it.copy(speechState = SpeechState.READY) }
        viewModelScope.launch { pumpSpeechQueue() }
    }

    /**
     * User tapped a subtitle line: hear THIS line now (clears the queue).
     */
    fun speakCueNow(cue: SrtItem) {
        speechQueue.clear()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        speechInFlight = false
        val idx = _uiState.value.parsedSubtitles.indexOf(cue)
        if (idx >= 0) {
            _uiState.update { it.copy(lastSpokenSubtitleIndex = idx) }
        }
        speakSubtitleCue(cue)
    }

    fun speakSubtitleCue(cue: SrtItem) {
        val ttsInstance = tts
        if (ttsInstance == null || !isTtsInitialized) {
            // Engine not ready (common right after install/offline boot):
            // retry init at most once per 10s, then keep the status honest.
            val now = System.currentTimeMillis()
            if (now - lastTtsRetryMs > 10_000L) {
                lastTtsRetryMs = now
                try {
                    tts?.shutdown()
                } catch (_: Exception) {
                }
                isTtsInitialized = false
                initTts()
            }
            _uiState.update { it.copy(statusMessage = "TTS engine loading — voice will start automatically") }
            return
        }

        speechSynthesisJob?.cancel()
        speechSynthesisJob = viewModelScope.launch {
            try {
                val state = _uiState.value
                // Respect mute / dubbing switches so voice never fights user settings.
                if (state.isDubbingMuted || state.isMasterMuted || !state.isAutoDubbingEnabled) return@launch

                // Keep an installed offline voice applied (internet-off safety).
                ensureOfflineVoiceApplied(state)

                val analysis = SpeakerDetector.analyzeLine(cue.text)
                // Precomputed at subtitle load (no per-cue regex cost -> no lag).
                var targetText = state.speechTextCache[cue.index]
                if (targetText.isNullOrBlank()) {
                    targetText = if (state.appLanguage == AppLanguage.HINDI || state.isOfflineHindiVoiceInSubtitles) {
                        if (isDevanagari(cue.text)) cue.text else OfflineSubtitleEngine.translateLineToHindi(cue.text)
                    } else {
                        cue.cleanSpokenText.ifBlank { cue.text }
                    }
                }

                // Audibility fallback: when the selected language voice data is
                // missing on device, speak romanized phonetic text with the
                // default locale instead of silence.
                if (!state.isSpokenLangSupported && DevanagariTransliteration.hasDevanagari(targetText)) {
                    targetText = DevanagariTransliteration.toPhoneticRoman(targetText)
                }
                // Clarity: terminal pause so words don't run together.
                targetText = SpeechClarity.withTerminalPause(targetText)
                if (targetText.isBlank()) return@launch

                // Persona pitch + speed (shared with export synthesis).
                val pitch = pitchForCue(cue, state)
                val baseSpeed = rateForCue(cue, state, targetText)

                ttsInstance.setPitch(pitch)
                ttsInstance.setSpeechRate(baseSpeed)

                val utteranceId = "cue_${cue.index}_${System.currentTimeMillis()}"
                val rc = try {
                    ttsInstance.speak(targetText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } catch (_: Exception) {
                    TextToSpeech.ERROR
                }
                if (rc == TextToSpeech.ERROR) {
                    speechInFlight = false
                    _uiState.update {
                        it.copy(statusMessage = "Voice ruk gayi — Offline Voice check me Install dabao")
                    }
                    refreshOfflineVoiceStatus()
                } else {
                    speechInFlight = true
                }
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
            val raw = if (!hindiMode) {
                // Multi-language mode: speak subtitles in the selected language as-is.
                item.cleanSpokenText.ifBlank { item.text }
            } else if (isDevanagari(item.text)) {
                item.text
            } else {
                try {
                    OfflineSubtitleEngine.translateLineToHindi(item.text)
                } catch (_: Exception) {
                    item.text
                }
            }
            // Clarity: terminal pause baked in once, so live speech never mumbles.
            val spoken = SpeechClarity.withTerminalPause(raw)
            if (spoken.isNotBlank()) map[item.index] = spoken
        }
        return map
    }

    /**
     * Guarantees an installed offline voice is applied to the engine.
     * Called on the speak path so internet-off playback never goes silent
     * because a network voice was selected.
     */
    private fun ensureOfflineVoiceApplied(state: TtsUiState = _uiState.value) {
        try {
            val manager = VoiceManager()
            val params = manager.getVoiceParamsForSpeaker(
                speaker = null,
                availableVoices = state.voices,
                selectedGlobalVoice = state.selectedVoice,
                genderVoiceMode = GenderVoiceMode.SINGLE_DEEP_VOICE,
                selectedVoicePersona = state.selectedVoicePersona
            )
            // Skip redundant setVoice() calls (they can blip ongoing speech).
            val candidate = params.voiceItem?.rawVoice
            if (candidate != null && tts?.voice?.name != candidate.name) {
                manager.applyVoiceToTts(tts, params, state.appLanguage.code)
            }
        } catch (_: Exception) {
        }
    }

    // --- Subtitle Loading & Translation ---

    fun loadSrtText(rawText: String, fileName: String? = null) {
        viewModelScope.launch {
            try {
                val format = SubtitleFormat.fromFileName(fileName)
                val parsedItems = SrtParser.parse(rawText)
                val langDetection = SrtLanguageDetector.detectLanguage(rawText)
                // Precompute spoken Hindi once (withContext Default would be nicer,
                // but cues are small; keeps ordering simple on Main).
                val speechCache = try {
                    withContext(Dispatchers.Default) { buildSpeechTextCache(parsedItems) }
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
                        showLanguageWarningBanner = !langDetection.isHindi && it.autoTranslateNonHindiOnLoad
                    )
                }

                // If non-Hindi and auto translate enabled, prompt or translate
                if (!langDetection.isHindi && _uiState.value.autoTranslateNonHindiOnLoad) {
                    _uiState.update { it.copy(showAutoTranslatePromptDialog = true) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse SRT: ${e.message}", e)
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
        _uiState.update { it.copy(isVideoPlaying = !it.isVideoPlaying) }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _uiState.value.videoDurationMs.coerceAtLeast(1000L))
        // Fresh moment: drop queued lines, stop current speech, restart at target.
        speechQueue.clear()
        speechInFlight = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        // pendingSeekMs bridges Compose state -> ExoPlayer.seekTo() in the surface view.
        _uiState.update { it.copy(pendingSeekMs = clamped, lastSpokenSubtitleIndex = -1) }
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
        if (kotlin.math.abs(exoPositionMs - st.videoPositionMs) > 350L) {
            updateVideoPosition(exoPositionMs.coerceAtLeast(0L))
        }
    }

    // --- Subtitle Synchronization ---

    fun setSubtitleOffset(offsetMs: Long) {
        _uiState.update { it.copy(soundTrackSyncDelayMs = offsetMs) }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        _uiState.update { it.copy(soundTrackSyncDelayMs = it.soundTrackSyncDelayMs + deltaMs) }
    }

    fun alignSubtitleToCurrentPosition() {
        val active = _uiState.value.activeSubtitle ?: return
        val currentVideoPos = _uiState.value.videoPositionMs
        val diff = currentVideoPos - active.startTimeMs
        setSubtitleOffset(diff)
    }

    fun toggleSameToSameTimeDelivery(enabled: Boolean) {
        _uiState.update { it.copy(isSameToSameTimeDelivery = enabled, isFastSubtitleSpeechEnabled = enabled) }
    }

    fun lockSameToSameSync() {
        // Fast reset and calibration
        setSubtitleOffset(0L)
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
        speechQueue.clear()
        speechInFlight = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _uiState.update { it.copy(speechState = SpeechState.READY, previewingPersonaId = null) }
    }

    fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sample_${System.currentTimeMillis()}")
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
        speechQueue.clear()
        speechInFlight = false
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

    fun showLanguageSelectorDialog(show: Boolean) {
        _uiState.update { it.copy(showLanguageSelectorDialog = show) }
    }

    fun showOfflineSubtitleReaderDialog(show: Boolean) {
        if (show) refreshOfflineVoiceStatus()
        _uiState.update { it.copy(showOfflineSubtitleReaderDialog = show) }
    }

    /**
     * Recomputes 100%-offline voice readiness from live engine data and
     * publishes it for the Offline Voice dialog + status badge.
     */
    fun refreshOfflineVoiceStatus() {
        try {
            val ttsInstance = tts
            val language = _uiState.value.appLanguage
            val hasEngine = ttsInstance != null && try {
                ttsInstance.engines?.isNotEmpty() == true
            } catch (_: Exception) {
                isTtsInitialized
            }
            val availability = if (ttsInstance != null && isTtsInitialized) {
                try {
                    ttsInstance.isLanguageAvailable(ttsLocaleFor(language))
                } catch (_: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
            } else {
                TextToSpeech.LANG_MISSING_DATA
            }
            val availCode = when {
                availability >= TextToSpeech.LANG_AVAILABLE -> OfflineVoiceCheck.AVAIL_OK
                availability == TextToSpeech.LANG_MISSING_DATA -> OfflineVoiceCheck.AVAIL_MISSING_DATA
                else -> OfflineVoiceCheck.AVAIL_NOT_SUPPORTED
            }
            val offlineVoice = pickOfflineVoice(_uiState.value.voices, language.code)
            val status = OfflineVoiceCheck.decide(
                languageNativeName = language.nativeName,
                languageEnglishName = language.englishName,
                hasEngine = hasEngine,
                langAvailability = availCode,
                offlineVoiceName = offlineVoice?.displayName
            )
            _uiState.update { it.copy(offlineVoiceStatus = status) }
        } catch (_: Exception) {
        }
    }

    /** Speaks a short test line; reports whether the engine accepted it. */
    fun testOfflineVoice() {
        val ttsInstance = tts
        if (ttsInstance == null || !isTtsInitialized) {
            _uiState.update { it.copy(statusMessage = "Engine taiyaar nahi — thodi der me Test dobara dabao") }
            refreshOfflineVoiceStatus()
            return
        }
        try {
            val language = _uiState.value.appLanguage
            ensureOfflineVoiceApplied(_uiState.value)
            val line = if (language == AppLanguage.HINDI) {
                "नमस्ते! ऑफ़लाइन आवाज़ तैयार है।"
            } else {
                "Offline subtitle voice is ready."
            }
            ttsInstance.setPitch(1.0f)
            ttsInstance.setSpeechRate(0.95f)
            val rc = ttsInstance.speak(
                SpeechClarity.withTerminalPause(line),
                TextToSpeech.QUEUE_FLUSH, null,
                "offline_test_${System.currentTimeMillis()}"
            )
            _uiState.update {
                it.copy(
                    statusMessage = if (rc == TextToSpeech.SUCCESS) "Test voice chal rahi hai — suno!"
                    else "Test fail — Offline Voice dialog me Install dabao"
                )
            }
            refreshOfflineVoiceStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Voice test failed: ${e.message}", e)
        }
    }

    /** One-tap download of missing offline voice data (needs internet once). */
    fun installTtsVoiceData() {
        try {
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed: ${e.message}", e)
            _uiState.update { it.copy(statusMessage = "Play Store se 'Google Speech Services' install karo") }
        }
    }

    /** Opens system TTS settings (engine + voice data management). */
    fun openTtsSettings() {
        try {
            val intent = android.content.Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            installTtsVoiceData()
        }
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
        _uiState.update { it.copy(soundTrackSyncDelayMs = delayMs.coerceIn(-5000L, 5000L)) }
    }

    fun toggleVocalClarityBoost() {
        _uiState.update { it.copy(isVocalClarityBoostEnabled = !it.isVocalClarityBoostEnabled) }
    }

    fun testSoundTrack() {
        val lang = _uiState.value.appLanguage
        speakText(if (lang == AppLanguage.HINDI) "यह ऑडियो ट्रैक परीक्षण है।" else "This is an audio track test.")
    }

    fun showPerfectTimingDialog(show: Boolean) {
        _uiState.update { it.copy(showPerfectTimingDialog = show) }
    }

    /** Voice pre-roll (0–2000ms). Directly drives the speak-ahead window. */
    fun setDialogueLeadTimeMs(leadMs: Long) {
        _uiState.update { it.copy(dialogueLeadTimeMs = leadMs.coerceIn(0L, 2000L)) }
    }

    fun togglePerfectTiming(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isPerfectTimingEnabled = enabled,
                dialogueLeadTimeMs = if (enabled) {
                    if (it.dialogueLeadTimeMs <= 0L) 200L else it.dialogueLeadTimeMs
                } else 0L
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
                dialogueLeadTimeMs = 200L,
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
                GenderVoiceMode.SINGLE_DEEP_VOICE -> 0.72f
                GenderVoiceMode.FORCE_MALE -> 0.85f
                GenderVoiceMode.FORCE_FEMALE -> 1.28f
                GenderVoiceMode.FORCE_CHILD -> 1.48f
                GenderVoiceMode.FORCE_NARRATOR -> 0.92f
                else -> 1.0f
            }
        }
        return try {
            when (SpeakerDetector.analyzeLine(cue.text).speaker.gender) {
                SpeakerGender.FEMALE -> 1.28f
                SpeakerGender.MALE -> 0.85f
                SpeakerGender.CHILD -> 1.45f
                SpeakerGender.NARRATOR -> 0.92f
                else -> 1.0f
            }
        } catch (_: Exception) {
            1.0f
        }
    }

    private fun speedForState(state: TtsUiState): Float {
        return SpeechClarity.baseRate(
            state.speedProgress,
            state.isFastSubtitleSpeechEnabled,
            state.fastSubtitleSpeechMultiplier
        )
    }

    /**
     * Per-cue speech rate. When dynamic auto-fit is on, the rate bends
     * slightly so long dialogues still fit inside short cue windows —
     * always bounded to clear-speech range (see [SpeechClarity]).
     */
    private fun rateForCue(cue: SrtItem, state: TtsUiState, speechText: String): Float {
        val base = speedForState(state)
        val windowMs = cue.endTimeMs - cue.startTimeMs - state.dialogueLeadTimeMs
        return SpeechClarity.autofitRate(
            base,
            speechText.length,
            windowMs,
            state.isDynamicRateAutoFitEnabled
        )
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
        exportJob = viewModelScope.launch {
            try {
                val snap = _uiState.value
                val config = VideoExportConfig(
                    includeHindiDubbing = true,
                    includeOriginalAudio = !snap.isVideoMuted,
                    originalAudioVolume = snap.videoVolume.coerceIn(0f, 1f),
                    voiceSpeechVolume = if (snap.isDubbingMuted) 0f else snap.dubbingVolume.coerceIn(0f, 1f),
                    includeTuneBgm = snap.isBgmEnabled,
                    bgmVolume = snap.bgmVolume.coerceIn(0f, 1f),
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
                    }
                )
                attachTtsListener()
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
                _uiState.update { it.copy(isExportingVideo = false, videoExportStatus = "Export cancelled") }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export crashed: ${e.message}", e)
                attachTtsListener()
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
            if (!state.isSpokenLangSupported && DevanagariTransliteration.hasDevanagari(speechText)) {
                speechText = DevanagariTransliteration.toPhoneticRoman(speechText)
            }
            // Clarity: terminal pause, same as live speech.
            speechText = SpeechClarity.withTerminalPause(speechText)
            if (speechText.isBlank()) return false
            ttsInstance.setPitch(pitchForCue(cue, state))
            ttsInstance.setSpeechRate(rateForCue(cue, state, speechText).coerceIn(0.5f, 1.5f))
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
                    ttsInstance.synthesizeToFile(speechText, null, outFile, exportUtteranceId)
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
        tts?.stop()
        tts?.shutdown()
        bgmAudioEngine.stop()
    }

    companion object {
        private const val TAG = "TtsViewModel"
    }
}
