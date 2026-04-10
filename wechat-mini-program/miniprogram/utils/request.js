const config = require('../config')
const {
  getFriendlyErrorMessage,
} = require('./feedback')

function getAppSafe() {
  try {
    return getApp()
  } catch (err) {
    return null
  }
}

function getRequestConfig() {
  const runtimeConfig = config && typeof config.getRuntimeConfig === 'function'
    ? config.getRuntimeConfig()
    : config

  return Object.assign({
    baseUrl: '',
    uploadUrl: '',
    downloadUrl: '',
    webviewUrl: '',
    requestTimeout: 120000,
    aiRequestTimeout: 300000,
  }, runtimeConfig || {})
}

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

function parseJsonSafely(data) {
  if (typeof data !== 'string') {
    return data
  }

  const text = data.trim()
  if (!text) {
    return data
  }

  try {
    return JSON.parse(text)
  } catch (err) {
    return data
  }
}

function isTokenMessage(message) {
  const normalizedMessage = String(message || '').toLowerCase()
  return normalizedMessage.indexOf('token') !== -1
    || normalizedMessage.indexOf('\u4ee4\u724c') !== -1
}

function isAuthFailure(statusCode, data) {
  if (statusCode === 401) {
    return true
  }

  if (typeof data !== 'object' || data === null) {
    return false
  }

  return data.code === 401
    || (typeof data.code === 'number' && data.code >= 400 && isTokenMessage(data.message))
}

function looksLikeHtmlResponse(data) {
  if (typeof data !== 'string') {
    return false
  }

  return /<(?:!doctype|html|head|body|script|meta|title)\b/i.test(data)
}

function uniqueUrls(list) {
  const result = []

  ;(list || []).forEach((item) => {
    const value = normalizeBaseUrl(item)
    if (!value || result.indexOf(value) !== -1) {
      return
    }
    result.push(value)
  })

  return result
}

function getConfiguredBaseUrls() {
  const runtimeConfig = getRequestConfig()
  return uniqueUrls([runtimeConfig.baseUrl])
}

function buildUnavailableBaseUrlError() {
  return {
    statusCode: 0,
    message: '\u5f53\u524d\u8fde\u63a5\u7684\u670d\u52a1\u5730\u5740\u4e0d\u53ef\u8bbf\u95ee\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u6216\u7a0d\u540e\u91cd\u8bd5\u3002',
  }
}

function getBaseUrlCandidates() {
  const runtimeConfig = getRequestConfig()
  const app = getAppSafe()
  const globalData = (app && app.globalData) || {}
  const cachedBaseUrl = wx.getStorageSync('docai_api_base_url') || ''

  return uniqueUrls([
    globalData.activeApiBaseUrl,
    cachedBaseUrl,
    globalData.apiBaseUrl,
    runtimeConfig.baseUrl,
  ]).filter(Boolean)
}

function getBaseUrl() {
  const runtimeConfig = getRequestConfig()
  return getBaseUrlCandidates()[0] || normalizeBaseUrl(runtimeConfig.baseUrl)
}

function getUploadBaseUrl() {
  const runtimeConfig = getRequestConfig()
  return normalizeBaseUrl(runtimeConfig.uploadUrl || runtimeConfig.baseUrl)
}

function setActiveBaseUrl(baseUrl) {
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl)
  const app = getAppSafe()

  if (app && app.globalData) {
    app.globalData.activeApiBaseUrl = normalizedBaseUrl
  }

  wx.setStorageSync('docai_api_base_url', normalizedBaseUrl)
}

function normalizeUrl(url, baseUrl) {
  if (/^https?:\/\//.test(url)) {
    return normalizeBaseUrl(url)
  }

  const normalizedBaseUrl = normalizeBaseUrl(baseUrl || getBaseUrl())
  if (url.startsWith('/')) {
    return normalizedBaseUrl + url
  }

  return normalizedBaseUrl + '/' + url
}

function clearAuth() {
  const app = getAppSafe()
  if (app && app.clearAuth) {
    app.clearAuth()
    return
  }

  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
}

