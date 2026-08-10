const test = require('node:test')
const { assert, fs, path, repoRoot, read } = require('./helpers.cjs')

test('maintainable uni-app source is present without generated output', () => {
  const sourceRoot = path.join(repoRoot, 'xiaochengxu-source')
  assert.ok(fs.existsSync(path.join(sourceRoot, 'App.vue')))
  assert.ok(fs.existsSync(path.join(sourceRoot, 'pages', 'index', 'index.vue')))
  assert.ok(fs.existsSync(path.join(sourceRoot, 'pages', 'api', 'api.js')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, 'unpackage')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, '.VSCodeCounter')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, 'design')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, 'image')))
})

test('source project keeps the active WeChat AppID', () => {
  const config = JSON.parse(read('xiaochengxu-source/project.config.json'))
  assert.equal(config.appid, 'wx718a307127ebbc96')
})
