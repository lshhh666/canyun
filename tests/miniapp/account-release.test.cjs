const test = require('node:test')
const vm = require('node:vm')
const { assert, fs, path, repoRoot, read, expectAll, expectNone } = require('./helpers.cjs')
const os = require('node:os')
const { spawnSync } = require('node:child_process')

function componentOptions(relativePath, context = {}) {
  const source = read(relativePath)
  const script = source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/)[1]
  const marker = 'export default '
  const optionsSource = script.slice(script.indexOf(marker) + marker.length)
  const sandbox = { module: { exports: {} }, console: { log() {} }, ...context }
  vm.runInNewContext(`module.exports = ${optionsSource}`, sandbox)
  return sandbox.module.exports
}

function mount(definition, { state = {}, emit = () => {} } = {}) {
  const instance = { ...(definition.data ? definition.data() : {}) }
  instance.$store = { state }
  instance.$emit = emit
  Object.entries(definition.methods || {}).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  return instance
}

function accountHarness(state = {}, overrides = {}) {
  const calls = {
    mutations: [],
    toasts: [],
    phones: [],
    redirects: [],
    navigations: [],
    backs: [],
    relaunches: []
  }
  const definition = componentOptions('xiaochengxu-source/pages/my/my.vue', {
    HeadInfo: {},
    OrderList: {},
    CloudmealHeader: {},
    AppTabbar: {},
    DEFAULT_AVATAR: '/static/brand/cloudmeal-logo.png',
    DEFAULT_NICKNAME: '微信用户',
    getErrorMessage: (error, fallback) => (error && error.message) || fallback,
    statusWord: status => `状态${status}`,
    getOvertime: () => 30,
    getOrderPage: overrides.getOrderPage || (async () => ({ code: 1, data: { records: [], total: 0 } })),
    repetitionOrder: overrides.repetitionOrder || (async () => ({ code: 1 })),
    delShoppingCart: overrides.delShoppingCart || (async () => ({ code: 1 })),
    getCurrentPages: overrides.getCurrentPages || (() => [
      { route: 'pages/index/index' },
      { route: 'pages/my/my' }
    ]),
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, payload => calls.mutations.push([name, payload])]))
    },
    uni: {
      showToast: options => calls.toasts.push(options),
      makePhoneCall: options => calls.phones.push(options),
      redirectTo: options => calls.redirects.push(options),
      navigateTo: options => calls.navigations.push(options),
      navigateBack: options => calls.backs.push(options),
      reLaunch: options => calls.relaunches.push(options),
      getSystemInfo: () => {},
      upx2px: value => value
    }
  })
  return { calls, definition, instance: mount(definition, { state }) }
}

test('account page contains only real destinations and CloudMeal shell', () => {
  const files = [
    'xiaochengxu-source/pages/my/my.vue',
    'xiaochengxu-source/pages/my/components/headInfo.vue',
    'xiaochengxu-source/pages/my/components/orderInfo.vue',
    'xiaochengxu-source/pages/my/components/orderList.vue'
  ].map(read).join('\n')
  expectAll(files, [
    '<cloudmeal-header', '<app-tabbar active="account"',
    '收货地址', '订单记录', '联系门店', '餐云 CloudMeal'
  ])
  expectNone(files, [
    '18500557668', '优惠券', '积分', '会员等级',
    '#ffc200', '#FFC200', 'linear-gradient', 'btn_waiter_sel.png'
  ])
})

test('account phone action toasts without a number and calls only a real number', () => {
  const empty = accountHarness({ shopPhone: '' })
  empty.instance.handlePhone()
  assert.equal(empty.calls.toasts.length, 1)
  assert.equal(empty.calls.toasts[0].title, '门店暂未提供联系电话')
  assert.equal(empty.calls.phones.length, 0)

  const available = accountHarness({ shopPhone: '021-12345678' })
  available.definition.onLoad.call(available.instance)
  available.instance.handlePhone()
  assert.equal(available.calls.toasts.length, 0)
  assert.equal(available.calls.phones[0].phoneNumber, '021-12345678')

  const objectValue = accountHarness({ shopPhone: { phone: '400-800-1234' } })
  objectValue.definition.onLoad.call(objectValue.instance)
  objectValue.instance.handlePhone()
  assert.equal(objectValue.calls.phones[0].phoneNumber, '400-800-1234')

  const emptyObject = accountHarness({ shopPhone: { phone: '' } })
  emptyObject.definition.onLoad.call(emptyObject.instance)
  emptyObject.instance.handlePhone()
  assert.equal(emptyObject.calls.toasts[0].title, '门店暂未提供联系电话')
  assert.equal(emptyObject.calls.phones.length, 0)
})

