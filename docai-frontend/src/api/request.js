import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 300000, // 5分钟超时，匹配后端SSE和模板填表的长耗时操作
})

// 导出CancelToken以供其他模块使用
export const CancelToken = axios.CancelToken

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  response => {
    const data = response.data
    // 如果是blob（文件下载），直接返回
    if (response.config.responseType === 'blob') {
      return response
    }
    // 业务错误码处理
    if (data.code && data.code !== 200) {
      if (data.code === 401) {
        handleTokenExpired()
      } else if (isTokenInvalidMessage(data.message)) {
        // 捕获"无效的令牌"等token相关业务错误
        handleTokenExpired()
      } else {
        const url = response.config?.url || ''
        // 不向用户暴露原始错误路径
        ElMessage.error(data.message || '请求失败')
      }
      const err = new Error(data.message)
      err.config = response.config
      err.response = { data }
      return Promise.reject(err)
    }
    return data
  },
  error => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error.response) {
      const status = error.response.status
      const msg = error.response.data?.message || error.response.statusText
      switch (status) {
        case 400:
          // 检查是否是token相关的400错误
          if (isTokenInvalidMessage(msg) || isTokenInvalidMessage(error.response.data?.message)) {
            handleTokenExpired()
          } else {
            ElMessage.error('请求参数有误，请检查后重试')
          }
          break
        case 401:
          handleTokenExpired()
          break
        case 413:
          ElMessage.error('上传文件过大')
          break
        case 429:
          ElMessage.warning('服务繁忙，请稍后重试')
          break
        case 500:
          ElMessage.error('服务器处理失败，请稍后重试')
          break
        case 503:
          ElMessage.warning('AI服务暂时不可用，请稍后重试')
          break
        default:
          ElMessage.error(`请求失败(${status}): ${msg}`)
      }
    } else if (error.message?.includes('Network Error')) {
      ElMessage.error('网络连接失败，请检查后端服务是否启动')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

// 判断是否是token失效相关的错误消息
function isTokenInvalidMessage(msg) {
  if (!msg) return false
  return msg.includes('令牌无效') || msg.includes('无效的令牌') || msg.includes('令牌过期') || msg.includes('登录已过期')
}

// 统一处理token过期：清除本地存储并跳转登录页
let isRedirecting = false
function handleTokenExpired() {
  if (isRedirecting) return
  isRedirecting = true
  ElMessage.error('登录已过期，请重新登录')
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('nickname')
  setTimeout(() => {
    isRedirecting = false
    window.location.href = '/login'
  }, 500)
}

export default request
