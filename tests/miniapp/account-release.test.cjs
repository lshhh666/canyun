const test = require('node:test')
const vm = require('node:vm')
const { assert, read, expectAll, expectNone } = require('./helpers.cjs')

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
    backs: []
  }
  const definition = componentOptions('xiaochengxu-source/pages/my/my.vue', {
    HeadInfo: {},
    OrderList: {},
    CloudmealHeader: {},
    AppTabbar: {},
    DEFAULT_AVATAR: '/static/brand/cloudmeal-logo.png',
    DEFAULT_NICKNAME: '微信用户',
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
  assert.equal(account.calls.backs.at(-1).delta, 2)
})

test('network and empty states expose recovery through state-panel', () => {
  const nonet = read('xiaochengxu-source/pages/nonet/index.vue')
  const empty = read('xiaochengxu-source/components/empty/empty.vue')
  expectAll(nonet, ['<state-panel', '网络连接失败', '请检查网络后重新加载', '重新加载'])
  expectAll(empty, ['<state-panel', ':action-text="actionText"', '@action="handleAction"'])
  expectNone(nonet + empty, ['#ffc200', '#FFC200', 'linear-gradient'])
})