test('account user identity uses real Vuex values with nickname and logo fallbacks', () => {
  const fallback = accountHarness({ baseUserInfo: {}, shopPhone: '' })
  fallback.definition.onLoad.call(fallback.instance)
  assert.equal(fallback.instance.nickName, '微信用户')
  assert.equal(fallback.instance.psersonUrl, '/static/brand/cloudmeal-logo.png')

  const real = accountHarness({
    baseUserInfo: { avatarUrl: 'https://example.test/avatar.png', nickName: '小云' },
    shopPhone: ''
  })
  real.definition.onLoad.call(real.instance)
  assert.equal(real.instance.nickName, '小云')
  assert.equal(real.instance.psersonUrl, 'https://example.test/avatar.png')
})

test('account routes address, order history and order detail to their real pages', () => {
  const harness = accountHarness()
  harness.instance.goAddress()
  harness.instance.goOrder()
  harness.instance.goDetail(88)

  assert.deepEqual(harness.calls.mutations, [
    ['setAddressBackUrl', '/pages/my/my'],
    ['setAddressBackUrl', '/pages/my/my']
  ])
  assert.deepEqual(harness.calls.redirects.map(call => call.url), [
    '/pages/address/address?form=my',
    '/pages/details/index?orderId=88'
  ])
  assert.deepEqual(harness.calls.navigations.map(call => call.url), [
    '/pages/historyOrder/historyOrder'
  ])
})

test('network recovery goes back with history and relaunches ordering without it', () => {
  function run(pages) {
    const calls = { backs: [], relaunches: [] }
    const definition = componentOptions('xiaochengxu-source/pages/nonet/index.vue', {
      StatePanel: {},
      getCurrentPages: () => pages,
      uni: {
        navigateBack: options => calls.backs.push(options),
        reLaunch: options => calls.relaunches.push(options)
      }
    })
    mount(definition).retry()
    return calls
  }

  const stacked = run([{ route: 'pages/index/index' }, { route: 'pages/nonet/index' }])
  assert.equal(stacked.backs[0].delta, 1)
  assert.equal(stacked.relaunches.length, 0)

  const direct = run([{ route: 'pages/nonet/index' }])
  assert.equal(direct.backs.length, 0)
  assert.equal(direct.relaunches[0].url, '/pages/index/index')
})

test('empty wrapper forwards the state-panel action event', () => {
  const events = []
  const definition = componentOptions('xiaochengxu-source/components/empty/empty.vue', {
    StatePanel: {}
  })
  const instance = mount(definition, { emit: name => events.push(name) })
  instance.handleAction()

  assert.deepEqual(events, ['action'])
  assert.deepEqual(Object.keys(definition.props).sort(), ['actionText', 'textLabel'])
})

test('recent order events reach account detail and repeat-order handlers', async () => {
  const events = []
  const sequence = []
  const pending = []
  const account = accountHarness({}, {
    delShoppingCart: async () => { sequence.push('clear-cart'); return { code: 1 } },
    repetitionOrder: async id => { sequence.push(`repeat:${id}`); return { code: 1 } }
  })
  const definition = componentOptions('xiaochengxu-source/pages/my/components/orderList.vue', {
    ReachBottom: {},
    statusWord: status => `状态${status}`
  })
  const instance = mount(definition, {
    emit(name, payload) {
      events.push([name, payload])
      const result = account.instance[name](payload)
      if (result && typeof result.then === 'function') pending.push(result)
    }
  })
  instance.goDetail(31)
  instance.oneOrderFun(32)
  await Promise.all(pending)

  assert.deepEqual(events, [['goDetail', 31], ['oneOrderFun', 32]])
  assert.equal(account.calls.redirects.at(-1).url, '/pages/details/index?orderId=31')
  assert.deepEqual(sequence, ['clear-cart', 'repeat:32'])
  assert.equal(account.calls.backs.at(-1).delta, 1)
  assert.equal(account.calls.relaunches.length, 0)
})

