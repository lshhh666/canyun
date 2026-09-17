const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const assert = require('node:assert/strict')

const root = path.resolve(__dirname, '../..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

function componentOptions(sendAiChatMessage, confirmAiChatAction = async () => ({}),
  uni = {}, getCurrentPages = () => [{}, {}], getRecentAiChatHistory = async () => ({ data: {} }),
  closeAiChatSession = async () => ({}), submitAiChatFeedback = async () => ({})) {
  const source = read('xiaochengxu-source/pages/aiChat/index.vue')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/import CloudmealHeader[^\n]*\n/, 'const CloudmealHeader = {}\n')
    .replace(/import \{[^}]*sendAiChatMessage[^}]*\}[^\n]*\n/, '')
    .replace('export default', 'return')
  return Function('sendAiChatMessage', 'confirmAiChatAction', 'getRecentAiChatHistory', 'closeAiChatSession',
    'submitAiChatFeedback',
    'uni', 'getCurrentPages', script)(
    sendAiChatMessage,
    confirmAiChatAction,
    getRecentAiChatHistory,
    closeAiChatSession,
    submitAiChatFeedback,
    uni,
    getCurrentPages
  )
}

function chatHarness(sendAiChatMessage, confirmAiChatAction, getRecentAiChatHistory, closeAiChatSession,
  submitAiChatFeedback, uni = {}) {
  const options = componentOptions(sendAiChatMessage, confirmAiChatAction,
    uni, () => [{}, {}], getRecentAiChatHistory, closeAiChatSession, submitAiChatFeedback)
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
  assert.match(home, /@touchmove\.stop\.prevent="moveAiEntry"/)
  assert.match(homeScript, /uni\.navigateTo\(\{ url: '\/pages\/aiChat\/index' \}\)/)
})

