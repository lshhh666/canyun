import { getUserProfile } from '../pages/api/api.js'

const TOKEN_STORAGE_KEY = 'cloudmeal.token'
const PROFILE_STORAGE_KEY = 'cloudmeal.profile'
let sessionRestorePromise = null

export function normalizeProfile(profile = {}) {
  return {
    nickName: profile.name ?? profile.nickName ?? '',
    avatarUrl: profile.avatar ?? profile.avatarUrl ?? ''
  }
}

function commitProfile(store, data) {
  const profile = normalizeProfile(data)
  store.commit('setBaseUserInfo', profile)
  if (typeof data.profileCompleted === 'boolean') {
    store.commit('setProfileCompleted', data.profileCompleted)
  }
  return profile
}

export function persistSession(store, loginData = {}) {
  const token = loginData.token || store.state.token || ''
  const profile = normalizeProfile(loginData)
  const stateSnapshot = {
    token: store.state.token,
    baseUserInfo: store.state.baseUserInfo,
    profileCompleted: store.state.profileCompleted
  }
  const storageKeys = typeof uni.getStorageInfoSync === 'function'
    ? uni.getStorageInfoSync().keys || []
    : []
  const storageSnapshot = {
    token: {
      exists: storageKeys.includes(TOKEN_STORAGE_KEY),
      value: uni.getStorageSync(TOKEN_STORAGE_KEY)
    },
    profile: {
      exists: storageKeys.includes(PROFILE_STORAGE_KEY),
      value: uni.getStorageSync(PROFILE_STORAGE_KEY)
    }
  }

  const restoreStorage = (key, snapshot) => {
    try {
      if (snapshot.exists) uni.setStorageSync(key, snapshot.value)
      else uni.removeStorageSync(key)
    } catch (error) {}
  }
  const restoreCommit = (name, value) => {
    try {
      store.commit(name, value)
    } catch (error) {}
  }

  try {
    uni.setStorageSync(TOKEN_STORAGE_KEY, token)
    uni.setStorageSync(PROFILE_STORAGE_KEY, profile)
    store.commit('setToken', token)
    store.commit('setBaseUserInfo', profile)
    if (typeof loginData.profileCompleted === 'boolean') {
      store.commit('setProfileCompleted', loginData.profileCompleted)
    }
    return profile
  } catch (error) {
    restoreCommit('setToken', stateSnapshot.token)
    restoreCommit('setBaseUserInfo', stateSnapshot.baseUserInfo)
    restoreCommit('setProfileCompleted', stateSnapshot.profileCompleted)
    restoreStorage(TOKEN_STORAGE_KEY, storageSnapshot.token)
    restoreStorage(PROFILE_STORAGE_KEY, storageSnapshot.profile)
    throw error
  }
}

export function clearSession(store) {
  const attempts = [
    () => store.commit('setToken', ''),
    () => store.commit('setBaseUserInfo', ''),
    () => store.commit('setProfileCompleted', null),
    () => store.commit('setProfilePromptSkipped', false),
    () => uni.removeStorageSync(TOKEN_STORAGE_KEY),
    () => uni.removeStorageSync(PROFILE_STORAGE_KEY)
  ]
  attempts.forEach(attempt => {
    try {
      attempt()
    } catch (error) {}
  })
}

export async function restoreSession(store, getProfileFn = getUserProfile) {
  const token = uni.getStorageSync(TOKEN_STORAGE_KEY)
  if (!token) return null

  const cachedProfile = uni.getStorageSync(PROFILE_STORAGE_KEY)
  store.commit('setToken', token)
  if (cachedProfile && typeof cachedProfile === 'object') {
    store.commit('setBaseUserInfo', normalizeProfile(cachedProfile))
  }

  try {
    const response = await getProfileFn()
    if (!response || !response.data) return cachedProfile || null
    const profile = normalizeProfile(response.data)
    uni.setStorageSync(PROFILE_STORAGE_KEY, profile)
    commitProfile(store, response.data)
    return response.data
  } catch (error) {
    if (Number(error && error.code) === 401) {
      if (store.state.token || store.state.baseUserInfo) clearSession(store)
      return null
    }
    return cachedProfile || null
  }
}

export function startSessionRestore(store, getProfileFn = getUserProfile) {
  if (!sessionRestorePromise) {
    sessionRestorePromise = restoreSession(store, getProfileFn)
  }
  return sessionRestorePromise
}

export function waitForSessionReady() {
  return sessionRestorePromise || Promise.resolve(null)
}
