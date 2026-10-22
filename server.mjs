import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, join, normalize, resolve, sep } from 'node:path'

const root = resolve(import.meta.dirname, 'dist')
const port = Number(process.env.PORT || 3000)
const mime = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp',
  '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2',
  '.mp4': 'video/mp4', '.webm': 'video/webm', '.srt': 'text/plain; charset=utf-8',
  '.vtt': 'text/vtt; charset=utf-8', '.txt': 'text/plain; charset=utf-8'
}

function sendError(res, code) {
  res.writeHead(code, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(code === 404 ? 'Not found' : 'Server error')
}

const server = createServer((req, res) => {
  try {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.writeHead(405, { Allow: 'GET, HEAD' })
      res.end()
      return
    }
    const url = new URL(req.url, 'http://localhost')
    let pathname = decodeURIComponent(url.pathname)
    let target = resolve(join(root, normalize(pathname)))
    if (target !== root && !target.startsWith(root + sep)) return sendError(res, 404)
    if (!existsSync(target) || statSync(target).isDirectory()) {
      if (!extname(pathname)) target = join(root, 'index.html')
      else return sendError(res, 404)
    }
    if (!existsSync(target) || statSync(target).isDirectory()) return sendError(res, 404)
    const stats = statSync(target)
    const type = mime[extname(target).toLowerCase()] || 'application/octet-stream'
    const headers = { 'Content-Type': type, 'Cache-Control': 'no-cache', 'Accept-Ranges': 'bytes' }
    const range = req.headers.range
    if (range) {
      const match = /^bytes=(\d*)-(\d*)$/.exec(range)
      if (match) {
        let start = match[1] === '' ? NaN : Number(match[1])
        let end = match[2] === '' ? NaN : Number(match[2])
        if (Number.isNaN(start)) { start = Math.max(0, stats.size - end); end = stats.size - 1 }
        else if (Number.isNaN(end) || end >= stats.size) end = stats.size - 1
        if (start > end || start >= stats.size) {
          res.writeHead(416, { 'Content-Range': `bytes */${stats.size}` })
          res.end()
          return
        }
        res.writeHead(206, { ...headers, 'Content-Range': `bytes ${start}-${end}/${stats.size}`, 'Content-Length': end - start + 1 })
        if (req.method === 'HEAD') res.end()
        else createReadStream(target, { start, end }).pipe(res)
        return
      }
    }
    res.writeHead(200, { ...headers, 'Content-Length': stats.size })
    if (req.method === 'HEAD') res.end()
    else createReadStream(target).pipe(res)
  } catch (error) {
    console.error(error)
    sendError(res, 500)
  }
})

server.listen(port, '0.0.0.0', () => {
  console.log(`SRT Video Player ready at http://0.0.0.0:${port} (${root})`)
})
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
