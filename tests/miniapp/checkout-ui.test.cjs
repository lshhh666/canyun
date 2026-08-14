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

function componentOptions(relativePath, context) {
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

function mount(definition, { state = {}, mutations = [], refs = {} } = {}) {
  const instance = { ...(definition.data ? definition.data() : {}) }
  instance.$store = { state }
  instance.$refs = refs
  Object.entries(definition.methods || {}).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  instance.__mutations = mutations
  return instance
}

function sharedContext(calls, state, apis = {}) {
  return {
    CloudmealHeader: {},
    AddressPop: {},
    DishDetail: {},
    DishInfo: {},
    Empty: {},
    Pikers: {},
    simpleAddress: {},
    uniNavBar: {},
    baseUrl: 'http://example.test',
    getLableVal: value => String(value),
    dateFormat: () => '2026-08-11 10:00:00',
    presentFormat: () => '2026-08-11 09:00:00',
    getWeekDate: () => '周二',
    getErrorMessage: (error, fallback) => (error && error.message) || fallback,
    dayjs: () => ({
      format: () => '10:00',
      hour: () => 10,
      minute: () => 0,
      add() { return this },
      set() { return this }
    }),
    mapState(names) {
      return Object.fromEntries(names.map(name => [name, function getState() {
        return state[name]
      }]))
    },
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, function commit(payload) {
        calls.mutations.push([name, payload])
        if (name === 'setAddress') state.address = payload
        if (name === 'setRemark') state.remark = payload
      }]))
    },
    uni: {
      showToast: options => calls.toasts.push(options),
      showLoading: options => calls.loadings.push(options),
      hideLoading: () => { calls.hidden += 1 },
      redirectTo: options => calls.redirects.push(options),
      navigateTo: options => calls.navigations.push(options),
      navigateBack: options => calls.backs.push(options),
      getSystemInfoSync: () => ({ platform: 'ios', statusBarHeight: 20 }),
      removeStorage() {},
      setNavigationBarTitle() {},
      hideKeyboard() {}
    },
    setTimeout: callback => callback(),
    ...apis
  }
}

function harness(relativePath, { state = {}, apis = {}, refs = {} } = {}) {
  const calls = {
    backs: [], hidden: 0, loadings: [], mutations: [], navigations: [],
    redirects: [], toasts: []
  }
  const definition = componentOptions(relativePath, sharedContext(calls, state, apis))
  const instance = mount(definition, { state, mutations: calls.mutations, refs })
  return { calls, definition, instance, state }
}

test('checkout renders address, dishes, remark, fees and submit in business order', () => {
  const page = read('xiaochengxu-source/pages/order/index.vue')
  const markers = ['<address-pop', '<dish-detail', '<dish-info', '费用明细', '@click="payOrderHandle()"']
  markers.reduce((last, marker) => {
    const index = page.indexOf(marker)
    assert.ok(index > last, `${marker} is out of order`)
    return index
  }, -1)
  expectAll(page, [
    '<cloudmeal-header', 'show-back', ':disabled="isHandlePy || previewState !== \'ready\'"',
    ':loading="isHandlePy"', 'totalAmount.toFixed(2)', 'packFeeAmount.toFixed(2)'
  ])
  expectNone(page, ['<app-tabbar', '#ffc200', '去支付</view>\n          <view v-else'])
})

