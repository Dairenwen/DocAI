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
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        localStorage.removeItem('username')
        localStorage.removeItem('nickname')
        window.location.href = '/login'
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
          ElMessage.error('请求参数有误，请检查后重试')
          break
        case 401:
          ElMessage.error('登录已过期，请重新登录')
          localStorage.removeItem('token')
          localStorage.removeItem('userId')
          localStorage.removeItem('username')
          localStorage.removeItem('nickname')
          window.location.href = '/login'
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

export default request
