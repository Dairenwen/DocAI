/**
 * DocAI mini program publish configuration.
 */

const APP_ORIGIN = 'https://docai.sa1.tunnelfrp.com'
const API_PREFIX = '/api/v1'
const ENABLE_WEBVIEW_ASSIST = /^https:\/\//i.test(APP_ORIGIN)
const WEB_AUTOFILL_PATH = '/autofill'
const WEB_UPLOAD_PATH = '/autofill'

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

function normalizePath(path) {
  const value = String(path || '').trim()
  if (!value) {
    return ''
  }

  return '/' + value.replace(/^\/+/, '')
}

function buildQueryString(query) {
  const pairs = []

  Object.keys(query || {}).forEach((key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') {
      return
    }

    pairs.push(
      encodeURIComponent(key) + '=' + encodeURIComponent(String(value))
    )
  })

  return pairs.join('&')
}

function buildUrl(baseUrl, path, query) {
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl)
  if (!normalizedBaseUrl) {
    return ''
  }

  const normalizedPath = normalizePath(path)
  const queryString = buildQueryString(query)
  const url = normalizedPath ? normalizedBaseUrl + normalizedPath : normalizedBaseUrl

  return queryString ? url + '?' + queryString : url
}

const BASE_URL = normalizeBaseUrl(APP_ORIGIN + API_PREFIX)
const DOWNLOAD_URL = BASE_URL
const WEBVIEW_URL = normalizeBaseUrl(APP_ORIGIN)

function getRuntimeConfig() {
  return {
    APP_ORIGIN,
    BASE_URL,
    DOWNLOAD_URL,
    WEBVIEW_URL,
    WEB_AUTOFILL_PATH,
    WEB_UPLOAD_PATH,
    appOrigin: APP_ORIGIN,
    baseUrl: BASE_URL,
    downloadUrl: DOWNLOAD_URL,
    webviewUrl: WEBVIEW_URL,
    webAutofillPath: WEB_AUTOFILL_PATH,
    webUploadPath: WEB_UPLOAD_PATH,
    remoteWebBaseUrl: WEBVIEW_URL,
    enableWebviewAssist: ENABLE_WEBVIEW_ASSIST,
    requestTimeout: 120000,
    aiRequestTimeout: 300000,
  }
}

const runtimeConfig = getRuntimeConfig()

module.exports = Object.assign({
  APP_ORIGIN,
  BASE_URL,
  DOWNLOAD_URL,
  WEBVIEW_URL,
  WEB_AUTOFILL_PATH,
  WEB_UPLOAD_PATH,
  appOrigin: APP_ORIGIN,
  remoteWebBaseUrl: WEBVIEW_URL,
  enableWebviewAssist: ENABLE_WEBVIEW_ASSIST,
  requestTimeout: runtimeConfig.requestTimeout,
  aiRequestTimeout: runtimeConfig.aiRequestTimeout,
  getRuntimeConfig,
  buildRequestUrl(path, query) {
    return buildUrl(BASE_URL, path, query)
  },
  buildDownloadUrl(path, query) {
    return buildUrl(DOWNLOAD_URL, path, query)
  },
  buildWebviewUrl(path, query) {
    if (!ENABLE_WEBVIEW_ASSIST) {
      return ''
    }

    return buildUrl(WEBVIEW_URL, path, query)
  },
  buildDocaiWebsiteUrl(entryKey, query) {
    if (!ENABLE_WEBVIEW_ASSIST) {
      return ''
    }

    const normalizedEntryKey = String(entryKey || '').trim().toLowerCase()
    const pathMap = {
      autofill: WEB_AUTOFILL_PATH,
      upload: WEB_UPLOAD_PATH,
    }
    const targetPath = pathMap[normalizedEntryKey] || normalizedEntryKey || WEB_AUTOFILL_PATH
    return buildUrl(WEBVIEW_URL, targetPath, query)
  },
}, runtimeConfig)