test('checkout delivery row keeps labels horizontal and uses the full card width', () => {
  const address = read('xiaochengxu-source/pages/order/components/address.vue')
  const styles = read('xiaochengxu-source/pages/order/style.scss')

  expectAll(address, ['class="bottomTime"', 'class="time_name_disabled"', '{{ arrivalTime }}送达'])
  assert.match(styles, /\.bottomTime\s*\{[\s\S]*?flex:\s*0 0 100%/)
  assert.match(styles, /\.bottomTime\s*\{[\s\S]*?width:\s*100%/)
  assert.match(styles, /\.time_name_disabled\s*\{[\s\S]*?white-space:\s*nowrap/)
})

test('checkout ignores duplicate submits and restores the guard after a failure', async () => {
  const request = deferred()
  let submitCalls = 0
  let submittedParams
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [], remarkData: '', addressData: {},
      storeInfo: {}, shopInfo: { shopId: 7 }, deliveryFee: 3
    },
    apis: {
      submitOrderSubmit: params => {
        submitCalls += 1
        submittedParams = params
        return request.promise
      },
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })
  Object.assign(order.instance, {
    address: '测试地址', addressBookId: 9, arrivalTime: '10:00', deliveryMode: 'scheduled',
    orderDishNumber: 2, orderDishPrice: 28, remark: '少辣', status: 0, num: 0,
    previewState: 'ready', previewData: { estimatedDeliveryTime: '2026-08-11 10:00:00' }
  })

  const first = order.instance.payOrderHandle()
  const second = order.instance.payOrderHandle()
  assert.equal(submitCalls, 1)
  assert.equal(order.instance.isHandlePy, true)
  assert.equal('amount' in submittedParams, false)
  assert.equal('deliveryFee' in submittedParams, false)
  assert.equal(submittedParams.addressBookId, 9)

  request.reject(new Error('下单服务不可用'))
  await first
  await second
  assert.equal(order.instance.isHandlePy, false)
  assert.equal(order.calls.toasts.at(-1).title, '下单服务不可用')
})

test('checkout unlocks and reports synchronous payload construction failures', async () => {
  let submitCalls = 0
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [], remarkData: '', addressData: {}, storeInfo: {},
      shopInfo: { shopId: 7 }, deliveryFee: 3
    },
    apis: {
      dateFormat: () => { throw new Error('配送时间格式化失败') },
      submitOrderSubmit: async () => { submitCalls += 1; return { code: 1, data: { id: 1 } } },
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })
  Object.assign(order.instance, {
    address: '测试地址', arrivalTime: '10:00', deliveryMode: 'scheduled', orderDishNumber: 2,
    orderDishPrice: 28, status: 0, num: 0,
    previewState: 'ready', previewData: { estimatedDeliveryTime: '2026-08-11 10:00:00' }
  })

  assert.equal(await order.instance.payOrderHandle(), null)
  assert.equal(submitCalls, 0)
  assert.equal(order.instance.isHandlePy, false)
  assert.equal(order.calls.toasts.at(-1).title, '配送时间格式化失败')
})

test('checkout success keeps the existing payload and navigates to payment', async () => {
  let submittedParams
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [], remarkData: '', addressData: {}, storeInfo: {},
      shopInfo: { shopId: 7 }, deliveryFee: 3
    },
    apis: {
      submitOrderSubmit: async params => {
        submittedParams = params
        return { code: 1, data: { id: 88 } }
      },
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })
  Object.assign(order.instance, {
    address: '测试地址', addressBookId: 9, arrivalTime: '10:00', deliveryMode: 'scheduled',
    orderDishNumber: 2, orderDishPrice: 28, remark: '少辣', status: 0, num: 0,
    previewState: 'ready', previewData: { estimatedDeliveryTime: '2026-08-11 10:00:00' }
  })

  const result = await order.instance.payOrderHandle()
  assert.equal(result.code, 1)
  assert.equal('amount' in submittedParams, false)
  assert.equal('packAmount' in submittedParams, false)
  assert.equal('deliveryFee' in submittedParams, false)
  assert.equal('shopId' in submittedParams, false)
  assert.equal(order.instance.isHandlePy, false)
  assert.deepEqual(order.calls.mutations.slice(-2), [
    ['setOrderData', { id: 88 }],
    ['setRemark', '']
  ])
  assert.equal(order.calls.navigations.at(-1).url, '/pages/pay/index?orderId=88')
})

