import store from './../store'
import { baseUrl } from './env'

export function request({ url = '', params = {}, method = 'GET' }) {
  const header = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    authentication: store.state.token
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: baseUrl + url,
      data: params,
      header,
      method,
      success: res => {
        const data = res.data || {}
        if (data.code === 200 || data.code === 1) {
          resolve(data)
          return
        }
        if (res.statusCode === 401 || data.code === 401) {
          store.commit('setToken', '')
          uni.removeStorageSync('token')
        }
        reject({
          code: data.code ?? res.statusCode,
          message: data.msg || '请求失败，请稍后重试',
          raw: res
        })
      },
      fail: error => reject({
        code: 'NETWORK_ERROR',
        message: '网络连接失败，请检查网络后重试',
        raw: error
      })
    })
  })
}
