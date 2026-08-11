const test = require('node:test')
const { execFileSync } = require('node:child_process')
const { assert, fs, path, repoRoot, read } = require('./helpers.cjs')

test('maintainable uni-app source keeps generated output out of version control', () => {
  const sourceRoot = path.join(repoRoot, 'xiaochengxu-source')
  assert.ok(fs.existsSync(path.join(sourceRoot, 'App.vue')))
  assert.ok(fs.existsSync(path.join(sourceRoot, 'pages', 'index', 'index.vue')))
  assert.ok(fs.existsSync(path.join(sourceRoot, 'pages', 'api', 'api.js')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, '.VSCodeCounter')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, 'design')))
  assert.ok(!fs.existsSync(path.join(sourceRoot, 'image')))
  assert.match(read('.gitignore'), /^xiaochengxu-source\/unpackage\/$/m)
  assert.equal(
    execFileSync('git', ['ls-files', '--', 'xiaochengxu-source/unpackage'], {
      cwd: repoRoot,
      encoding: 'utf8'
    }).trim(),
    ''
  )
})

test('source project keeps the active WeChat AppID', () => {
  const config = JSON.parse(read('xiaochengxu-source/project.config.json'))
  assert.equal(config.appid, 'wx718a307127ebbc96')
})