test('checkout renders and submits only the authoritative preview', async () => {
  let submittedParams
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [{ number: 2, amount: 10 }],
      remarkData: '', addressData: {}, storeInfo: {},
      shopInfo: { shopId: 7 }, deliveryFee: '3'
    },
    apis: {
      submitOrderSubmit: async params => {
        submittedParams = params
        return { code: 1, data: { id: 91 } }
      },
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })

  assert.match(read('xiaochengxu-source/pages/order/index.vue'), /deliveryFeeAmount\.toFixed\(2\)/)
  Object.assign(order.instance, {
    address: '测试地址', addressBookId: 9, arrivalTime: '10:00', deliveryMode: 'scheduled',
    remark: '', status: 0, num: 0, previewState: 'ready',
    previewData: {
      goodsAmount: '20.00', packAmount: '2.00', deliveryFee: '3.00',
      totalAmount: '25.00', estimatedDeliveryTime: '2026-08-11 10:00:00'
    }
  })
  assert.equal(order.instance.dishAmount, 20)
  assert.equal(order.instance.packFeeAmount, 2)
  assert.equal(order.instance.deliveryFeeAmount, 3)
  assert.equal(order.instance.totalAmount, 25)
  await order.instance.payOrderHandle()

  assert.equal('amount' in submittedParams, false)
  assert.equal('deliveryFee' in submittedParams, false)

  const invalid = harness('xiaochengxu-source/pages/order/index.js', {
    state: { orderListData: [], deliveryFee: 'not-a-number' }
  })
  invalid.instance.previewData = { deliveryFee: 'not-a-number', totalAmount: null }
  assert.equal(invalid.instance.deliveryFeeAmount, 0)
  assert.equal(invalid.instance.totalAmount, 0)
})

test('checkout keeps immediate delivery mode and submits the server ETA', async () => {
  let submittedParams
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: { orderListData: [], remarkData: '', addressData: {}, deliveryFee: 3 },
    apis: {
      submitOrderSubmit: async params => {
        submittedParams = params
        return { code: 1, data: { id: 92 } }
      }
    }
  })
  Object.assign(order.instance, {
    address: '测试地址', addressBookId: 12, previewState: 'ready',
    previewData: { estimatedDeliveryTime: '2026-08-11 10:00' }
  })

  order.instance.setTime('立即派送')
  await order.instance.payOrderHandle()

  assert.equal(order.instance.deliveryMode, 'immediate')
  assert.equal(submittedParams.deliveryStatus, 1)
  assert.equal(submittedParams.estimatedDeliveryTime, '2026-08-11 10:00:00')
})

test('checkout preview waits for an address and keeps failures retryable', async () => {
  const calls = []
  let fail = true
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: { orderListData: [], remarkData: '', addressData: {}, deliveryFee: 3 },
    apis: {
      previewOrder: async params => {
        calls.push(params)
        if (fail) throw new Error('报价服务不可用')
        return { code: 1, data: {
          goodsAmount: 20, packAmount: 2, deliveryFee: 3, totalAmount: 25,
          estimatedDeliveryTime: '2026-08-11 10:00:00'
        } }
      }
    }
  })

  order.instance.getDateDate = () => {}

  assert.equal(await order.instance.loadPreview(), null)
  assert.equal(calls.length, 0)
  order.instance.addressBookId = 12
  assert.equal(await order.instance.loadPreview(), null)
  assert.equal(order.instance.previewState, 'error')

  fail = false
  await order.instance.loadPreview()
  assert.equal(calls.at(-1).addressBookId, 12)
  assert.equal(order.instance.previewState, 'ready')
  assert.equal(order.instance.totalAmount, 25)
})

test('checkout only routes to add-address after a successful empty-list load', async () => {
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [], remarkData: '', addressData: {}, storeInfo: {},
      shopInfo: { shopId: 7 }, deliveryFee: 3
    },
    apis: {
      submitOrderSubmit: async () => ({ code: 1, data: {} }),
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })

  assert.equal(order.instance.addressLoadState, 'loading')
  order.instance.goAddress()
  assert.equal(order.calls.redirects.length, 0)
  assert.equal(order.calls.toasts.at(-1).title, '地址加载中，请稍候')

  await order.instance.getAddressList()
  assert.equal(order.instance.addressLoadState, 'ready')
  order.instance.goAddress()
  assert.equal(order.calls.redirects.at(-1).url, '/pages/addOrEditAddress/addOrEditAddress')
  assert.deepEqual(order.calls.mutations.at(-1), ['setAddressBackUrl', '/pages/order/index'])

  order.instance.addressList = [{ id: 1 }]
  order.instance.goAddress()
  assert.equal(order.calls.redirects.at(-1).url, '/pages/address/address')
})

