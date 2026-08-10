export function getErrorMessage(error, fallback = '操作失败，请稍后重试') {
  if (!error) return fallback
  if (error.code === 'NETWORK_ERROR') return '网络连接失败，请检查网络后重试'
  if (error.code === 401) return '登录状态已失效，请重新登录'
  return error.message || (error.data && error.data.msg) || fallback
}
