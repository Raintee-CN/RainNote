import { existsSync, renameSync } from 'node:fs'

const source = 'dist/web-pc/index.pc.html'
const target = 'dist/web-pc/index.html'

if (existsSync(source)) {
  renameSync(source, target)
}
