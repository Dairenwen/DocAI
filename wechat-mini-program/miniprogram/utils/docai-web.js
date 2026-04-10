const config = require('../config')

const WEB_ENTRY_CONFIG = {
  autofill: {
    title: '网页智能填表',
    routeKey: 'autofill',
    query: {
      from: 'miniapp',
      scene: 'form',
      entry: 'wx-autofill',
    },
    errorTitle: '网页智能填表',
  },
  'upload-documents': {
    title: '网页上传文档',
    routeKey: 'upload',
    query: {
      from: 'miniapp',
      scene: 'upload',
      entry: 'wx-upload-documents',
      target: 'source',
    },
    errorTitle: '网页上传文档',
  },
  'upload-source': {
    title: '网页上传资料',
    routeKey: 'upload',
    query: {
      from: 'miniapp',
      scene: 'upload',
      entry: 'wx-upload-source',
      target: 'source',
    },
    errorTitle: '网页上传资料',
  },
  'upload-template': {
    title: '网页上传模板',
    routeKey: 'upload',
    query: {
      from: 'miniapp',
      scene: 'upload',
      entry: 'wx-upload-template',
      target: 'template',
    },
    errorTitle: '网页上传模板',
  },
}

function normalizeText(value) {
  return String(value || '').trim()
}

function normalizeBaseUrl(url) {
  return normalizeText(url).replace(/\/+$/, '')
}

function buildQueryString(query) {
  return Object.keys(query || {}).reduce((pairs, key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') {
      return pairs
    }

    pairs.push(
      encodeURIComponent(key) + '=' + encodeURIComponent(String(value))
    )
    return pairs
  }, []).join('&')
}

function getRemoteWebBaseUrl() {
  return normalizeBaseUrl(config && (
    config.remoteWebBaseUrl
    || config.webviewUrl
    || config.WEBVIEW_URL
  ))
}

function canUseDocaiWeb() {
  return Boolean(config && config.enableWebviewAssist) && /^https:\/\//i.test(getRemoteWebBaseUrl())
}

function resolveWebEntryConfig(mode) {
  const normalizedMode = normalizeText(mode).toLowerCase()
  return WEB_ENTRY_CONFIG[normalizedMode] || WEB_ENTRY_CONFIG.autofill
}

function buildDocaiWebUrl(mode, query) {
  if (!canUseDocaiWeb()) {
    return ''
  }

  const entryConfig = resolveWebEntryConfig(mode)
  const mergedQuery = Object.assign({}, entryConfig.query, query || {})

  if (config && typeof config.buildDocaiWebsiteUrl === 'function') {
    return config.buildDocaiWebsiteUrl(entryConfig.routeKey, mergedQuery)
  }

  if (config && typeof config.buildWebviewUrl === 'function') {
    return config.buildWebviewUrl('/autofill', mergedQuery)
  }

  const baseUrl = getRemoteWebBaseUrl()
  const queryString = buildQueryString(mergedQuery)
  return queryString ? baseUrl + '/autofill?' + queryString : baseUrl + '/autofill'
}

function buildDocaiWebPageUrl(mode, query) {
  const queryString = buildQueryString(Object.assign({
    mode: normalizeText(mode) || 'autofill',
  }, query || {}))

  return '/pages/docai/autofill-web/index' + (queryString ? '?' + queryString : '')
}

module.exports = {
  WEB_ENTRY_CONFIG,
  canUseDocaiWeb,
  getRemoteWebBaseUrl,
  resolveWebEntryConfig,
  buildDocaiWebUrl,
  buildDocaiWebPageUrl,
}
