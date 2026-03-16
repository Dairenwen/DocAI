import request from './request'

// ==================== 用户认证 ====================

export const authLogin = (data) => request.post('/users/auth', data)
export const authRegister = (data) => request.post('/users/auth', data)
export const authByEmailCode = (data) => request.post('/users/auth', data)
export const sendVerificationCode = (email) => request.post('/users/verification-code', { email })
export const resetPasswordByEmail = (data) => request.post('/users/password/reset-by-email', data)
export const changePassword = (data) => request.post('/users/change-password', data)
export const getCurrentUser = () => request.get('/users/info')
export const userLogout = () => request.post('/users/logout')

// ==================== 源文档与信息提取 ====================

export const uploadSourceDocument = (file, onProgress, cancelToken) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/source/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
    cancelToken
  })
}

export const getSourceDocuments = () => request.get('/source/documents')
export const getDocumentStatuses = () => request.get('/source/documents/status')
export const getDocument = (id) => request.get(`/source/${id}`)
export const getDocumentFields = (id) => request.get(`/source/${id}/fields`)
export const downloadSourceDocument = (docId) => request.get(`/source/${docId}/download`, { responseType: 'blob' })

export const getDocumentStats = async () => {
  const res = await getSourceDocuments()
  const docs = res.data || []
  return {
    ...res,
    data: {
      total: docs.length,
      docx: docs.filter(d => d.fileType === 'docx').length,
      xlsx: docs.filter(d => d.fileType === 'xlsx').length,
      txt: docs.filter(d => d.fileType === 'txt').length,
      md: docs.filter(d => d.fileType === 'md').length
    }
  }
}

export const deleteDocument = (docId) => request.delete(`/source/${docId}`)
export const batchDeleteDocuments = (docIds) => request.post('/source/batch-delete', { docIds })

// ==================== 模板自动填表 ====================

export const uploadTemplateFile = (file, onProgress) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/template/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export const parseTemplateSlots = (templateId) => request.post(`/template/${templateId}/parse`)
export const fillTemplate = (templateId, docIds = []) => request.post(`/template/${templateId}/fill`, { docIds })
export const listTemplateFiles = () => request.get('/template/list')
export const getTemplateAudit = (templateId) => request.get(`/template/${templateId}/audit`)
export const getTemplateDecisions = (templateId) => request.get(`/template/${templateId}/decisions`)
export const downloadTemplateResult = (templateId) => request.get(`/template/${templateId}/download`, { responseType: 'blob' })

// ==================== 文件服务（Excel） ====================

export const uploadExcelFile = (file, category = 'upload') => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('category', category)
  return request.post('/files/upload/single', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const getExcelFiles = (params = {}) => request.get('/files/list', {
  params: {
    pageNum: params.pageNum || 1,
    pageSize: params.pageSize || 50,
    fileName: params.fileName || undefined,
    uploadStatus: params.uploadStatus
  }
})

export const downloadExcelFile = (fileId) => request.get('/files/download', {
  params: { fileId },
  responseType: 'blob'
})

export const deleteExcelFiles = (fileIds) => request.delete('/files/delete', { data: { fileIds } })

// ==================== AI 对话（SSE） ====================

export const aiChat = async ({ message, documentId, signal }) => {
  const token = localStorage.getItem('token')
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 300000) // 5分钟超时，匹配后端SSE超时

  // If an external signal is provided, link it to our controller
  if (signal) {
    if (signal.aborted) { clearTimeout(timeoutId); controller.abort(); }
    else { signal.addEventListener('abort', () => { clearTimeout(timeoutId); controller.abort() }, { once: true }) }
  }

  let response
  try {
    response = await fetch('/api/v1/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({ fileId: documentId || null, userInput: message }),
      signal: controller.signal
    })
  } catch (err) {
    clearTimeout(timeoutId)
    if (err.name === 'AbortError') {
      throw new Error('AI响应超时（5分钟），请尝试缩短文档内容或简化指令后重试')
    }
    throw err
  }

  if (!response.ok || !response.body) {
    clearTimeout(timeoutId)
    const text = await response.text().catch(() => '')
    throw new Error(text || `请求失败(${response.status})`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finalText = ''
  let modifiedExcelUrl = ''
  let resultData = []

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let splitIndex = buffer.indexOf('\n\n')
      while (splitIndex !== -1) {
        const block = buffer.slice(0, splitIndex)
        buffer = buffer.slice(splitIndex + 2)
        splitIndex = buffer.indexOf('\n\n')

        const lines = block.split('\n')
        let eventName = ''
        let dataLine = ''

        lines.forEach((line) => {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          if (line.startsWith('data:')) dataLine += line.slice(5).trim()
        })

        if (!dataLine) continue

        try {
          const payload = JSON.parse(dataLine)
          if (payload.error) {
            throw new Error(payload.error)
          }
          if (eventName === 'complete' || payload.eventType === 'complete') {
            const result = payload.result || {}
            finalText = result.aiResponse || payload.aiResponseContent || ''
            modifiedExcelUrl = result.modifiedExcelUrl || ''
            resultData = Array.isArray(result.resultData) ? result.resultData : []
            if (!finalText && Array.isArray(result.resultData) && result.resultData.length > 0) {
              finalText = JSON.stringify(result.resultData, null, 2)
            }
            if (!finalText) {
              finalText = 'AI 已完成处理，但未返回可展示文本。'
            }
          }
          // 从进度事件中提取AI响应内容（用于兜底）
          if (!finalText && payload.aiResponseContent) {
            finalText = payload.aiResponseContent
          }
        } catch (e) {
          if (e instanceof Error) throw e
        }
      }
    }
  } catch (err) {
    clearTimeout(timeoutId)
    if (err.name === 'AbortError') {
      throw new Error('AI响应超时（5分钟），请尝试缩短文档内容或简化指令后重试')
    }
    throw err
  } finally {
    clearTimeout(timeoutId)
  }

  // 如果流结束但没有收到complete事件，使用兜底信息
  if (!finalText) {
    finalText = '请求已完成，但未收到完整的AI响应。请检查后端服务状态后重试。'
  }

  return {
    reply: finalText,
    modifiedExcelUrl,
    resultData
  }
}

export const sendAiResultEmail = (data) => request.post('/ai/send-email', data)

export const sendContentEmail = (data) => request.post('/ai/send-content-email', data)

export const aiGenerate = () => Promise.reject(new Error('当前后端未提供 AI 写作生成接口'))
export const aiPolish = () => Promise.reject(new Error('当前后端未提供 AI 润色接口'))
export const updateDocumentContent = () => Promise.reject(new Error('当前后端未提供文档回写接口'))

// ==================== 模型管理 ====================

export const getLlmProviders = () => request.get('/llm/providers/list')
export const getCurrentLlmProvider = () => request.get('/llm/providers/current')
export const switchLlmProvider = (providerName) => request.post('/llm/providers/switch', { providerName })

// ==================== 对话会话管理 ====================

export const listConversations = () => request.get('/ai/conversations')
export const createConversation = (data) => request.post('/ai/conversations', data)
export const updateConversation = (id, data) => request.put(`/ai/conversations/${id}`, data)
export const deleteConversationApi = (id) => request.delete(`/ai/conversations/${id}`)
export const getConversationMessages = (id) => request.get(`/ai/conversations/${id}/messages`)
export const addConversationMessage = (id, data) => request.post(`/ai/conversations/${id}/messages`, data)

// ==================== 工具函数 ====================

/**
 * 下载blob文件
 */
export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
