const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')

const repoRoot = path.resolve(__dirname, '..', '..')

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

function expectAll(text, values) {
  values.forEach(value => assert.ok(text.includes(value), `missing: ${value}`))
}

function expectNone(text, values) {
  values.forEach(value => assert.ok(!text.includes(value), `unexpected: ${value}`))
}

module.exports = { assert, fs, path, repoRoot, read, expectAll, expectNone }
