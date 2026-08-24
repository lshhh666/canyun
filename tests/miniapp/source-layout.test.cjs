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

test('source and generated projects keep only the public WeChat test AppID', () => {
  const sourceConfig = JSON.parse(read('xiaochengxu-source/project.config.json'))
  const generatedConfig = JSON.parse(read('xiaochengxu/project.config.json'))

  for (const config of [sourceConfig, generatedConfig]) {
    assert.equal(config.appid, 'touristappid')
    assert.equal(config.projectname, 'cloudmeal')
  }
})

test('profile bindings compile to WeChat-compatible WXML expressions', () => {
  const sourceTemplates = [
    read('xiaochengxu-source/pages/index/index.vue'),
    read('xiaochengxu-source/pages/my/my.vue')
  ]
  const generatedTemplates = [
    read('xiaochengxu/pages/index/index.wxml'),
    read('xiaochengxu/pages/my/my.wxml')
  ]

  sourceTemplates.forEach(source => assert.doesNotMatch(source, /:profile="\$store\.state\.baseUserInfo\s*\|\|/))
  generatedTemplates.forEach(source => assert.doesNotMatch(source, /\|\|\{\}/))
})
