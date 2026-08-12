import store from './../store'
import { baseUrl } from './env'
import { clearSession } from './session.js'

const NETWORK_MESSAGE = '网络连接失败，请检查网络后重试'
const REQUEST_MESSAGE = '请求失败，请稍后重试'

function rejectShape(code, message, raw) {
  return { code, message, raw }
}

export function uploadAvatar(filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${baseUrl}/user/user/avatar`,
      filePath,
      name: 'file',
      header: { authentication: store.state.token },
      success: res => {
        let data
        try {
          data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch (error) {
          reject(rejectShape('INVALID_RESPONSE', REQUEST_MESSAGE, res))
          return
        }

        data = data || {}
        if (res.statusCode === 401 || Number(data.code) === 401) {
          clearSession(store)
          reject(rejectShape(401, data.msg || REQUEST_MESSAGE, res))
          return
        }
        if ((data.code === 1 || data.code === 200) && res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data)
          return
        }
        reject(rejectShape(data.code ?? res.statusCode, data.msg || REQUEST_MESSAGE, res))
      },
      fail: error => reject(rejectShape('NETWORK_ERROR', NETWORK_MESSAGE, error))
    })
  })
}
