const test = require('node:test')
const vm = require('node:vm')
const { assert, read, expectAll, expectNone } = require('./helpers.cjs')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function loadOrderSegments() {
  const source = read('xiaochengxu-source/utils/order-segments.js')
    .replace(/export\s+/g, '')
  const context = { module: { exports: {} } }
  vm.runInNewContext(`${source}\nmodule.exports = { ORDER_SEGMENTS, filterOrdersBySegment, getOrderActions }`, context)
  return context.module.exports
}

function componentOptions(relativePath, context = {}) {
  const source = read(relativePath)
  const script = relativePath.endsWith('.vue')
    ? source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/)[1]
    : source
  const marker = 'export default '
  const optionsSource = script.slice(script.indexOf(marker) + marker.length)
  const sandbox = { module: { exports: {} }, console: { log() {} }, ...context }
  vm.runInNewContext(`module.exports = ${optionsSource}`, sandbox)
  return sandbox.module.exports
}

function mount(definition, refs = {}) {
  const instance = { ...(definition.data ? definition.data() : {}), $refs: refs }
  Object.entries(definition.methods || {}).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  return instance
}

function historyHarness(pages) {
  const calls = { requests: [], toasts: [], navigations: [], mutations: [], loading: 0, hidden: 0 }
  const segments = loadOrderSegments()
  const definition = componentOptions('xiaochengxu-source/pages/historyOrder/historyOrder.vue', {
    CloudmealHeader: {}, AppTabbar: {}, Empty: {},
    ...segments,
    resolveOrderActions: segments.getOrderActions,
    statusWord: status => `状态${status}`,
    getOvertime: () => 100,
    getOrderPage: async params => {
      calls.requests.push(params.page)
      return pages[params.page - 1] || { code: 1, data: { records: [], total: 0 } }
    },
    repetitionOrder: async () => ({ code: 1 }),
    reminderOrder: async () => ({ code: 1 }),
    delShoppingCart: async () => ({ code: 1 }),
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, payload => calls.mutations.push([name, payload])]))
    },
    uni: {
      showLoading: () => { calls.loading += 1 },
      hideLoading: () => { calls.hidden += 1 },
      showToast: options => calls.toasts.push(options),
      navigateTo: options => calls.navigations.push(options),
      reLaunch: options => calls.navigations.push(options),
      stopPullDownRefresh() {}
    },
    getCurrentPages: () => []
  })
  const instance = mount(definition, { commonPopup: { open() {}, close() {} } })
  return { calls, definition, instance }
}

function payHarness({ paymentOrder, requestPayment }) {
  const calls = { cancellations: [], redirects: [], toasts: [] }
  const definition = componentOptions('xiaochengxu-source/pages/pay/index.vue', {
    CloudmealHeader: {},
    paymentOrder,
    cancelOrder: async id => { calls.cancellations.push(id); return { code: 1 } },
    getErrorMessage: (error, fallback) => (error && error.message) || fallback,
    mapState(names) {
      const state = {
        orderData: () => ({ orderNumber: 'CY-6', orderAmount: 28, orderTime: '2026-08-11 12:00:00' }),
        shopInfo: () => ({ shopName: '餐云门店' })
      }
      return Object.fromEntries(names.map(name => [name, state[name]]))
    },
    uni: {
      requestPayment,
      showToast: options => { calls.toasts.push(options); return Promise.resolve() },
      redirectTo: options => calls.redirects.push(options)
    },
    setTimeout: callback => { callback(); return 1 },
    clearTimeout() {}
  })
  const instance = mount(definition)
  Object.assign(instance, {
    orderId: 66,
    orderDataInfo: { orderNumber: 'CY-6', orderAmount: 28, orderTime: '2026-08-11 12:00:00' }
  })
  return { calls, definition, instance }
}

test('order helper filters numeric and string statuses and exposes legal actions', () => {
  const { ORDER_SEGMENTS, filterOrdersBySegment, getOrderActions } = loadOrderSegments()
  assert.deepEqual(Array.from(ORDER_SEGMENTS.current), [1, 2, 3, 4, 5])
  assert.deepEqual(Array.from(ORDER_SEGMENTS.history), [6, 7])
  const orders = [{ status: 1 }, { status: '5' }, { status: 6 }, { status: '7' }, { status: 9 }]
  assert.deepEqual(Array.from(filterOrdersBySegment(orders, 'current'), order => Number(order.status)), [1, 5])
  assert.deepEqual(Array.from(filterOrdersBySegment(orders, 'history'), order => Number(order.status)), [6, 7])
  assert.deepEqual(Array.from(getOrderActions(1)), ['pay'])
  assert.deepEqual(Array.from(getOrderActions(1, { timeout: true })), [])
  assert.deepEqual(Array.from(getOrderActions(2)), ['reminder'])
  assert.deepEqual(Array.from(getOrderActions(6)), ['repeat'])
  assert.deepEqual(Array.from(getOrderActions(7)), ['repeat'])
  assert.deepEqual(Array.from(getOrderActions(3)), [])
})

