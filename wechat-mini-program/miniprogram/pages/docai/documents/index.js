const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')
const {
  forgetDocumentName,
} = require('../../../utils/document-name')
const {
  getPickerErrorMessage,
  getUploadPickerHint,
  isPickerCancelError,
} = require('../../../utils/message-file-picker')
const {
  selectLocalFiles,
  uploadSelectedFiles,
} = require('../../../utils/upload-workflow')
const {
  isDocumentReady,
} = require('../../../utils/document-stage')
const {
  listAutofillResults,
  removeAutofillResult,
  updateAutofillResult,
} = require('../../../utils/autofill-result')
const {
  loadAutofillDraft,
  updateAutofillDraft,
} = require('../../../utils/autofill-draft')

const FAVORITE_STORAGE_KEY = 'docai_document_favorites'
const DOCUMENT_PICKER_CONTEXT_KEY = 'docai_autofill_document_picker_context'
const UPLOAD_EXTENSIONS = ['docx', 'xlsx', 'md', 'txt']
const CATEGORY_COMPLETED = 'completed'
const CATEGORIES = [
  { key: 'recent', label: '最近' },
  { key: CATEGORY_COMPLETED, label: '成表' },
  { key: 'favorite', label: '收藏' },
]

const TYPE_META = {
  doc: { badge: 'W', label: 'Word 文档', theme: 'word' },
  docx: { badge: 'W', label: 'Word 文档', theme: 'word' },
  xls: { badge: 'X', label: 'Excel 表格', theme: 'excel' },
  xlsx: { badge: 'X', label: 'Excel 表格', theme: 'excel' },
  pdf: { badge: 'P', label: 'PDF 文档', theme: 'pdf' },
  md: { badge: 'M', label: 'Markdown 文档', theme: 'markdown' },
  txt: { badge: 'T', label: '文本文件', theme: 'text' },
}

