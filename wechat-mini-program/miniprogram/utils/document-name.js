const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
} = require('./storage')

function normalizeFileName(fileName) {
  const rawText = String(fileName || '').trim()
  if (!rawText) {
    return ''
  }

  const baseName = rawText.split(/[\\/]/).pop() || rawText
  const decodedName = decodePotentialUtf8Mojibake(baseName)
  return String(decodedName || baseName).trim()
}

function looksLikeUtf8Mojibake(text) {
  const value = String(text || '')
  if (!value) {
    return false
  }

  if (/[\u0080-\u009f]/.test(value)) {
    return true
  }

  const suspiciousGroups = value.match(/(?:Ã.|Â.|â.|æ.|ç.|å.|ä.|é.|è.|ê.|ë.|ï.|ð.|ñ.|ô.|ö.|û.|ü.)/g) || []
  return suspiciousGroups.length >= 2
}

function decodeLatin1Utf8(text) {
  const value = String(text || '')
  if (!value) {
    return ''
  }

  let encodedText = ''
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code > 0xff) {
      return ''
    }

    encodedText += '%' + code.toString(16).padStart(2, '0')
  }

  try {
    return decodeURIComponent(encodedText)
  } catch (err) {
    return ''
  }
}

function getReadableNameScore(text) {
  const value = String(text || '')
  if (!value) {
    return -100
  }

  let score = 0

  if (/[\u4e00-\u9fff]/.test(value)) {
    score += 6
  }
  if (/[\u3040-\u30ff]/.test(value)) {
    score += 4
  }
  if (/[\uac00-\ud7af]/.test(value)) {
    score += 4
  }
  if (/\.[A-Za-z0-9]{1,8}$/.test(value)) {
    score += 2
  }
  if (/[\u0080-\u009f]/.test(value)) {
    score -= 8
  }
  if (/�/.test(value)) {
    score -= 6
  }
  if (/[ÃÂâæçåäéèêëïðñôöûü]/.test(value)) {
    score -= 3
  }

  return score
}

function decodePotentialUtf8Mojibake(text) {
  const value = String(text || '')
  if (!looksLikeUtf8Mojibake(value)) {
    return value
  }

  let currentValue = value

  for (let round = 0; round < 2; round += 1) {
    const decodedValue = decodeLatin1Utf8(currentValue)
    if (!decodedValue || decodedValue === currentValue) {
      break
    }

    if (getReadableNameScore(decodedValue) <= getReadableNameScore(currentValue)) {
      break
    }

    currentValue = decodedValue
    if (!looksLikeUtf8Mojibake(currentValue)) {
      break
    }
  }

  return currentValue
}

function loadDocumentNameMap() {
  const nameMap = getCurrentUserCache(USER_CACHE_KEYS.documentNameMap, {}) || {}
  return nameMap && typeof nameMap === 'object' ? nameMap : {}
}

function saveDocumentNameMap(nameMap) {
  setCurrentUserCache(USER_CACHE_KEYS.documentNameMap, nameMap)
}

function rememberDocumentName(docId, fileName) {
  const key = docId || docId === 0 ? String(docId).trim() : ''
  const normalizedFileName = normalizeFileName(fileName)

  if (!key || !normalizedFileName) {
    return
  }

  const nameMap = loadDocumentNameMap()
  if (nameMap[key] === normalizedFileName) {
    return
  }

  nameMap[key] = normalizedFileName
  saveDocumentNameMap(nameMap)
}

function forgetDocumentName(docId) {
  const key = docId || docId === 0 ? String(docId).trim() : ''
  if (!key) {
    return
  }

  const nameMap = loadDocumentNameMap()
  if (!Object.prototype.hasOwnProperty.call(nameMap, key)) {
    return
  }

  delete nameMap[key]
  saveDocumentNameMap(nameMap)
}

function getDocumentNameById(docId) {
  const key = docId || docId === 0 ? String(docId).trim() : ''
  if (!key) {
    return ''
  }

  const nameMap = loadDocumentNameMap()
  return normalizeFileName(nameMap[key])
}

function resolveDocumentName(item) {
  if (!item) {
    return ''
  }

  const storedName = getDocumentNameById(item.id)
  if (storedName) {
    return storedName
  }

  return normalizeFileName(
    item.originalFileName
      || item.originalFilename
      || item.fileName
      || item.title
      || item.name
      || ''
  )
}

module.exports = {
  forgetDocumentName,
  normalizeFileName,
  rememberDocumentName,
  resolveDocumentName,
}
