import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const assets = fileURLToPath(new URL('../dist/assets/', import.meta.url))
for (const name of await readdir(assets)) {
  if (!/\.(js|css)$/.test(name)) continue
  const body = await readFile(`${assets}/${name}`, 'utf8')
  for (const marker of ['/prototype/', '进入演示工作台', '创建演示账户', '本地原型演示']) {
    assert.ok(!body.includes(marker), `Production asset ${name} contains prototype content: ${marker}`)
  }
}
console.log('Production assets exclude prototype routes and demo content.')
