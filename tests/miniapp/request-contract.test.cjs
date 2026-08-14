const test = require('node:test')
const vm = require('node:vm')
const { assert, read, expectAll, expectNone } = require('./helpers.cjs')

function loadRequest(overrides = {}) {
  const source = read('xiaochengxu-source/utils/request.js')
    .replace(/^import .*$/gm, '')
    .replace('export function request', 'function request')
  const commits = []
  const removedStorageKeys = []
  let options
  const context = {
    baseUrl: 'http://example.test',
    store: {
      state: { token: 'token-123' },
      commit: (...args) => commits.push(args)
    },
    uni: {
      request: requestOptions => {
        options = requestOptions
      },
      removeStorageSync: key => removedStorageKeys.push(key)
    },
    clearSession: overrides.clearSession || (targetStore => {
      targetStore.commit('setToken', '')
      targetStore.commit('setBaseUserInfo', '')
      context.uni.removeStorageSync('cloudmeal.token')
      context.uni.removeStorageSync('cloudmeal.profile')
    }),
    module: { exports: {} }
  }

  vm.runInNewContext(`${source}\nmodule.exports = { request }`, context)
  return {
    request: context.module.exports.request,
    getOptions: () => options,
    commits,
    removedStorageKeys
  }
}

function expectRejected(promise, callback) {
  return assert.rejects(promise, error => {
    callback(error)
    return true
  })
}

test('local development uses the backend on port 8080', () => {
  const env = read('xiaochengxu-source/utils/env.js')
  expectAll(env, ["local: 'http://localhost:8080'", 'export const baseUrl'])
  expectNone(env, ['cpolar.top', 'reggie-dev.itheima.net'])
})

test('request forwards URL, params, method, and authentication header', async () => {
  const harness = loadRequest()
  const params = { dishId: 7 }
  const promise = harness.request({ url: '/user/dish/list', params, method: 'POST' })
  const options = harness.getOptions()

  assert.equal(options.url, 'http://example.test/user/dish/list')
  assert.strictEqual(options.data, params)
  assert.equal(options.method, 'POST')
  assert.equal(options.header.Accept, 'application/json')
  assert.equal(options.header['Content-Type'], 'application/json')
  assert.equal(options.header.authentication, 'token-123')

  const data = { code: 1 }
  options.success({ statusCode: 200, data })
  assert.strictEqual(await promise, data)
})

test('request resolves the original payload for success codes 1 and 200', async () => {
  for (const code of [1, 200]) {
    const harness = loadRequest()
    const promise = harness.request({ url: '/success' })
    const data = { code, value: `success-${code}` }
    harness.getOptions().success({ statusCode: 200, data })
    assert.strictEqual(await promise, data)
  }
})

test('request rejects business code 0 without converting it to HTTP status', async () => {
  const harness = loadRequest()
  const promise = harness.request({ url: '/business-error' })
  const response = { statusCode: 200, data: { code: 0, msg: 'business error' } }
  harness.getOptions().success(response)

  await expectRejected(promise, error => {
    assert.equal(error.code, 0)
    assert.equal(error.message, 'business error')
    assert.strictEqual(error.raw, response)
  })
})

test('network failures reject a stable error object', async () => {
  const harness = loadRequest()
  const promise = harness.request({ url: '/offline' })
  const networkError = { errMsg: 'request:fail' }
  harness.getOptions().fail(networkError)

  await expectRejected(promise, error => {
    assert.equal(error.code, 'NETWORK_ERROR')
    assert.equal(error.message, '网络连接失败，请检查网络后重试')
    assert.strictEqual(error.raw, networkError)
  })
})

test('HTTP and business 401 responses clear login state', async () => {
  for (const response of [
    { statusCode: 401, data: { code: 500, msg: 'unauthorized' } },
    { statusCode: 200, data: { code: 401, msg: 'unauthorized' } }
  ]) {
    const harness = loadRequest()
    const promise = harness.request({ url: '/protected' })
    harness.getOptions().success(response)

    await expectRejected(promise, error => {
      assert.strictEqual(error.raw, response)
    })
    assert.deepEqual(harness.commits, [['setToken', ''], ['setBaseUserInfo', '']])
    assert.deepEqual(harness.removedStorageKeys, ['cloudmeal.token', 'cloudmeal.profile'])
  }
})

test('HTTP 401 wins over a misleading success payload', async () => {
  const harness = loadRequest()
  const promise = harness.request({ url: '/protected' })
  const response = { statusCode: 401, data: { code: 1, data: { secret: true } } }
  harness.getOptions().success(response)

  await expectRejected(promise, error => {
    assert.equal(error.code, 401)
    assert.strictEqual(error.raw, response)
  })
  assert.deepEqual(harness.removedStorageKeys, ['cloudmeal.token', 'cloudmeal.profile'])
})

test('request still rejects the original 401 when cleanup unexpectedly throws', async () => {
  const cleanupError = new Error('cleanup failed')
  const harness = loadRequest({ clearSession() { throw cleanupError } })
  const promise = harness.request({ url: '/protected' })
  const response = { statusCode: 401, data: { code: 401, msg: 'expired' } }

  assert.doesNotThrow(() => harness.getOptions().success(response))
  await expectRejected(promise, error => {
    assert.equal(error.code, 401)
    assert.equal(error.message, 'expired')
    assert.strictEqual(error.raw, response)
  })
})

test('core backend paths and methods remain unchanged', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  const pairs = [
    ["'/user/user/login'", 'POST'],
    ["'/user/user/profile'", 'PUT'],
    ["'/user/category/list'", 'GET'],
    ["'/user/dish/list'", 'GET'],
    ["'/user/setmeal/list'", 'GET'],
    ["'/user/shoppingCart/list'", 'GET'],
    ["'/user/shoppingCart/add'", 'POST'],
    ["'/user/shoppingCart/sub'", 'POST'],
    ["'/user/shoppingCart/clean'", 'DELETE'],
    ["'/user/order/submit'", 'POST'],
    ["'/user/order/preview'", 'POST'],
    ["'/user/order/historyOrders'", 'GET'],
    ["'/user/addressBook/list'", 'GET'],
    ["'/user/addressBook/default'", 'PUT'],
    ["'/user/addressBook/default'", 'GET'],
    ['`/user/order/orderDetail/${params}`', 'GET'],
    ['`/user/order/repetition/${params}`', 'POST'],
    ['`/user/shop/status`', 'GET'],
    ["'/user/shop/info'", 'GET']
  ]

  pairs.forEach(([url, method]) => {
    const escapedUrl = url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const pattern = new RegExp(`url:\\s*${escapedUrl}[\\s\\S]{0,120}?method:\\s*'${method}'`)
    assert.match(api, pattern, `missing URL/method pair: ${url} ${method}`)
  })
  expectNone(api, ['/user/order/getEstimatedDeliveryTime', '/user/shop/getMerchantInfo'])
})

test('profile API wrappers preserve the exact GET-default and PUT contracts', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  assert.match(api, /getUserProfile\s*=\s*\(\)\s*=>\s*request\(\{\s*url:\s*'\/user\/user\/profile'\s*\}\)/)
  assert.match(api, /updateUserProfile\s*=\s*params\s*=>\s*request\(\{[\s\S]{0,120}?method:\s*'PUT'[\s\S]{0,80}?params/)
})
