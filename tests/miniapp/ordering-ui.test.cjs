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

function loadOrderingController(overrides = {}) {
  const source = read('xiaochengxu-source/pages/index/index.js')
  const exportMarker = 'export default '
  const optionsSource = source.slice(source.indexOf(exportMarker) + exportMarker.length)
  const state = {
    shopInfo: { shopAddress: '测试门店' },
    shopPhone: '',
    orderListData: [],
    baseUserInfo: {},
    lodding: false,
    token: 'token-123',
    deliveryFee: 3,
    ...(overrides.state || {})
  }
  const calls = { mutations: [], toasts: [], navigations: [] }
  const apiDefaults = {
    userLogin: async () => ({ code: 1, data: {} }),
    getCategoryList: async () => ({ code: 1, data: [] }),
    dishListByCategoryId: async () => ({ code: 1, data: [] }),
    querySetmeaList: async () => ({ code: 1, data: [] }),
    getShoppingCartList: async () => ({ code: 1, data: [] }),
    newAddShoppingCartAdd: async () => ({ code: 1 }),
    newShoppingCartSub: async () => ({ code: 1 }),
    delShoppingCart: async () => ({ code: 1 }),
    querySetmealDishById: async () => ({ code: 1, data: [] }),
    getShopStatus: async () => ({ code: 1, data: 1 }),
    getMerchantInfo: async () => ({ code: 1, data: { phone: '' } })
  }
  const context = {
    module: { exports: {} },
    console: { log() {} },
    setTimeout,
    clearTimeout,
    baseUrl: 'http://example.test',
    getErrorMessage: (error, fallback) => (error && error.message) || fallback,
    Phone: {},
    CloudmealHeader: {},
    AppTabbar: {},
    StatePanel: {},
    popMask: {},
    popCart: {},
    dishDetail: {},
    mapState(names) {
      return Object.fromEntries(names.map(name => [name, function getVuexState() {
        return state[name]
      }]))
    },
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, function commitVuex(payload) {
        calls.mutations.push([name, payload])
        if (name === 'initdishListMut') state.orderListData = payload
        if (name === 'setShopInfo') state.shopInfo = payload
        if (name === 'setDeliveryFee') state.deliveryFee = payload
      }]))
    },
    uni: {
      showToast(options) {
        calls.toasts.push(options)
      },
      navigateTo(options) {
        calls.navigations.push(options)
      }
    },
    ...apiDefaults,
    ...(overrides.apis || {})
  }

  vm.runInNewContext(`module.exports = ${optionsSource}`, context)
  const definition = context.module.exports
  const instance = { ...definition.data() }
  Object.entries(definition.methods).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  instance.$nextTick = callback => callback.call(instance)

  return { calls, definition, instance, state }
}

test('ordering page keeps business handlers and uses CloudMeal shell', () => {
  const page = read('xiaochengxu-source/pages/index/index.vue')
  expectAll(page, [
    '<cloudmeal-header',
    '<app-tabbar active="order"',
    '@tap.stop="swichMenu(item, index)"',
    '@click="openDetailHandle(item)"',
    "redDishAction(item, '普通')",
    "addDishAction(item, '普通')",
    '@click="goOrder()"'
  ])
  expectNone(page, ['苍穹外卖', '月销量', '店铺已打烊', '本店已打样', 'logo_ruiji.png'])
})

test('cart and dish overlays preserve their event contracts', () => {
  const files = ['dishDetail.vue', 'popMask.vue', 'popCart.vue']
    .map(name => read(`xiaochengxu-source/pages/index/components/${name}`))
    .join('\n')
  expectAll(files, [
    '$emit("checkMoreNormPop"',
    '$emit("addShop"',
    '$emit("closeMoreNorm"',
    '$emit("clearCardOrder"',
    '$emit("addDishAction"',
    '$emit("redDishAction"',
    '$emit("moreNormDataesHandle"',
    '$emit("dishClose"'
  ])
  expectNone(files, ['#ffc200', '#FFC200', 'linear-gradient'])
})

test('ordering layout keeps browsing available while checkout follows store status', () => {
  const page = read('xiaochengxu-source/pages/index/index.vue')
  const script = read('xiaochengxu-source/pages/index/index.js')
  const styles = read('xiaochengxu-source/pages/index/style.scss')
  expectAll(page, [
    'class="menu-layout"',
    'v-else-if="dishListItems && dishListItems.length > 0"',
    ':disabled="orderListData().length === 0 || shopStatus !== 1"',
    "shopStatus === null ? '状态加载中'",
    '<state-panel'
  ])
  expectAll(styles, [
    'width: 176rpx;',
    'background: #147ee8;',
    'bottom: calc(108rpx + env(safe-area-inset-bottom));',
    'padding-bottom: calc(228rpx + env(safe-area-inset-bottom));'
  ])
  expectAll(script, [
    'getErrorMessage(error, "菜单加载失败，请重试")',
    'title: "门店休息中，暂时无法结算"',
    'if (item && item.obj)',
    'form = item.item',
    'item = item.obj',
    'const loaded = await this.getDishListDataes(',
    'const requestId = ++this.menuRequestId',
    'if (requestId !== this.menuRequestId) return',
    'selectAll(".type_list .type_item")',
    'this.arr = rects || []'
  ])
  expectNone(page + styles + script, ['linear-gradient', '#ffc200', '#FFC200', 'selectAll(".class-item")'])
})