test('repeat order returns to an existing ordering page or relaunches it from a single-page stack', async () => {
  const stacked = accountHarness({}, {
    getCurrentPages: () => [{ route: 'pages/index/index' }, { route: 'pages/my/my' }]
  })
  assert.equal(await stacked.instance.oneOrderFun(41), true)
  assert.equal(stacked.calls.backs[0].delta, 1)
  assert.equal(stacked.calls.relaunches.length, 0)

  const direct = accountHarness({}, {
    getCurrentPages: () => [{ route: 'pages/my/my' }]
  })
  assert.equal(await direct.instance.oneOrderFun(42), true)
  assert.equal(direct.calls.backs.length, 0)
  assert.equal(direct.calls.relaunches[0].url, '/pages/index/index')
})

test('repeat order API failures stay put and show specific or fallback errors', async () => {
  let repeatCalls = 0
  const clearFailure = accountHarness({}, {
    delShoppingCart: async () => { throw new Error('购物车清理失败') },
    repetitionOrder: async () => { repeatCalls += 1; return { code: 1 } }
  })
  assert.equal(await clearFailure.instance.oneOrderFun(51), false)
  assert.equal(repeatCalls, 0)
  assert.equal(clearFailure.calls.toasts[0].title, '购物车清理失败')
  assert.equal(clearFailure.calls.backs.length + clearFailure.calls.relaunches.length, 0)

  const repeatFailure = accountHarness({}, {
    delShoppingCart: async () => ({ code: 1 }),
    repetitionOrder: async () => ({ code: 0 })
  })
  assert.equal(await repeatFailure.instance.oneOrderFun(52), false)
  assert.equal(repeatFailure.calls.toasts[0].title, '再来一单失败，请重试')
  assert.equal(repeatFailure.calls.backs.length + repeatFailure.calls.relaunches.length, 0)
})

test('recent-order pagination retries a failed page without advancing or replaying successes', async () => {
  const requests = []
  let pageTwoAttempts = 0
  const harness = accountHarness({}, {
    getOrderPage: async params => {
      requests.push(params.page)
      if (params.page === 2 && pageTwoAttempts++ === 0) throw new Error('第二页暂不可用')
      return {
        code: 1,
        data: {
          records: [{ id: params.page, orderDetailList: [] }],
          total: 3
        }
      }
    }
  })
  harness.instance.pageInfo.pageSize = 1

  assert.equal(await harness.instance.getList(), true)
  assert.equal(await harness.instance.lower(), false)
  assert.equal(harness.instance.pageInfo.page, 1)
  assert.equal(harness.instance.failedPage, 2)
  assert.equal(harness.instance.loading, false)
  assert.equal(harness.instance.loadingText, '第二页暂不可用')
  assert.equal(harness.calls.toasts.at(-1).title, '第二页暂不可用')

  assert.equal(await harness.instance.lower(), true)
  assert.equal(harness.instance.pageInfo.page, 2)
  assert.equal(harness.instance.failedPage, null)
  assert.equal(await harness.instance.lower(), true)
  assert.equal(harness.instance.pageInfo.page, 3)
  assert.deepEqual(requests, [1, 2, 2, 3])
  assert.deepEqual(Array.from(harness.instance.recentOrdersList, order => order.id), [1, 2, 3])
})

