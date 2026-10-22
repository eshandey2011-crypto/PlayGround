import { cpSync, existsSync, mkdirSync, rmSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname)
const source = join(root, 'src')
const output = join(root, 'dist')
const started = performance.now()

if (!existsSync(join(source, 'index.html'))) {
  console.error('Missing src/index.html')
  process.exit(1)
}

rmSync(output, { recursive: true, force: true })
mkdirSync(output, { recursive: true })
cpSync(source, output, { recursive: true })
console.log(`Built ${output} in ${((performance.now() - started) / 1000).toFixed(2)}s`)
console.log(`Static output: ${statSync(output).size ? 'ready' : 'empty'}`)
