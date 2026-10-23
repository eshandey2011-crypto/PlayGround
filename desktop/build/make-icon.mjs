import { writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { join, resolve } from 'node:path'

const outDir = resolve(import.meta.dirname)
const SIZES = [16, 24, 32, 48, 64, 128, 256]
const SS = 4

const clamp01 = (v) => Math.min(1, Math.max(0, v))
const mix = (a, b, t) => a + (b - a) * t

function insideRoundRect(x, y, size) {
  const inset = size * 0.03
  const r = size * 0.235
  const left = inset
  const top = inset
  const right = size - inset
  const bottom = size - inset
  if (x < left || x > right || y < top || y > bottom) return false
  const cx = Math.min(Math.max(x, left + r), right - r)
  const cy = Math.min(Math.max(y, top + r), bottom - r)
  return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
}

function insideTriangle(x, y, size) {
  const ax = size * 0.335
  const ay = size * 0.255
  const bx = size * 0.335
  const by = size * 0.745
  const cx = size * 0.765
  const cy = size * 0.5
  const d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by)
  const d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy)
  const d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay)
  const hasNeg = d1 < 0 || d2 < 0 || d3 < 0
  const hasPos = d1 > 0 || d2 > 0 || d3 > 0
  return !(hasNeg && hasPos)
}

function render(size) {
  const pixels = Buffer.alloc(size * size * 4)
  const step = 1 / SS
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let bg = 0
      let fg = 0
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = x + (sx + 0.5) * step
          const py = y + (sy + 0.5) * step
          if (!insideRoundRect(px, py, size)) continue
          bg++
          if (insideTriangle(px, py, size)) fg++
        }
      }
      const total = SS * SS
      const alpha = bg / total
      const tri = bg ? fg / bg : 0
      const t = clamp01(y / size)
      const r = mix(mix(12, 0, t), 255, tri)
      const g = mix(mix(126, 74, t), 255, tri)
      const b = mix(mix(255, 214, t), 255, tri)
      const i = (y * size + x) * 4
      pixels[i] = Math.round(r)
      pixels[i + 1] = Math.round(g)
      pixels[i + 2] = Math.round(b)
      pixels[i + 3] = Math.round(alpha * 255)
    }
  }
  return pixels
}

function crc32(buf) {
  let c = ~0
  for (const byte of buf) {
    c ^= byte
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1))
  }
  return (~c) >>> 0
}

function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

function encodePng(size, rgba) {
  const raw = Buffer.alloc(size * (size * 4 + 1))
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

function encodeIco(entries) {
  const header = Buffer.alloc(6)
  header.writeUInt16LE(0, 0)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(entries.length, 4)
  const directory = Buffer.alloc(16 * entries.length)
  let offset = 6 + directory.length
  const images = []
  entries.forEach(({ size, rgba }, index) => {
    const rowMask = Math.ceil(size / 32) * 4
    const image = Buffer.alloc(40 + size * size * 4 + rowMask * size)
    image.writeUInt32LE(40, 0)
    image.writeInt32LE(size, 4)
    image.writeInt32LE(size * 2, 8)
    image.writeUInt16LE(1, 12)
    image.writeUInt16LE(32, 14)
    image.writeUInt32LE(0, 16)
    image.writeUInt32LE(size * size * 4 + rowMask * size, 20)
    let cursor = 40
    for (let y = size - 1; y >= 0; y--) {
      for (let x = 0; x < size; x++) {
        const i = (y * size + x) * 4
        image[cursor++] = rgba[i + 2]
        image[cursor++] = rgba[i + 1]
        image[cursor++] = rgba[i]
        image[cursor++] = rgba[i + 3]
      }
    }
    const entry = index * 16
    directory[entry] = size >= 256 ? 0 : size
    directory[entry + 1] = size >= 256 ? 0 : size
    directory[entry + 3] = 0
    directory.writeUInt16LE(1, entry + 4)
    directory.writeUInt16LE(32, entry + 6)
    directory.writeUInt32LE(image.length, entry + 8)
    directory.writeUInt32LE(offset, entry + 12)
    offset += image.length
    images.push(image)
  })
  return Buffer.concat([header, directory, ...images])
}

const entries = SIZES.map((size) => ({ size, rgba: render(size) }))
writeFileSync(join(outDir, 'icon.ico'), encodeIco(entries))
writeFileSync(join(outDir, 'icon.png'), encodePng(512, render(512)))
console.log(`Icons written: icon.ico (${SIZES.join(', ')}), icon.png (512)`)
