const test = require('node:test')
const vm = require('node:vm')
const { assert, read, expectAll, expectNone } = require('./helpers.cjs')

function exportedModule(relativePath, exportNames, context = {}) {
  const source = read(relativePath)
    .replace(/^import .*$/gm, '')
    .replace(/export function /g, 'function ')
    .replace(/export async function /g, 'async function ')
    .replace(/export const /g, 'const ')
  const sandbox = { module: { exports: {} }, console: { log() {} }, ...context }
  vm.runInNewContext(`${source}\nmodule.exports = { ${exportNames.join(', ')} }`, sandbox)
  return sandbox.module.exports
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

function mount(definition, { state = {}, props = {}, emit = () => {}, refs = {} } = {}) {
  const commits = []
  const instance = { ...(definition.data ? definition.data() : {}), ...props }
  instance.$store = {
    state,
    commit(name, payload) {
      commits.push([name, payload])
      const stateKeys = {
        setToken: 'token',
        setBaseUserInfo: 'baseUserInfo',
        setProfileCompleted: 'profileCompleted',
        setProfilePromptSkipped: 'profilePromptSkipped'
      }
      if (stateKeys[name]) state[stateKeys[name]] = payload
    }
  }
  instance.$emit = emit
  instance.$refs = refs
  instance.$nextTick = callback => callback.call(instance)
  Object.entries(definition.methods || {}).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  Object.entries(definition.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(instance, name, { get: getter.bind(instance) })
  })
  return { instance, commits }
}

function makeStore(initial = {}) {
  const state = {
    token: '',
    baseUserInfo: '',
    profileCompleted: null,
    profilePromptSkipped: false,
    ...initial
  }
  const commits = []
  const keys = {
    setToken: 'token',
    setBaseUserInfo: 'baseUserInfo',
    setProfileCompleted: 'profileCompleted',
    setProfilePromptSkipped: 'profilePromptSkipped'
  }
  return {
    state,
    commits,
    commit(name, payload) {
      commits.push([name, payload])
      if (keys[name]) state[keys[name]] = payload
    }
  }
}

function sessionHarness({ initialState, stored = {}, getUserProfile } = {}) {
  const storage = { ...stored }
  const removed = []
  const store = makeStore(initialState)
  const session = exportedModule(
    'xiaochengxu-source/utils/session.js',
    ['normalizeProfile', 'persistSession', 'restoreSession', 'clearSession'],
    {
      getUserProfile: getUserProfile || (async () => ({
        code: 1,
        data: { name: '远端用户', avatar: 'https://img/remote.png', profileCompleted: true }
      })),
      uni: {
        getStorageSync: key => storage[key],
        setStorageSync: (key, value) => { storage[key] = value },
        removeStorageSync: key => {
          removed.push(key)
          delete storage[key]
        }
      }
    }
  )
  return { ...session, store, storage, removed }
}

