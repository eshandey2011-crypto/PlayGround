(() => {
  'use strict'

  const $ = (id) => document.getElementById(id)
  const root = document.documentElement
  const app = $('app')
  const video = $('video')
  const frame = $('playerFrame')

  const TRACKS = {
    dawn: {
      title: 'Himalayan Sunrise', meta: 'Nature · Cinematic', file: 'sample-en.srt',
      video: 'assets/dawn.mp4', poster: 'assets/dawn.jpg', duration: 18, startAt: 3.2,
      cues: [
        { start: 0.4, end: 2.6, en: 'The first light touches the Himalayan peaks.', hi: 'पहली किरण हिमालय की चोटियों को छूती है।' },
        { start: 2.6, end: 4.8, en: 'A quiet valley wakes beneath a river of gold.', hi: 'सुनहरी नदी के नीचे एक शांत घाटी जागती है।' },
        { start: 4.8, end: 7.0, en: 'Mist drifts through the pines like a whispered secret.', hi: 'धुंध देवदारों के बीच फुसफुसाए रहस्य की तरह बहती है।' },
        { start: 7.0, end: 9.2, en: 'Somewhere, a temple bell greets the morning.', hi: 'कहीं दूर एक मंदिर की घंटी सुबह का स्वागत करती है।' },
        { start: 9.2, end: 11.4, en: 'Every sunrise is a promise the mountains keep.', hi: 'हर सूर्योदय वह वादा है जो पहाड़ निभाते हैं।' },
        { start: 11.4, end: 13.6, en: 'Breathe in the stillness. Let the world wait.', hi: 'इस ठहराव में साँस लें। दुनिया रुकी रह सकती है।' },
        { start: 13.6, end: 15.8, en: 'The day begins not with noise, but with light.', hi: 'दिन शोर से नहीं, रोशनी से शुरू होता है।' },
        { start: 15.8, end: 17.7, en: 'And for a moment, everything is enough.', hi: 'और एक पल के लिए, सब कुछ पर्याप्त है।' }
      ]
    },
    city: {
      title: 'Neon City Nights', meta: 'Cinematic · Synthwave', file: 'neon-city.srt',
      video: 'assets/city.mp4', poster: 'assets/city.jpg', duration: 10, startAt: 1.6,
      cues: [
        { start: 0.3, end: 2.2, en: 'The city switches on, one window at a time.', hi: 'शहर एक-एक खिड़की जलाकर चालू होता है।' },
        { start: 2.2, end: 4.1, en: 'Neon rivers run through streets of rain.', hi: 'नीयन की नदियाँ बारिश की सड़कों पर बहती हैं।' },
        { start: 4.1, end: 6.0, en: 'Every sign is a story nobody finishes.', hi: 'हर साइन एक कहानी है जो कोई पूरी नहीं करता।' },
        { start: 6.0, end: 7.9, en: 'Midnight hums in electric blue.', hi: 'आधी रात बिजली-सी नीली धुन गुनगुनाती है।' },
        { start: 7.9, end: 9.7, en: 'Stay a while. The night is still young.', hi: 'थोड़ी देर रुकिए। रात अभी जवान है।' }
      ]
    },
    ocean: {
      title: 'Ocean Horizon', meta: 'Travel · Golden hour', file: 'ocean-horizon.srt',
      video: 'assets/ocean.mp4', poster: 'assets/ocean.jpg', duration: 10, startAt: 1.5,
      cues: [
        { start: 0.3, end: 2.2, en: 'A single sail leans into the evening wind.', hi: 'एक अकेला पाल शाम की हवा में झुकता है।' },
        { start: 2.2, end: 4.1, en: 'The sea keeps every colour the sun lets go.', hi: 'समुद्र सूरज के छोड़े हर रंग को संजो लेता है।' },
        { start: 4.1, end: 6.0, en: 'Waves write and rewrite the same horizon.', hi: 'लहरें वही क्षितिज बार-बार लिखती और मिटाती हैं।' },
        { start: 6.0, end: 7.9, en: 'There is no hurry out here.', hi: 'यहाँ कोई जल्दी नहीं है।' },
        { start: 7.9, end: 9.7, en: 'Only water, light, and the long way home.', hi: 'बस पानी, रोशनी और घर लौटने का लंबा रास्ता।' }
      ]
    }
  }

  const STORE = 'srt-player-web-v1'
  const settings = loadSettings()
  let currentKey = settings.track && TRACKS[settings.track] ? settings.track : 'dawn'
  let track = TRACKS[currentKey]
  let cues = track.cues.slice()
  let offset = Number(settings.offset) || 0
  let activeCue = -1
  let subtitlesVisible = settings.cc !== false
  let bilingual = settings.bilingual !== false
  let ttsLang = settings.ttsLang || 'hi'
  let ttsRate = Number(settings.ttsRate) || 0.95
  let ttsPlaying = false
  let ttsIndex = -1
  let ttsToken = 0
  let uiLang = settings.uiLang || 'en'
  let suppressScroll = false
  let lastTime = -1
  let objectUrl = null

  const fmt = (seconds) => {
    if (!Number.isFinite(seconds) || seconds < 0) seconds = 0
    const m = Math.floor(seconds / 60)
    const s = Math.floor(seconds % 60)
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v))

  function loadSettings() {
    try { return JSON.parse(localStorage.getItem(STORE) || '{}') || {} } catch { return {} }
  }
  function saveSettings() {
    try {
      localStorage.setItem(STORE, JSON.stringify({
        theme: root.dataset.theme, track: currentKey, offset, cc: subtitlesVisible,
        bilingual, ttsLang, ttsRate, uiLang, style: settings.style
      }))
    } catch {}
  }
  function toast(message, kind = 'info') {
    const el = $('toast')
    const icon = kind === 'error' ? 'i-x' : kind === 'warn' ? 'i-help' : 'i-check'
    el.innerHTML = `<svg class="ico"><use href="#${icon}"></use></svg><span></span>`
    el.querySelector('span').textContent = message
    el.classList.add('is-visible')
    clearTimeout(toast._t)
    toast._t = setTimeout(() => el.classList.remove('is-visible'), 3200)
  }

  const I18N = {
    appTitle: ['SRT Video Player', 'SRT वीडियो प्लेयर'],
    tagline: ['Smart subtitles · Hindi dubbing', 'स्मार्ट सबटाइटल · हिन्दी डबिंग'],
    navLocal: ['Local', 'लोकल'],
    navPlayer: ['Player', 'प्लेयर'],
    navTts: ['Subtitle TTS', 'सबटाइटल TTS'],
    nowPlaying: ['Now playing', 'अभी चल रहा है'],
    synced: ['Subtitle synced', 'सबटाइटल सिंक'],
    openSrt: ['Open SRT', 'SRT खोलें'],
    addSubtitle: ['Add subtitle', 'सबटाइटल जोड़ें'],
    quickSync: ['Subtitle sync', 'सबटाइटल सिंक'],
    quickStyle: ['Appearance', 'रूप-रंग'],
    quickTts: ['Offline TTS', 'ऑफ़लाइन TTS'],
    quickTtsSub: ['Hindi narration', 'हिन्दी वाचन'],
    quickExport: ['Export', 'एक्सपोर्ट'],
    quickExportSub: ['SRT · burned-in', 'SRT · बर्न-इन'],
    featured: ['Built for bilingual viewing', 'द्विभाषी देखने के लिए बना'],
    tabSubs: ['Subtitles', 'सबटाइटल'],
    tabStyle: ['Style', 'स्टाइल'],
    tabVoice: ['Voice', 'आवाज़'],
    searchSubs: ['Search dialogue or speakers…', 'संवाद या वक्ता खोजें…'],
    pasteSrt: ['Paste SRT', 'SRT पेस्ट करें'],
    loadFile: ['Load file', 'फ़ाइल खोलें'],
    fontSize: ['Font size', 'फ़ॉन्ट आकार'],
    textColor: ['Text color', 'टेक्स्ट रंग'],
    backdrop: ['Backdrop', 'बैकड्रॉप'],
    position: ['Position', 'स्थिति'],
    boldText: ['Bold text', 'मोटा टेक्स्ट'],
    boldTextSub: ['High-contrast subtitle weight', 'हाई-कॉन्ट्रास्ट सबटाइटल'],
    outline: ['Outline', 'आउटलाइन'],
    outlineSub: ['Crisp edge on bright scenes', 'चमकीले दृश्यों में स्पष्ट किनारा'],
    offlineNarration: ['Offline Hindi narration', 'ऑफ़लाइन हिन्दी वाचन'],
    offlineNarrationSub: ['Read every cue with a natural voice. No network needed.', 'हर पंक्ति को प्राकृतिक आवाज़ में सुनें। इंटरनेट की ज़रूरत नहीं।'],
    voice: ['Voice', 'आवाज़'],
    rate: ['Rate', 'गति'],
    readAll: ['Read all cues', 'सभी पंक्तियाँ पढ़ें'],
    tipTitle: ['Gesture ready', 'जेस्चर तैयार'],
    tipBody: ['Double-tap the left or right side to seek. Swipe up for volume, down for brightness.', 'सीक करने के लिए बाएँ या दाएँ दो बार टैप करें। आवाज़ के लिए ऊपर स्वाइप करें।'],
    onDevice: ['On-device library', 'डिवाइस लाइब्रेरी'],
    localTitle: ['Your videos, beautifully subtitled', 'आपके वीडियो, सुंदर सबटाइटल के साथ'],
    localSub: ['Pick up where you left off. Every SRT is indexed and ready for bilingual playback.', 'जहाँ छोड़ा था वहीं से शुरू करें। हर SRT द्विभाषी प्लेबैक के लिए तैयार है।'],
    importVideo: ['Import video', 'वीडियो इम्पोर्ट करें'],
    searchLibrary: ['Search your library…', 'अपनी लाइब्रेरी खोजें…'],
    filterAll: ['All', 'सभी'],
    filterReady: ['SRT ready', 'SRT तैयार'],
    filterRecent: ['Recently played', 'हाल में चलाए'],
    filterSamples: ['Samples', 'नमूने'],
    importTitle: ['Add a local video', 'लोकल वीडियो जोड़ें'],
    importBody: ['MP4, WebM or MKV from this device. Nothing is uploaded.', 'इस डिवाइस से MP4, WebM या MKV। कुछ भी अपलोड नहीं होता।'],
    browseFiles: ['Browse files', 'फ़ाइलें देखें'],
    privacyTitle: ['Private by design', 'निजी और सुरक्षित'],
    privacyBody: ['Videos and subtitle files stay in this browser. Translation context and voice playback run entirely on-device.', 'वीडियो और सबटाइटल इसी ब्राउज़र में रहते हैं। अनुवाद और आवाज़ पूरी तरह डिवाइस पर चलते हैं।'],
    offlineReady: ['Offline ready', 'ऑफ़लाइन तैयार'],
    accessibility: ['Accessibility', 'सुगम्यता'],
    ttsTitle: ['Offline subtitle reader', 'ऑफ़लाइन सबटाइटल रीडर'],
    ttsSub: ['Listen to any SRT with expressive Hindi narration. Perfect for language practice, accessibility and dubbing prep.', 'किसी भी SRT को हिन्दी वाचन में सुनें। भाषा अभ्यास, सुगम्यता और डबिंग के लिए बेहतरीन।'],
    loadSrt: ['Load SRT', 'SRT खोलें'],
    nowReading: ['Now reading', 'अभी पढ़ रहे हैं'],
    cueQueue: ['Cue queue', 'पंक्ति कतार'],
    cueQueueSub: ['Tap any line to read it aloud', 'सुनने के लिए कोई पंक्ति टैप करें'],
    searchDialogue: ['Search dialogue…', 'संवाद खोजें…']
  }

  function applyLanguage() {
    root.lang = uiLang === 'hi' ? 'hi' : 'en'
    $('langLabel').textContent = uiLang === 'hi' ? 'हि' : 'EN'
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const pair = I18N[el.dataset.i18n]
      if (pair) el.textContent = uiLang === 'hi' ? pair[1] : pair[0]
    })
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const pair = I18N[el.dataset.i18nPlaceholder]
      if (pair) el.placeholder = uiLang === 'hi' ? pair[1] : pair[0]
    })
  }

  function setTheme(theme) {
    root.dataset.theme = theme
    $('themeToggle').setAttribute('aria-label', theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme')
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.content = theme === 'dark' ? '#070b16' : '#eef3fb'
    saveSettings()
  }

  function applyStyle(style) {
    settings.style = Object.assign({
      size: 30, color: '#ffd600', bg: 55, bottom: 18, bold: true, outline: true
    }, settings.style || {}, style || {})
    const s = settings.style
    root.style.setProperty('--sub-size', `${s.size}px`)
    root.style.setProperty('--sub-color', s.color)
    root.style.setProperty('--sub-bg', String(s.bg / 100))
    root.style.setProperty('--sub-bottom', `${s.bottom}%`)
    $('fontSize').value = s.size
    $('fontSizeOut').textContent = `${s.size}px`
    $('bgOpacity').value = s.bg
    $('bgOpacityOut').textContent = `${s.bg}%`
    $('position').value = s.bottom
    $('positionOut').textContent = `${s.bottom}%`
    document.querySelectorAll('#swatches .swatch').forEach(sw => sw.classList.toggle('is-active', sw.dataset.color === s.color))
    $('boldToggle').classList.toggle('is-on', s.bold)
    $('boldToggle').setAttribute('aria-checked', String(s.bold))
    $('outlineToggle').classList.toggle('is-on', s.outline)
    $('outlineToggle').setAttribute('aria-checked', String(s.outline))
    const pill = $('subtitlePill')
    pill.classList.toggle('bold', s.bold)
    pill.classList.toggle('outline', s.outline)
    $('stylePreview').querySelector('.preview-en').style.fontSize = `${Math.max(11, s.size * 0.5)}px`
    $('stylePreview').querySelector('.preview-hi').style.fontSize = `${Math.max(9, s.size * 0.4)}px`
    $('stylePreview').querySelectorAll('span').forEach(el => { el.style.color = s.color })
    $('styleSummary').textContent = `${colorName(s.color)} · ${s.size}px`
    setRange($('fontSize')); setRange($('bgOpacity')); setRange($('position'))
    saveSettings()
  }

  function colorName(hex) {
    return { '#ffd600': 'Yellow', '#ffffff': 'White', '#00e5ff': 'Cyan', '#ff9bd2': 'Pink' }[hex] || 'Custom'
  }

  function setRange(input) {
    if (!input) return
    const min = Number(input.min || 0), max = Number(input.max || 100)
    const pct = max === min ? 0 : ((Number(input.value) - min) / (max - min)) * 100
    input.style.setProperty('--pct', `${pct}%`)
  }

  function parseTime(value) {
    const cleaned = value.trim().replace(',', '.')
    const parts = cleaned.split(':').map(Number)
    if (parts.some(Number.isNaN)) return NaN
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2]
    if (parts.length === 2) return parts[0] * 60 + parts[1]
    return NaN
  }

  function parseSubtitles(text) {
    const normalized = text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
    const blocks = normalized.split(/\n{2,}/)
    const out = []
    for (const block of blocks) {
      const lines = block.split('\n').map(l => l.trim()).filter(Boolean)
      if (!lines.length) continue
      let timeLine = lines.find(l => l.includes('-->'))
      if (!timeLine) continue
      const idx = lines.indexOf(timeLine)
      const payloadLines = lines.slice(idx + 1).map(line => line.replace(/<[^>]+>/g, '').trim()).filter(Boolean)
      const payload = payloadLines.join(' ')
      if (!payload) continue
      const [a, b] = timeLine.split('-->').map(s => s.trim().split(/\s+/)[0])
      const start = parseTime(a), end = parseTime(b)
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue
      out.push({ start, end, text: payload, lines: payloadLines })
    }
    return out.sort((a, b) => a.start - b.start)
  }

  function toCues(parsed) {
    if (!parsed.length) return []
    return parsed.map((c, i) => {
      const lines = c.lines && c.lines.length ? c.lines : [c.text]
      const hiLine = lines.find(line => /[\u0900-\u097F]/.test(line))
      const enLine = lines.find(line => !/[\u0900-\u097F]/.test(line))
      const previous = cues[i] || {}
      if (hiLine && enLine) return { start: c.start, end: c.end, en: enLine, hi: hiLine }
      if (hiLine) return { start: c.start, end: c.end, hi: c.text, en: previous.en && !/[\u0900-\u097F]/.test(previous.en) ? previous.en : `Line ${i + 1}` }
      return { start: c.start, end: c.end, en: c.text, hi: previous.hi || '' }
    })
  }

  function applyTrack(key, opts = {}) {
    if (!TRACKS[key]) return
    currentKey = key
    track = TRACKS[key]
    cues = track.cues.map(c => ({ ...c }))
    offset = 0
    activeCue = -1
    if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null }
    video.src = track.video
    video.poster = track.poster
    video.load()
    $('videoTitle').textContent = track.title
    $('videoMeta').textContent = track.meta
    $('trackName').textContent = 'English + हिन्दी'
    $('trackFile').textContent = track.file
    const setDuration = () => { $('duration').textContent = fmt(video.duration || track.duration) }
    if (video.readyState >= 1) setDuration()
    video.addEventListener('loadedmetadata', setDuration, { once: true })
    $('duration').textContent = fmt(track.duration)
    $('offsetRange').value = 0
    $('offsetValue').textContent = '+0.0'
    $('syncSummary').textContent = 'Offset 0.0s'
    renderCueList()
    renderQueue()
    renderMiniQueue()
    setSubtitleVisible(subtitlesVisible)
    updateCueCounts()
    const startAt = track.startAt
    const startPlayback = () => {
      try { video.currentTime = startAt } catch {}
      video.play().catch(() => frame.classList.add('is-paused'))
    }
    if (video.readyState >= 1) startPlayback()
    else video.addEventListener('loadedmetadata', startPlayback, { once: true })
    saveSettings()
    if (opts.toast) toast(`${track.title} loaded`, 'info')
  }

  function updateCueCounts() {
    $('cueCount').textContent = String(cues.length)
    $('trackCues').textContent = `${cues.length} cues`
    $('ttsCueCount').textContent = String(cues.length)
    $('readerIndex').textContent = `Cue ${Math.max(0, activeCue) + 1} of ${cues.length}`
  }

  function renderCueList(filter = '') {
    const list = $('cueList')
    const q = filter.trim().toLowerCase()
    const rows = cues.map((cue, i) => ({ cue, i })).filter(({ cue }) => !q || `${cue.en} ${cue.hi}`.toLowerCase().includes(q))
    list.innerHTML = rows.map(({ cue, i }) => `
      <button class="cue${i === activeCue ? ' is-active' : ''}" data-cue="${i}" aria-label="Seek to ${fmt(cue.start)}">
        <span class="cue-time">${fmt(cue.start)}</span>
        <span class="cue-copy"><span class="cue-en">${escapeHtml(cue.en || '')}</span><span class="cue-hi">${escapeHtml(cue.hi || '')}</span></span>
        <span class="cue-play" aria-hidden="true"><svg class="ico"><use href="#i-play"></use></svg></span>
      </button>`).join('') || `<p class="empty-note">No cues match “${escapeHtml(filter)}”.</p>`
    list.querySelectorAll('[data-cue]').forEach(btn => {
      btn.addEventListener('click', () => seekToCue(Number(btn.dataset.cue)))
    })
    if (activeCue >= 0 && !suppressScroll) {
      const el = list.querySelector('.cue.is-active')
      if (el) {
        const listRect = list.getBoundingClientRect()
        const elRect = el.getBoundingClientRect()
        if (elRect.top < listRect.top) list.scrollTop -= listRect.top - elRect.top
        else if (elRect.bottom > listRect.bottom) list.scrollTop += elRect.bottom - listRect.bottom
      }
    }
  }

  function renderQueue(filter = '') {
    const list = $('queueList')
    const q = filter.trim().toLowerCase()
    const rows = cues.map((cue, i) => ({ cue, i })).filter(({ cue }) => !q || `${cue.en} ${cue.hi}`.toLowerCase().includes(q))
    list.innerHTML = rows.map(({ cue, i }) => `
      <button class="queue-item${i === ttsIndex ? ' is-active' : ''}" data-queue="${i}">
        <span class="queue-num">${String(i + 1).padStart(2, '0')}</span>
        <span class="queue-copy"><span class="queue-hi">${escapeHtml(cue.hi || cue.en || '')}</span><span class="queue-en">${escapeHtml(cue.en || '')}</span></span>
        <span class="queue-play" aria-hidden="true"><svg class="ico"><use href="#i-speaker"></use></svg></span>
      </button>`).join('') || `<p class="empty-note">No cues match your search.</p>`
    list.querySelectorAll('[data-queue]').forEach(btn => btn.addEventListener('click', () => { speakOne(Number(btn.dataset.queue)) }))
  }

  function renderMiniQueue() {
    const wrap = $('miniQueue')
    wrap.innerHTML = cues.slice(0, 4).map((cue, i) => `
      <button class="mini-cue${i === ttsIndex ? ' is-active' : ''}" data-mini="${i}">
        <span class="num">${String(i + 1).padStart(2, '0')}</span>
        <span>${escapeHtml(cue.hi || cue.en || '')}</span>
      </button>`).join('')
    wrap.querySelectorAll('[data-mini]').forEach(btn => btn.addEventListener('click', () => speakOne(Number(btn.dataset.mini))))
  }

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]))
  }

  function currentCueAt(t) {
    const time = t - offset
    return cues.findIndex(c => time >= c.start && time < c.end)
  }

  function updateSubtitles(force = false) {
    const time = video.currentTime
    const found = currentCueAt(time)
    if (!force && time === lastTime && found === activeCue) return
    lastTime = time
    if (found !== activeCue) {
      activeCue = found
      renderCueList($('subSearch').value)
      updateCueCounts()
      if (found >= 0) updateReader(found)
    }
    const cue = cues[found]
    if (cue && subtitlesVisible) {
      $('subEn').textContent = cue.en || ''
      $('subHi').textContent = cue.hi || ''
      $('subEn').style.display = cue.en ? '' : 'none'
      $('subHi').style.display = cue.hi ? '' : 'none'
      $('subtitleWrap').classList.remove('is-hidden')
    } else if (subtitlesVisible) {
      const prev = cues[Math.max(0, found - 1)]
      if (prev && time >= prev.end - 0.4 && time < prev.end + 0.5) {
        $('subEn').textContent = prev.en || ''
        $('subHi').textContent = prev.hi || ''
        $('subtitleWrap').classList.remove('is-hidden')
      } else {
        $('subtitleWrap').classList.add('is-hidden')
      }
    }
    $('timeline').setAttribute('aria-valuenow', String(Math.round(time)))
  }

  function updateReader(index) {
    const cue = cues[index] || cues[0]
    if (!cue) return
    const hi = ttsLang === 'hi'
    $('readerLineHi').textContent = cue.hi || cue.en || ''
    $('readerLineEn').textContent = cue.en || ''
    $('readerLineHi').style.opacity = hi ? '1' : '.55'
    $('readerLineEn').style.opacity = hi ? '.75' : '1'
    $('readerIndex').textContent = `Cue ${index + 1} of ${cues.length}`
    const pct = cues.length ? ((index + 1) / cues.length) * 100 : 0
    $('readerProgress').style.width = `${Math.min(100, pct)}%`
    renderQueue($('queueSearch').value)
    renderMiniQueue()
  }

  function seekToCue(index) {
    const cue = cues[index]
    if (!cue) return
    video.currentTime = clamp(cue.start + offset + 0.03, 0, video.duration || track.duration)
    video.play().catch(() => {})
    activeCue = -1
    updateSubtitles(true)
    toast(`Jumped to ${fmt(cue.start)}`, 'info')
  }

  function setSubtitleVisible(visible) {
    subtitlesVisible = visible
    $('subtitleWrap').classList.toggle('is-hidden', !visible)
    $('ccBtn').classList.toggle('is-on', visible)
    $('ccTopBtn').classList.toggle('is-on', visible)
    saveSettings()
    updateSubtitles(true)
  }

  function updateTimeline() {
    const duration = video.duration || track.duration || 1
    const pct = clamp((video.currentTime / duration) * 100, 0, 100)
    $('timelineFill').style.width = `${pct}%`
    $('timelineKnob').style.left = `${pct}%`
    let buffered = 0
    try { if (video.buffered.length) buffered = (video.buffered.end(video.buffered.length - 1) / duration) * 100 } catch {}
    $('timelineBuffer').style.width = `${clamp(buffered, 0, 100)}%`
    $('currentTime').textContent = fmt(video.currentTime)
    $('duration').textContent = fmt(duration)
    $('timeline').setAttribute('aria-valuemax', String(Math.round(duration)))
    $('readerProgress').style.width = `${pct}%`
  }

  function tick() {
    updateTimeline()
    updateSubtitles()
    requestAnimationFrame(tick)
  }

  function togglePlay() {
    if (video.paused) video.play().catch(() => {})
    else video.pause()
  }

  function syncPlayState() {
    frame.classList.toggle('is-paused', video.paused)
    $('playPause').setAttribute('aria-label', video.paused ? 'Play video' : 'Pause video')
  }

  function cycleSpeed() {
    const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2]
    const now = video.playbackRate || 1
    const idx = speeds.indexOf(now)
    const next = speeds[(idx + 1) % speeds.length]
    video.playbackRate = next
    $('speedBtn').textContent = `${next}×`
    toast(`Playback speed ${next}×`, 'info')
  }

  function seekBy(delta) {
    video.currentTime = clamp(video.currentTime + delta, 0, video.duration || track.duration)
    updateSubtitles(true)
  }

  function seekFromEvent(e) {
    const rect = $('timeline').getBoundingClientRect()
    const clientX = e.touches ? e.touches[0].clientX : e.clientX
    const pct = clamp((clientX - rect.left) / rect.width, 0, 1)
    video.currentTime = pct * (video.duration || track.duration)
    updateSubtitles(true)
  }

  function bindTimeline() {
    const el = $('timeline')
    let dragging = false
    el.addEventListener('pointerdown', e => {
      dragging = true
      el.setPointerCapture(e.pointerId)
      seekFromEvent(e)
    })
    el.addEventListener('pointermove', e => { if (dragging) seekFromEvent(e) })
    el.addEventListener('pointerup', () => { dragging = false })
    el.addEventListener('pointercancel', () => { dragging = false })
    el.addEventListener('keydown', e => {
      if (e.key === 'ArrowRight') { e.preventDefault(); seekBy(5) }
      if (e.key === 'ArrowLeft') { e.preventDefault(); seekBy(-5) }
    })
  }

  function updateOffset(next) {
    offset = Math.round(clamp(next, -5, 5) * 10) / 10
    $('offsetRange').value = String(offset)
    $('offsetValue').textContent = `${offset >= 0 ? '+' : ''}${offset.toFixed(1)}`
    $('syncSummary').textContent = `Offset ${offset >= 0 ? '+' : ''}${offset.toFixed(1)}s`
    setRange($('offsetRange'))
    activeCue = -1
    updateSubtitles(true)
    saveSettings()
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {})
    else if (frame.requestFullscreen) frame.requestFullscreen().catch(() => {})
    else toast('Fullscreen is not available in this browser', 'warn')
  }

  function captureFrame() {
    if (!video.videoWidth) { toast('Video is not ready yet', 'warn'); return }
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0)
    const cue = cues[activeCue]
    if (cue && subtitlesVisible) {
      ctx.font = `800 ${Math.round(canvas.width * 0.035)}px Inter, sans-serif`
      ctx.textAlign = 'center'
      ctx.fillStyle = 'rgba(0,0,0,.55)'
      const text = cue.en || ''
      const width = ctx.measureText(text).width
      ctx.fillRect((canvas.width - width) / 2 - 18, canvas.height - 120, width + 36, 56)
      ctx.fillStyle = settings.style.color
      ctx.fillText(text, canvas.width / 2, canvas.height - 82)
    }
    canvas.toBlob(blob => {
      if (!blob) return
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${track.title.toLowerCase().replace(/\s+/g, '-')}-frame.png`
      a.click()
      setTimeout(() => URL.revokeObjectURL(a.href), 2000)
      toast('Frame saved as PNG', 'info')
    }, 'image/png')
  }

  function buildSrt(mode = 'bilingual') {
    return cues.map((cue, i) => {
      const text = mode === 'hi' ? (cue.hi || cue.en || '') : [cue.en, cue.hi].filter(Boolean).join('\n')
      return `${i + 1}\n${srtTime(cue.start)} --> ${srtTime(cue.end)}\n${text}`
    }).join('\n\n') + '\n'
  }

  function srtTime(seconds) {
    const ms = Math.max(0, Math.round(seconds * 1000))
    const h = Math.floor(ms / 3600000)
    const m = Math.floor((ms % 3600000) / 60000)
    const s = Math.floor((ms % 60000) / 1000)
    const rem = ms % 1000
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')},${String(rem).padStart(3, '0')}`
  }

  function downloadSrt(mode) {
    const blob = new Blob([buildSrt(mode)], { type: 'text/plain;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${currentKey}-${mode === 'hi' ? 'hindi' : 'bilingual'}.srt`
    a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 2500)
  }

  function doExport(mode) {
    if (mode === 'video') {
      downloadSrt('bilingual')
      toast('Bilingual SRT downloaded — burn-in is queued in the app renderer', 'info')
    } else {
      downloadSrt(mode)
      toast(mode === 'hi' ? 'Hindi SRT downloaded' : 'Bilingual SRT downloaded', 'info')
    }
  }

  function speakOne(index) {
    if (!('speechSynthesis' in window)) { toast('Speech synthesis is unavailable in this browser', 'warn'); return }
    const cue = cues[index]
    if (!cue) return
    window.speechSynthesis.cancel()
    ttsToken++
    const token = ttsToken
    ttsIndex = index
    ttsPlaying = false
    updateReader(index)
    setTtsUi(false)
    const utter = new SpeechSynthesisUtterance(ttsLang === 'hi' ? (cue.hi || cue.en) : (cue.en || cue.hi))
    utter.lang = $('voiceSelect').value
    utter.rate = ttsRate
    const voices = window.speechSynthesis.getVoices()
    const match = voices.find(v => v.lang && v.lang.toLowerCase().startsWith(utter.lang.slice(0, 2).toLowerCase())) || voices.find(v => v.lang && v.lang.toLowerCase() === utter.lang.toLowerCase())
    if (match) utter.voice = match
    utter.onend = () => { if (token === ttsToken) { setTtsUi(false); updateReader(index) } }
    utter.onerror = () => { if (token === ttsToken) setTtsUi(false) }
    window.speechSynthesis.speak(utter)
    toast(`Reading cue ${index + 1}`, 'info')
  }

  function speakAll(start = 0) {
    if (!('speechSynthesis' in window)) { toast('Speech synthesis is unavailable in this browser', 'warn'); return }
    if (ttsPlaying) { stopTts(); return }
    window.speechSynthesis.cancel()
    ttsToken++
    const token = ttsToken
    ttsPlaying = true
    setTtsUi(true)
    const speakFrom = (i) => {
      if (token !== ttsToken || !ttsPlaying) return
      if (i >= cues.length) { ttsPlaying = false; ttsIndex = -1; setTtsUi(false); renderQueue($('queueSearch').value); renderMiniQueue(); toast('Finished reading subtitle track', 'info'); return }
      ttsIndex = i
      updateReader(i)
      const cue = cues[i]
      const utter = new SpeechSynthesisUtterance(ttsLang === 'hi' ? (cue.hi || cue.en) : (cue.en || cue.hi))
      utter.lang = $('voiceSelect').value
      utter.rate = ttsRate
      const voices = window.speechSynthesis.getVoices()
      const match = voices.find(v => v.lang && v.lang.toLowerCase() === utter.lang.toLowerCase()) || voices.find(v => v.lang && v.lang.toLowerCase().startsWith(utter.lang.slice(0, 2).toLowerCase()))
      if (match) utter.voice = match
      utter.onend = () => { if (token === ttsToken && ttsPlaying) setTimeout(() => speakFrom(i + 1), 160) }
      utter.onerror = () => { if (token === ttsToken) { ttsPlaying = false; setTtsUi(false) } }
      window.speechSynthesis.speak(utter)
    }
    speakFrom(start)
    toast('Reading all cues', 'info')
  }

  function stopTts() {
    ttsToken++
    ttsPlaying = false
    if ('speechSynthesis' in window) window.speechSynthesis.cancel()
    setTtsUi(false)
    renderQueue($('queueSearch').value)
    renderMiniQueue()
  }

  function setTtsUi(playing) {
    $('readerPlay').classList.toggle('is-playing', playing)
    $('readerPlay').setAttribute('aria-label', playing ? 'Stop reading' : 'Read all cues')
    $('queueReadAll').querySelector('span').textContent = playing ? 'Stop reading' : (I18N.readAll[uiLang === 'hi' ? 1 : 0])
  }

  function showScreen(name) {
    document.querySelectorAll('[data-screen-panel]').forEach(panel => panel.classList.toggle('is-active', panel.dataset.screenPanel === name))
    document.querySelectorAll('[data-screen]').forEach(btn => btn.classList.toggle('is-active', btn.dataset.screen === name))
    if (name !== 'player') { video.pause(); stopTts() }
    window.scrollTo({ top: 0, behavior: 'smooth' })
    if (name === 'tts') { updateReader(Math.max(0, activeCue)); }
  }

  function setSidePanel(name) {
    document.querySelectorAll('.side-tab').forEach(tab => tab.classList.toggle('is-active', tab.dataset.panel === name))
    document.querySelectorAll('.side-pane').forEach(pane => pane.classList.toggle('is-active', pane.dataset.pane === name))
  }

  function applyLoadedSubtitle(text, name = 'pasted.srt') {
    const parsed = parseSubtitles(text)
    if (!parsed.length) { toast('No valid SRT cues found. Check the timestamps.', 'error'); return false }
    cues = toCues(parsed)
    if (!cues.length) { toast('No valid SRT cues found.', 'error'); return false }
    activeCue = -1
    offset = 0
    $('offsetRange').value = 0
    $('offsetValue').textContent = '+0.0'
    $('syncSummary').textContent = 'Offset 0.0s'
    $('trackName').textContent = name.replace(/\.(srt|vtt)$/i, '') || 'Custom track'
    $('trackFile').textContent = name
    $('videoTitle').textContent = track.title
    renderCueList($('subSearch').value)
    renderQueue($('queueSearch').value)
    renderMiniQueue()
    updateCueCounts()
    updateSubtitles(true)
    setSubtitleVisible(true)
    if (cues[0]) video.currentTime = clamp(cues[0].start + 0.05, 0, video.duration || track.duration)
    if (document.querySelector('.screen.is-active')?.id === 'screen-player') video.play().catch(() => {})
    saveSettings()
    toast(`${cues.length} cues loaded`, 'info')
    return true
  }

  function readFileText(file, cb) {
    const reader = new FileReader()
    reader.onload = () => cb(String(reader.result || ''), file.name)
    reader.onerror = () => toast('Could not read that file', 'error')
    reader.readAsText(file, 'utf-8')
  }

  function bind() {
    $('themeToggle').addEventListener('click', () => setTheme(root.dataset.theme === 'dark' ? 'light' : 'dark'))
    $('langToggle').addEventListener('click', () => { uiLang = uiLang === 'hi' ? 'en' : 'hi'; applyLanguage(); saveSettings() })
    $('helpBtn').addEventListener('click', () => $('helpDialog').showModal())
    $('brandHome').addEventListener('click', e => { e.preventDefault(); showScreen('player') })

    document.querySelectorAll('[data-screen]').forEach(btn => btn.addEventListener('click', () => showScreen(btn.dataset.screen)))
    document.querySelectorAll('[data-screen-jump]').forEach(btn => btn.addEventListener('click', () => showScreen(btn.dataset.screenJump)))
    document.querySelectorAll('.quick[data-open]').forEach(btn => btn.addEventListener('click', () => {
      const target = btn.dataset.open
      if (target === 'sync') $('syncDialog').showModal()
      else if (target === 'export') $('exportDialog').showModal()
      else if (target === 'style') setSidePanel('style')
    }))
    document.querySelectorAll('.side-tab').forEach(tab => tab.addEventListener('click', () => setSidePanel(tab.dataset.panel)))

    $('playPause').addEventListener('click', togglePlay)
    $('centerPlay').addEventListener('click', togglePlay)
    video.addEventListener('click', togglePlay)
    $('back10').addEventListener('click', () => seekBy(-10))
    $('fwd10').addEventListener('click', () => seekBy(10))
    $('muteBtn').addEventListener('click', () => { video.muted = !video.muted; updateVolumeUi() })
    $('volume').addEventListener('input', e => { video.volume = Number(e.target.value); video.muted = video.volume === 0; updateVolumeUi() })
    $('speedBtn').addEventListener('click', cycleSpeed)
    $('ccBtn').addEventListener('click', () => setSubtitleVisible(!subtitlesVisible))
    $('ccTopBtn').addEventListener('click', () => setSubtitleVisible(!subtitlesVisible))
    $('fullscreenBtn').addEventListener('click', toggleFullscreen)
    $('shotBtn').addEventListener('click', captureFrame)
    video.addEventListener('play', syncPlayState)
    video.addEventListener('pause', syncPlayState)
    video.addEventListener('loadedmetadata', updateTimeline)
    video.addEventListener('timeupdate', updateSubtitles)
    bindTimeline()

    $('openSrtBtn').addEventListener('click', () => $('srtFile').click())
    $('pasteSubBtn').addEventListener('click', () => { $('subText').value = ''; $('addDialog').showModal() })
    $('addSubBtn').addEventListener('click', () => { $('subText').value = buildSrt('bilingual'); $('addDialog').showModal() })
    $('srtFile').addEventListener('change', e => { const f = e.target.files[0]; if (f) readFileText(f, (t, n) => applyLoadedSubtitle(t, n)); e.target.value = '' })
    $('srtFile2').addEventListener('change', e => {
      const f = e.target.files[0]
      if (f) readFileText(f, (t, n) => { $('subText').value = t; toast(`${n} ready to apply`, 'info') })
      e.target.value = ''
    })
    $('addForm').addEventListener('submit', e => {
      if (e.submitter && e.submitter.id === 'applySub') {
        e.preventDefault()
        if (applyLoadedSubtitle($('subText').value, 'custom-track.srt')) $('addDialog').close()
      }
    })
    document.querySelectorAll('[data-sample]').forEach(btn => btn.addEventListener('click', () => {
      const key = btn.dataset.sample
      $('subText').value = buildSrtFor(TRACKS[key])
      toast(`${TRACKS[key].title} sample inserted`, 'info')
    }))
    $('applySub').addEventListener('click', () => {})

    $('ttsLoadSrt').addEventListener('click', () => $('srtFile').click())
    $('subSearch').addEventListener('input', e => renderCueList(e.target.value))
    $('queueSearch').addEventListener('input', e => renderQueue(e.target.value))

    $('fontSize').addEventListener('input', e => { applyStyle({ size: Number(e.target.value) }) })
    $('bgOpacity').addEventListener('input', e => { applyStyle({ bg: Number(e.target.value) }) })
    $('position').addEventListener('input', e => { applyStyle({ bottom: Number(e.target.value) }) })
    document.querySelectorAll('#swatches .swatch').forEach(sw => sw.addEventListener('click', () => applyStyle({ color: sw.dataset.color })))
    $('boldToggle').addEventListener('click', () => applyStyle({ bold: !settings.style.bold }))
    $('outlineToggle').addEventListener('click', () => applyStyle({ outline: !settings.style.outline }))

    $('offsetRange').addEventListener('input', e => updateOffset(Number(e.target.value)))
    document.querySelectorAll('[data-offset]').forEach(btn => btn.addEventListener('click', () => {
      const v = btn.dataset.offset
      updateOffset(v === 'reset' ? 0 : offset + Number(v))
    }))

    $('doExport').addEventListener('click', () => {
      const selected = document.querySelector('input[name="export"]:checked')
      doExport(selected ? selected.value : 'srt')
      $('exportDialog').close()
    })

    $('readAllBtn').addEventListener('click', () => speakAll(0))
    $('stopReadBtn').addEventListener('click', stopTts)
    $('queueReadAll').addEventListener('click', () => ttsPlaying ? stopTts() : speakAll(0))
    $('queueStop').addEventListener('click', stopTts)
    $('readerPlay').addEventListener('click', () => ttsPlaying ? stopTts() : speakAll(0))
    $('readerBack').addEventListener('click', () => { ttsIndex = Math.max(0, (ttsIndex < 0 ? 0 : ttsIndex) - 1); updateReader(ttsIndex); if (ttsPlaying) { ttsPlaying = false; speakAll(ttsIndex) } })
    $('readerNext').addEventListener('click', () => { ttsIndex = Math.min(cues.length - 1, (ttsIndex < 0 ? -1 : ttsIndex) + 1); updateReader(ttsIndex); if (ttsPlaying) { ttsPlaying = false; speakAll(ttsIndex) } })
    document.querySelectorAll('[data-tts-lang]').forEach(btn => btn.addEventListener('click', () => {
      ttsLang = btn.dataset.ttsLang
      document.querySelectorAll('[data-tts-lang]').forEach(b => b.classList.toggle('is-active', b === btn))
      updateReader(Math.max(0, ttsIndex))
      saveSettings()
    }))
    $('ttsRate').addEventListener('input', e => {
      ttsRate = Number(e.target.value)
      $('rateOut').textContent = `${ttsRate.toFixed(2)}×`
      $('readerRate').value = String(ttsRate)
      $('readerRateOut').textContent = `${ttsRate.toFixed(2)}×`
      setRange($('ttsRate')); setRange($('readerRate'))
      saveSettings()
    })
    $('readerRate').addEventListener('input', e => {
      ttsRate = Number(e.target.value)
      $('readerRateOut').textContent = `${ttsRate.toFixed(2)}×`
      $('ttsRate').value = String(ttsRate)
      $('rateOut').textContent = `${ttsRate.toFixed(2)}×`
      setRange($('readerRate')); setRange($('ttsRate'))
      saveSettings()
    })
    $('voiceSelect').addEventListener('change', e => { $('readerVoice').value = e.target.value; saveSettings() })
    $('readerVoice').addEventListener('change', e => { $('voiceSelect').value = e.target.value; saveSettings() })
    $('queueCopy').addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(buildSrt('bilingual')); toast('All cues copied', 'info') }
      catch { toast('Clipboard access is blocked', 'warn') }
    })

    document.querySelectorAll('.video-card').forEach(card => card.addEventListener('click', () => {
      applyTrack(card.dataset.video, { toast: true })
      showScreen('player')
    }))
    document.querySelectorAll('.chip[data-filter]').forEach(chip => chip.addEventListener('click', () => {
      document.querySelectorAll('.chip[data-filter]').forEach(c => c.classList.toggle('is-active', c === chip))
      filterLibrary()
    }))
    $('librarySearch').addEventListener('input', filterLibrary)
    $('videoFile').addEventListener('change', e => {
      const file = e.target.files[0]
      if (!file) return
      if (objectUrl) URL.revokeObjectURL(objectUrl)
      objectUrl = URL.createObjectURL(file)
      video.src = objectUrl
      video.poster = track.poster
      video.load()
      $('videoTitle').textContent = file.name.replace(/\.[^.]+$/, '')
      $('videoMeta').textContent = `${file.type || 'Local video'} · on-device`
      video.play().catch(() => {})
      showScreen('player')
      toast('Video loaded from this device', 'info')
      e.target.value = ''
    })
    $('moreBtn').addEventListener('click', () => $('helpDialog').showModal())

    document.addEventListener('keydown', e => {
      const tag = (e.target.tagName || '').toLowerCase()
      const editing = ['input', 'textarea', 'select'].includes(tag) || e.target.isContentEditable
      if (editing) return
      if (e.key === ' ' || e.code === 'Space') { e.preventDefault(); if (document.querySelector('.screen.is-active')?.id === 'screen-player') togglePlay() }
      if (e.key === 'ArrowRight') { e.preventDefault(); seekBy(5) }
      if (e.key === 'ArrowLeft') { e.preventDefault(); seekBy(-5) }
      if (e.key.toLowerCase() === 'f') toggleFullscreen()
      if (e.key.toLowerCase() === 'm') { video.muted = !video.muted; updateVolumeUi() }
      if (e.key.toLowerCase() === 's') setSubtitleVisible(!subtitlesVisible)
      if (e.key === '/' && document.querySelector('.screen.is-active')?.id === 'screen-player') { e.preventDefault(); $('subSearch').focus() }
    })

    window.addEventListener('beforeunload', () => { if (objectUrl) URL.revokeObjectURL(objectUrl) })
  }

  function filterLibrary() {
    const query = $('librarySearch').value.trim().toLowerCase()
    const active = document.querySelector('.chip[data-filter].is-active')?.dataset.filter || 'all'
    document.querySelectorAll('.video-card').forEach(card => {
      const tags = (card.dataset.filterTags || '').split(/\s+/)
      const text = card.textContent.toLowerCase()
      const okTag = active === 'all' || tags.includes(active)
      const okText = !query || text.includes(query)
      card.classList.toggle('is-hidden', !(okTag && okText))
    })
  }

  function buildSrtFor(t) {
    return t.cues.map((cue, i) => `${i + 1}\n${srtTime(cue.start)} --> ${srtTime(cue.end)}\n${[cue.en, cue.hi].filter(Boolean).join('\n')}`).join('\n\n') + '\n'
  }

  function updateVolumeUi() {
    $('muteBtn').classList.toggle('is-muted', video.muted || video.volume === 0)
    $('volume').value = String(video.muted ? 0 : video.volume)
  }

  function initTtsVoices() {
    if (!('speechSynthesis' in window)) return
    const fill = () => {
      const voices = window.speechSynthesis.getVoices()
      if (!voices.length) return
      for (const select of [$('voiceSelect'), $('readerVoice')]) {
        const previous = select.value
        select.innerHTML = ''
        const ordered = voices.slice().sort((a, b) => (a.lang || '').localeCompare(b.lang || ''))
        for (const v of ordered) {
          const opt = document.createElement('option')
          opt.value = v.lang || 'en-US'
          opt.textContent = `${v.name} · ${v.lang}`
          select.appendChild(opt)
        }
        const preferred = ordered.find(v => (v.lang || '').toLowerCase() === (previous || '').toLowerCase())
          || ordered.find(v => (v.lang || '').toLowerCase().startsWith('hi'))
          || ordered.find(v => (v.lang || '').toLowerCase().startsWith('en'))
        if (preferred) select.value = preferred.lang
      }
    }
    fill()
    window.speechSynthesis.addEventListener?.('voiceschanged', fill)
    window.speechSynthesis.onvoiceschanged = fill
  }

  function init() {
    const savedTheme = settings.theme === 'light' ? 'light' : 'dark'
    root.dataset.theme = savedTheme
    setTheme(savedTheme)
    applyLanguage()
    applyStyle()
    $('offsetRange').value = String(offset)
    updateOffset(offset)
    $('ttsRate').value = String(ttsRate)
    $('readerRate').value = String(ttsRate)
    $('rateOut').textContent = `${ttsRate.toFixed(2)}×`
    $('readerRateOut').textContent = `${ttsRate.toFixed(2)}×`
    setRange($('ttsRate')); setRange($('readerRate'))

    const theme = settings.bilingual !== false
    bilingual = theme

    bind()
    video.volume = 0.55
    applyTrack(currentKey)
    setSidePanel('subs')
    updateVolumeUi()
    initTtsVoices()
    requestAnimationFrame(tick)

    const markReady = () => {
      app.dataset.ready = 'true'
      window.__SRT_PLAYER_READY = true
      document.body.dataset.ready = 'true'
    }
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(markReady)
    setTimeout(markReady, 900)
  }

  init()
})()