test('recent order helpers normalize null detail lists', () => {
  const definition = componentOptions('xiaochengxu-source/pages/my/components/orderList.vue', {
    statusWord: status => `状态${status}`,
    getOvertime: () => 30
  })
  const instance = mount(definition)

  assert.equal(instance.orderDetails(null).length, 0)
  assert.equal(instance.numes(null).count, 0)
  assert.equal(instance.dishSummary(null), '订单餐品')
  expectAll(read('xiaochengxu-source/pages/my/components/orderList.vue'), [
    'orderDetails(item.orderDetailList).length',
    'orderDetails(item.orderDetailList)[0].image'
  ])
})

test('network and empty states expose recovery through state-panel', () => {
  const nonet = read('xiaochengxu-source/pages/nonet/index.vue')
  const empty = read('xiaochengxu-source/components/empty/empty.vue')
  expectAll(nonet, ['<state-panel', '网络连接失败', '请检查网络后重新加载', '重新加载'])
  expectAll(empty, ['<state-panel', ':action-text="actionText"', '@action="handleAction"'])
  expectNone(nonet + empty, ['#ffc200', '#FFC200', 'linear-gradient'])
})

function makeSyncSandbox() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cloudmeal-miniapp-sync-'))
  const scriptDir = path.join(root, 'scripts')
  const buildRoot = path.join(root, 'xiaochengxu-source', 'unpackage', 'dist', 'dev', 'mp-weixin')
  const targetRoot = path.join(root, 'xiaochengxu')
  fs.mkdirSync(scriptDir, { recursive: true })
  fs.mkdirSync(buildRoot, { recursive: true })
  fs.mkdirSync(targetRoot, { recursive: true })
  fs.copyFileSync(
    path.join(repoRoot, 'scripts', 'sync-miniapp-output.ps1'),
    path.join(scriptDir, 'sync-miniapp-output.ps1')
  )
  return { root, buildRoot, targetRoot, script: path.join(scriptDir, 'sync-miniapp-output.ps1') }
}

function runSync(sandbox, args = []) {
  return spawnSync('powershell.exe', [
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', sandbox.script, ...args
  ], { encoding: 'utf8' })
}

function runSyncWithSecondMoveFailure(sandbox) {
  const runner = path.join(sandbox.root, 'inject-second-move-failure.ps1')
  fs.writeFileSync(runner, [
    '$script:moveCount = 0',
    'function Move-Item {',
    '  param([string]$LiteralPath, [string]$Destination)',
    '  $script:moveCount += 1',
    "  if ($script:moveCount -eq 2) { throw 'Injected second move failure.' }",
    '  Microsoft.PowerShell.Management\\Move-Item -LiteralPath $LiteralPath -Destination $Destination',
    '}',
    '& $args[0]'
  ].join('\r\n'))
  return spawnSync('powershell.exe', [
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', runner, sandbox.script
  ], { encoding: 'utf8' })
}

function removeSandbox(sandbox) {
  fs.rmSync(sandbox.root, { recursive: true, force: true })
}

test('release sync stops before deleting target output when the build is missing', () => {
  const sandbox = makeSyncSandbox()
  try {
    const config = Buffer.from([0xff, 0x00, 0x7f, 0x0d, 0x0a])
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.config.json'), config)
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'keep until build exists')

    const result = runSync(sandbox)

    assert.notEqual(result.status, 0)
    assert.equal(fs.readFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'utf8'), 'keep until build exists')
    assert.deepEqual(fs.readFileSync(path.join(sandbox.targetRoot, 'project.config.json')), config)
  } finally {
    removeSandbox(sandbox)
  }
})

test('release sync refuses a source junction before it touches target output', () => {
  const sandbox = makeSyncSandbox()
  let outsideBuild
  try {
    fs.rmSync(sandbox.buildRoot, { recursive: true, force: true })
    outsideBuild = fs.mkdtempSync(path.join(os.tmpdir(), 'cloudmeal-miniapp-outside-'))
    fs.writeFileSync(path.join(outsideBuild, 'app.json'), '{}')
    fs.writeFileSync(path.join(outsideBuild, 'sentinel.txt'), 'outside build must stay unchanged')
    fs.symlinkSync(outsideBuild, sandbox.buildRoot, 'junction')

    const config = Buffer.from([0xff, 0x00, 0x7f])
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.config.json'), config)
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'do not delete')

    const result = runSync(sandbox)

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /reparse point/i)
    assert.equal(
      fs.readFileSync(path.join(outsideBuild, 'sentinel.txt'), 'utf8'),
      'outside build must stay unchanged'
    )
    assert.equal(fs.readFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'utf8'), 'do not delete')
    assert.deepEqual(fs.readFileSync(path.join(sandbox.targetRoot, 'project.config.json')), config)
  } finally {
    if (fs.existsSync(sandbox.buildRoot)) fs.rmSync(sandbox.buildRoot, { recursive: true, force: true })
    if (outsideBuild) fs.rmSync(outsideBuild, { recursive: true, force: true })
    removeSandbox(sandbox)
  }
})