test('ordering loading overlay is structural and missing metadata has stable copy', () => {
  const page = read('xiaochengxu-source/pages/index/index.vue')
  expectAll(page, [
    'v-if="menuLoading"',
    'v-if="menuLoadFailed"',
    'actionText="重新加载"',
    '@action="reloadMenu"',
    '{{ shopAddressText }}',
    '￥{{ deliveryFeeText }}'
  ])
  expectNone(page, ['v-show="loaddingSt"', '￥{{ deliveryFee() }}', '正在获取门店信息'])

  const missing = loadOrderingController({
    state: { shopInfo: null, deliveryFee: null }
  })
  assert.equal(missing.instance.shopAddressText, '门店信息暂未完善')
  assert.equal(missing.instance.deliveryFeeText, '0.00')

  const valid = loadOrderingController({
    state: { shopInfo: { shopAddress: '望京门店' }, deliveryFee: '3' }
  })
  assert.equal(valid.instance.shopAddressText, '望京门店')
  assert.equal(valid.instance.deliveryFeeText, '3.00')
})

test('Vuex state helpers run as bound methods and null carts normalize to arrays', async () => {
  const harness = loadOrderingController({
    state: { token: 'bound-token', orderListData: [{ id: 9 }] },
    apis: { getShoppingCartList: async () => ({ code: 1, data: null }) }
  })

  assert.equal(harness.instance.token(), 'bound-token')
  assert.equal(harness.instance.orderListData()[0].id, 9)
  await harness.instance.getTableOrderDishListes()

  assert.equal(Array.isArray(harness.state.orderListData), true)
  assert.equal(harness.state.orderListData.length, 0)
  assert.equal(harness.calls.mutations.at(-1)[0], 'initdishListMut')
  assert.equal(Array.isArray(harness.calls.mutations.at(-1)[1]), true)
  assert.equal(harness.calls.mutations.at(-1)[1].length, 0)
  assert.equal(harness.instance.orderDishNumber, 0)
  assert.equal(harness.instance.orderDishPrice, 0)
})

test('cart writes refresh cart before synchronizing menu counts', async () => {
  const cases = [
    ['add', instance => instance.addDishAction({ id: 1, type: 1, dishNumber: 0 }, '普通')],
    ['spec add', instance => instance.addShop({ id: 1, type: 1, dishNumber: 0 })],
    ['subtract', instance => instance.redDishAction({ id: 1, type: 1, dishNumber: 1 }, '普通')],
    ['clear', instance => instance.clearCardOrder()]
  ]

  for (const [label, runAction] of cases) {
    const harness = loadOrderingController()
    const sequence = []
    harness.instance.dishDetailes = { dishNumber: 1 }
    harness.instance.rightIdAndType = { id: 2, type: 1 }
    harness.instance.getTableOrderDishListes = async () => {
      sequence.push('cart:start')
      await Promise.resolve()
      sequence.push('cart:end')
    }
    harness.instance.getDishListDataes = async () => {
      sequence.push('menu')
    }

    await runAction(harness.instance)
    await Promise.resolve()
    await Promise.resolve()
    assert.deepEqual(sequence, ['cart:start', 'cart:end', 'menu'], label)
  }
})

test('overlapping category requests ignore the older response', async () => {
  const oldRequest = deferred()
  const newRequest = deferred()
  let requestCount = 0
  const harness = loadOrderingController({
    apis: {
      getCategoryList: () => (++requestCount === 1 ? oldRequest.promise : newRequest.promise)
    }
  })
  const dishRequests = []
  harness.instance.getMerchantInfo = async () => {}
  harness.instance.getTableOrderDishListes = async () => {}
  harness.instance.getDishListDataes = async category => dishRequests.push(category.id)

  const firstInit = harness.instance.init()
  const secondInit = harness.instance.init()
  newRequest.resolve({ code: 1, data: [{ id: 'new', type: 1 }] })
  await secondInit
  oldRequest.resolve({ code: 1, data: [{ id: 'old', type: 1 }] })
  await firstInit

  assert.equal(harness.instance.typeListData[0].id, 'new')
  assert.deepEqual(dishRequests, ['new'])
})

