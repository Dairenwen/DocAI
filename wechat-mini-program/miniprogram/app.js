const config = require('./config')
const {
  USER_CACHE_KEYS,
  getAuthContext,
  setAuthContext,
  clearAuthContext,
  resolveUserIdFromUser,
  migrateLegacyBusinessCaches,
  clearLegacyBusinessCache,
  clearGuestCache,
  clearAllAuthRelatedCache,
  clearUserCacheNamespace,
  removeUserCache,
} = require('./utils/storage')

const initialRuntimeConfig = config && typeof config.getRuntimeConfig === 'function'
  ? config.getRuntimeConfig()
  : config
const PRIVACY_AGREE_BUTTON_ID = 'docai-privacy-agree-btn'
const BYPASS_PRIVACY_FOR_TEST = true

const PRIVACY_SCENE_TEXT = {
  'account-login': '登录或注册 DocAI 账号',
  'document-upload': '选择并上传资料文档',
  'autofill-source-upload': '选择并上传智能填表数据源',
  'autofill-template-upload': '选择智能填表模板文件',
  general: '继续当前操作',
}

const TRANSIENT_USER_CACHE_KEYS = [
  USER_CACHE_KEYS.documentStageTracker,
  USER_CACHE_KEYS.chatCurrentSessionId,
  USER_CACHE_KEYS.chatSessions,
  USER_CACHE_KEYS.currentDoc,
  USER_CACHE_KEYS.autofillDocumentPickerContext,
  USER_CACHE_KEYS.autofillWebNoticeAck,
]

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

function getRuntimeConfig() {
  return config && typeof config.getRuntimeConfig === 'function'
    ? config.getRuntimeConfig()
    : config
}

function getAllowedBaseUrls() {
  const runtimeConfig = getRuntimeConfig()
  return [runtimeConfig.baseUrl || runtimeConfig.apiBaseUrl].map(normalizeBaseUrl).filter(Boolean)
}

function createPrivacyDialogState(scene) {
  const sceneKey = String(scene || 'general')
  return {
    visible: false,
    scene: sceneKey,
    sceneText: PRIVACY_SCENE_TEXT[sceneKey] || PRIVACY_SCENE_TEXT.general,
    referrer: '',
    agreeButtonId: PRIVACY_AGREE_BUTTON_ID,
  }
}

function getPrivacyErrorMessage(scene) {
  const sceneText = PRIVACY_SCENE_TEXT[String(scene || 'general')] || PRIVACY_SCENE_TEXT.general
  return '需先完成隐私授权，才能' + sceneText
}

function normalizeUserId(value) {
  return String(value || '').trim()
}