function indexHarness(overrides = {}) {
  const events = []
  const storageWrites = []
  const state = {
    token: '',
    baseUserInfo: '',
    profileCompleted: null,
    profilePromptSkipped: false,
    shopInfo: {},
    shopPhone: '',
    orderListData: [],
    deliveryFee: 0,
    ...overrides.state
  }
  const uni = {
    login(options) {
      events.push('uni.login:start')
      const response = overrides.loginResponse || { errMsg: 'login:ok', code: 'CODE-1' }
      queueMicrotask(() => {
        if (response.fail) options.fail(response.fail)
        else {
          events.push('uni.login:success')
          options.success(response)
        }
      })
    },
    getLocation(options) {
      events.push('location:start')
      if (overrides.locationThrows) throw overrides.locationThrows
      queueMicrotask(() => {
        if (overrides.locationError) options.fail(overrides.locationError)
        else options.success({ longitude: 121.1, latitude: 31.2 })
      })
    },
    showToast() {},
    showModal() {},
    onNetworkStatusChange() {},
    offNetworkStatusChange() {},
    getMenuButtonBoundingClientRect: () => ({ top: 0, height: 0 })
  }
  const definition = componentOptions('xiaochengxu-source/pages/index/index.js', {
    Phone: {}, CloudmealHeader: {}, AppTabbar: {}, StatePanel: {}, popMask: {}, popCart: {},
    dishDetail: {}, ProfileEditor: {}, baseUrl: 'http://example.test', getErrorMessage: () => 'error',
    userLogin: overrides.userLogin || (async ({ code }) => {
      events.push(`api.login:${code}`)
      return {
        code: 1,
        data: {
          token: 'jwt-token', name: '小餐', avatar: 'https://img/a.png',
          profileCompleted: false, deliveryFee: 2, shopName: '餐云', shopAddress: '测试路', shopId: 7
        }
      }
    }),
    persistSession: overrides.persistSession || ((store, data) => {
      const profile = { nickName: data.name, avatarUrl: data.avatar }
      store.commit('setToken', data.token)
      store.commit('setBaseUserInfo', profile)
      store.commit('setProfileCompleted', data.profileCompleted)
      storageWrites.push(['cloudmeal.token', data.token])
      storageWrites.push(['cloudmeal.profile', profile])
      return profile
    }),
    uploadAvatar: overrides.uploadAvatar || (async path => ({ data: { url: `https://img/${path}` } })),
    updateUserProfile: overrides.updateUserProfile || (async params => ({ code: 1, data: params })),
    getCategoryList: async () => ({ code: 1, data: [] }),
    dishListByCategoryId: async () => ({ code: 1, data: [] }),
    querySetmeaList: async () => ({ code: 1, data: [] }),
    getShoppingCartList: async () => ({ code: 1, data: [] }),
    newAddShoppingCartAdd: async () => ({ code: 1 }),
    newShoppingCartSub: async () => ({ code: 1 }),
    delShoppingCart: async () => ({ code: 1 }),
    querySetmealDishById: async () => ({ code: 1, data: [] }),
    getShopStatus: async () => ({ code: 1, data: 1 }),
    getMerchantInfo: async () => ({ code: 1, data: {} }),
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, payload => {
        const keys = {
          setToken: 'token', setBaseUserInfo: 'baseUserInfo',
          setProfileCompleted: 'profileCompleted', setProfilePromptSkipped: 'profilePromptSkipped'
        }
        if (keys[name]) state[keys[name]] = payload
      }]))
    },
    mapState(names) {
      return Object.fromEntries(names.map(name => [name, function () { return this.$store.state[name] }]))
    },
    uni,
    wx: { getMenuButtonBoundingClientRect: () => ({ height: 0 }) },
    process: { env: {} },
    getCurrentPages: () => []
  })
  const { instance } = mount(definition, { state })
  instance.init = overrides.init || (async () => { events.push('menu:init') })
  return { definition, instance, state, events, storageWrites, uni }
}

test('session persistence uses exact keys and normalizes backend profile fields', () => {
  const harness = sessionHarness()
  harness.persistSession(harness.store, {
    token: 'jwt-token',
    name: '小餐',
    avatar: 'https://img/a.png',
    profileCompleted: true
  })

  assert.equal(harness.storage['cloudmeal.token'], 'jwt-token')
  assert.deepEqual(JSON.parse(JSON.stringify(harness.storage['cloudmeal.profile'])), {
    nickName: '小餐',
    avatarUrl: 'https://img/a.png'
  })
  assert.deepEqual(JSON.parse(JSON.stringify(harness.store.state.baseUserInfo)), {
    nickName: '小餐',
    avatarUrl: 'https://img/a.png'
  })
  assert.equal(harness.storage.profilePromptSkipped, undefined)
})

test('session restore commits cache first and replaces it with a verified profile', async () => {
  const events = []
  const harness = sessionHarness({
    stored: {
      'cloudmeal.token': 'cached-token',
      'cloudmeal.profile': { nickName: '缓存用户', avatarUrl: 'cached.png' }
    },
    getUserProfile: async () => {
      events.push('api.profile')
      assert.equal(harness.store.state.token, 'cached-token')
      assert.equal(harness.store.state.baseUserInfo.nickName, '缓存用户')
      return {
        code: 1,
        data: { name: '已验证用户', avatar: 'verified.png', profileCompleted: false }
      }
    }
  })

  await harness.restoreSession(harness.store)

  assert.deepEqual(events, ['api.profile'])
  assert.deepEqual(JSON.parse(JSON.stringify(harness.store.state.baseUserInfo)), {
    nickName: '已验证用户', avatarUrl: 'verified.png'
  })
  assert.equal(harness.store.state.profileCompleted, false)
  assert.equal(harness.storage['cloudmeal.profile'].nickName, '已验证用户')
})