function createHeader(options) {
  const method = String(options.method || 'GET').toUpperCase()
  const token = wx.getStorageSync('token') || ''
  const header = Object.assign({}, options.header || {})

  if (
    !header['Content-Type']
    && !header['content-type']
    && !options.skipJsonContentType
    && method !== 'UPLOAD'
  ) {
    header['Content-Type'] = 'application/json'
  }

  if (token) {
    header.Authorization = 'Bearer ' + token
  }

  return header
}

function buildRequestError(statusCode, data, fallbackMessage) {
  const message = getFriendlyErrorMessage({
    statusCode,
    data,
    message: (data && data.message) || fallbackMessage,
  }, fallbackMessage || '请求失败，请稍后重试')
  return {
    statusCode,
    data,
    message,
  }
}

function buildUnexpectedApiResponseError(statusCode, data, baseUrl) {
  return {
    statusCode,
    data,
    baseUrl,
    retryable: true,
    message: looksLikeHtmlResponse(data)
      ? '服务暂时不可用，请稍后重试'
      : '服务返回异常，请稍后重试',
  }
}

function normalizeResponse(statusCode, data, fallbackMessage, options) {
  const normalizedOptions = Object.assign({
    expectJson: false,
    baseUrl: '',
  }, options || {})

  if (isAuthFailure(statusCode, data)) {
    clearAuth()
  }

  if (typeof data === 'object' && data !== null && Object.prototype.hasOwnProperty.call(data, 'code')) {
    if (data.code === 200) {
      return {
        ok: true,
        data,
      }
    }

    return {
      ok: false,
      error: buildRequestError(statusCode, data, fallbackMessage),
    }
  }

  if (statusCode >= 200 && statusCode < 300) {
    if (normalizedOptions.expectJson && typeof data === 'string') {
      return {
        ok: false,
        error: buildUnexpectedApiResponseError(statusCode, data, normalizedOptions.baseUrl),
      }
    }

    return {
      ok: true,
      data,
    }
  }

  return {
    ok: false,
    error: buildRequestError(statusCode, data, fallbackMessage),
  }
}

function performRequest(baseUrl, options) {
  const runtimeConfig = getRequestConfig()
  const method = String(options.method || 'GET').toUpperCase()
  const header = createHeader(options)

  return new Promise((resolve, reject) => {
    wx.request({
      url: normalizeUrl(options.url, baseUrl),
      method,
      data: options.data || {},
      header,
      timeout: options.timeout || runtimeConfig.requestTimeout,
      responseType: options.responseType || 'text',
      success(res) {
        const responseData = parseJsonSafely(res.data)
        const normalized = normalizeResponse(res.statusCode, responseData, '请求失败，请稍后重试', {
          expectJson: true,
          baseUrl,
        })
        if (normalized.ok) {
          setActiveBaseUrl(baseUrl)
          resolve(normalized.data)
          return
        }
        reject(normalized.error)
      },
      fail(err) {
        reject({
          statusCode: 0,
          message: getFriendlyErrorMessage(err, '网络连接不稳定，请稍后重试'),
        })
      },
    })
  })
}

async function request(options) {
  const baseUrl = getBaseUrl()
  if (!baseUrl) {
    throw buildUnavailableBaseUrlError()
  }

  return performRequest(baseUrl, options)
}

function performUpload(baseUrl, options) {
  const runtimeConfig = getRequestConfig()
  const header = createHeader(Object.assign({}, options, {
    method: 'UPLOAD',
    skipJsonContentType: true,
  }))

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: normalizeUrl(options.url, baseUrl),
      filePath: options.filePath,
      name: options.name || 'file',
      formData: options.formData || {},
      header,
      timeout: options.timeout || runtimeConfig.requestTimeout,
      success(res) {
        const responseData = parseJsonSafely(res.data)
        const normalized = normalizeResponse(res.statusCode, responseData, '上传失败，请稍后重试', {
          expectJson: true,
          baseUrl,
        })
        if (normalized.ok) {
          setActiveBaseUrl(baseUrl)
          resolve(normalized.data)
          return
        }
        reject(normalized.error)
      },
      fail(err) {
        reject({
          statusCode: 0,
          message: getFriendlyErrorMessage(err, '上传未完成，请检查网络后重试', {
            tooLargeMessage: '文件过大，请压缩后重试',
          }),
        })
      },
    })
  })
}

