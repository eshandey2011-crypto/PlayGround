import { cpSync, existsSync, mkdirSync, rmSync, statSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, resolve } from 'node:path'

const desktop = resolve(import.meta.dirname, '..')
const project = resolve(desktop, '..')
const renderer = join(desktop, 'renderer')

const t0 = performance.now()
execFileSync(process.execPath, [join(project, 'build.mjs')], { cwd: project, stdio: 'inherit' })

const source = join(project, 'dist')
if (!existsSync(join(source, 'index.html'))) {
  console.error('Missing dist/index.html after build')
  process.exit(1)
}

rmSync(renderer, { recursive: true, force: true })
mkdirSync(renderer, { recursive: true })
cpSync(source, renderer, { recursive: true })

const size = statSync(join(renderer, 'index.html')).size
console.log(`Renderer ready at ${renderer} in ${((performance.now() - t0) / 1000).toFixed(2)}s (index.html ${size} bytes)`)
