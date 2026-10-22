import { createRequire } from 'node:module'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir, platform } from 'node:os'
import { join, resolve } from 'node:path'

class TransientError extends Error { constructor(message) { super(message); this.exitCode = 75 } }
class DefectError extends Error { constructor(message) { super(message); this.exitCode = 1 } }

const url = process.env.CAPTURE_URL
const output = process.env.CAPTURE_DIR
if (!url || !output) {
  console.error('Set CAPTURE_URL and CAPTURE_DIR.')
  process.exit(1)
}
const outDir = resolve(output)
mkdirSync(outDir, { recursive: true })

const runtime = process.env.OMGITHUB_PLAYWRIGHT_ROOT || join(homedir(), '.local/share/omgithub-playwright')
let chromium
let config
try {
  const require = createRequire(join(runtime, 'package.json'))
  ;({ chromium } = require('playwright'))
  const configName = platform() === 'darwin' ? 'metal.json' : 'linux.json'
  config = JSON.parse(readFileSync(join(runtime, configName), 'utf8'))
} catch (error) {
  console.error(`Playwright runtime unavailable: ${error.message}`)
  process.exit(75)
}
if (platform() === 'linux' && !process.env.DISPLAY) {
  try { process.env.DISPLAY = ':' + readFileSync(join(runtime, 'display'), 'utf8').trim() } catch {}
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))
async function timed(name, fn) {
  const started = performance.now()
  try {
    const result = await fn()
    console.log(`[time] ${name}: ${((performance.now() - started) / 1000).toFixed(2)}s`)
    return result
  } catch (error) {
    console.log(`[time] ${name}: ${((performance.now() - started) / 1000).toFixed(2)}s (failed)`)
    throw error
  }
}
function pngSize(buffer) {
  if (!buffer || buffer.subarray(1, 4).toString() !== 'PNG') return null
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

const views = [
  { name: 'desktop', width: 1440, height: 900, scale: 1, mobile: false },
  { name: 'mobile', width: 390, height: 844, scale: 2, mobile: true }
]
const evidence = { url, captured_at: new Date().toISOString(), views: [] }
let browser
let failure

try {
  browser = await timed('launch chromium', () => chromium.launch({ ...config.browser.launchOptions, timeout: 30000 })
    .catch(error => { throw new TransientError(`Browser launch failed: ${error.message}`) }))

  for (const view of views) {
    console.log(`Capturing ${view.name} ${view.width}x${view.height}`)
    const context = await browser.newContext({
      viewport: { width: view.width, height: view.height },
      deviceScaleFactor: view.scale,
      isMobile: view.mobile,
      hasTouch: view.mobile
    })
    const page = await context.newPage()
    page.setDefaultTimeout(30000)
    const pageErrors = []
    const failedRequests = []
    page.on('pageerror', error => pageErrors.push(error.message))
    page.on('requestfailed', request => {
      const errorText = request.failure()?.errorText || ''
      if (['document', 'script', 'stylesheet', 'image', 'media', 'font'].includes(request.resourceType()) && !/ERR_ABORTED/.test(errorText)) {
        failedRequests.push(`${request.resourceType()} ${request.url()} ${errorText}`)
      }
    })
    page.on('response', response => {
      const type = response.request().resourceType()
      if (type === 'media' && response.status() >= 400) failedRequests.push(`media HTTP ${response.status()} ${response.url()}`)
    })

    try {
      const response = await timed(`${view.name} navigate`, () => page.goto(url, { waitUntil: 'load', timeout: 45000 })
        .catch(error => {
          const message = String(error.message || error)
          if (/net::|ERR_|Timeout|ECONN|ENOTFOUND|NS_ERROR/i.test(message)) throw new TransientError(`Navigation failed: ${message}`)
          throw error
        }))
      const status = response?.status() ?? 0
      if (!response) throw new TransientError('Navigation returned no response')
      if (status >= 400) {
        if (status === 408 || status === 429 || status >= 500) throw new TransientError(`Preview returned HTTP ${status}`)
        throw new DefectError(`Preview returned HTTP ${status}`)
      }

      await timed(`${view.name} wait ready`, () => page.waitForSelector('#app[data-ready="true"]', { state: 'visible', timeout: 25000 })
        .catch(error => { throw new DefectError(`App never reached ready state: ${error.message}`) }))
      await timed(`${view.name} fonts`, () => page.waitForFunction(() => document.fonts && document.fonts.status === 'loaded', null, { timeout: 15000 })
        .catch(() => console.log('Fonts still loading; continuing with bundled swap fonts')))

      const media = await timed(`${view.name} wait media`, () => page.waitForFunction(() => {
        const video = document.getElementById('video')
        const poster = document.querySelector('.player-frame video')
        const posterOk = poster && poster.getAttribute('poster')
        return Boolean(video && (video.readyState >= 2 || (posterOk && document.readyState === 'complete')))
      }, null, { timeout: 25000 }).then(() => page.evaluate(() => {
        const video = document.getElementById('video')
        return {
          readyState: video.readyState,
          videoWidth: video.videoWidth,
          videoHeight: video.videoHeight,
          currentTime: Number(video.currentTime.toFixed(2)),
          paused: video.paused,
          duration: Number.isFinite(video.duration) ? Number(video.duration.toFixed(2)) : null,
          error: video.error ? video.error.code : null
        }
      })).catch(error => { throw new DefectError(`Player media did not render: ${error.message}`) }))

      const state = await timed(`${view.name} inspect`, () => page.evaluate(() => {
        const cue = document.querySelector('.cue.is-active')
        const subtitle = document.getElementById('subEn')
        const pills = [...document.querySelectorAll('.side-pill, .badge')]
        return {
          title: document.getElementById('videoTitle')?.textContent?.trim() || '',
          subtitle: subtitle?.textContent?.trim() || '',
          subtitleVisible: !document.getElementById('subtitleWrap')?.classList.contains('is-hidden'),
          activeCue: cue?.textContent?.trim() || '',
          cueCount: document.querySelectorAll('#cueList .cue').length,
          libraryCards: document.querySelectorAll('.video-card').length,
          readyFlag: window.__SRT_PLAYER_READY === true,
          scrollWidth: document.documentElement.scrollWidth,
          innerWidth: window.innerWidth,
          bodyHeight: document.body.scrollHeight,
          theme: document.documentElement.dataset.theme,
          screen: document.querySelector('.screen.is-active')?.id || '',
          pills: pills.length
        }
      }))
      console.log(`${view.name} state: ${JSON.stringify(state)}`)
      console.log(`${view.name} media: ${JSON.stringify(media)}`)

      if (!state.title) throw new DefectError('Video title is empty')
      if (state.cueCount < 1) throw new DefectError('Subtitle cue list is empty')
      if (media.error) throw new DefectError(`Video element error code ${media.error}`)
      if (media.readyState < 2) throw new DefectError(`Video not ready (readyState ${media.readyState})`)
      if (!state.subtitleVisible || !state.subtitle) throw new DefectError('Active subtitle is not visible')
      if (state.scrollWidth > state.innerWidth + 2) console.log(`Warning: horizontal overflow ${state.scrollWidth} > ${state.innerWidth}`)
      if (pageErrors.length) throw new DefectError(`Page errors: ${pageErrors.join(' | ')}`)
      const mediaFailures = failedRequests.filter(line => line.startsWith('media'))
      if (mediaFailures.length) throw new DefectError(`Media failed to load: ${mediaFailures.join(' | ')}`)

      await sleep(1100)
      const file = join(outDir, `final-${view.name}.png`)
      const buffer = await timed(`${view.name} screenshot`, () => page.screenshot({ path: file, fullPage: false, timeout: 30000 })
        .catch(error => {
          if (error.name === 'TimeoutError' || !browser.isConnected()) throw new TransientError(`Screenshot failed: ${error.message}`)
          throw error
        }))
      const size = pngSize(buffer)
      if (!size) throw new DefectError('Screenshot is not a PNG')
      const minWidth = view.mobile ? 380 : 1200
      const minHeight = view.mobile ? 800 : 700
      if (size.width < minWidth || size.height < minHeight) throw new DefectError(`Screenshot too small: ${size.width}x${size.height}`)
      if (buffer.length < 15000) throw new DefectError(`Screenshot is suspiciously small: ${buffer.length} bytes`)
      console.log(`${view.name} screenshot: ${size.width}x${size.height}, ${buffer.length} bytes`)

      evidence.views.push({ name: view.name, ...size, bytes: buffer.length, media, state, pageErrors, failedRequests })
    } finally {
      await page.close().catch(() => {})
      await context.close().catch(() => {})
    }
  }
} catch (error) {
  failure = error
  console.error(error.stack || error.message)
} finally {
  if (browser) await browser.close().catch(error => console.error(`Browser close failed: ${error.message}`))
}

evidence.ok = !failure
evidence.error = failure ? failure.message : ''
writeFileSync(join(outDir, 'capture-evidence.json'), JSON.stringify(evidence, null, 2))
if (failure) process.exit(failure.exitCode || 1)
console.log('Both viewports rendered and captured.')
