const { app, BrowserWindow, Menu, shell } = require('electron')
const path = require('node:path')

const APP_ID = 'com.srtvideoplayer.desktop'
let mainWindow = null

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.focus()
  })
  app.whenReady().then(createWindow)
}

function createWindow() {
  Menu.setApplicationMenu(null)
  app.setAppUserModelId(APP_ID)

  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 960,
    minHeight: 620,
    show: false,
    title: 'SRT Video Player',
    backgroundColor: '#070b16',
    autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
      backgroundThrottling: false
    }
  })

  mainWindow.once('ready-to-show', () => mainWindow.show())
  mainWindow.on('closed', () => { mainWindow = null })

  const { session } = mainWindow.webContents
  session.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(new Set(['clipboard-sanitized-write', 'clipboard-read', 'fullscreen']).has(permission))
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith('file://')) return
    event.preventDefault()
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
  })

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'))
}

app.on('window-all-closed', () => app.quit())
