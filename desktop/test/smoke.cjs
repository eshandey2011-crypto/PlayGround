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
  const image = await win.webContents.capturePage()
  writeFileSync(path.join(outDir, 'smoke.png'), image.toPNG())
  console.log(JSON.stringify({ ...state, consoleErrors: errors }, null, 2))
  const ok = state.ready && state.screen === 'screen-player' && state.bodyChars > 500 && image.getSize().width === 1440
  app.exit(ok ? 0 : 1)
})
