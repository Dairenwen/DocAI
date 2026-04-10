const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
} = require('./storage')

function normalizeFileName(fileName) {
  return String(fileName || '').trim()
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