test('session restore clears token and profile together when verification returns 401', async () => {
  const harness = sessionHarness({
    stored: {
      'cloudmeal.token': 'expired-token',
      'cloudmeal.profile': { nickName: '过期用户', avatarUrl: 'old.png' }
    },
    getUserProfile: async () => { throw { code: 401, message: 'expired' } }
  })

  await harness.restoreSession(harness.store)

  assert.equal(harness.store.state.token, '')
  assert.equal(harness.store.state.baseUserInfo, '')
  assert.deepEqual(harness.removed, ['cloudmeal.token', 'cloudmeal.profile'])
})

test('App launch awaits the shared session restore helper', async () => {
  const events = []
  const definition = componentOptions('xiaochengxu-source/App.vue', {
    restoreSession: async store => {
      events.push(['restore', store])
      await Promise.resolve()
      events.push(['restored', store])
    }
  })
  const store = { state: {} }
  await definition.onLaunch.call({ $store: store })
  assert.deepEqual(events, [['restore', store], ['restored', store]])
})

test('login awaits a non-empty uni.login code before calling the backend', async () => {
  const harness = indexHarness()

  await harness.instance.loginAndInitialize()

  assert.deepEqual(harness.events.slice(0, 3), [
    'uni.login:start',
    'uni.login:success',
    'api.login:CODE-1'
  ])
  assert.equal(harness.state.token, 'jwt-token')
  assert.deepEqual(harness.state.baseUserInfo, {
    nickName: '小餐', avatarUrl: 'https://img/a.png'
  })
  assert.equal(harness.instance.profileEditorVisible, true)
})

test('login rejects fail, non-ok, and empty-code responses instead of hanging', { timeout: 200 }, async () => {
  for (const loginResponse of [
    { fail: { errMsg: 'login:fail denied' } },
    { errMsg: 'login:fail' },
    { errMsg: 'login:ok', code: '' }
  ]) {
    const harness = indexHarness({ loginResponse })
    await assert.rejects(harness.instance.loginSync())
  }
})

test('geolocation failure never blocks or clears an authenticated login', async () => {
  const harness = indexHarness({ locationError: { errMsg: 'getLocation:fail denied' } })

  await harness.instance.loginAndInitialize()
  await new Promise(resolve => setImmediate(resolve))

  assert.equal(harness.state.token, 'jwt-token')
  assert.ok(harness.events.indexOf('location:start') > harness.events.indexOf('api.login:CODE-1'))
  assert.ok(harness.events.includes('menu:init'))
})

test('profile skip is runtime-only and suppresses repeat prompts in the same store', () => {
  const firstPage = indexHarness({ state: { profileCompleted: false } })
  firstPage.instance.profileEditorVisible = true
  firstPage.instance.skipProfileEditor()

  assert.equal(firstPage.state.profilePromptSkipped, true)
  assert.equal(firstPage.instance.profileEditorVisible, false)
  assert.deepEqual(firstPage.storageWrites, [])

  const nextDefinition = firstPage.definition
  const nextPage = mount(nextDefinition, { state: firstPage.state }).instance
  nextPage.syncProfileEditorVisibility()
  assert.equal(nextPage.profileEditorVisible, false)

  const freshRuntime = indexHarness({ state: {
    token: 'fresh-token', profileCompleted: false, profilePromptSkipped: false
  } })
  freshRuntime.instance.syncProfileEditorVisibility()
  assert.equal(freshRuntime.instance.profileEditorVisible, true)
})

test('a profile restored after page creation reactively requires onboarding', () => {
  const harness = indexHarness({ state: {
    token: '', profileCompleted: null, profilePromptSkipped: false
  } })
  assert.equal(harness.instance.shouldPromptProfileEditor, false)

  harness.state.token = 'restored-token'
  harness.state.profileCompleted = false
  assert.equal(harness.instance.shouldPromptProfileEditor, true)

  harness.state.profilePromptSkipped = true
  assert.equal(harness.instance.shouldPromptProfileEditor, false)
  expectAll(read('xiaochengxu-source/pages/index/index.vue'), [
    'v-if="profileEditorVisible || shouldPromptProfileEditor"'
  ])
})

