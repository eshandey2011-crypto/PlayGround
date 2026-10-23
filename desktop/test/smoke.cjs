const { app, BrowserWindow } = require('electron')
const { mkdirSync, writeFileSync } = require('node:fs')
const path = require('node:path')

const outDir = path.join(__dirname, 'out')
mkdirSync(outDir, { recursive: true })

app.commandLine.appendSwitch('disable-gpu')
app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    show: false,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, offscreen: true }
  })
  const errors = []
  win.webContents.on('console-message', (event) => {
    if (event.level === 'error') errors.push(event.message)
  })
  await win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'))
  await win.webContents.executeJavaScript(`
    new Promise((resolve) => {
      const started = Date.now()
      const check = () => {
        if (window.__SRT_PLAYER_READY) return resolve(true)
        if (Date.now() - started > 8000) return resolve(false)
        setTimeout(check, 100)
      }
      check()
    })
  `)
  await new Promise((resolve) => setTimeout(resolve, 1200))
  const state = await win.webContents.executeJavaScript(`(() => {
    const video = document.getElementById('video')
    const cues = document.querySelectorAll('.cue-row, .subtitle-row, [data-cue]').length
    return {
      ready: window.__SRT_PLAYER_READY === true,
      title: document.title,
      screen: document.querySelector('.screen.is-active')?.id || null,
      videoSrc: video?.currentSrc || '',
      videoReadyState: video?.readyState ?? -1,
      videoDuration: Number.isFinite(video?.duration) ? video.duration : null,
      subtitleVisible: document.getElementById('subtitleWrap')?.style.display !== 'none',
      cueElements: cues,
      bodyChars: document.body.innerText.length
    }
  })()`)
  const srt = '1\n00:00:00,000 --> 00:00:02,000\nTest subtitle cue one.\nपरीक्षण उपशीर्षक एक।\n\n2\n00:00:02,000 --> 00:00:04,000\nSecond test cue.\nदूसरा परीक्षण।\n'
  const overlayVisible = await win.webContents.executeJavaScript(`(() => {
    const text = ${JSON.stringify(srt)}
    const file = new File([text], 'drop-test.srt', { type: 'text/plain' })
    const dataTransfer = new DataTransfer()
    dataTransfer.items.add(file)
    window.__dropTestTransfer = dataTransfer
    window.dispatchEvent(new DragEvent('dragenter', { bubbles: true, cancelable: true, dataTransfer }))
    return document.body.classList.contains('is-dragging')
  })()`)
  await new Promise((resolve) => setTimeout(resolve, 250))
  const overlayImage = await win.webContents.capturePage()
  writeFileSync(path.join(outDir, 'smoke-drop.png'), overlayImage.toPNG())
  const drop = await win.webContents.executeJavaScript(`(async () => {
    const dataTransfer = window.__dropTestTransfer
    window.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer }))
    await new Promise((resolve) => setTimeout(resolve, 800))
    return {
      overlayVisible: ${overlayVisible},
      draggingCleared: !document.body.classList.contains('is-dragging'),
      cueCount: document.getElementById('cueCount')?.textContent || '',
      trackFile: document.getElementById('trackFile')?.textContent || '',
      cueRows: document.querySelectorAll('#cueList .cue').length
    }
  })()`)
  const image = await win.webContents.capturePage()
  writeFileSync(path.join(outDir, 'smoke.png'), image.toPNG())
  const dropOk = drop.overlayVisible && drop.draggingCleared && drop.cueCount === '2' && drop.trackFile === 'drop-test.srt'
  const imageOk = image.getSize().width >= 1400 && image.getSize().height >= 800
  const ok = state.ready && state.screen === 'screen-player' && state.bodyChars > 500 && imageOk && dropOk
  console.log(JSON.stringify({ ...state, drop, image: image.getSize(), dropOk, imageOk, ok, consoleErrors: errors }, null, 2))
  app.exit(ok ? 0 : 1)
})
