const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const assert = require('node:assert/strict')

const root = path.resolve(__dirname, '../..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

function componentOptions(sendAiChatMessage, uni = {}, getCurrentPages = () => [{}, {}]) {
  const source = read('xiaochengxu-source/pages/aiChat/index.vue')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/import CloudmealHeader[^\n]*\n/, 'const CloudmealHeader = {}\n')
    .replace(/import \{ sendAiChatMessage \}[^\n]*\n/, '')
    .replace('export default', 'return')
  return Function('sendAiChatMessage', 'uni', 'getCurrentPages', script)(
    sendAiChatMessage,
    uni,
    getCurrentPages
  )
}

function chatHarness(sendAiChatMessage) {
  const options = componentOptions(sendAiChatMessage)
  const instance = {
    ...options.data(),
    $nextTick(callback) { callback() }
  }
  Object.entries(options.methods).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  return instance
}

test('AI 客服页面已注册且首页提供云小餐入口', () => {
  const pages = read('xiaochengxu-source/pages.json')
  const home = read('xiaochengxu-source/pages/index/index.vue')
  const homeScript = read('xiaochengxu-source/pages/index/index.js')

  assert.match(pages, /"path": "pages\/aiChat\/index"/)
  assert.match(home, /yunxiaocan-mascot\.png/)
  assert.match(homeScript, /uni\.navigateTo\(\{ url: '\/pages\/aiChat\/index' \}\)/)
})

test('AI 客服请求仅发送消息并由 JWT 请求封装提供身份', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  const page = read('xiaochengxu-source/pages/aiChat/index.vue')

  assert.match(api, /url: '\/user\/ai\/chat'/)
  assert.match(api, /method: 'POST'/)
  assert.match(api, /params: \{ message \}/)
  assert.doesNotMatch(page, /userId/)
})

test('发送期间锁定输入并展示服务端 answer', () => {
  const page = read('xiaochengxu-source/pages/aiChat/index.vue')

  assert.match(page, /maxlength="100"/)
  assert.match(page, /:disabled="sending"/)
  assert.match(page, /if \(!message \|\| this\.sending\) return/)
  assert.match(page, /result\.data\.answer/)
  assert.match(page, /云小餐暂时有点忙，请稍后再试/)
})

test('发送流程立即加锁、阻止重复请求并展示服务端回答', async () => {
  let resolveRequest
  const calls = []
  const request = message => {
    calls.push(message)
    return new Promise(resolve => { resolveRequest = resolve })
  }
  const chat = chatHarness(request)
  chat.inputValue = '  推荐一道菜  '

  const first = chat.sendMessage()
  const duplicate = chat.sendMessage()
  assert.equal(chat.sending, true)
  assert.deepEqual(calls, ['推荐一道菜'])
  assert.equal(chat.messages.filter(item => item.role === 'user').length, 1)

  resolveRequest({ data: { answer: '可以试试清淡菜品。' } })
  await Promise.all([first, duplicate])
  assert.equal(chat.sending, false)
  assert.equal(chat.messages.at(-1).content, '可以试试清淡菜品。')
})

test('空回答和请求失败都使用统一友好降级，不暴露底层异常', async () => {
  const empty = chatHarness(async () => ({ data: { answer: '   ' } }))
  empty.inputValue = '你好'
  await empty.sendMessage()
  assert.equal(empty.messages.at(-1).content, '云小餐暂时有点忙，请稍后再试。')

  const failed = chatHarness(async () => { throw new Error('provider secret') })
  failed.inputValue = '你好'
  await failed.sendMessage()
  assert.equal(failed.messages.at(-1).content, '云小餐暂时有点忙，请稍后再试。')
  assert.doesNotMatch(failed.messages.at(-1).content, /provider secret/)
})

test('聊天页直接打开时返回首页，正常进入时返回上一页', () => {
  const directCalls = []
  const direct = componentOptions(async () => ({}), {
    navigateBack: () => directCalls.push('back'),
    reLaunch: payload => directCalls.push(payload.url)
  }, () => [{}])
  direct.methods.goBack()
  assert.deepEqual(directCalls, ['/pages/index/index'])

  const stackCalls = []
  const stacked = componentOptions(async () => ({}), {
    navigateBack: () => stackCalls.push('back'),
    reLaunch: payload => stackCalls.push(payload.url)
  }, () => [{}, {}])
  stacked.methods.goBack()
  assert.deepEqual(stackCalls, ['back'])
})

test('微信生成端包含聊天路由、页面文件和形象素材', () => {
  const generatedPages = JSON.parse(read('xiaochengxu/app.json')).pages
  assert.ok(generatedPages.includes('pages/aiChat/index'))
  for (const relative of ['index.js', 'index.json', 'index.wxml', 'index.wxss']) {
    assert.equal(fs.existsSync(path.join(root, 'xiaochengxu/pages/aiChat', relative)), true)
  }
  assert.equal(fs.existsSync(path.join(root, 'xiaochengxu/static/ai/yunxiaocan-mascot.png')), true)
})

test('云小餐形象素材存在于小程序源码', () => {
  assert.equal(fs.existsSync(path.join(root, 'xiaochengxu-source/static/ai/yunxiaocan-mascot.png')), true)
})
