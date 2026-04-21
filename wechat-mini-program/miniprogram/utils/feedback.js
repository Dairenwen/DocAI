function trimText(text, maxLength) {
  const normalizedText = String(text || '').replace(/\s+/g, ' ').trim()
  if (!normalizedText) {
    return ''
  }

  if (!maxLength || normalizedText.length <= maxLength) {
    return normalizedText
  }

  return normalizedText.slice(0, maxLength) + '...'
}

function normalizeRawMessage(error) {
  return String(
    (error && (error.message || error.errMsg || error.msg))
    || (error && error.data && error.data.message)
    || ''
  ).replace(/\s+/g, ' ').trim()
}

function getStatusCode(error) {
  const statusCode = Number((error && error.statusCode) || (error && error.code) || 0)
  return Number.isFinite(statusCode) ? statusCode : 0
}

function includesAny(text, fragments) {
  const normalizedText = String(text || '').toLowerCase()
  return (fragments || []).some((fragment) => normalizedText.indexOf(String(fragment).toLowerCase()) !== -1)
}

function getFriendlyErrorMessage(error, fallbackMessage, options) {
  const settings = Object.assign({
    authMessage: '登录状态已失效，请重新登录',
    forbiddenMessage: '当前账号暂无此操作权限',
    notFoundMessage: '当前内容暂时不可用，请稍后重试',
    tooLargeMessage: '文件过大，请压缩后重试',
    unsupportedTypeMessage: '当前文件类型暂不支持，请重新选择',
    timeoutMessage: '处理超时，请检查网络后重试',
    networkMessage: '网络连接不稳定，请稍后重试',
    busyMessage: '当前请求较多，请稍后重试',
    serverMessage: '服务暂时不可用，请稍后重试',
    maxLength: 58,
  }, options || {})

  const statusCode = getStatusCode(error)
  const rawMessage = normalizeRawMessage(error)

  if (statusCode === 401) {
    return settings.authMessage
  }

  if (statusCode === 403) {
    return settings.forbiddenMessage
  }

  if (statusCode === 404) {
    return settings.notFoundMessage
  }

  if (statusCode === 408) {
    return settings.timeoutMessage
  }

  if (statusCode === 413) {
    return settings.tooLargeMessage
  }

  if (statusCode === 415) {
    return settings.unsupportedTypeMessage
  }

  if (statusCode === 429) {
    return settings.busyMessage
  }

  if (statusCode >= 500) {
    return settings.serverMessage
  }

  if (includesAny(rawMessage, ['cancel'])) {
    return '已取消'
  }

  if (includesAny(rawMessage, ['timeout', 'timed out', '超时'])) {
    return settings.timeoutMessage
  }

  if (includesAny(rawMessage, ['network', 'fail', 'socket', '连接', '断开'])) {
    return settings.networkMessage
  }

  if (includesAny(rawMessage, ['html', 'json', 'api', 'url', '地址无效', 'template id', '模板 id'])) {
    return settings.serverMessage
  }

  if (rawMessage && !includesAny(rawMessage, ['request:fail', 'uploadfile:fail', 'downloadfile:fail', 'errMsg'])) {
    return trimText(rawMessage, settings.maxLength)
  }

  return fallbackMessage || settings.serverMessage
}
function buildUploadIssueText(fileName, reason) {
  const normalizedFileName = trimText(fileName, 24) || '未命名文件'
  const normalizedReason = trimText(reason, 42) || '上传未完成，请稍后重试'
  return normalizedFileName + '：' + normalizedReason
}
module.exports = {
  trimText,
  getFriendlyErrorMessage,
  buildUploadIssueText,
}