test('首页客服入口可拖动、不会越界且拖动结束不误开聊天页', () => {
  const source = read('xiaochengxu-source/pages/index/index.js')
  assert.match(source, /initializeAiEntryPosition\(\)/)
  assert.match(source, /Math\.min\(maximumLeft, Math\.max\(0,/)
  assert.match(source, /Date\.now\(\) - this\.aiEntryLastDragAt < 300/)
})

test('AI 客服请求只发送消息和会话ID，用户身份仍由 JWT 请求封装提供', () => {
  const api = read('xiaochengxu-source/pages/api/api.js')
  const page = read('xiaochengxu-source/pages/aiChat/index.vue')

  assert.match(api, /url: '\/user\/ai\/chat'/)
  assert.match(api, /method: 'POST'/)
  assert.match(api, /params: \{ message, sessionId \}/)
  assert.match(api, /url: `\/user\/ai\/actions\/\$\{actionId\}\/confirm`/)
  assert.match(api, /url: '\/user\/ai\/session\/recent'/)
  assert.match(api, /url: `\/user\/ai\/session\/\$\{sessionId\}\/close`/)
  assert.match(api, /url: '\/user\/ai\/session\/feedback'/)
  assert.match(api, /params: \{ sessionId, result \}/)
  const confirmBlock = api.match(/export const confirmAiChatAction[\s\S]*?\n\}/)[0]
  assert.doesNotMatch(confirmBlock, /orderId|params/)
  assert.doesNotMatch(page, /userId/)
})

test('新对话会先关闭后端会话，再清空本地上下文和待确认按钮', async () => {
  const closedSessions = []
  const chat = chatHarness(
    async () => ({}),
    async () => ({}),
    async () => ({ data: {} }),
    async sessionId => { closedSessions.push(sessionId) }
  )
  chat.sessionId = 25
  chat.messages.push({
    id: 'ai-message-1',
    role: 'assistant',
    content: '确定取消吗？',
    action: { actionId: 35, label: '确认取消', consumed: false }
  })

  await chat.startNewConversation()

  assert.deepEqual(closedSessions, [25])
  assert.equal(chat.sessionId, null)
  assert.equal(chat.messages.length, 1)
  assert.match(chat.messages[0].content, /你好，我是云小餐/)

  chat.sending = true
  chat.sessionId = 26
  await chat.startNewConversation()
  assert.deepEqual(closedSessions, [25])
})

test('结束会话后单独提交评价，评价失败也不影响开始新对话', async () => {
  const calls = []
  const uni = {
    showActionSheet: options => options.success({ tapIndex: 1 }),
    showToast: options => calls.push(['toast', options.title])
  }
  const chat = chatHarness(
    async () => ({}),
    async () => ({}),
    async () => ({ data: {} }),
    async sessionId => { calls.push(['close', sessionId]) },
    async (sessionId, result) => {
      calls.push(['feedback', sessionId, result])
      throw new Error('save failed')
    },
    uni
  )
  chat.sessionId = 25

  await chat.startNewConversation()

  assert.deepEqual(calls.slice(0, 2), [
    ['close', 25],
    ['feedback', 25, 'UNSOLVED']
  ])
  assert.equal(chat.sessionId, null)
  assert.equal(chat.messages.length, 1)
  assert.ok(calls.some(call => call[0] === 'toast' && call[1] === '已开始新对话'))
  assert.ok(calls.some(call => call[0] === 'toast' && call[1] === '评价暂未提交'))
})

test('评价请求未返回时也立即解锁新会话', async () => {
  const chat = chatHarness(
    async () => ({}),
    async () => ({}),
    async () => ({ data: {} }),
    async () => ({}),
    () => new Promise(() => {}),
    { showActionSheet: options => options.success({ tapIndex: 0 }) }
  )
  chat.sessionId = 25

  await chat.startNewConversation()

  assert.equal(chat.sessionId, null)
  assert.equal(chat.historyLoading, false)
  assert.equal(chat.sending, false)
})

test('关闭评价选择框不会误结束当前会话', async () => {
  const closedSessions = []
  const chat = chatHarness(
    async () => ({}),
    async () => ({}),
    async () => ({ data: {} }),
    async sessionId => { closedSessions.push(sessionId) },
    async () => ({}),
    { showActionSheet: options => options.fail({ errMsg: 'cancel' }) }
  )
  chat.sessionId = 25

  await chat.startNewConversation()

  assert.deepEqual(closedSessions, [])
  assert.equal(chat.sessionId, 25)
  assert.equal(chat.historyLoading, false)
})

test('重新进入页面会恢复最近会话、历史消息和未过期确认按钮', async () => {
  const chat = chatHarness(
    async () => ({}),
    async () => ({}),
    async () => ({
      data: {
        sessionId: 25,
        processing: false,
        messages: [
          { messageId: 44, role: 'user', content: '帮我取消', sequenceNo: 3 },
          {
            messageId: 45,
            role: 'assistant',
            content: '确定要取消吗？',
            sequenceNo: 4,
            action: { actionId: 35, actionType: 'CANCEL_ORDER', label: '确认取消' }
          }
        ]
      }
    })
  )

  const options = componentOptions(async () => ({}), async () => ({}),
    {}, () => [{}, {}], async () => ({ data: {} }))
  await chat.loadRecentHistory()

  assert.equal(chat.sessionId, 25)
  assert.equal(chat.sending, false)
  assert.equal(chat.messages.length, 3)
  assert.equal(chat.messages[1].content, '帮我取消')
  assert.equal(chat.messages[2].action.actionId, 35)
  assert.equal(chat.messages[2].action.consumed, false)
  assert.equal(typeof options.onLoad, 'function')
  assert.equal(typeof options.onUnload, 'function')
})

test('发送期间锁定输入并展示服务端 answer', () => {
  const page = read('xiaochengxu-source/pages/aiChat/index.vue')

  assert.match(page, /maxlength="100"/)
  assert.match(page, />\s*新对话\s*<\/button>/)
  assert.ok(page.indexOf('class="conversation-tools"') < page.indexOf('<scroll-view'))
  assert.match(page, /\.conversation-tools \{[\s\S]*?position: fixed/)
  assert.match(page, /:disabled="sending \|\| historyLoading"/)
  assert.match(page, /if \(!message \|\| this\.sending \|\| this\.historyLoading\) return/)
  assert.match(page, /result\.data\.answer/)
  assert.match(page, /云小餐暂时有点忙，请稍后再试/)
})

test('发送流程立即加锁、阻止重复请求并展示服务端回答', async () => {
  let resolveRequest
  const calls = []
  const request = (message, sessionId) => {
    calls.push([message, sessionId])
    return new Promise(resolve => { resolveRequest = resolve })
  }
  const chat = chatHarness(request)
  chat.inputValue = '  推荐一道菜  '

  const first = chat.sendMessage()
  const duplicate = chat.sendMessage()
  assert.equal(chat.sending, true)
  assert.deepEqual(calls, [['推荐一道菜', null]])
  assert.equal(chat.messages.filter(item => item.role === 'user').length, 1)

  resolveRequest({ data: { answer: '可以试试清淡菜品。', sessionId: 41 } })
  await Promise.all([first, duplicate])
  assert.equal(chat.sending, false)
  assert.equal(chat.sessionId, 41)
  assert.equal(chat.messages.at(-1).content, '可以试试清淡菜品。')

  chat.inputValue = '那主食呢？'
  const second = chat.sendMessage()
  assert.deepEqual(calls.at(-1), ['那主食呢？', 41])
  resolveRequest({ data: { answer: '可以看看主食分类。', sessionId: 41 } })
  await second
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

test('模型调用失败时仍保存后端返回的会话ID，下一问可以继续原会话', async () => {
  const chat = chatHarness(async () => {
    throw { raw: { data: { code: 0, data: { sessionId: 88 } } } }
  })
  chat.inputValue = '你们是否营业？'

  await chat.sendMessage()

  assert.equal(chat.sessionId, 88)
  assert.equal(chat.messages.at(-1).content, '云小餐暂时有点忙，请稍后再试。')
})

test('取消订单只展示后端签发的确认按钮，点击按钮才调用确认接口', async () => {
  const confirmations = []
  const chat = chatHarness(
    async () => ({
      data: {
        sessionId: 25,
        answer: '确定要取消这笔订单吗？',
        action: { actionId: 35, actionType: 'CANCEL_ORDER', label: '确认取消' }
      }
    }),
    async actionId => {
      confirmations.push(actionId)
      return { data: { sessionId: 25, answer: '订单尾号3346已取消。' } }
    }
  )
  chat.inputValue = '帮我取消这单'
  await chat.sendMessage()
  const prompt = chat.messages.at(-1)

  assert.equal(prompt.action.actionId, 35)
  assert.deepEqual(confirmations, [])
  await chat.confirmPendingAction(prompt)
  assert.deepEqual(confirmations, [35])
  assert.equal(prompt.action.consumed, true)
  assert.equal(chat.messages.at(-1).content, '订单尾号3346已取消。')
})

test('聊天页直接打开时返回首页，正常进入时返回上一页', () => {
  const directCalls = []
  const direct = componentOptions(async () => ({}), async () => ({}), {
    navigateBack: () => directCalls.push('back'),
    reLaunch: payload => directCalls.push(payload.url)
  }, () => [{}])
  direct.methods.goBack()
  assert.deepEqual(directCalls, ['/pages/index/index'])

  const stackCalls = []
  const stacked = componentOptions(async () => ({}), async () => ({}), {
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
  assert.match(read('xiaochengxu/common/vendor.js'), /getRecentAiChatHistory/)
  assert.match(read('xiaochengxu/common/vendor.js'), /closeAiChatSession/)
  assert.match(read('xiaochengxu/common/vendor.js'), /submitAiChatFeedback/)
  assert.match(read('xiaochengxu/pages/aiChat/index.js'), /loadRecentHistory/)
  assert.match(read('xiaochengxu/pages/aiChat/index.js'), /startNewConversation/)
  assert.match(read('xiaochengxu/pages/aiChat/index.js'), /chooseSessionFeedback/)
  assert.match(read('xiaochengxu/pages/aiChat/index.wxml'), /新对话/)
})

test('云小餐形象素材存在于小程序源码', () => {
  assert.equal(fs.existsSync(path.join(root, 'xiaochengxu-source/static/ai/yunxiaocan-mascot.png')), true)
})