function getTimeValue(value) {
  const timestamp = value ? new Date(value).getTime() : 0
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function getFallbackType(fileName) {
  const parts = String(fileName || '').split('.')
  if (parts.length < 2) {
    return ''
  }

  return String(parts.pop() || '').toLowerCase()
}

function getUploadStatusText(status) {
  const normalizedStatus = String(status || '').toLowerCase()

  if (normalizedStatus === 'parsed') {
    return '已解析'
  }

  if (normalizedStatus === 'parsing') {
    return '解析中'
  }

  if (normalizedStatus === 'failed') {
    return '解析失败'
  }

  if (!normalizedStatus) {
    return '待处理'
  }

  return status
}

function getUploadStatusTone(status) {
  const normalizedStatus = String(status || '').toLowerCase()

  if (normalizedStatus === 'parsed') {
    return 'success'
  }

  if (normalizedStatus === 'parsing') {
    return 'warning'
  }

  if (normalizedStatus === 'failed') {
    return 'danger'
  }

  return 'plain'
}

function buildEntryId(kind, rawId) {
  return kind + ':' + String(rawId)
}

function buildSlideButtons(entryId, isFavorite, kind) {
  return [
    {
      text: isFavorite ? '取消收藏' : '收藏',
      extClass: 'document-swipe-btn document-swipe-btn--favorite',
      data: {
        action: 'favorite',
        id: String(entryId),
      },
    },
    {
      type: 'warn',
      text: '移除',
      extClass: 'document-swipe-btn document-swipe-btn--delete',
      data: {
        action: 'delete',
        id: String(entryId),
      },
    },
  ]
}

function uniqueIdList(list) {
  const result = []

  ;(Array.isArray(list) ? list : []).forEach((item) => {
    if (!(item || item === 0)) {
      return
    }

    const value = String(item)
    if (!value || result.indexOf(value) !== -1) {
      return
    }

    result.push(value)
  })

  return result
}

function getDefaultPrivacyDialog() {
  return {
    visible: false,
    scene: 'general',
    sceneText: '继续当前操作',
    referrer: '',
    agreeButtonId: 'docai-privacy-agree-btn',
  }
}

Page({
  data: {
    loading: false,
    downloadingEntryId: '',
    sharingEntryId: '',
    toolPressedKey: '',
    keyword: '',
    showFloatingSearch: false,
    userName: '团队成员',
    avatarText: '团',
    categories: CATEGORIES,
    activeCategory: 'recent',
    favoriteMap: {},
    allDocuments: [],
    documents: [],
    isEmpty: true,
    emptyTitle: '暂无匹配文档',
    emptyDesc: '上传文件后会自动出现在这里。',
    openSwipeId: '',
    uploadingFiles: false,
    lastPickedFiles: [],
    lastPickedCount: 0,
    lastPickedMoreCount: 0,
    uploadNoticeTitle: '',
    uploadNoticeItems: [],
    canRetryUpload: false,
    uploadPickerHint: getUploadPickerHint(),
    autofillDraftDocIds: [],
    autofillPickerMode: false,
    autofillPickerReturnUrl: '/pages/docai/autofill/index',
    privacyDialog: getDefaultPrivacyDialog(),
  },

  onLoad() {
    this.documentPollTimer = null
    this.lastRetryFiles = []
    const app = getApp()
    if (app && app.bindPrivacyDialog) {
      app.bindPrivacyDialog(this)
    }
  },

  async onShow() {
    if (!ensureLogin()) {
      return
    }

    this.loadUser()
    this.syncAutofillPickerContext()
    await this.loadDocuments()
  },

  onHide() {
    this.handleToolTouchEnd()
    this.stopDocumentPolling()
  },

  onUnload() {
    this.handleToolTouchEnd()
    this.stopDocumentPolling()
    const app = getApp()
    if (app && app.unbindPrivacyDialog) {
      app.unbindPrivacyDialog(this)
    }
  },

  async onPullDownRefresh() {
    if (!ensureLogin()) {
      wx.stopPullDownRefresh()
      return
    }

    this.loadUser()
    const success = await this.loadDocuments({ silent: true })
    wx.stopPullDownRefresh()

    wx.showToast({
      title: success ? '文档已刷新' : '刷新失败',
      icon: success ? 'success' : 'none',
    })
  },

  onPageScroll(e) {
    const showFloatingSearch = e.scrollTop > 108
    if (showFloatingSearch !== this.data.showFloatingSearch) {
      this.setData({ showFloatingSearch })
    }
  },

  loadUser() {
    const app = getApp()
    const appUser = (app && app.globalData && app.globalData.user) || null
    const cachedUser = wx.getStorageSync('user') || null
    const user = appUser || cachedUser || {}
    const userName = user.nickname || user.username || user.userName || '团队成员'
    const avatarText = String(userName || '文').trim().slice(0, 1).toUpperCase() || '文'

    this.setData({
      userName,
      avatarText,
    })
  },

  openPrivacyPolicy() {
    const app = getApp()
    if (app && app.openPrivacyContract) {
      app.openPrivacyContract()
      return
    }

    wx.navigateTo({
      url: '/pages/legal/privacy-policy/index',
    })
  },

  openUserAgreement() {
    const app = getApp()
    if (app && app.openUserAgreement) {
      app.openUserAgreement()
      return
    }

    wx.navigateTo({
      url: '/pages/legal/user-agreement/index',
    })
  },

  handlePrivacyAgree(e) {
    const app = getApp()
    if (app && app.handlePrivacyAgree) {
      app.handlePrivacyAgree(e)
    }
  },

  handlePrivacyDisagree() {
    const app = getApp()
    if (app && app.handlePrivacyDisagree) {
      app.handlePrivacyDisagree()
    }
  },

  ensurePrivacyAuthorized(scene, action) {
    const app = getApp()
    if (app && typeof app.ensurePrivacyAuthorized === 'function') {
      return app.ensurePrivacyAuthorized(scene, action)
    }
    return typeof action === 'function' ? action() : Promise.resolve()
  },

  handleToolTouchStart(e) {
    const key = String((e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.key) || '')
    if (!key || key === this.data.toolPressedKey) {
      return
    }

    this.setData({
      toolPressedKey: key,
    })
  },

  handleToolTouchEnd() {
    if (!this.data.toolPressedKey) {
      return
    }

    this.setData({
      toolPressedKey: '',
    })
  },

  handleToolTouchCancel() {
    this.handleToolTouchEnd()
  },

  syncAutofillPickerContext() {
    const context = wx.getStorageSync(DOCUMENT_PICKER_CONTEXT_KEY) || {}
    const autofillPickerMode = Boolean(context && typeof context === 'object' && context.returnUrl)
    const autofillPickerReturnUrl = autofillPickerMode
      ? String(context.returnUrl || '/pages/docai/autofill/index')
      : '/pages/docai/autofill/index'

    this.setData({
      autofillPickerMode,
      autofillPickerReturnUrl,
    })
  },

  clearAutofillPickerContext() {
    wx.removeStorageSync(DOCUMENT_PICKER_CONTEXT_KEY)
    this.setData({
      autofillPickerMode: false,
      autofillPickerReturnUrl: '/pages/docai/autofill/index',
    })
  },

  loadFavoriteMap() {
    const favoriteMap = wx.getStorageSync(FAVORITE_STORAGE_KEY) || {}
    return favoriteMap && typeof favoriteMap === 'object' ? favoriteMap : {}
  },

  saveFavoriteMap(favoriteMap) {
    wx.setStorageSync(FAVORITE_STORAGE_KEY, favoriteMap)
  },

  getTypeMeta(fileType) {
    const normalizedType = String(fileType || '').toLowerCase()
    const fallbackBadge = (normalizedType || 'F').slice(0, 1).toUpperCase()
    return TYPE_META[normalizedType] || {
      badge: fallbackBadge,
      label: '资料文档',
      theme: 'default',
    }
  },

  formatDate(value) {
    if (!value) {
      return '--'
    }

    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return '--'
    }

    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return month + '-' + day + ' ' + hours + ':' + minutes
  },

  formatSize(size) {
    if (!size && size !== 0) {
      return '-'
    }

    const kb = 1024
    const mb = kb * 1024

    if (size < kb) {
      return size + ' B'
    }

    if (size < mb) {
      return (size / kb).toFixed(1) + ' KB'
    }

    return (size / mb).toFixed(1) + ' MB'
  },

  getFavoriteState(favoriteMap, entryId, legacyKey) {
    return Boolean(favoriteMap[entryId] || (legacyKey !== undefined && favoriteMap[legacyKey]))
  },

  buildSourceDocument(item, favoriteMap, autofillDraftDocIds) {
    const title = item.fileName || item.title || '未命名文件'
    const fileType = String(item.fileType || getFallbackType(title)).toLowerCase()
    const typeMeta = this.getTypeMeta(fileType)
    const uploadedAt = item.createdAt || item.updatedAt || ''
    const sortTime = item.createdAt || item.updatedAt || ''
    const authorText = item.author || item.ownerName || item.userName || item.nickname || this.data.userName || '系统用户'
    const statusText = item.questionStageText || getUploadStatusText(item.uploadStatus)
    const previewText = String(item.docSummary || item.contentText || item.rawText || '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 60)
    const entryId = buildEntryId('source', item.id)
    const isFavorite = this.getFavoriteState(favoriteMap, entryId, item.id)
    const normalizedId = String(item.id)
    const stageKey = String(item.questionStageKey || item.uploadStatus || '').toLowerCase()
    const selectedForAutofill = (autofillDraftDocIds || []).indexOf(normalizedId) !== -1
    const autofillSelectable = UPLOAD_EXTENSIONS.indexOf(fileType) !== -1 && stageKey !== 'failed'

    return Object.assign({}, item, {
      kind: 'source',
      entryId,
      idText: entryId,
      rawId: item.id,
      title,
      fileType,
      statusText,
      fileBadge: typeMeta.badge,
      fileTheme: typeMeta.theme,
      fileTypeLabel: typeMeta.label,
      fileSizeText: this.formatSize(Number(item.fileSize) || 0),
      authorText,
      uploadedAtText: this.formatDate(uploadedAt),
      uploadedAtValue: getTimeValue(uploadedAt),
      sortTimeValue: getTimeValue(sortTime),
      previewText,
      statusTone: item.questionStageTone || getUploadStatusTone(item.uploadStatus),
      statusDesc: item.questionStageDesc || '',
      canChat: Boolean(item.canChat),
      isFavorite,
      selectedForAutofill,
      autofillSelectable,
      autofillActionText: selectedForAutofill ? '移出填表' : (autofillSelectable ? '加入填表' : '暂不可加入'),
      resultTagText: '',
      slideButtons: buildSlideButtons(entryId, isFavorite, 'source'),
      searchText: [
        title,
        authorText,
        typeMeta.label,
        fileType,
        statusText,
        previewText,
      ].join(' ').toLowerCase(),
    })
  },

  buildCompletedDocument(record, favoriteMap) {
    const title = record.outputName || record.fileName || '智能填表结果'
    const fileType = String(getFallbackType(title) || getFallbackType(record.templateName)).toLowerCase()
    const typeMeta = this.getTypeMeta(fileType)
    const entryId = String(record.recordId || buildEntryId('result', title))
    const isFavorite = this.getFavoriteState(favoriteMap, entryId)
    const previewText = String(record.summaryText || '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 60)

    return {
      kind: 'result',
      entryId,
      idText: entryId,
      rawId: record.recordId,
      recordId: record.recordId,
      templateId: record.templateId,
      title,
      fileType,
      statusText: '已完成填表',
      fileBadge: typeMeta.badge,
      fileTheme: typeMeta.theme,
      fileTypeLabel: typeMeta.label,
      fileSizeText: record.localFileSize
        ? this.formatSize(Number(record.localFileSize) || 0)
        : '云端结果',
      authorText: record.templateName || '智能填表',
      uploadedAtText: this.formatDate(record.createdAt),
      uploadedAtValue: getTimeValue(record.createdAt),
      sortTimeValue: getTimeValue(record.createdAt),
      previewText,
      summaryText: record.summaryText || '',
      statusTone: 'success',
      statusDesc: record.savedFilePath
        ? '已保存到本地，可继续转发或重新下载'
        : '结果文件已生成，可下载或转发',
      canChat: false,
      isFavorite,
      resultTagText: '成表',
      auditId: record.auditId || '',
      sourceCount: Number(record.sourceCount) || 0,
      filledCount: Number(record.filledCount) || 0,
      blankCount: Number(record.blankCount) || 0,
      totalSlots: Number(record.totalSlots) || 0,
      fillTimeMs: Number(record.fillTimeMs) || 0,
      savedFilePath: record.savedFilePath || '',
      lastDownloadedAt: record.lastDownloadedAt || '',
      slideButtons: buildSlideButtons(entryId, isFavorite, 'result'),
      searchText: [
        title,
        record.templateName || '',
        typeMeta.label,
        fileType,
        '已完成填表',
        previewText,
        record.auditId || '',
      ].join(' ').toLowerCase(),
    }
  },

  getCategoryDocuments(list) {
    const categoryKey = this.data.activeCategory
    const nextList = list.slice()

    if (categoryKey === 'favorite') {
      return nextList.filter((item) => item.isFavorite)
    }

    if (categoryKey === CATEGORY_COMPLETED) {
      return nextList
        .filter((item) => item.kind === 'result')
        .sort((left, right) => right.sortTimeValue - left.sortTimeValue)
    }

    return nextList
      .sort((left, right) => right.sortTimeValue - left.sortTimeValue)
      .slice(0, 20)
  },

  getEmptyStateCopy() {
    if (this.data.activeCategory === CATEGORY_COMPLETED) {
      return {
        title: '暂无成表文件',
        desc: '智能填表完成后，生成的结果文件会自动沉淀到这里。',
      }
    }

    if (this.data.activeCategory === 'favorite') {
      return {
        title: '暂无收藏内容',
        desc: '向左滑动文档或成表记录即可加入收藏。',
      }
    }

    return {
      title: '暂无匹配文档',
      desc: '上传文件或完成一次智能填表后，会自动出现在这里。',
    }
  },

  applyFilters() {
    const keyword = String(this.data.keyword || '').trim().toLowerCase()
    const categoryList = this.getCategoryDocuments(this.data.allDocuments)
    const documents = categoryList.filter((item) => {
      if (!keyword) {
        return true
      }

      return item.searchText.indexOf(keyword) !== -1
    })
    const openSwipeId = documents.some((item) => item.idText === this.data.openSwipeId)
      ? this.data.openSwipeId
      : ''
    const emptyState = this.getEmptyStateCopy()

    this.setData({
      documents,
      isEmpty: documents.length === 0,
      emptyTitle: emptyState.title,
      emptyDesc: emptyState.desc,
      openSwipeId,
    })
  },

  async loadDocuments(options) {
    const settings = Object.assign({
      silent: false,
      background: false,
    }, options || {})

    if (!settings.background) {
      this.setData({
        loading: true,
        openSwipeId: '',
      })
    }

    try {
      const favoriteMap = this.loadFavoriteMap()
      let autofillDraftDocIds = uniqueIdList((loadAutofillDraft().sourceDocIds || []).map(String))
      const res = await api.getSourceDocuments()
      const sourceDocuments = (res.data || [])
        .map((item) => this.buildSourceDocument(item, favoriteMap, autofillDraftDocIds))
      const selectedSourceDocs = sourceDocuments.filter((item) => item.selectedForAutofill)
      autofillDraftDocIds = uniqueIdList(selectedSourceDocs.map((item) => String(item.rawId || item.id)))
      updateAutofillDraft({
        sourceDocIds: autofillDraftDocIds,
        sourceDocs: selectedSourceDocs,
        parsedReadyCount: selectedSourceDocs.filter((item) => item.canChat).length,
      })
      const completedDocuments = listAutofillResults()
        .map((item) => this.buildCompletedDocument(item, favoriteMap))
      const documents = sourceDocuments
        .concat(completedDocuments)
        .sort((left, right) => right.sortTimeValue - left.sortTimeValue)

      this.setData({
        autofillDraftDocIds,
        favoriteMap,
        allDocuments: documents,
      })
      this.applyFilters()
      this.scheduleDocumentPolling()
      return true
    } catch (err) {
      this.stopDocumentPolling()
      if (!settings.silent) {
        wx.showToast({ title: '文档加载失败', icon: 'none' })
      }
      return false
    } finally {
      if (!settings.background) {
        this.setData({ loading: false })
      }
    }
  },

  hasProcessingDocuments() {
    return (this.data.allDocuments || []).some((item) => (
      item.kind === 'source'
      && !item.canChat
      && item.questionStageKey !== 'failed'
    ))
  },

  stopDocumentPolling() {
    if (this.documentPollTimer) {
      clearTimeout(this.documentPollTimer)
      this.documentPollTimer = null
    }
  },

  scheduleDocumentPolling() {
    this.stopDocumentPolling()

    if (!this.hasProcessingDocuments()) {
      return
    }

    this.documentPollTimer = setTimeout(async () => {
      this.documentPollTimer = null

      if (!ensureLogin()) {
        return
      }

      const success = await this.loadDocuments({
        silent: true,
        background: true,
      })

      if (!success) {
        this.scheduleDocumentPolling()
      }
    }, 3000)
  },

  onKeywordInput(e) {
    this.setData({
      keyword: e.detail.value || '',
    }, () => {
      this.applyFilters()
    })
  },

  clearKeyword() {
    this.setData({
      keyword: '',
      openSwipeId: '',
    }, () => {
      this.applyFilters()
    })
  },

  switchCategory(e) {
    const activeCategory = e.currentTarget.dataset.key || 'recent'
    this.setData({
      activeCategory,
      openSwipeId: '',
    }, () => {
      this.applyFilters()
    })
  },

  buildPickedFilePreview(files) {
    return (Array.isArray(files) ? files : []).map((file) => ({
      key: String(Math.random()).slice(2),
      name: file.name || '鏈懡鍚嶆枃浠?',
      sizeText: this.formatSize(Number(file.size) || 0),
    }))
  },

  syncPickedFilePreview(files) {
    const pickedFiles = this.buildPickedFilePreview(files)
    this.setData({
      lastPickedFiles: pickedFiles.slice(0, 6),
      lastPickedCount: pickedFiles.length,
      lastPickedMoreCount: Math.max(0, pickedFiles.length - 6),
    })
  },

  syncUploadNotice(title, items) {
    const noticeItems = Array.isArray(items) ? items.filter(Boolean) : []
    this.setData({
      uploadNoticeTitle: noticeItems.length ? (title || '涓婁紶鎻愰啋') : '',
      uploadNoticeItems: noticeItems,
      canRetryUpload: Boolean(this.lastRetryFiles && this.lastRetryFiles.length),
    })
  },

  async retryFailedUpload() {
    if (!ensureLogin() || this.data.uploadingFiles) {
      return
    }

    if (!(this.lastRetryFiles && this.lastRetryFiles.length)) {
      wx.showToast({
        title: '褰撳墠娌℃湁鍙噸璇曠殑鏂囦欢',
        icon: 'none',
      })
      return
    }

    return this.runUploadFlow({
      retryFiles: this.lastRetryFiles.slice(),
      retrying: true,
    })
  },

  async chooseAndUploadLegacy() {
    try {
      await this.ensurePrivacyAuthorized('document-upload', async () => {
        const pick = await chooseMessageFileAsync({
          count: 9,
          type: 'file',
          extension: UPLOAD_EXTENSIONS,
        })
        const files = pick.tempFiles || []

        if (files.length === 0) {
          return
        }

        const pickedFiles = files.map((file) => ({
          key: String(Math.random()).slice(2),
          name: resolvePickedFileName(file),
          sizeText: this.formatSize(Number(file.size) || 0),
        }))

        this.setData({
          lastPickedFiles: pickedFiles.slice(0, 6),
          lastPickedCount: pickedFiles.length,
          lastPickedMoreCount: Math.max(0, pickedFiles.length - 6),
        })

        wx.showLoading({
          title: '正在上传',
          mask: true,
        })

        const uploadedDocIds = []

        for (let index = 0; index < files.length; index += 1) {
          const file = files[index]
          const uploadRes = await api.uploadDocument(
            resolvePickedFilePath(file),
            resolvePickedFileName(file)
          )
          const uploadedDocId = uploadRes && uploadRes.data
            ? uploadRes.data.id
            : (uploadRes && uploadRes.id)
          if (uploadedDocId || uploadedDocId === 0) {
            uploadedDocIds.push(uploadedDocId)
          }
        }

        const refreshed = await this.loadDocuments({ silent: true })
        if (uploadedDocIds.length) {
          this.scheduleDocumentPolling()
        }
        wx.showToast({
          title: refreshed ? '上传成功，后台正在解析' : '上传成功，下拉刷新可查看',
          icon: 'success',
        })
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][documents] chooseAndUpload failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '上传失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
    }
  },

  async chooseAndUpload() {
    return this.runUploadFlow()
  },

  async runUploadFlow(options) {
    const settings = Object.assign({
      retryFiles: [],
      retrying: false,
    }, options || {})

    if (!ensureLogin() || this.data.uploadingFiles) {
      return
    }

    try {
      this.setData({ uploadingFiles: true })

      let pickedFiles = []
      let validFiles = []
      let rejectedFiles = []

      if (Array.isArray(settings.retryFiles) && settings.retryFiles.length) {
        validFiles = settings.retryFiles.slice()
        pickedFiles = validFiles.slice()
      } else {
        const selection = await this.ensurePrivacyAuthorized('document-upload', () => selectLocalFiles({
          count: 9,
          allowedExtensions: UPLOAD_EXTENSIONS,
        }))

        pickedFiles = selection.pickedFiles || []
        validFiles = selection.validFiles || []
        rejectedFiles = selection.rejectedFiles || []
        this.syncPickedFilePreview(pickedFiles)
      }

      if (!validFiles.length) {
        this.lastRetryFiles = []
        this.syncUploadNotice('鏂囦欢绛涢€夌粨鏋?', rejectedFiles.map((item) => item.issueText))
        wx.showToast({
          title: rejectedFiles.length ? '鎵€閫夋枃浠朵笉鍙笂浼?' : '鏈€夋嫨鏂囦欢',
          icon: 'none',
        })
        return
      }

      wx.showLoading({
        title: settings.retrying ? '姝ｅ湪閲嶈瘯' : '姝ｅ湪涓婁紶',
        mask: true,
      })

      const uploadResult = await uploadSelectedFiles(validFiles, {
        beforeUpload: () => api.checkUploadConnection(),
        uploadOne: (file) => api.uploadDocument(file.path, file.name),
        failureMessage: '鏂囦欢涓婁紶澶辫触',
        onProgress: ({ index, total }) => {
          wx.showLoading({
            title: '涓婁紶 ' + (index + 1) + '/' + total,
            mask: true,
          })
        },
      })

      const uploadedDocIds = uploadResult.successItems
        .map((item) => {
          const response = item && item.response
          if (!response) {
            return null
          }

          return response.data ? response.data.id : response.id
        })
        .filter((item) => item || item === 0)

      this.lastRetryFiles = uploadResult.failedItems.map((item) => item.file)

      const noticeItems = rejectedFiles
        .map((item) => item.issueText)
        .concat(uploadResult.issueTexts || [])

      this.syncUploadNotice('鏈畬鎴愮殑鏂囦欢', noticeItems)

      const refreshed = await this.loadDocuments({ silent: true })
      if (uploadedDocIds.length) {
        this.scheduleDocumentPolling()
      }

      if (uploadResult.successCount > 0) {
        wx.showToast({
          title: noticeItems.length
            ? '宸查儴鍒嗕笂浼狅紝鍏朵綑璇风湅鎻愮ず'
            : (refreshed ? '涓婁紶鎴愬姛锛屽悗鍙版鍦ㄨВ鏋?' : '涓婁紶鎴愬姛'),
          icon: 'success',
        })
        return
      }

      wx.showToast({
        title: settings.retrying ? '閲嶈瘯鍚庝粛鏈笂浼犳垚鍔?' : '娌℃湁鎴愬姛涓婁紶鏂囦欢',
        icon: 'none',
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][documents] chooseAndUpload failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '涓婁紶澶辫触'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ uploadingFiles: false })
    }
  },

  getDocumentFromEvent(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (!Number.isNaN(index) && this.data.documents[index]) {
      return this.data.documents[index]
    }

    const id = e.currentTarget.dataset.id
    if (id || id === 0) {
      return this.findDocumentById(id)
    }

    return null
  },

  findDocumentById(id) {
    const entryId = String(id)
    for (let index = 0; index < this.data.allDocuments.length; index += 1) {
      const item = this.data.allDocuments[index]
      if (String(item.idText) === entryId) {
        return item
      }
    }

    return null
  },

  previewDocumentItem(item) {
    if (!item) {
      return
    }

    if (item.kind === 'result') {
      const content = [
        '状态：已完成填表',
        item.templateId ? '\n模板 ID：' + item.templateId : '',
        item.auditId ? '\n审计编号：' + item.auditId : '',
        item.sourceCount ? '\n来源文档：' + item.sourceCount + ' 份' : '',
        '\n已填字段：' + item.filledCount,
        '\n待补字段：' + item.blankCount,
        '\n总字段数：' + item.totalSlots,
        item.fillTimeMs ? '\n处理耗时：' + item.fillTimeMs + 'ms' : '',
        item.savedFilePath
          ? '\n本地状态：已保存到本地'
          : '\n本地状态：尚未保存到本地',
        item.summaryText ? '\n\n' + String(item.summaryText).slice(0, 760) : '',
      ].join('')

      wx.showModal({
        title: item.title || '成表预览',
        content: String(content).slice(0, 900),
        showCancel: false,
      })
      return
    }

    const summary = item.docSummary || item.contentText || item.rawText || item.previewText || ''
    const content = [
      '状态：' + (item.questionStageText || getUploadStatusText(item.uploadStatus)),
      summary
        ? '\n' + String(summary).slice(0, 820)
        : '\n' + (item.statusDesc || (item.uploadStatus === 'parsing'
          ? '文档已上传，DocAI 正在后台处理，请稍后刷新列表查看。'
          : '当前暂无可预览摘要。')),
    ].join('')

    wx.showModal({
      title: item.title || '文档预览',
      content: String(content).slice(0, 900),
      showCancel: false,
    })
  },

  previewDoc(e) {
    const item = this.getDocumentFromEvent(e)
    if (!item) {
      return
    }

    if (this.data.openSwipeId && item.idText === this.data.openSwipeId) {
      this.setData({ openSwipeId: '' })
      return
    }

    this.previewDocumentItem(item)
  },

  handleSwipeShow(e) {
    const id = String(e.currentTarget.dataset.id || '')
    if (!id || id === this.data.openSwipeId) {
      return
    }

    this.setData({ openSwipeId: id })
  },

  handleSwipeHide(e) {
    const id = String(e.currentTarget.dataset.id || '')
    if (!id || this.data.openSwipeId !== id) {
      return
    }

    this.setData({ openSwipeId: '' })
  },

  handleSwipeButtonTap(e) {
    const detail = e.detail || {}
    const data = detail.data || {}
    const item = this.findDocumentById(data.id)

    this.setData({ openSwipeId: '' })

    if (!item) {
      return
    }

    if (data.action === 'favorite') {
      this.toggleFavoriteByItem(item)
      return
    }

    if (data.action === 'delete') {
      this.deleteDocumentByItem(item)
    }
  },

  goChat() {
    wx.removeStorageSync('docai_current_doc')
    wx.switchTab({ url: '/pages/docai/chat/index' })
  },

  goDashboard() {
    wx.switchTab({ url: '/pages/docai/dashboard/index' })
  },

  openChatWithItem(item) {
    if (!item) {
      return
    }

    if (item.kind !== 'source') {
      wx.showToast({
        title: '成表文件不支持直接发起 AI 对话',
        icon: 'none',
      })
      return
    }

    if (!isDocumentReady(item)) {
      this.scheduleDocumentPolling()
      wx.showToast({
        title: item.questionStageText || '文档尚未就绪',
        icon: 'none',
      })
      return
    }

    wx.setStorageSync('docai_current_doc', {
      id: item.rawId,
      title: item.title,
      uploadStatus: item.uploadStatus,
      questionStageKey: item.questionStageKey,
      questionStageText: item.questionStageText,
      canChat: true,
    })
    wx.switchTab({ url: '/pages/docai/chat/index' })
  },

  canUseAsAutofillSource(item) {
    if (!item || item.kind !== 'source') {
      return {
        ok: false,
        message: '当前只有来源文档可以加入智能填表',
      }
    }

    const fileType = String(item.fileType || getFallbackType(item.title)).toLowerCase()
    if (UPLOAD_EXTENSIONS.indexOf(fileType) === -1) {
      return {
        ok: false,
        message: '当前文件类型暂不支持作为智能填表数据源',
      }
    }

    const stageKey = String(item.questionStageKey || item.uploadStatus || '').toLowerCase()
    if (stageKey === 'failed') {
      return {
        ok: false,
        message: '解析失败的文档不能加入智能填表',
      }
    }

    return { ok: true }
  },

  getSelectedAutofillSourceDocs(sourceDocIds) {
    const normalizedIds = uniqueIdList(sourceDocIds)

    return (this.data.allDocuments || []).filter((item) => (
      item.kind === 'source'
      && normalizedIds.indexOf(String(item.rawId || item.id)) !== -1
    ))
  },

  syncAutofillSelection(sourceDocIds) {
    const normalizedIds = uniqueIdList(sourceDocIds)
    const allDocuments = (this.data.allDocuments || []).map((item) => {
      if (item.kind !== 'source') {
        return item
      }

      const selectedForAutofill = normalizedIds.indexOf(String(item.rawId || item.id)) !== -1
      return Object.assign({}, item, {
        selectedForAutofill,
        autofillActionText: selectedForAutofill
          ? '移出填表'
          : (item.autofillSelectable ? '加入填表' : '暂不可加入'),
      })
    })

    this.setData({
      autofillDraftDocIds: normalizedIds,
      allDocuments,
      openSwipeId: '',
    }, () => {
      this.applyFilters()
    })
  },

  saveAutofillSourceSelection(sourceDocIds) {
    const selectedSourceDocs = this.getSelectedAutofillSourceDocs(sourceDocIds)
    const nextSourceDocIds = uniqueIdList(selectedSourceDocs.map((item) => String(item.rawId || item.id)))

    updateAutofillDraft({
      sourceDocIds: nextSourceDocIds,
      sourceDocs: selectedSourceDocs,
      parsedReadyCount: selectedSourceDocs.filter((item) => item.canChat).length,
    })
    this.syncAutofillSelection(nextSourceDocIds)
    return nextSourceDocIds
  },

  toggleAutofillSourceByItem(item) {
    if (!ensureLogin() || !item || item.kind !== 'source') {
      return false
    }

    const sourceId = String(item.rawId || item.id || '')
    if (!sourceId) {
      return false
    }

    const currentIds = uniqueIdList(this.data.autofillDraftDocIds)
    const currentIndex = currentIds.indexOf(sourceId)

    if (currentIndex === -1) {
      const validation = this.canUseAsAutofillSource(item)
      if (!validation.ok) {
        wx.showToast({
          title: validation.message,
          icon: 'none',
        })
        return false
      }

      currentIds.push(sourceId)
      this.saveAutofillSourceSelection(currentIds)
      wx.showToast({
        title: '已加入智能填表',
        icon: 'success',
      })
      return true
    }

    currentIds.splice(currentIndex, 1)
    this.saveAutofillSourceSelection(currentIds)
    wx.showToast({
      title: '已移出智能填表',
      icon: 'none',
    })
    return false
  },

  toggleAutofillSource(e) {
    const item = this.getDocumentFromEvent(e)
    if (!item) {
      return
    }

    this.toggleAutofillSourceByItem(item)
  },

  openAutofillWithItem(item) {
    if (!ensureLogin()) {
      return
    }

    if (item && item.kind === 'source') {
      const sourceId = String(item.rawId || item.id || '')
      const currentIds = uniqueIdList(this.data.autofillDraftDocIds)

      if (currentIds.indexOf(sourceId) === -1) {
        const validation = this.canUseAsAutofillSource(item)
        if (!validation.ok) {
          wx.showToast({
            title: validation.message,
            icon: 'none',
          })
          return
        }

        currentIds.push(sourceId)
        this.saveAutofillSourceSelection(currentIds)
      }
    }

    if (this.data.autofillPickerMode) {
      this.finishAutofillPicker()
      return
    }

    wx.navigateTo({
      url: '/pages/docai/autofill/index',
    })
  },

  openAutofillFromDocument(e) {
    const item = this.getDocumentFromEvent(e)
    this.openAutofillWithItem(item)
  },

  finishAutofillPicker() {
    if (!ensureLogin()) {
      return
    }

    const selectedCount = uniqueIdList(this.data.autofillDraftDocIds).length
    if (selectedCount <= 0) {
      wx.showToast({
        title: '请先至少选择 1 份数据源',
        icon: 'none',
      })
      return
    }

    const targetUrl = this.data.autofillPickerReturnUrl || '/pages/docai/autofill/index'
    this.clearAutofillPickerContext()
    wx.navigateTo({
      url: targetUrl,
    })
  },

  cancelAutofillPicker() {
    this.clearAutofillPickerContext()
    wx.showToast({
      title: '已退出数据源选择',
      icon: 'none',
    })
  },

  toggleFavoriteByItem(item) {
    if (!item) {
      return
    }

    const favoriteMap = Object.assign({}, this.data.favoriteMap)
    const entryId = item.entryId
    const legacyKey = item.kind === 'source' ? item.rawId : ''
    const nextValue = !this.getFavoriteState(favoriteMap, entryId, legacyKey)

    if (nextValue) {
      favoriteMap[entryId] = true
    } else {
      delete favoriteMap[entryId]
    }

    if (legacyKey || legacyKey === 0) {
      delete favoriteMap[legacyKey]
    }

    this.saveFavoriteMap(favoriteMap)

    const allDocuments = this.data.allDocuments.map((doc) => {
      if (String(doc.idText) !== String(entryId)) {
        return doc
      }

      return Object.assign({}, doc, {
        isFavorite: nextValue,
        slideButtons: buildSlideButtons(doc.idText, nextValue, doc.kind),
      })
    })

    this.setData({
      favoriteMap,
      allDocuments,
      openSwipeId: '',
    }, () => {
      this.applyFilters()
    })

    wx.showToast({
      title: nextValue ? '已收藏' : '已取消收藏',
      icon: 'none',
    })
  },

  async removeResultRecordByItem(item) {
    if (!item || item.kind !== 'result') {
      return
    }

    wx.showModal({
      title: '移除成表记录',
      content: '该操作只会从小程序文档区移除这条成表记录，不会修改 DocAI 后端结果文件，是否继续？',
      success: async (res) => {
        if (!res.confirm) {
          return
        }

        try {
          removeAutofillResult(item.recordId)

          const favoriteMap = Object.assign({}, this.data.favoriteMap)
          delete favoriteMap[item.entryId]
          this.saveFavoriteMap(favoriteMap)

          await this.loadDocuments({ silent: true })
          wx.showToast({
            title: '已移除记录',
            icon: 'success',
          })
        } catch (err) {
          wx.showToast({
            title: '移除失败',
            icon: 'none',
          })
        }
      },
    })
  },

  deleteDocumentByItem(item) {
    if (!item) {
      return
    }

    if (item.kind === 'result') {
      this.removeResultRecordByItem(item)
      return
    }

    wx.showModal({
      title: '确认移除',
      content: '移除后会从当前小程序文档列表中隐藏，后端存储文件会保留，是否继续？',
      success: async (res) => {
        if (!res.confirm) {
          return
        }

        try {
          await api.deleteDocument(item.rawId)
          forgetDocumentName(item.rawId)

          const favoriteMap = Object.assign({}, this.data.favoriteMap)
          delete favoriteMap[item.entryId]
          delete favoriteMap[item.rawId]
          this.saveFavoriteMap(favoriteMap)
          const nextAutofillDocIds = uniqueIdList(this.data.autofillDraftDocIds)
            .filter((docId) => docId !== String(item.rawId))
          this.saveAutofillSourceSelection(nextAutofillDocIds)

          await this.loadDocuments()
          wx.showToast({
            title: '已移除',
            icon: 'success',
          })
        } catch (err) {
          wx.showToast({
            title: '移除失败',
            icon: 'none',
          })
        }
      },
    })
  },

  chooseDownloadAction() {
    return new Promise((resolve) => {
      wx.showActionSheet({
        itemList: ['下载并打开', '仅下载到本地'],
        success: (res) => {
          resolve(res.tapIndex === 0 ? 'open' : 'save')
        },
        fail: () => resolve(''),
      })
    })
  },

  downloadFileAsync(options) {
    return new Promise((resolve, reject) => {
      wx.downloadFile(Object.assign({}, options, {
        success: resolve,
        fail: reject,
      }))
    })
  },

  saveFileAsync(tempFilePath) {
    return new Promise((resolve, reject) => {
      wx.saveFile({
        tempFilePath,
        success: resolve,
        fail: reject,
      })
    })
  },

  openDocumentAsync(filePath, fileType) {
    return new Promise((resolve, reject) => {
      wx.openDocument({
        filePath,
        fileType: fileType || undefined,
        showMenu: true,
        success: resolve,
        fail: reject,
      })
    })
  },

  shareFileMessageAsync(filePath, fileName) {
    return new Promise((resolve, reject) => {
      if (typeof wx.shareFileMessage !== 'function') {
        reject(new Error('当前微信环境不支持直接转发文件'))
        return
      }

      wx.shareFileMessage({
        filePath,
        fileName: fileName || '智能填表结果',
        success: resolve,
        fail: reject,
      })
    })
  },

  async downloadCompletedItem(item, action) {
    if (!item || item.kind !== 'result') {
      return
    }

    if (!ensureLogin() || this.data.downloadingEntryId === item.idText) {
      return
    }

    if (!item.templateId) {
      wx.showToast({ title: '当前成表记录缺少模板 ID', icon: 'none' })
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(item.templateId)
    if (!downloadUrl) {
      wx.showToast({ title: '结果下载地址无效', icon: 'none' })
      return
    }

    this.setData({ downloadingEntryId: item.idText })
    wx.showLoading({ title: '正在下载结果', mask: true })

    try {
      const downloadRes = await this.downloadFileAsync({
        url: downloadUrl,
        header: {
          Authorization: 'Bearer ' + token,
        },
        timeout: 120000,
      })

      if (!downloadRes || Number(downloadRes.statusCode) !== 200 || !downloadRes.tempFilePath) {
        if (Number(downloadRes && downloadRes.statusCode) === 401) {
          throw new Error('登录已过期，请重新登录')
        }
        throw new Error('成表结果下载失败')
      }

      if (action === 'save') {
        const saveRes = await this.saveFileAsync(downloadRes.tempFilePath)
        updateAutofillResult(item.recordId, {
          savedFilePath: saveRes.savedFilePath || '',
          lastDownloadedAt: new Date().toISOString(),
        })
        await this.loadDocuments({ silent: true, background: true })
        wx.showToast({ title: '已下载到本地', icon: 'success' })
        return
      }

      updateAutofillResult(item.recordId, {
        lastDownloadedAt: new Date().toISOString(),
      })
      wx.showToast({ title: '下载完成', icon: 'success' })

      try {
        await this.openDocumentAsync(downloadRes.tempFilePath, item.fileType)
      } catch (openErr) {
        wx.showToast({ title: '已下载，可稍后打开', icon: 'none' })
      }
    } catch (err) {
      wx.showToast({
        title: (err && err.message) || '成表结果下载失败',
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ downloadingEntryId: '' })
    }
  },

  async shareCompletedItem(item) {
    if (!item || item.kind !== 'result') {
      return
    }

    if (!ensureLogin() || this.data.sharingEntryId === item.idText) {
      return
    }

    if (!item.templateId) {
      wx.showToast({ title: '当前成表记录缺少模板 ID', icon: 'none' })
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(item.templateId)
    if (!downloadUrl) {
      wx.showToast({ title: '结果下载地址无效', icon: 'none' })
      return
    }

    this.setData({ sharingEntryId: item.idText })
    wx.showLoading({ title: '正在准备转发', mask: true })

    try {
      const downloadRes = await this.downloadFileAsync({
        url: downloadUrl,
        header: {
          Authorization: 'Bearer ' + token,
        },
        timeout: 120000,
      })

      if (!downloadRes || Number(downloadRes.statusCode) !== 200 || !downloadRes.tempFilePath) {
        if (Number(downloadRes && downloadRes.statusCode) === 401) {
          throw new Error('登录已过期，请重新登录')
        }
        throw new Error('结果文件下载失败')
      }

      try {
        await this.shareFileMessageAsync(downloadRes.tempFilePath, item.title)
        updateAutofillResult(item.recordId, {
          lastDownloadedAt: new Date().toISOString(),
        })
        wx.showToast({ title: '已打开转发面板', icon: 'success' })
        return
      } catch (shareErr) {
        const errorMessage = String((shareErr && shareErr.errMsg) || (shareErr && shareErr.message) || '')
        if (errorMessage.indexOf('cancel') !== -1) {
          return
        }
      }

      await this.openDocumentAsync(downloadRes.tempFilePath, item.fileType)
      wx.showToast({
        title: '当前环境不支持直接转发，请在文档菜单中继续转发',
        icon: 'none',
      })
    } catch (err) {
      wx.showToast({
        title: (err && err.message) || '转发准备失败',
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ sharingEntryId: '' })
    }
  },

  async handleResultDownload(e) {
    const item = this.getDocumentFromEvent(e)
    if (!item) {
      return
    }

    const action = await this.chooseDownloadAction()
    if (!action) {
      return
    }

    this.downloadCompletedItem(item, action)
  },

  handleResultShare(e) {
    const item = this.getDocumentFromEvent(e)
    if (!item) {
      return
    }

    this.shareCompletedItem(item)
  },

  onShareAppMessage() {
    return {
      title: '智能文档处理系统',
      path: '/pages/docai/documents/index',
    }
  },

  openMoreActions(e) {
    const item = this.getDocumentFromEvent(e)
    if (!item) {
      return
    }

    this.setData({ openSwipeId: '' })

    const favoriteAction = item.isFavorite ? '取消收藏' : '收藏'
    const actions = item.kind === 'result'
      ? ['预览', '下载结果', '转发结果', favoriteAction, '移除记录']
      : ['预览', 'AI 对话', item.selectedForAutofill ? '移出智能填表' : '加入智能填表', '前往智能填表', favoriteAction, '移除']

    wx.showActionSheet({
      itemList: actions,
      success: async (res) => {
        const action = actions[res.tapIndex]

        if (action === '预览') {
          this.previewDocumentItem(item)
          return
        }

        if (action === 'AI 对话') {
          this.openChatWithItem(item)
          return
        }

        if (action === '加入智能填表' || action === '移出智能填表') {
          this.toggleAutofillSourceByItem(item)
          return
        }

        if (action === '前往智能填表') {
          this.openAutofillWithItem(item)
          return
        }

        if (action === '下载结果') {
          const downloadAction = await this.chooseDownloadAction()
          if (downloadAction) {
            this.downloadCompletedItem(item, downloadAction)
          }
          return
        }

        if (action === '转发结果') {
          this.shareCompletedItem(item)
          return
        }

        if (action === '移除' || action === '移除记录') {
          this.deleteDocumentByItem(item)
          return
        }

        if (action === '收藏' || action === '取消收藏') {
          this.toggleFavoriteByItem(item)
        }
      },
    })
  },

})