test('release sync refuses a nested build junction without traversing or replacing target', () => {
  const sandbox = makeSyncSandbox()
  let outsideDirectory
  const nestedJunction = path.join(sandbox.buildRoot, 'linked-pages')
  try {
    outsideDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'cloudmeal-miniapp-linked-'))
    fs.writeFileSync(path.join(outsideDirectory, 'sentinel.txt'), 'nested outside data must stay unchanged')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.json'), '{}')
    fs.symlinkSync(outsideDirectory, nestedJunction, 'junction')
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.config.json'), '{}')
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'target must stay unchanged')

    const result = runSync(sandbox)

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /reparse point/i)
    assert.equal(
      fs.readFileSync(path.join(outsideDirectory, 'sentinel.txt'), 'utf8'),
      'nested outside data must stay unchanged'
    )
    assert.equal(
      fs.readFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'utf8'),
      'target must stay unchanged'
    )
  } finally {
    if (fs.existsSync(nestedJunction)) fs.rmSync(nestedJunction, { recursive: true, force: true })
    if (outsideDirectory) fs.rmSync(outsideDirectory, { recursive: true, force: true })
    removeSandbox(sandbox)
  }
})

test('release sync preserves WeChat config bytes and replaces only stale generated output', () => {
  const sandbox = makeSyncSandbox()
  try {
    const projectConfig = Buffer.from([0xff, 0x00, 0x7f, 0x0d, 0x0a])
    const privateConfig = Buffer.from([0xfe, 0x01, 0x0d, 0x0a])
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.config.json'), projectConfig)
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.private.config.json'), privateConfig)
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'remove me')
    fs.mkdirSync(path.join(sandbox.targetRoot, 'stale-directory'))
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale-directory', 'old.json'), '{}')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.json'), '{"pages":[]}')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.js'), 'App({})')
    fs.mkdirSync(path.join(sandbox.buildRoot, 'pages'))
    fs.writeFileSync(path.join(sandbox.buildRoot, 'pages', 'index.js'), 'Page({})')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'project.config.json'), 'generated config must not replace target config')

    const result = runSync(sandbox)

    assert.equal(result.status, 0, result.stderr || result.stdout)
    assert.equal(fs.existsSync(path.join(sandbox.targetRoot, 'stale.js')), false)
    assert.equal(fs.existsSync(path.join(sandbox.targetRoot, 'stale-directory')), false)
    assert.equal(fs.readFileSync(path.join(sandbox.targetRoot, 'app.js'), 'utf8'), 'App({})')
    assert.equal(fs.readFileSync(path.join(sandbox.targetRoot, 'pages', 'index.js'), 'utf8'), 'Page({})')
    assert.deepEqual(fs.readFileSync(path.join(sandbox.targetRoot, 'project.config.json')), projectConfig)
    assert.deepEqual(fs.readFileSync(path.join(sandbox.targetRoot, 'project.private.config.json')), privateConfig)
  } finally {
    removeSandbox(sandbox)
  }
})

