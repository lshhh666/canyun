import axios from 'axios'
import { Message } from 'element-ui'
import { UserModule } from '@/store/modules/user'
import {
  getRequestKey,
  pending,
  checkPending,
  removePending,
} from './requestOptimize'
import router from '@/router'
import { baseUrl } from '@/config.json'

const CancelToken = axios.CancelToken
let redirectingToLogin = false

const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || baseUrl,
  timeout: 600000,
})

function cleanupRequest(config: any) {
  if (!config) return
  if (config.url) {
    config.url = config.url.replace('/api', '')
  }
  removePending(getRequestKey(config))
}

function redirectToLoginOnce() {
  if (redirectingToLogin || router.currentRoute.path === '/login') return
  redirectingToLogin = true
  router.push('/login').catch(() => undefined)
}

// Request interceptors
service.interceptors.request.use(
  (config: any) => {
    if (UserModule.token) {
      config.headers['token'] = UserModule.token
    }

    // Keep the legacy GET serialization so backend parameter contracts remain
    // unchanged during the visual-redesign phase.
    if (config.method === 'get' && config.params) {
      let url = config.url + '?'
      for (const propName of Object.keys(config.params)) {
        const value = config.params[propName]
        const part = encodeURIComponent(propName) + '='
        if (value !== null && typeof value !== 'undefined') {
          if (typeof value === 'object') {
            for (const key of Object.keys(value)) {
              const params = propName + '[' + key + ']'
              const subPart = encodeURIComponent(params) + '='
              url += subPart + encodeURIComponent(value[key]) + '&'
            }
          } else {
            url += part + encodeURIComponent(value) + '&'
          }
        }
      }
      config.params = {}
      config.url = url.endsWith('&') ? url.slice(0, -1) : url
    }

    const key = getRequestKey(config)
    if (checkPending(key)) {
      const source = CancelToken.source()
      config.cancelToken = source.token
      source.cancel('重复请求')
    } else {
      pending[key] = true
    }
    return config
  },
  (error: any) => Promise.reject(error)
)

// Response interceptors
service.interceptors.response.use(
  (response: any) => {
    cleanupRequest(response.config)

    if (response.data && response.data.status === 401) {
      redirectToLoginOnce()
      const error: any = new Error('登录状态已失效')
      error.response = response
      return Promise.reject(error)
    }

    // Business code !== 1 is intentionally returned for existing page-level
    // handling. Changing this would alter backend result semantics.
    return response
  },
  (error: any) => {
    cleanupRequest(error && error.config)

    const status = error && error.response && error.response.status
    if (status === 401) {
      redirectToLoginOnce()
    } else if (status === 405) {
      error.message = '请求方式错误'
    } else if (!axios.isCancel(error)) {
      Message.error(error && error.message ? `网络请求失败：${error.message}` : '网络连接异常，请稍后重试')
    }

    return Promise.reject(error)
  }
)

export default service