test('latest initialization owns loading until its first menu settles', async () => {
  const categories = deferred()
  const dishes = deferred()
  const harness = loadOrderingController({
    apis: {
      getCategoryList: () => categories.promise,
      dishListByCategoryId: () => dishes.promise
    }
  })
  harness.instance.getMerchantInfo = async () => {}
  harness.instance.getTableOrderDishListes = async () => {}

  const loading = harness.instance.init()
  assert.equal(harness.instance.menuLoading, true)
  categories.resolve({ code: 1, data: [{ id: 1, type: 1 }] })
  await Promise.resolve()
  assert.equal(harness.instance.menuLoading, true)
  dishes.resolve({ code: 1, data: [] })
  await loading

  assert.equal(harness.instance.menuLoading, false)
  assert.equal(harness.instance.menuLoadFailed, false)
})

test('failed initialization clears loading and retry can recover', async () => {
  let calls = 0
  const harness = loadOrderingController({
    apis: {
      getCategoryList: async () => {
        calls += 1
        if (calls === 1) throw new Error('network down')
        return { code: 1, data: [] }
      }
    }
  })
  harness.instance.getMerchantInfo = async () => {}
  harness.instance.getTableOrderDishListes = async () => {}

  await harness.instance.init()
  assert.equal(harness.instance.menuLoading, false)
  assert.equal(harness.instance.menuLoadFailed, true)
  await harness.instance.reloadMenu()
  assert.equal(harness.instance.menuLoading, false)
  assert.equal(harness.instance.menuLoadFailed, false)
})

test('an older initialization cannot clear the newer loading state', async () => {
  const oldRequest = deferred()
  const newRequest = deferred()
  let calls = 0
  const harness = loadOrderingController({
    apis: {
      getCategoryList: () => (++calls === 1 ? oldRequest.promise : newRequest.promise)
    }
  })
  harness.instance.getMerchantInfo = async () => {}
  harness.instance.getTableOrderDishListes = async () => {}

  const oldInit = harness.instance.init()
  const newInit = harness.instance.init()
  oldRequest.resolve({ code: 1, data: [] })
  await oldInit
  assert.equal(harness.instance.menuLoading, true)
  newRequest.resolve({ code: 1, data: [] })
  await newInit
  assert.equal(harness.instance.menuLoading, false)
})

test('a category switch owns its failure state over an older initial menu request', async () => {
  const categories = deferred()
  const initialDishes = deferred()
  const switchedDishes = deferred()
  const initialDishStarted = deferred()
  let dishCalls = 0
  const harness = loadOrderingController({
    apis: {
      getCategoryList: () => categories.promise,
      dishListByCategoryId: () => {
        dishCalls += 1
        if (dishCalls === 1) {
          initialDishStarted.resolve()
          return initialDishes.promise
        }
        return switchedDishes.promise
      }
    }
  })
  harness.instance.arr = [{}]
  harness.instance.getMerchantInfo = async () => {}
  harness.instance.getTableOrderDishListes = async () => {}
  harness.instance.leftMenuStatus = async () => {}

  const initial = harness.instance.init()
  categories.resolve({ code: 1, data: [{ id: 1, type: 1 }, { id: 2, type: 1 }] })
  await initialDishStarted.promise

  const switched = harness.instance.swichMenu({ id: 2, type: 1 }, 1)
  switchedDishes.resolve({ code: 0, msg: 'switch failed' })
  await switched

  assert.equal(harness.instance.menuLoadFailed, true)
  assert.equal(harness.instance.menuLoading, false)
  initialDishes.resolve({ code: 1, data: [] })
  await initial

  assert.equal(harness.instance.menuLoadFailed, true)
  assert.equal(harness.instance.menuLoading, false)
})

test('loading and closed stores block checkout without blocking add requests', async () => {
  let addCalls = 0
  const harness = loadOrderingController({
    state: { orderListData: [{ id: 1 }] },
    apis: {
      newAddShoppingCartAdd: async () => {
        addCalls += 1
        return { code: 1 }
      }
    }
  })
  harness.instance.getTableOrderDishListes = async () => {}
  harness.instance.getDishListDataes = async () => {}
  harness.instance.dishDetailes = { dishNumber: 0 }

  assert.equal(harness.instance.shopStatusText, '状态加载中')
  harness.instance.shopStatus = 0
  assert.equal(harness.instance.shopStatusText, '休息中')
  harness.instance.goOrder()
  await harness.instance.addDishAction({ id: 1, type: 1, dishNumber: 0 }, '普通')

  assert.equal(harness.calls.navigations.length, 0)
  assert.equal(harness.calls.toasts.at(-1).title, '门店休息中，暂时无法结算')
  assert.equal(addCalls, 1)
})
