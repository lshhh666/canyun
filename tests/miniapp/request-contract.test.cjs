const test = require('node:test')
const { read, expectAll, expectNone } = require('./helpers.cjs')

test('local development uses the backend on port 8080', () => {
  const env = read('xiaochengxu-source/utils/env.js')
  expectAll(env, ["local: 'http://localhost:8080'", 'export const baseUrl'])
  expectNone(env, ['cpolar.top', 'reggie-dev.itheima.net'])
})

test('request failures expose a stable error object and clear expired login', () => {
  const request = read('xiaochengxu-source/utils/request.js')
  expectAll(request, ["code: 'NETWORK_ERROR'", 'message:', 'raw:', 'setToken', '401'])
})

test('core backend paths and methods remain unchanged', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  expectAll(api, [
    "url: '/user/user/login'",
    "url: '/user/category/list'",
    "url: '/user/dish/list'",
    "url: '/user/setmeal/list'",
    "url: '/user/shoppingCart/list'",
    "url: '/user/shoppingCart/add'",
    "url: '/user/shoppingCart/sub'",
    "url: '/user/shoppingCart/clean'",
    "url: '/user/order/submit'",
    "url: '/user/order/historyOrders'",
    "url: '/user/addressBook/list'",
    "url: '/user/addressBook/default'",
    'url: `/user/order/orderDetail/${params}`',
    'url: `/user/order/repetition/${params}`',
    'url: `/user/shop/status`'
  ])
})
