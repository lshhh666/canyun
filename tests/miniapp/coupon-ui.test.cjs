const test = require('node:test')
const vm = require('node:vm')
const { assert, read, expectAll } = require('./helpers.cjs')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function couponUtilities() {
  const source = read('xiaochengxu-source/utils/coupon.js')
    .replace(/export function /g, 'function ')
  const sandbox = { module: { exports: {} }, Date, Number }
  vm.runInNewContext(`${source}\nmodule.exports = { couponTimestamp, getCouponEligibility, normalizeCouponStatus }`, sandbox)
  return sandbox.module.exports
}

function couponPage(apis = {}) {
  const source = read('xiaochengxu-source/pages/coupon/index.vue')
  const script = source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/)[1]
  const optionsSource = script.slice(script.indexOf('export default ') + 'export default '.length)
  const helpers = couponUtilities()
  const calls = { backs: [], commits: [], toasts: [] }
  const context = {
    module: { exports: {} },
    CloudmealHeader: {},
    StatePanel: {},
    getAvailableCoupons: async () => ({ data: [] }),
    getMyCoupons: async () => ({ data: [] }),
    receiveCoupon: async () => ({ code: 1 }),
    getErrorMessage: (error, fallback) => (error && error.message) || fallback,
    uni: {
      navigateBack: options => calls.backs.push(options),
      showToast: options => calls.toasts.push(options)
    },
    ...helpers,
    ...apis
  }
  vm.runInNewContext(`module.exports = ${optionsSource}`, context)
  const definition = context.module.exports
  const instance = { ...definition.data() }
  instance.$store = {
    state: { selectedCoupon: null },
    commit: (name, payload) => {
      calls.commits.push([name, payload])
      if (name === 'setSelectedCoupon') instance.$store.state.selectedCoupon = payload
    }
  }
  Object.entries(definition.methods || {}).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  return { calls, instance }
}

test('coupon APIs preserve the backend URL and method contracts', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  const contracts = [
    [/getAvailableCoupons[\s\S]*?url:\s*'\/user\/coupon\/list'[\s\S]*?method:\s*'GET'/, '可领取列表'],
    [/receiveCoupon[\s\S]*?`\/user\/coupon\/\$\{couponId\}\/receive`[\s\S]*?method:\s*'POST'/, '领取'],
    [/getMyCoupons[\s\S]*?url:\s*'\/user\/coupon\/my'[\s\S]*?method:\s*'GET'/, '我的优惠券']
  ]
  contracts.forEach(([pattern, label]) => assert.match(api, pattern, `missing coupon API: ${label}`))
})

test('coupon page supports center, mine, selection, empty state and retry state', () => {
  const page = read('xiaochengxu-source/pages/coupon/index.vue')
  expectAll(page, [
    '领券中心', '我的优惠券', '不使用优惠券', '重新加载',
    'isSelectable(coupon)', 'setSelectedCoupon', 'receiveCoupon(coupon.id)',
    '优惠券门槛按菜品金额计算'
  ])
})

test('coupon page is registered and checkout renders server-preview-based discount fields', () => {
  const pages = JSON.parse(read('xiaochengxu-source/pages.json'))
  assert.ok(pages.pages.some(page => page.path === 'pages/coupon/index'))

  const checkout = read('xiaochengxu-source/pages/order/index.vue')
  expectAll(checkout, [
    '@tap="openCouponSelector"', 'couponDiscount.toFixed(2)',
    'payableAmount.toFixed(2)', 'totalAmount.toFixed(2)'
  ])
})

test('coupon eligibility requires status, threshold and the complete validity window', () => {
  const { getCouponEligibility } = couponUtilities()
  const now = Date.parse('2026-08-23T12:00:00+08:00')
  const coupon = {
    status: 'AVAILABLE',
    thresholdAmount: '20.00',
    validStartTime: '2026-08-23 00:00:00',
    validEndTime: '2026-08-24 00:00:00'
  }

  assert.equal(getCouponEligibility(coupon, 30, now).eligible, true)
  assert.equal(getCouponEligibility({
    ...coupon,
    validStartTime: '2026-08-24 00:00:00',
    validEndTime: '2026-08-25 00:00:00'
  }, 30, now).reason, '优惠券尚未生效')
  assert.equal(getCouponEligibility({ ...coupon, validEndTime: '2026-08-23 11:00:00' }, 30, now).reason, '优惠券已过期')
  assert.equal(getCouponEligibility({ ...coupon, validEndTime: null }, 30, now).reason, '优惠券有效期信息异常')
  assert.match(getCouponEligibility(coupon, 10, now).reason, /还差￥10\.00可用/)
})

test('coupon page reloads the newly selected tab after a rapid switch', async () => {
  const firstMine = deferred()
  let mineCalls = 0
  const page = couponPage({
    getAvailableCoupons: async () => ({ data: [{ id: 7, name: '新客券' }] }),
    getMyCoupons: async () => {
      mineCalls += 1
      return mineCalls === 1 ? firstMine.promise : { data: [] }
    }
  })
  page.instance.activeTab = 'my'

  const loading = page.instance.loadCoupons()
  page.instance.switchTab('center')
  firstMine.resolve({ data: [{ id: 1, couponName: '旧券' }] })
  await loading

  assert.equal(page.instance.activeTab, 'center')
  assert.equal(page.instance.loadState, 'ready')
  assert.equal(page.instance.availableCoupons.length, 1)
  assert.equal(page.instance.availableCoupons[0].id, 7)
  assert.equal(mineCalls, 2)
})
