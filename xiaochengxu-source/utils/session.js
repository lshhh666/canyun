import { getUserProfile } from '../pages/api/api.js'

const TOKEN_STORAGE_KEY = 'cloudmeal.token'
const PROFILE_STORAGE_KEY = 'cloudmeal.profile'

export function normalizeProfile(profile = {}) {
  return {
    nickName: profile.name ?? profile.nickName ?? '',
    avatarUrl: profile.avatar ?? profile.avatarUrl ?? ''
  }
}

function applyProfile(store, data) {
  const profile = normalizeProfile(data)
  store.commit('setBaseUserInfo', profile)
  if (typeof data.profileCompleted === 'boolean') {
    store.commit('setProfileCompleted', data.profileCompleted)
  }
  uni.setStorageSync(PROFILE_STORAGE_KEY, profile)
  return profile
}

export function persistSession(store, loginData = {}) {
  const token = loginData.token || store.state.token || ''
  if (token) {
    store.commit('setToken', token)
    uni.setStorageSync(TOKEN_STORAGE_KEY, token)
  }
  return applyProfile(store, loginData)
}

export function clearSession(store) {
  store.commit('setToken', '')
  store.commit('setBaseUserInfo', '')
  store.commit('setProfileCompleted', null)
  store.commit('setProfilePromptSkipped', false)
  uni.removeStorageSync(TOKEN_STORAGE_KEY)
  uni.removeStorageSync(PROFILE_STORAGE_KEY)
}

export async function restoreSession(store) {
  const token = uni.getStorageSync(TOKEN_STORAGE_KEY)
  if (!token) return null

  const cachedProfile = uni.getStorageSync(PROFILE_STORAGE_KEY)
  store.commit('setToken', token)
  if (cachedProfile && typeof cachedProfile === 'object') {
    store.commit('setBaseUserInfo', normalizeProfile(cachedProfile))
  }

  try {
    const response = await getUserProfile()
    if (!response || !response.data) return cachedProfile || null
    applyProfile(store, response.data)
    return response.data
  } catch (error) {
    if (Number(error && error.code) === 401) {
      if (store.state.token || store.state.baseUserInfo) clearSession(store)
      return null
    }
    return cachedProfile || null
  }
}
