const STORAGE_PREFIX = 'docai'
const USER_PREFIX = STORAGE_PREFIX + ':user:'
const GUEST_PREFIX = STORAGE_PREFIX + ':guest:'
const AUTH_CONTEXT_KEY = STORAGE_PREFIX + ':auth:context'

const USER_CACHE_KEYS = Object.freeze({
  autofillDraft: 'autofillDraft',
  autofillResultSession: 'autofillResultSession',
  autofillResults: 'autofillResults',
  documentNameMap: 'documentNameMap',
  documentStageTracker: 'documentStageTracker',
  documentFavorites: 'documentFavorites',
  chatCurrentSessionId: 'chatCurrentSessionId',
  chatSessions: 'chatSessions',
  currentDoc: 'currentDoc',
  autofillDocumentPickerContext: 'autofillDocumentPickerContext',
  autofillWebNoticeAck: 'autofillWebNoticeAck',
})

const LEGACY_STORAGE_KEYS = Object.freeze({
  [USER_CACHE_KEYS.autofillDraft]: 'docai_autofill_draft',
  [USER_CACHE_KEYS.autofillResultSession]: 'docai_autofill_result_session',
  [USER_CACHE_KEYS.autofillResults]: 'docai_autofill_results',
  [USER_CACHE_KEYS.documentNameMap]: 'docai_document_name_map',
  [USER_CACHE_KEYS.documentStageTracker]: 'docai_document_stage_tracker',
  [USER_CACHE_KEYS.documentFavorites]: 'docai_document_favorites',
  [USER_CACHE_KEYS.chatCurrentSessionId]: 'docai_chat_current_session_id',
  [USER_CACHE_KEYS.chatSessions]: 'docai_chat_sessions',
  [USER_CACHE_KEYS.currentDoc]: 'docai_current_doc',
  [USER_CACHE_KEYS.autofillDocumentPickerContext]: 'docai_autofill_document_picker_context',
  [USER_CACHE_KEYS.autofillWebNoticeAck]: 'docai_autofill_web_notice_ack',
})

const AUTH_STORAGE_KEYS = Object.freeze([
  'token',
  'refreshToken',
  'refresh_token',
  'session',
  'user',
  AUTH_CONTEXT_KEY,
])

function normalizeText(value) {
  return String(value || '').trim()
}

function normalizeUserId(userId) {
  return normalizeText(userId)
}

function resolveUserIdFromUser(user) {
  if (!user || typeof user !== 'object') {
    return ''
  }

  return normalizeUserId(user.id || user.userId || user.uid)
}

function getStorageKeys() {
  if (typeof wx === 'undefined' || !wx || typeof wx.getStorageInfoSync !== 'function') {
    return []
  }

  try {
    const info = wx.getStorageInfoSync() || {}
    return Array.isArray(info.keys) ? info.keys.map((item) => String(item)) : []
  } catch (err) {
    return []
  }
}

function hasStorageKey(storageKey) {
  return getStorageKeys().indexOf(String(storageKey || '')) !== -1
}

function removeStorageKeys(list) {
  (Array.isArray(list) ? list : []).forEach((item) => {
    const storageKey = normalizeText(item)
    if (!storageKey) {
      return
    }

    try {
      wx.removeStorageSync(storageKey)
    } catch (err) {
      // ignore storage remove failures during cleanup
    }
  })
}

function getUserScopedKey(userId, key) {
  const normalizedUserId = normalizeUserId(userId)
  const normalizedKey = normalizeText(key)
  if (!normalizedUserId || !normalizedKey) {
    return ''
  }

  return USER_PREFIX + normalizedUserId + ':' + normalizedKey
}

function normalizeAuthContext(context) {
  const rawContext = context && typeof context === 'object' ? context : {}
  return {
    currentUserId: normalizeUserId(rawContext.currentUserId),
    currentSessionToken: normalizeText(rawContext.currentSessionToken),
    lastLoginUserId: normalizeUserId(rawContext.lastLoginUserId),
  }
}

function getAuthContext() {
  const rawValue = wx.getStorageSync(AUTH_CONTEXT_KEY)
  if (!rawValue || typeof rawValue !== 'object') {
    return normalizeAuthContext({})
  }

  return normalizeAuthContext(rawValue)
}

function setAuthContext(context) {
  const normalizedContext = normalizeAuthContext(context)
  wx.setStorageSync(AUTH_CONTEXT_KEY, normalizedContext)
  return normalizedContext
}

function clearAuthContext(preservedLastLoginUserId) {
  const normalizedLastLoginUserId = normalizeUserId(preservedLastLoginUserId)
  if (normalizedLastLoginUserId) {
    return setAuthContext({
      currentUserId: '',
      currentSessionToken: '',
      lastLoginUserId: normalizedLastLoginUserId,
    })
  }

  wx.removeStorageSync(AUTH_CONTEXT_KEY)
  return normalizeAuthContext({})
}

function getCurrentUserId() {
  try {
    const app = getApp()
    const appUserId = normalizeUserId(app && app.globalData && app.globalData.currentUserId)
    if (appUserId) {
      return appUserId
    }
  } catch (err) {
    // ignore getApp failures before app initialization
  }

  const authContext = getAuthContext()
  if (authContext.currentUserId) {
    return authContext.currentUserId
  }

  return resolveUserIdFromUser(wx.getStorageSync('user'))
}