test('checkout address-list errors never masquerade as a confirmed empty list', async () => {
  const order = harness('xiaochengxu-source/pages/order/index.js', {
    state: {
      orderListData: [], remarkData: '', addressData: {}, storeInfo: {},
      shopInfo: { shopId: 7 }, deliveryFee: 3
    },
    apis: {
      submitOrderSubmit: async () => ({ code: 1, data: {} }),
      getAddressBookDefault: async () => ({ code: 1, data: {} }),
      queryAddressBookList: async () => { throw new Error('地址列表暂不可用') },
      getEstimatedDeliveryTime: async () => ({ code: 1, data: '2026-08-11 10:00:00' })
    }
  })

  await order.instance.getAddressList()
  assert.equal(order.instance.addressLoadState, 'error')
  assert.equal(order.calls.toasts.at(-1).title, '地址列表暂不可用')
  order.instance.goAddress()
  assert.ok(order.calls.redirects.every(call => call.url !== '/pages/addOrEditAddress/addOrEditAddress'))
  assert.equal(order.calls.redirects.at(-1).url, '/pages/address/address')
})

test('address list preserves select, edit, add and default-address behavior', async () => {
  const defaultCalls = []
  const address = harness('xiaochengxu-source/pages/address/address.vue', {
    state: { addressBackUrl: '/pages/order/index' },
    apis: {
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      putAddressBookDefault: async params => {
        defaultCalls.push(params)
        return { code: 1 }
      }
    }
  })
  const item = { id: 12, isDefault: 0 }

  address.instance.addOrEdit('新增')
  assert.equal(address.calls.redirects.at(-1).url, '/pages/addOrEditAddress/addOrEditAddress')
  address.instance.addOrEdit('编辑', item)
  assert.match(address.calls.redirects.at(-1).url, /type=编辑&id=12$/)
  address.instance.choseAddress(0, item)
  assert.equal(address.calls.mutations.at(-1)[0], 'setAddress')
  assert.match(address.calls.redirects.at(-1).url, /^\/pages\/order\/index\?address=/)

  await address.instance.getRadio(0, item)
  assert.equal(defaultCalls.length, 1)
  assert.equal(defaultCalls[0].id, 12)
  assert.equal(address.instance.isActive, 0)
  assert.equal(address.calls.toasts.at(-1).title, '默认地址设置成功')
})

test('address form saves new data and deletes an existing address', async () => {
  const adds = []
  const deletes = []
  const edit = harness('xiaochengxu-source/pages/addOrEditAddress/addOrEditAddress.vue', {
    apis: {
      addAddressBook: async params => { adds.push(params); return { code: 1 } },
      editAddressBook: async () => ({ code: 1 }),
      delAddressBook: async id => { deletes.push(id); return { code: 1 } },
      queryAddressBookById: async () => ({ code: 1, data: {} })
    }
  })
  Object.assign(edit.instance.form, {
    name: '测试用户', phone: '13800138000', type: 2, sex: '0', detail: '1号楼101'
  })
  edit.instance.address = '浙江省/杭州市/西湖区'

  await edit.instance.addAddressFun()
  assert.equal(adds.length, 1)
  assert.equal(adds[0].consignee, '测试用户')
  assert.equal(adds[0].provinceName, '浙江省')
  assert.equal(edit.calls.redirects.at(-1).url, '/pages/address/address')

  edit.instance.delId = 22
  edit.instance.showDel = true
  await edit.instance.deleteAddressFun()
  assert.deepEqual(deletes, [22])
  assert.equal(edit.calls.toasts.at(-1).title, '地址删除成功')
})