test('current-order pagination skips empty segment pages and stops on the first match', async () => {
  const history = historyHarness([
    { code: 1, data: { records: [{ id: 1, status: 6 }], total: 3 } },
    { code: 1, data: { records: [{ id: 2, status: 7 }], total: 3 } },
    { code: 1, data: { records: [{ id: 3, status: 2 }], total: 3 } }
  ])
  history.instance.pageInfo.pageSize = 1

  await history.instance.getList()

  assert.deepEqual(history.calls.requests, [1, 2, 3])
  assert.deepEqual(Array.from(history.instance.visibleOrders, order => order.id), [3])
  assert.equal(history.instance.pageInfo.page, 3)
})

test('pagination stops at the final page when a segment has no orders', async () => {
  const history = historyHarness([
    { code: 1, data: { records: [{ id: 1, status: 6 }], total: 2 } },
    { code: 1, data: { records: [{ id: 2, status: 7 }], total: 2 } }
  ])
  history.instance.pageInfo.pageSize = 1

  await history.instance.getList()
  await history.instance.getList()

  assert.deepEqual(history.calls.requests, [1, 2])
  assert.deepEqual(Array.from(history.instance.visibleOrders), [])
  assert.equal(history.instance.canLoadMore, false)
})

test('history page renders only actions allowed for each status', () => {
  const page = read('xiaochengxu-source/pages/historyOrder/historyOrder.vue')
  expectAll(page, [
    '当前订单', '历史订单', '<app-tabbar active="orders"',
    '继续支付', '催单', '再来一单', "hasAction(item, 'pay')",
    "hasAction(item, 'reminder')", "hasAction(item, 'repeat')"
  ])
  expectNone(page, ['<uni-nav-bar', '#ffc200', '#FFC200', 'linear-gradient'])
})

test('detail status component preserves its props and event contract', () => {
  const events = []
  const definition = componentOptions('xiaochengxu-source/pages/details/components/status.vue', {
    statusWord: status => `状态${status}`,
    getOrderActions: status => status === 1 ? ['pay'] : []
  })
  const instance = mount(definition)
  instance.$emit = (name, payload) => events.push([name, payload])

  instance.statusWord(2)
  instance.paymentTime('00:30')
  instance.handlePay(8)
  instance.handleReminder('center', 8)
  instance.handleRefund('center')

  assert.deepEqual(Object.keys(definition.props).sort(), ['orderDetailsData', 'rocallTime', 'timeout'])
  assert.deepEqual(events.map(event => event[0]), [
    'statusWord', 'paymentTime', 'handlePay', 'handleReminder', 'handleRefund'
  ])
})

test('payment ignores duplicate taps and routes only after payment succeeds', async () => {
  const request = deferred()
  let paymentCalls = 0
  let requestPaymentCalls = 0
  const pay = payHarness({
    paymentOrder: () => { paymentCalls += 1; return request.promise },
    requestPayment: async () => { requestPaymentCalls += 1; return [null, { ok: true }] }
  })

  const first = pay.instance.handleSave()
  const second = pay.instance.handleSave()
  assert.equal(paymentCalls, 1)
  assert.equal(pay.instance.isPaying, true)
  request.resolve({ code: 1, data: { timeStamp: '1', packageStr: 'prepay_id=1' } })
  await first
  await second

  assert.equal(requestPaymentCalls, 1)
  assert.equal(pay.calls.toasts.at(-1).title, '支付成功')
  assert.equal(pay.calls.redirects.at(-1).url, '/pages/success/index?orderId=66')
  assert.equal(pay.instance.isPaying, false)
})

test('payment failure stays retryable and never enters the success page', async () => {
  let paymentCalls = 0
  const pay = payHarness({
    paymentOrder: async () => {
      paymentCalls += 1
      return { code: 1, data: { timeStamp: '1', packageStr: 'prepay_id=1' } }
    },
    requestPayment: async () => [{ errMsg: 'requestPayment:fail cancel' }]
  })

  assert.equal(await pay.instance.handleSave(), false)
  assert.equal(pay.instance.isPaying, false)
  assert.equal(pay.calls.toasts.at(-1).title, '支付失败，请重试')
  assert.equal(pay.calls.redirects.length, 0)
  await pay.instance.handleSave()
  assert.equal(paymentCalls, 2)
})

test('success page reLaunches to the two primary destinations', () => {
  const calls = []
  const definition = componentOptions('xiaochengxu-source/pages/success/index.vue', {
    uni: { reLaunch: options => calls.push(options) }
  })
  const instance = mount(definition)

  instance.goOrder()
  instance.goIndex()

  assert.deepEqual(calls.map(call => call.url), [
    '/pages/historyOrder/historyOrder',
    '/pages/index/index'
  ])
  const page = read('xiaochengxu-source/pages/success/index.vue')
  expectAll(page, ['支付成功', '查看订单', '返回点餐'])
  expectNone(page, ['<app-tabbar', '#ffc200', '#FFC200', 'linear-gradient'])
})

test('detail and payment are focused CloudMeal task pages', () => {
  const detail = read('xiaochengxu-source/pages/details/index.vue')
  const pay = read('xiaochengxu-source/pages/pay/index.vue')
  expectAll(detail, ['<cloudmeal-header', 'show-back', '订单详情', '联系商家', '<delivery-info', '<order-info'])
  expectAll(pay, ['<cloudmeal-header', 'show-back', '支付订单', '确认支付', ':disabled="isPaying"'])
  expectNone(detail + pay, ['<app-tabbar', '<uni-nav-bar', '#ffc200', '#FFC200', 'linear-gradient'])
})