async function uploadFile(options) {
  const uploadBaseUrl = getUploadBaseUrl()
  if (!uploadBaseUrl) {
    throw buildUnavailableBaseUrlError()
  }

  return performUpload(uploadBaseUrl, options)
}

function arrayBufferToText(buffer) {
  if (typeof TextDecoder !== 'undefined') {
    return new TextDecoder('utf-8').decode(buffer)
  }

  const bytes = new Uint8Array(buffer)
  let result = ''
  const chunkSize = 0x8000

  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize)
    result += String.fromCharCode.apply(null, chunk)
  }

  try {
    return decodeURIComponent(escape(result))
  } catch (err) {
    return result
  }
}

function parseSseBlock(block) {
  const lines = String(block || '').split('\n')
  let eventName = ''
  let dataText = ''

  lines.forEach((line) => {
    if (line.indexOf('event:') === 0) {
      eventName = line.slice(6).trim()
      return
    }

    if (line.indexOf('data:') === 0) {
      dataText += line.slice(5).trim()
    }
  })

  if (!dataText) {
    return null
  }

  let payload = dataText
  try {
    payload = JSON.parse(dataText)
  } catch (err) {
    // Keep plain text when backend does not return JSON.
  }

  return {
    eventName,
    payload,
  }
}

function extractAiReply(payload) {
  if (!payload) {
    return '暂未获取到回复，请稍后重试。'
  }

  if (typeof payload === 'string') {
    return payload
  }

  const result = payload.result || {}
  const reply = result.aiResponse
    || payload.aiResponseContent
    || payload.reply
    || payload.content
    || payload.answer
    || result.result
    || payload.message

  if (reply) {
    return reply
  }

  if (Array.isArray(result.resultData) && result.resultData.length) {
    return JSON.stringify(result.resultData, null, 2)
  }

  return '请求已完成，但未收到可展示的 AI 文本结果。'
}

function shouldFlushSseTail(text) {
  return /(?:^|\n)(?:event|data):/i.test(String(text || ''))
}

function buildAiChatSuccess(payload, extra) {
  return {
    code: 200,
    message: 'success',
    data: Object.assign({
      reply: extractAiReply(payload),
      modifiedExcelUrl: (payload && payload.result && payload.result.modifiedExcelUrl) || (payload && payload.modifiedExcelUrl) || '',
      resultData: (payload && payload.result && payload.result.resultData) || (payload && payload.resultData) || [],
      raw: payload || null,
    }, extra || {}),
  }
}