test('address operation failures keep state recoverable and show server messages', async () => {
  const defaultAddress = harness('xiaochengxu-source/pages/address/address.vue', {
    state: { addressBackUrl: '/pages/order/index' },
    apis: {
      queryAddressBookList: async () => ({ code: 1, data: [] }),
      putAddressBookDefault: async () => { throw new Error('默认地址不可用') }
    }
  })
  defaultAddress.instance.addressList = [{ id: 1, isDefault: 1 }, { id: 2, isDefault: 0 }]
  defaultAddress.instance.isActive = 0
  await defaultAddress.instance.getRadio(1, defaultAddress.instance.addressList[1])
  assert.equal(defaultAddress.instance.isActive, 0)
  assert.equal(defaultAddress.calls.toasts.at(-1).title, '默认地址不可用')

  const save = harness('xiaochengxu-source/pages/addOrEditAddress/addOrEditAddress.vue', {
    apis: {
      addAddressBook: async () => { throw new Error('地址保存服务不可用') },
      editAddressBook: async () => ({ code: 1 }),
      delAddressBook: async () => ({ code: 1 }),
      queryAddressBookById: async () => ({ code: 1, data: {} })
    }
  })
  Object.assign(save.instance.form, {
    name: '测试用户', phone: '13800138000', type: 2, sex: '0', detail: '1号楼101'
  })
  save.instance.address = '浙江省/杭州市/西湖区'
  assert.equal(await save.instance.addAddressFun(), false)
  assert.equal(save.calls.redirects.length, 0)
  assert.equal(save.calls.toasts.at(-1).title, '地址保存服务不可用')

  const editSave = harness('xiaochengxu-source/pages/addOrEditAddress/addOrEditAddress.vue', {
    apis: {
      addAddressBook: async () => ({ code: 1 }),
      editAddressBook: async () => { throw new Error('地址修改服务不可用') },
      delAddressBook: async () => ({ code: 1 }),
      queryAddressBookById: async () => ({ code: 1, data: {} })
    }
  })
  Object.assign(editSave.instance.form, {
    name: '测试用户', phone: '13800138000', type: 2, sex: '0', detail: '1号楼101', id: 23
  })
  editSave.instance.address = '浙江省/杭州市/西湖区'
  editSave.instance.showDel = true
  assert.equal(await editSave.instance.addAddressFun(), false)
  assert.equal(editSave.calls.redirects.length, 0)
  assert.equal(editSave.calls.toasts.at(-1).title, '地址修改服务不可用')

  const deletion = harness('xiaochengxu-source/pages/addOrEditAddress/addOrEditAddress.vue', {
    apis: {
      addAddressBook: async () => ({ code: 1 }),
      editAddressBook: async () => ({ code: 1 }),
      delAddressBook: async () => { throw new Error('地址删除服务不可用') },
      queryAddressBookById: async () => ({ code: 1, data: {} })
    }
  })
  deletion.instance.delId = 24
  assert.equal(await deletion.instance.deleteAddressFun(), false)
  assert.equal(deletion.calls.redirects.length, 0)
  assert.equal(deletion.calls.toasts.at(-1).title, '地址删除服务不可用')
})

test('remark save commits the text before returning to checkout', () => {
  const remark = harness('xiaochengxu-source/pages/remark/index.vue', {
    state: { remarkData: '已有备注' }
  })
  remark.definition.onLoad.call(remark.instance)
  assert.equal(remark.instance.remark, '已有备注')
  remark.instance.remark = '门口放置即可'
  remark.instance.handleSaveRemark()

  assert.deepEqual(remark.calls.mutations.at(-1), ['setRemark', '门口放置即可'])
  assert.equal(remark.calls.redirects.at(-1).url, '/pages/order/index')
  expectAll(read('xiaochengxu-source/pages/remark/index.vue'), ['保存备注', 'maxlength="50"'])
})

test('all checkout task pages use the CloudMeal header without tabbars or yellow accents', () => {
  const pages = [
    'xiaochengxu-source/pages/order/index.vue',
    'xiaochengxu-source/pages/address/address.vue',
    'xiaochengxu-source/pages/addOrEditAddress/addOrEditAddress.vue',
    'xiaochengxu-source/pages/remark/index.vue'
  ].map(read).join('\n')
  expectAll(pages, ['还没有收货地址', '新增收货地址', '保存地址', '删除地址', '保存备注'])
  expectNone(pages, ['<app-tabbar', '<uni-nav-bar', '#ffc200', '#FFC200', 'linear-gradient'])
})