test('profile editor emits avatar, nickname, save, skip, and close contracts without APIs', () => {
  const events = []
  const definition = componentOptions('xiaochengxu-source/components/profile-editor/profile-editor.vue')
  const { instance } = mount(definition, {
    props: {
      profile: { nickName: '旧昵称', avatarUrl: 'old.png' },
      allowSkip: true,
      saving: false
    },
    emit: (name, payload) => events.push([name, payload])
  })
  if (definition.created) definition.created.call(instance)
  instance.handleChooseAvatar({ detail: { avatarUrl: 'temp/avatar.jpg' } })
  instance.handleNicknameInput({ detail: { value: '新昵称' } })
  instance.submit()
  instance.skip()
  instance.close()

  assert.deepEqual(JSON.parse(JSON.stringify(events[0])), [
    'save', { name: '新昵称', tempAvatarPath: 'temp/avatar.jpg', currentAvatar: 'old.png' }
  ])
  assert.deepEqual(events.slice(1), [['skip', undefined], ['close', undefined]])
  const source = read('xiaochengxu-source/components/profile-editor/profile-editor.vue')
  expectAll(source, ['open-type="chooseAvatar"', 'type="nickname"', '保存', '暂时跳过'])
  expectNone(source, ['uploadAvatar(', 'updateUserProfile('])
})

test('index profile save uploads a temp avatar before PUT and stays retryable on failure', async () => {
  const sequence = []
  let attempts = 0
  const harness = indexHarness({
    uploadAvatar: async path => {
      sequence.push(`upload:${path}`)
      return { code: 1, data: { url: 'https://img/new.png' } }
    },
    updateUserProfile: async params => {
      sequence.push(`put:${params.name}:${params.avatar}`)
      attempts += 1
      if (attempts === 1) throw { code: 500, message: 'retry me' }
      return { code: 1, data: { ...params, profileCompleted: true } }
    }
  })
  harness.instance.profileEditorVisible = true

  assert.equal(await harness.instance.saveProfile({
    name: '新昵称', tempAvatarPath: 'temp.jpg', currentAvatar: 'old.png'
  }), false)
  assert.equal(harness.instance.profileEditorVisible, true)
  assert.equal(harness.instance.profileSaving, false)

  assert.equal(await harness.instance.saveProfile({
    name: '新昵称', tempAvatarPath: 'temp.jpg', currentAvatar: 'old.png'
  }), true)
  assert.equal(harness.instance.profileEditorVisible, false)
  assert.deepEqual(sequence, [
    'upload:temp.jpg', 'put:新昵称:https://img/new.png',
    'upload:temp.jpg', 'put:新昵称:https://img/new.png'
  ])
})

test('uploadAvatar uses the authenticated multipart contract and safely rejects invalid JSON', async () => {
  let options
  const { uploadAvatar } = exportedModule('xiaochengxu-source/utils/upload.js', ['uploadAvatar'], {
    baseUrl: 'http://example.test',
    store: { state: { token: 'token-123' } },
    clearSession() {},
    uni: { uploadFile(value) { options = value } }
  })
  const promise = uploadAvatar('temp/avatar.jpg')
  assert.equal(options.url, 'http://example.test/user/user/avatar')
  assert.equal(options.filePath, 'temp/avatar.jpg')
  assert.equal(options.name, 'file')
  assert.equal(options.header.authentication, 'token-123')
  options.success({ statusCode: 200, data: '{not-json' })
  await assert.rejects(promise, error => {
    assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'raw'])
    assert.equal(error.code, 'INVALID_RESPONSE')
    return true
  })
})

test('uploadAvatar returns the backend URL payload and clears the full session on 401', async () => {
  let options
  let clearedStore
  const store = { state: { token: 'token-123' } }
  const { uploadAvatar } = exportedModule('xiaochengxu-source/utils/upload.js', ['uploadAvatar'], {
    baseUrl: 'http://example.test',
    store,
    clearSession(value) { clearedStore = value },
    uni: { uploadFile(value) { options = value } }
  })

  const success = uploadAvatar('temp/avatar.jpg')
  options.success({ statusCode: 200, data: JSON.stringify({
    code: 1, data: 'https://oss/avatar.png'
  }) })
  assert.equal((await success).data, 'https://oss/avatar.png')

  const unauthorized = uploadAvatar('temp/avatar.jpg')
  const response = { statusCode: 401, data: JSON.stringify({ code: 401, msg: 'expired' }) }
  options.success(response)
  await assert.rejects(unauthorized, error => {
    assert.equal(error.code, 401)
    assert.equal(error.message, 'expired')
    assert.strictEqual(error.raw, response)
    return true
  })
  assert.strictEqual(clearedStore, store)
})