function doAiChatRequest(baseUrl, data, options) {
  const runtimeConfig = getRequestConfig()
  const token = wx.getStorageSync('token') || ''
  const header = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }

  const normalizedOptions = Object.assign({
    onProgress: null,
  }, options || {})

  if (!token) {
    return Promise.reject({
      statusCode: 401,
      message: '登录状态已失效，请重新登录。',
    })
  }

  header.Authorization = 'Bearer ' + token

  return new Promise((resolve, reject) => {
    let settled = false
    let requestTask = null
    let buffer = ''
    let lastProgressPayload = null
    let lastProgressReply = ''

    const finish = (handler, value) => {
      if (settled) {
        return
      }
      settled = true
      handler(value)
    }

    const fail = (error) => {
      const message = String((error && error.message) || '').toLowerCase()
      if (message.indexOf('token') !== -1 || message.indexOf('令牌') !== -1) {
        clearAuth()
      }

      finish(reject, error)
    }

    const handleEvent = (event) => {
      if (!event) {
        return
      }

      const payload = event.payload
      const nextReply = extractAiReply(payload)
      const errorMessage = typeof payload === 'object' && payload !== null
        ? payload.error || payload.message
        : ''

      if (event.eventName === 'error' || errorMessage) {
        fail({
          statusCode: 0,
          data: payload,
          message: getFriendlyErrorMessage({
            statusCode: 0,
            data: payload,
            message: errorMessage,
          }, 'AI 服务暂时不可用，请稍后重试'),
        })
        return
      }

      if (event.eventName !== 'complete') {
        lastProgressPayload = payload
        if (nextReply) {
          lastProgressReply = nextReply
        }

        if (typeof normalizedOptions.onProgress === 'function') {
          normalizedOptions.onProgress({
            eventName: event.eventName || '',
            stage: payload && payload.stage ? payload.stage : '',
            progress: payload && typeof payload.progress === 'number' ? payload.progress : 0,
            message: payload && payload.message ? payload.message : '',
            detail: payload && payload.detail ? payload.detail : '',
            reply: nextReply,
            raw: payload,
          })
        }
      }

      if (event.eventName === 'complete' || (payload && payload.eventType === 'complete')) {
        finish(resolve, buildAiChatSuccess(payload))
      }
    }

    const consumeText = (text, flushTail) => {
      buffer += String(text || '').replace(/\r\n/g, '\n')

      let separatorIndex = buffer.indexOf('\n\n')
      while (separatorIndex !== -1) {
        const block = buffer.slice(0, separatorIndex).trim()
        buffer = buffer.slice(separatorIndex + 2)

        if (block) {
          handleEvent(parseSseBlock(block))
        }

        separatorIndex = buffer.indexOf('\n\n')
      }

      if (flushTail && buffer.trim() && shouldFlushSseTail(buffer)) {
        const tailBlock = buffer.trim()
        buffer = ''
        handleEvent(parseSseBlock(tailBlock))
      }
    }

    requestTask = wx.request({
      url: normalizeUrl('/ai/chat/stream', baseUrl),
      method: 'POST',
      data: {
        fileId: data.documentId || data.fileId || null,
        userInput: data.message || data.userInput || '',
      },
      header,
      timeout: runtimeConfig.aiRequestTimeout,
      responseType: 'arraybuffer',
      enableChunked: true,
      success(res) {
        if (settled) {
          return
        }

        if (res.statusCode < 200 || res.statusCode >= 300) {
          finish(reject, {
            statusCode: res.statusCode,
            data: res.data,
            message: 'AI 服务暂时不可用，请稍后重试',
          })
          return
        }

        if (res.data) {
          if (typeof res.data === 'string') {
            consumeText(res.data, true)
          } else {
            consumeText(arrayBufferToText(res.data), true)
          }
        }

        if (!settled) {
          if (lastProgressPayload || lastProgressReply) {
            finish(resolve, buildAiChatSuccess(lastProgressPayload || {
              aiResponseContent: lastProgressReply,
              eventType: 'progress',
            }, {
              incomplete: true,
            }))
            return
          }

          finish(reject, {
            statusCode: 0,
            message: '暂未收到完整的 AI 回复，请稍后重试。',
          })
        }
      },
      fail(err) {
        if (settled) {
          return
        }

        finish(reject, {
          statusCode: 0,
          message: getFriendlyErrorMessage(err, 'AI 服务暂时不可用，请稍后重试'),
        })
      },
    })

    if (requestTask && typeof requestTask.onChunkReceived === 'function') {
      requestTask.onChunkReceived((res) => {
        if (settled || !res || !res.data) {
          return
        }

        try {
          consumeText(arrayBufferToText(res.data), false)
        } catch (err) {
          fail({
            statusCode: 0,
            message: 'AI 回复解析失败，请稍后重试。',
          })
        }
      })
    }
  })
}

module.exports = {
  request,
  uploadFile,
  getBaseUrl,
  getBaseUrlCandidates,
  normalizeUrl,
  clearAuth,
  doAiChatRequest,
  getConfiguredBaseUrls,
}