function getLegacyStorageKey(key) {
  return LEGACY_STORAGE_KEYS[normalizeText(key)] || ''
}

function migrateLegacyCacheToUser(userId, key) {
  const normalizedUserId = normalizeUserId(userId)
  const normalizedKey = normalizeText(key)
  const legacyStorageKey = getLegacyStorageKey(normalizedKey)
  const scopedStorageKey = getUserScopedKey(normalizedUserId, normalizedKey)

  if (!normalizedUserId || !normalizedKey || !legacyStorageKey || !scopedStorageKey) {
    return false
  }

  if (!hasStorageKey(legacyStorageKey)) {
    return false
  }

  if (!hasStorageKey(scopedStorageKey)) {
    wx.setStorageSync(scopedStorageKey, wx.getStorageSync(legacyStorageKey))
  }

  wx.removeStorageSync(legacyStorageKey)
  return true
}

function migrateLegacyBusinessCaches(userId, keys) {
  const normalizedUserId = normalizeUserId(userId)
  if (!normalizedUserId) {
    return []
  }

  const migratedKeys = []
  ;(Array.isArray(keys) && keys.length ? keys : Object.keys(LEGACY_STORAGE_KEYS)).forEach((key) => {
    if (migrateLegacyCacheToUser(normalizedUserId, key)) {
      migratedKeys.push(String(key))
    }
  })
  return migratedKeys
}

function getUserCache(userId, key, defaultValue) {
  const normalizedUserId = normalizeUserId(userId)
  const normalizedKey = normalizeText(key)
  if (!normalizedUserId || !normalizedKey) {
    return defaultValue
  }

  migrateLegacyCacheToUser(normalizedUserId, normalizedKey)
  const scopedStorageKey = getUserScopedKey(normalizedUserId, normalizedKey)
  if (!hasStorageKey(scopedStorageKey)) {
    return defaultValue
  }

  return wx.getStorageSync(scopedStorageKey)
}

function setUserCache(userId, key, value) {
  const normalizedUserId = normalizeUserId(userId)
  const normalizedKey = normalizeText(key)
  if (!normalizedUserId || !normalizedKey) {
    return value
  }

  wx.setStorageSync(getUserScopedKey(normalizedUserId, normalizedKey), value)
  const legacyStorageKey = getLegacyStorageKey(normalizedKey)
  if (legacyStorageKey) {
    wx.removeStorageSync(legacyStorageKey)
  }
  return value
}

function removeUserCache(userId, key) {
  const normalizedUserId = normalizeUserId(userId)
  const normalizedKey = normalizeText(key)
  if (!normalizedUserId || !normalizedKey) {
    return
  }

  wx.removeStorageSync(getUserScopedKey(normalizedUserId, normalizedKey))
  const legacyStorageKey = getLegacyStorageKey(normalizedKey)
  if (legacyStorageKey) {
    wx.removeStorageSync(legacyStorageKey)
  }
}

function clearUserCacheNamespace(userId) {
  const normalizedUserId = normalizeUserId(userId)
  if (!normalizedUserId) {
    return
  }

  const prefix = USER_PREFIX + normalizedUserId + ':'
  removeStorageKeys(getStorageKeys().filter((item) => item.indexOf(prefix) === 0))
}

function clearGuestCache() {
  removeStorageKeys(getStorageKeys().filter((item) => item.indexOf(GUEST_PREFIX) === 0))
}

function clearLegacyBusinessCache(keys) {
  const targetKeys = Array.isArray(keys) && keys.length
    ? keys.map((item) => getLegacyStorageKey(item)).filter(Boolean)
    : Object.keys(LEGACY_STORAGE_KEYS).map((item) => LEGACY_STORAGE_KEYS[item])
  removeStorageKeys(targetKeys)
}

function clearAllAuthRelatedCache() {
  removeStorageKeys(AUTH_STORAGE_KEYS)
}

function getCurrentUserCache(key, defaultValue) {
  return getUserCache(getCurrentUserId(), key, defaultValue)
}

function setCurrentUserCache(key, value) {
  return setUserCache(getCurrentUserId(), key, value)
}

function removeCurrentUserCache(key) {
  return removeUserCache(getCurrentUserId(), key)
}

function clearCurrentUserCacheNamespace() {
  return clearUserCacheNamespace(getCurrentUserId())
}

module.exports = {
  AUTH_CONTEXT_KEY,
  USER_CACHE_KEYS,
  LEGACY_STORAGE_KEYS,
  getUserScopedKey,
  setUserCache,
  getUserCache,
  removeUserCache,
  clearUserCacheNamespace,
  clearGuestCache,
  clearAllAuthRelatedCache,
  getCurrentUserId,
  getCurrentUserCache,
  setCurrentUserCache,
  removeCurrentUserCache,
  clearCurrentUserCacheNamespace,
  getAuthContext,
  setAuthContext,
  clearAuthContext,
  resolveUserIdFromUser,
  migrateLegacyCacheToUser,
  migrateLegacyBusinessCaches,
  clearLegacyBusinessCache,
}