test('uploadAvatar rejects business and transport failures with request-compatible shapes', async () => {
  let options
  const { uploadAvatar } = exportedModule('xiaochengxu-source/utils/upload.js', ['uploadAvatar'], {
    baseUrl: 'http://example.test',
    store: { state: { token: 'token-123' } },
    clearSession() {},
    uni: { uploadFile(value) { options = value } }
  })

  const business = uploadAvatar('temp/avatar.jpg')
  const businessResponse = {
    statusCode: 200,
    data: JSON.stringify({ code: 0, msg: '图片不可用' })
  }
  options.success(businessResponse)
  await assert.rejects(business, error => {
    assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'raw'])
    assert.equal(error.code, 0)
    assert.equal(error.message, '图片不可用')
    assert.strictEqual(error.raw, businessResponse)
    return true
  })

  const transport = uploadAvatar('temp/avatar.jpg')
  const networkError = { errMsg: 'uploadFile:fail offline' }
  options.fail(networkError)
  await assert.rejects(transport, error => {
    assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'raw'])
    assert.equal(error.code, 'NETWORK_ERROR')
    assert.strictEqual(error.raw, networkError)
    return true
  })
})

test('My profile header opens a non-skippable shared editor and refreshes after save', async () => {
  const sequence = []
  const state = {
    baseUserInfo: { nickName: '旧昵称', avatarUrl: 'old.png' },
    token: 'token-123',
    shopPhone: ''
  }
  const definition = componentOptions('xiaochengxu-source/pages/my/my.vue', {
    HeadInfo: {}, OrderList: {}, CloudmealHeader: {}, AppTabbar: {}, ProfileEditor: {},
    DEFAULT_AVATAR: 'fallback.png', DEFAULT_NICKNAME: '微信用户',
    getErrorMessage: error => error.message,
    statusWord: () => '', getOvertime: () => 30,
    getOrderPage: async () => ({ code: 1, data: { records: [], total: 0 } }),
    repetitionOrder: async () => ({ code: 1 }), delShoppingCart: async () => ({ code: 1 }),
    uploadAvatar: async path => {
      sequence.push(`upload:${path}`)
      return { code: 1, data: { url: 'new.png' } }
    },
    updateUserProfile: async params => {
      sequence.push(`put:${params.name}:${params.avatar}`)
      return { code: 1, data: { ...params, profileCompleted: true } }
    },
    persistSession: (store, data) => {
      store.commit('setBaseUserInfo', { nickName: data.name, avatarUrl: data.avatar })
    },
    mapMutations(names) {
      return Object.fromEntries(names.map(name => [name, payload => {
        if (name === 'setBaseUserInfo') state.baseUserInfo = payload
      }]))
    },
    uni: {
      showToast() {}, makePhoneCall() {}, redirectTo() {}, navigateTo() {}, navigateBack() {},
      reLaunch() {}, getSystemInfo() {}, upx2px: value => value
    },
    getCurrentPages: () => []
  })
  const { instance } = mount(definition, { state })
  definition.onLoad.call(instance)
  instance.openProfileEditor()
  assert.equal(instance.profileEditorVisible, true)

  await instance.saveProfile({ name: '新昵称', tempAvatarPath: 'temp.jpg', currentAvatar: 'old.png' })
  assert.deepEqual(sequence, ['upload:temp.jpg', 'put:新昵称:new.png'])
  assert.equal(instance.nickName, '新昵称')
  assert.equal(instance.psersonUrl, 'new.png')
  assert.equal(instance.profileEditorVisible, false)

  const source = read('xiaochengxu-source/pages/my/my.vue')
  expectAll(source, ['@edit-profile="openProfileEditor"', ':allow-skip="false"'])
  expectNone(source, ['@skip='])
})

test('headInfo emits edit-profile when the profile card is tapped', () => {
  const events = []
  const definition = componentOptions('xiaochengxu-source/pages/my/components/headInfo.vue')
  const { instance } = mount(definition, { emit: name => events.push(name) })
  instance.editProfile()
  assert.deepEqual(events, ['edit-profile'])
  expectAll(read('xiaochengxu-source/pages/my/components/headInfo.vue'), [
    '@click="editProfile"', "$emit('edit-profile')"
  ])
})