App({
  onLaunch() {
    const runtimeConfig = getRuntimeConfig()
    const token = wx.getStorageSync('token') || ''
    const user = wx.getStorageSync('user') || null
    const cachedBaseUrl = normalizeBaseUrl(wx.getStorageSync('docai_api_base_url'))
    const allowedBaseUrls = getAllowedBaseUrls()
    const activeApiBaseUrl = allowedBaseUrls.indexOf(cachedBaseUrl) !== -1
      ? cachedBaseUrl
      : normalizeBaseUrl(runtimeConfig.baseUrl || runtimeConfig.apiBaseUrl)

    wx.setStorageSync('docai_api_base_url', activeApiBaseUrl)

    const authContext = getAuthContext()
    const currentUserId = token ? (resolveUserIdFromUser(user) || authContext.currentUserId) : ''
    const lastLoginUserId = currentUserId || authContext.lastLoginUserId

    if (currentUserId) {
      migrateLegacyBusinessCaches(currentUserId)
    }

    const nextAuthContext = setAuthContext({
      currentUserId,
      currentSessionToken: token,
      lastLoginUserId,
    })

    this.globalData.token = token
    this.globalData.isLogin = Boolean(token)
    this.globalData.user = user
    this.globalData.apiBaseUrl = normalizeBaseUrl(runtimeConfig.baseUrl || runtimeConfig.apiBaseUrl)
    this.globalData.activeApiBaseUrl = activeApiBaseUrl
    this.globalData.currentUserId = nextAuthContext.currentUserId
    this.globalData.currentSessionToken = nextAuthContext.currentSessionToken
    this.globalData.lastLoginUserId = nextAuthContext.lastLoginUserId
    this.initPrivacyFlow()
  },

  globalData: {
    apiBaseUrl: normalizeBaseUrl(initialRuntimeConfig.baseUrl || initialRuntimeConfig.apiBaseUrl),
    activeApiBaseUrl: normalizeBaseUrl(initialRuntimeConfig.baseUrl || initialRuntimeConfig.apiBaseUrl),
    token: '',
    isLogin: false,
    user: null,
    currentUserId: '',
    currentSessionToken: '',
    lastLoginUserId: '',
    privacyDialog: createPrivacyDialogState(),
    privacySetting: {
      needAuthorization: false,
    },
  },

  initPrivacyFlow() {
    if (this.__privacyFlowInitialized) {
      return
    }

    this.__privacyFlowInitialized = true
    this.__privacyDialogState = createPrivacyDialogState()
    this.__privacyDialogListeners = []
    this.__pendingPrivacyResolvers = []
    this.__pendingPrivacyScene = 'general'
    this.__pendingPrivacyAction = null

    if (BYPASS_PRIVACY_FOR_TEST) {
      this.globalData.privacySetting = { needAuthorization: false }
      return
    }

    this.refreshPrivacySetting()

    if (typeof wx === 'undefined' || !wx || typeof wx.onNeedPrivacyAuthorization !== 'function') {
      return
    }

    wx.onNeedPrivacyAuthorization((resolve, eventInfo) => {
      this.__pendingPrivacyResolvers.push(resolve)
      this.showPrivacyDialog(this.__pendingPrivacyScene || 'general', eventInfo)
    })
  },

  refreshPrivacySetting() {
    if (BYPASS_PRIVACY_FOR_TEST) {
      this.globalData.privacySetting = { needAuthorization: false }
      return Promise.resolve(this.globalData.privacySetting)
    }

    if (typeof wx === 'undefined' || !wx || typeof wx.getPrivacySetting !== 'function') {
      this.globalData.privacySetting = { needAuthorization: false }
      return Promise.resolve(this.globalData.privacySetting)
    }

    return new Promise((resolve) => {
      wx.getPrivacySetting({
        success: (res) => {
          this.globalData.privacySetting = Object.assign({
            needAuthorization: false,
            privacyContractName: '',
          }, res || {})
          resolve(this.globalData.privacySetting)
        },
        fail: () => {
          this.globalData.privacySetting = { needAuthorization: false }
          resolve(this.globalData.privacySetting)
        },
      })
    })
  },

  getPrivacySceneText(scene) {
    const sceneKey = String(scene || 'general')
    return PRIVACY_SCENE_TEXT[sceneKey] || PRIVACY_SCENE_TEXT.general
  },

  getPrivacyDialogState() {
    const dialogState = this.__privacyDialogState || createPrivacyDialogState()
    return Object.assign({}, dialogState)
  },

  notifyPrivacyDialogChange() {
    const dialogState = this.getPrivacyDialogState()
    this.globalData.privacyDialog = dialogState

    ;(this.__privacyDialogListeners || []).forEach((listener) => {
      try {
        listener(dialogState)
      } catch (err) {
        // keep privacy state updates isolated from page rendering issues
      }
    })
  },

  bindPrivacyDialog(page) {
    if (!page || typeof page.setData !== 'function' || page.__docaiPrivacyDialogListener) {
      return
    }

    const listener = (dialogState) => {
      try {
        page.setData({
          privacyDialog: Object.assign({}, dialogState),
        })
      } catch (err) {
        // ignore page lifecycle race conditions
      }
    }

    page.__docaiPrivacyDialogListener = listener
    this.__privacyDialogListeners = (this.__privacyDialogListeners || []).concat(listener)
    listener(this.getPrivacyDialogState())
  },

  unbindPrivacyDialog(page) {
    const listener = page && page.__docaiPrivacyDialogListener
    if (!listener) {
      return
    }

    this.__privacyDialogListeners = (this.__privacyDialogListeners || []).filter((item) => item !== listener)
    delete page.__docaiPrivacyDialogListener
  },

  showPrivacyDialog(scene, eventInfo) {
    const dialogState = createPrivacyDialogState(scene)
    dialogState.visible = true
    dialogState.referrer = String((eventInfo && eventInfo.referrer) || '')
    this.__privacyDialogState = dialogState
    this.notifyPrivacyDialogChange()
  },

  hidePrivacyDialog() {
    const currentScene = (this.__privacyDialogState && this.__privacyDialogState.scene) || 'general'
    this.__privacyDialogState = createPrivacyDialogState(currentScene)
    this.notifyPrivacyDialogChange()
  },

  resolvePendingPrivacyAuthorization(result) {
    const pendingResolvers = (this.__pendingPrivacyResolvers || []).splice(0)
    pendingResolvers.forEach((resolve) => {
      try {
        resolve(result)
      } catch (err) {
        // ignore resolver errors
      }
    })
  },

  replacePendingPrivacyAction(nextAction) {
    const previousAction = this.__pendingPrivacyAction
    this.__pendingPrivacyAction = nextAction || null

    if (previousAction && typeof previousAction.reject === 'function') {
      previousAction.reject(this.createPrivacyError(previousAction.scene, {
        message: getPrivacyErrorMessage(previousAction.scene),
      }))
    }
  },

  resolvePendingPrivacyAction() {
    const pendingAction = this.__pendingPrivacyAction
    this.__pendingPrivacyAction = null

    if (!pendingAction) {
      return
    }

    let actionResult
    try {
      actionResult = typeof pendingAction.action === 'function'
        ? pendingAction.action()
        : undefined
    } catch (err) {
      if (typeof pendingAction.reject === 'function') {
        pendingAction.reject(err)
      }
      return
    }

    Promise.resolve(actionResult)
      .then((result) => {
        if (typeof pendingAction.resolve === 'function') {
          pendingAction.resolve(result)
        }
      })
      .catch((error) => {
        if (typeof pendingAction.reject === 'function') {
          pendingAction.reject(error)
        }
      })
  },

  rejectPendingPrivacyAction(scene, rawErr) {
    const pendingAction = this.__pendingPrivacyAction
    this.__pendingPrivacyAction = null

    if (!pendingAction || typeof pendingAction.reject !== 'function') {
      return
    }

    pendingAction.reject(this.createPrivacyError(scene || pendingAction.scene, rawErr))
  },

  requestPrivacyAuthorizationWithDialog(scene) {
    const currentScene = String(scene || 'general')

    return new Promise((resolve, reject) => {
      this.replacePendingPrivacyAction({
        scene: currentScene,
        action: () => undefined,
        resolve,
        reject,
      })
      this.showPrivacyDialog(currentScene)
    })
  },

  requestPrivacyAuthorization(scene) {
    this.initPrivacyFlow()

    if (BYPASS_PRIVACY_FOR_TEST) {
      return Promise.resolve()
    }

    const currentScene = String(scene || 'general')
    this.__pendingPrivacyScene = currentScene

    if (typeof wx === 'undefined' || !wx || typeof wx.requirePrivacyAuthorize !== 'function') {
      return this.requestPrivacyAuthorizationWithDialog(currentScene)
    }

    return new Promise((resolve, reject) => {
      wx.requirePrivacyAuthorize({
        success: (res) => {
          this.hidePrivacyDialog()
          this.refreshPrivacySetting()
          resolve(res)
        },
        fail: (err) => {
          const rawMessage = String((err && (err.errMsg || err.message)) || '').toLowerCase()
          const canFallbackToDialog = (
            rawMessage.indexOf('invalid request data') !== -1
            || rawMessage.indexOf('not support') !== -1
            || rawMessage.indexOf('not supported') !== -1
            || rawMessage.indexOf('no such api') !== -1
            || rawMessage.indexOf('caniuse') !== -1
          )

          if (canFallbackToDialog) {
            this.hidePrivacyDialog()
            this.requestPrivacyAuthorizationWithDialog(currentScene)
              .then(resolve)
              .catch(reject)
              .finally(() => {
                this.refreshPrivacySetting()
              })
            return
          }

          this.hidePrivacyDialog()
          this.refreshPrivacySetting()
          reject(this.createPrivacyError(currentScene, err))
        },
      })
    })
  },

  handlePrivacyAgree(event) {
    const buttonId = (event && event.currentTarget && event.currentTarget.id) || PRIVACY_AGREE_BUTTON_ID
    this.resolvePendingPrivacyAuthorization({
      event: 'agree',
      buttonId,
    })
    this.hidePrivacyDialog()
    this.resolvePendingPrivacyAction()
    this.refreshPrivacySetting()
  },

  handlePrivacyDisagree() {
    this.resolvePendingPrivacyAuthorization({
      event: 'disagree',
    })
    this.hidePrivacyDialog()
    this.rejectPendingPrivacyAction(this.__pendingPrivacyScene || 'general', {
      message: getPrivacyErrorMessage(this.__pendingPrivacyScene || 'general'),
    })
  },

  createPrivacyError(scene, rawErr) {
    const rawMessage = String((rawErr && (rawErr.errMsg || rawErr.message)) || '').toLowerCase()
    if (!rawMessage) {
      return new Error(getPrivacyErrorMessage(scene))
    }

    if (
      rawMessage.indexOf('cancel') !== -1
      || rawMessage.indexOf('deny') !== -1
      || rawMessage.indexOf('disagree') !== -1
      || rawMessage.indexOf('refuse') !== -1
    ) {
      return new Error(getPrivacyErrorMessage(scene))
    }

    return new Error((rawErr && rawErr.message) || getPrivacyErrorMessage(scene))
  },

  async ensurePrivacyAuthorized(scene, action) {
    if (BYPASS_PRIVACY_FOR_TEST) {
      this.hidePrivacyDialog()
      return Promise.resolve(typeof action === 'function' ? action() : undefined)
    }

    this.initPrivacyFlow()

    const previousScene = this.__pendingPrivacyScene || 'general'
    const currentScene = String(scene || 'general')
    this.__pendingPrivacyScene = currentScene

    try {
      const privacySetting = await this.refreshPrivacySetting()
      if (privacySetting && privacySetting.needAuthorization) {
        await this.requestPrivacyAuthorization(currentScene)
      }

      return await Promise.resolve(typeof action === 'function' ? action() : undefined)
    } finally {
      if (this.__pendingPrivacyScene === currentScene) {
        this.__pendingPrivacyScene = previousScene
      }
      this.refreshPrivacySetting()
    }
  },

  runNativePrivacyApi(scene, action) {
    if (BYPASS_PRIVACY_FOR_TEST) {
      this.hidePrivacyDialog()
      return typeof action === 'function' ? action() : Promise.resolve()
    }

    return this.ensurePrivacyAuthorized(scene, action)
  },

  openPrivacyContract() {
    wx.navigateTo({
      url: '/pages/legal/privacy-policy/index',
    })
  },

  openUserAgreement() {
    wx.navigateTo({
      url: '/pages/legal/user-agreement/index',
    })
  },

  clearLegacyUnscopedBusinessCache() {
    clearGuestCache()
    clearLegacyBusinessCache()
  },

  syncAuthContext(token, user) {
    const normalizedToken = String(token || '')
    const nextUserId = normalizeUserId(resolveUserIdFromUser(user))
    const currentContext = getAuthContext()
    const previousUserId = normalizeUserId(
      this.globalData.currentUserId
      || currentContext.currentUserId
      || currentContext.lastLoginUserId
    )

    if (nextUserId && previousUserId && previousUserId !== nextUserId) {
      this.clearLegacyUnscopedBusinessCache()
    }

    if (nextUserId) {
      migrateLegacyBusinessCaches(nextUserId)
    }

    const nextContext = setAuthContext({
      currentUserId: normalizedToken ? nextUserId : '',
      currentSessionToken: normalizedToken,
      lastLoginUserId: nextUserId || currentContext.lastLoginUserId || previousUserId,
    })

    this.globalData.currentUserId = nextContext.currentUserId
    this.globalData.currentSessionToken = nextContext.currentSessionToken
    this.globalData.lastLoginUserId = nextContext.lastLoginUserId
    return nextContext
  },

  clearCurrentUserBusinessCache(userId, options) {
    const targetUserId = normalizeUserId(
      userId
      || this.globalData.currentUserId
      || this.globalData.lastLoginUserId
      || getAuthContext().lastLoginUserId
    )
    const clearMode = options && options.mode === 'sessionOnly' ? 'sessionOnly' : 'all'

    if (targetUserId) {
      if (clearMode === 'all') {
        clearUserCacheNamespace(targetUserId)
      } else {
        TRANSIENT_USER_CACHE_KEYS.forEach((cacheKey) => {
          removeUserCache(targetUserId, cacheKey)
        })
      }
    }

    this.clearLegacyUnscopedBusinessCache()
  },

  clearAuthState(options) {
    const currentContext = getAuthContext()
    const preservedLastLoginUserId = normalizeUserId(
      options && Object.prototype.hasOwnProperty.call(options, 'lastLoginUserId')
        ? options.lastLoginUserId
        : (this.globalData.currentUserId || currentContext.lastLoginUserId)
    )

    clearAllAuthRelatedCache()
    const nextContext = clearAuthContext(preservedLastLoginUserId)

    this.globalData.token = ''
    this.globalData.isLogin = false
    this.globalData.user = null
    this.globalData.currentUserId = ''
    this.globalData.currentSessionToken = ''
    this.globalData.lastLoginUserId = nextContext.lastLoginUserId
  },

  setAuth(token, user) {
    this.globalData.token = token || ''
    this.globalData.isLogin = Boolean(token)
    this.globalData.user = user || null
    this.syncAuthContext(token, user)

    wx.setStorageSync('token', token || '')
    wx.setStorageSync('user', user || null)
  },

  clearAuth() {
    this.clearAuthState()
  },
})