test('release sync restores the old target when staging activation fails', () => {
  const sandbox = makeSyncSandbox()
  try {
    const config = Buffer.from([0xff, 0x00, 0x7f, 0x0d, 0x0a])
    fs.writeFileSync(path.join(sandbox.targetRoot, 'project.config.json'), config)
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'old target must be restored')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.json'), '{}')
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.js'), 'new generated output')

    const result = runSyncWithSecondMoveFailure(sandbox)

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /Injected second move failure/i)
    assert.equal(
      fs.readFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'utf8'),
      'old target must be restored'
    )
    assert.equal(fs.existsSync(path.join(sandbox.targetRoot, 'app.js')), false)
    assert.deepEqual(fs.readFileSync(path.join(sandbox.targetRoot, 'project.config.json')), config)
    assert.deepEqual(
      fs.readdirSync(sandbox.root).filter(name => name.startsWith('.miniapp-sync-')),
      []
    )
  } finally {
    removeSandbox(sandbox)
  }
})

test('release sync requires the current target project configuration before cleanup', () => {
  const sandbox = makeSyncSandbox()
  try {
    fs.writeFileSync(path.join(sandbox.buildRoot, 'app.json'), '{}')
    fs.writeFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'keep until target config exists')

    const result = runSync(sandbox)

    assert.notEqual(result.status, 0)
    assert.equal(fs.readFileSync(path.join(sandbox.targetRoot, 'stale.js'), 'utf8'), 'keep until target config exists')
  } finally {
    removeSandbox(sandbox)
  }
})

test('release sync fixes both paths and stages an atomic rollback-capable exchange', () => {
  const script = read('scripts/sync-miniapp-output.ps1')
  expectAll(script, [
    'xiaochengxu-source\\unpackage\\dist\\dev\\mp-weixin',
    'xiaochengxu\\project.config.json',
    'xiaochengxu\\project.private.config.json',
    'StartsWith($repoRoot',
    '[System.IO.FileAttributes]::ReparsePoint',
    'Source and target paths must not overlap.',
    '.miniapp-sync-staging-',
    '.miniapp-sync-backup-',
    '$targetMovedToBackup',
    'Move-Item -LiteralPath $targetRoot -Destination $backupRoot',
    'Move-Item -LiteralPath $stagingRoot -Destination $targetRoot',
    'Move-Item -LiteralPath $backupRoot -Destination $targetRoot',
    'Remove-Item -LiteralPath $backupRoot'
  ])
  expectNone(script, ['BuildRelativePath', 'TargetRelativePath', 'WriteAllBytes'])
})

function sourceFilesUnder(relativeDirectory) {
  const directory = path.join(repoRoot, relativeDirectory)
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const relativePath = path.join(relativeDirectory, entry.name)
    if (entry.isDirectory()) return sourceFilesUnder(relativePath)
    return /\.(vue|scss)$/.test(entry.name) ? [relativePath] : []
  })
}

test('user-facing source has no legacy brand or yellow theme', () => {
  const activeFiles = [
    'App.vue', 'manifest.json', 'pages.json', 'styles/common.scss',
    'pages/index/index.vue', 'pages/index/index.js', 'pages/index/style.scss',
    'pages/order/index.vue', 'pages/order/index.js', 'pages/order/style.scss',
    'pages/address/address.vue', 'pages/addOrEditAddress/addOrEditAddress.vue',
    'pages/remark/index.vue', 'pages/historyOrder/historyOrder.vue',
    'pages/details/index.vue', 'pages/details/index.js', 'pages/pay/index.vue',
    'pages/success/index.vue', 'pages/my/my.vue', 'pages/nonet/index.vue'
  ].map(file => path.join('xiaochengxu-source', file))
  const componentFiles = [
    'xiaochengxu-source/pages/index/components',
    'xiaochengxu-source/pages/order/components',
    'xiaochengxu-source/pages/details/components',
    'xiaochengxu-source/pages/my/components'
  ].flatMap(sourceFilesUnder)
  const files = [...activeFiles, ...componentFiles].map(read).join('\n')
  expectNone(files, ['苍穹外卖', '#ffc200', '#FFC200', '月销量'])
})

test('source styles remain compatible with the current Dart Sass compiler', () => {
  const sourceStyles = sourceFilesUnder('xiaochengxu-source')
    .filter(file => file.endsWith('.vue') || file.endsWith('.scss'))
    .map(read)
    .join('\n')

  expectNone(sourceStyles, ['/deep/', '../image/phone.png'])
})
