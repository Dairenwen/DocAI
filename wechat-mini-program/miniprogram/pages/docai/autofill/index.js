const api = require('../../../api/docai')
const config = require('../../../config')
const { ensureLogin } = require('../../../utils/auth')
const {
  getPickerErrorMessage,
  getUploadPickerHint,
  isPickerCancelError,
} = require('../../../utils/message-file-picker')
const {
  normalizeFileName,
} = require('../../../utils/document-name')
const {
  selectLocalFiles,
  uploadSelectedFiles,
} = require('../../../utils/upload-workflow')
const {
  loadAutofillDraft,
  updateAutofillDraft,
  clearAutofillDraft,
  saveAutofillResultSession,
  loadAutofillResultSession,
  clearAutofillResultSession,
  getFileTypeFromName,
} = require('../../../utils/autofill-draft')
const {
  rememberAutofillResult,
  updateAutofillResult,
} = require('../../../utils/autofill-result')
const {
  AUTOFILL_SOURCE_UPLOAD_POLICY,
  AUTOFILL_TEMPLATE_UPLOAD_POLICY,
  buildPolicyRules,
} = require('../../../utils/upload-policy')

const WEB_AUTOFILL_NOTICE_KEY = 'docai_autofill_web_notice_ack'
const DOCUMENT_PICKER_CONTEXT_KEY = 'docai_autofill_document_picker_context'
const SOURCE_EXTENSIONS = ['docx', 'xlsx', 'txt', 'md']
const TEMPLATE_EXTENSIONS = ['docx', 'xlsx']
const SOURCE_POLL_INTERVAL = 4000
const SOURCE_UPLOAD_RULES = buildPolicyRules(AUTOFILL_SOURCE_UPLOAD_POLICY, {
  retryText: '上传失败后可重新选择后再次上传。',
})
const TEMPLATE_UPLOAD_RULES = buildPolicyRules(AUTOFILL_TEMPLATE_UPLOAD_POLICY, {
  retryText: '每次只保留 1 份模板，重新选择会覆盖当前模板。',
})

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '')
}

function normalizeText(value) {
  return String(value || '').trim()
}

function toNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function getRemoteWebBaseUrl() {
  return normalizeBaseUrl(config && (
    config.remoteWebBaseUrl
    || config.webviewUrl
    || config.WEBVIEW_URL
  ))
}

function canUseRemoteWebAutofill() {
  return Boolean(config && config.enableWebviewAssist) && /^https:\/\//i.test(getRemoteWebBaseUrl())
}

function getQuestionStageKey(item) {
  return normalizeText(item && item.questionStageKey).toLowerCase()
}

function getUploadStatus(item) {
  return normalizeText(item && item.uploadStatus).toLowerCase()
}

function toStageKey(item) {
  const stageKey = getQuestionStageKey(item)
  if (stageKey) {
    return stageKey
  }

  const uploadStatus = getUploadStatus(item)
  if (uploadStatus === 'failed') {
    return 'failed'
  }
  if (uploadStatus === 'parsing') {
    return 'parsing'
  }
  if (item && (item.canChat === true || uploadStatus === 'parsed' || item.docSummary)) {
    return 'ready'
  }

  return 'uploaded'
}

function buildSourceSummary(list) {
  return (Array.isArray(list) ? list : []).reduce((summary, item) => {
    summary.total += 1

    const stageKey = toStageKey(item)
    if (stageKey === 'ready') {
      summary.ready += 1
    } else if (stageKey === 'failed') {
      summary.failed += 1
    } else if (stageKey === 'parsing' || stageKey === 'indexing') {
      summary.parsing += 1
    } else {
      summary.uploaded += 1
    }

    return summary
  }, {
    total: 0,
    ready: 0,
    parsing: 0,
    failed: 0,
    uploaded: 0,
  })
}

function buildSourceCard(item, selectedIds) {
  const stageKey = toStageKey(item)
  const selected = (selectedIds || []).indexOf(String(item.id)) !== -1
  const fileType = normalizeText(item && item.fileType).toLowerCase()
  const fileName = normalizeFileName(item && (item.fileName || item.title)) || '未命名文档'
  let stageTone = 'plain'

  if (stageKey === 'ready') {
    stageTone = 'success'
  } else if (stageKey === 'parsing') {
    stageTone = 'warning'
  } else if (stageKey === 'indexing') {
    stageTone = 'info'
  } else if (stageKey === 'failed') {
    stageTone = 'danger'
  }

  return Object.assign({}, item, {
    id: String(item.id),
    fileName,
    fileType,
    typeText: String(fileType || 'file').toUpperCase(),
    fileTheme: fileType === 'xlsx' || fileType === 'xls' ? 'excel' : (fileType === 'pdf' ? 'pdf' : 'word'),
    stageKey,
    stageTone,
    stageText: normalizeText(item.questionStageText) || '待处理',
    stageDisplayText: normalizeText(item.questionStageText) || '待处理',
    stageDesc: normalizeText(item.questionStageDesc) || '当前资料还没有可用的处理说明。',
    authorText: normalizeText(item.author || item.ownerName || item.userName || item.nickname) || '系统用户',
    updatedAtText: formatDateTime(item.updatedAt || item.createdAt),
    fileSizeText: formatSize(item.fileSize),
    selected,
    selectDisabled: !selected && stageKey === 'failed',
    actionText: selected ? '移出本次填表' : (stageKey === 'failed' ? '不可选择' : '加入本次填表'),
  })
}

function summarizeSelection(list) {
  return (Array.isArray(list) ? list : []).reduce((summary, item) => {
    summary.total += 1

    const stageKey = toStageKey(item)
    if (stageKey === 'ready') {
      summary.ready += 1
    } else if (stageKey === 'failed') {
      summary.failed += 1
    } else {
      summary.pending += 1
    }

    return summary
  }, {
    total: 0,
    ready: 0,
    pending: 0,
    failed: 0,
  })
}

function buildExecutionBlockedText(summary) {
  if (!summary.total) {
    return '请先选择至少 1 份数据源资料，再开始智能填表。'
  }
  if (summary.failed > 0) {
    return '当前选择中有 ' + summary.failed + ' 份失败资料，请先移出后再继续。'
  }
  if (summary.pending > 0) {
    return '当前仍有 ' + summary.pending + ' 份资料未就绪，请刷新状态后再执行。'
  }

  return '所有已选资料都已进入可填表状态，可以直接开始执行。'
}

function formatSize(size) {
  const numericSize = Number(size) || 0
  const kb = 1024
  const mb = kb * 1024

  if (numericSize < kb) {
    return numericSize + ' B'
  }
  if (numericSize < mb) {
    return (numericSize / kb).toFixed(1) + ' KB'
  }

  return (numericSize / mb).toFixed(1) + ' MB'
}

function formatDateTime(value) {
  const timestamp = value ? new Date(value).getTime() : 0
  if (!timestamp) {
    return '--'
  }

  const date = new Date(timestamp)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return month + '-' + day + ' ' + hours + ':' + minutes
}

function normalizeDecision(item) {
  if (!item || typeof item !== 'object') {
    return null
  }

  const confidence = Number(item.finalConfidence || item.confidence || 0) || 0
  return {
    fieldName: normalizeText(item.fieldName || item.slotName || item.placeholder || item.key) || '未命名字段',
    finalValue: normalizeText(item.finalValue || item.value || item.outputValue),
    confidenceText: Math.round(confidence * 100) + '%',
    reason: normalizeText(item.reason),
  }
}

function normalizeResultPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }

  const outputName = normalizeText(payload.outputName || payload.fileName)
  const templateName = normalizeText(payload.templateName)
  return {
    recordId: normalizeText(payload.recordId),
    templateId: normalizeText(payload.templateId),
    auditId: normalizeText(payload.auditId),
    templateName: templateName || '未命名模板',
    outputName: outputName || templateName || '智能填表结果',
    outputFile: normalizeText(payload.outputFile),
    fileType: normalizeText(payload.fileType).toLowerCase() || getFileTypeFromName(outputName || templateName),
    summaryText: normalizeText(payload.summaryText),
    filledCount: toNumber(payload.filledCount),
    blankCount: toNumber(payload.blankCount),
    totalSlots: toNumber(payload.totalSlots),
    fillTimeMs: toNumber(payload.fillTimeMs),
    sourceCount: toNumber(payload.sourceCount),
    fileSizeText: normalizeText(payload.fileSizeText),
    createdAtText: formatDateTime(payload.createdAt),
    decisions: (Array.isArray(payload.decisions) ? payload.decisions : []).map(normalizeDecision).filter(Boolean),
    sourceDocs: Array.isArray(payload.sourceDocs) ? payload.sourceDocs : [],
  }
}

function trimErrorMessage(message, fallbackMessage) {
  const text = String(message || '').replace(/\s+/g, ' ').trim()
  return text || fallbackMessage
}

function buildCurrentTemplate(templateName, templateLocalPath, templateId) {
  const fileName = normalizeText(templateName)
  const localPath = normalizeText(templateLocalPath)
  const id = normalizeText(templateId)
  if (!fileName && !localPath && !id) {
    return null
  }

  const resolvedName = fileName || '未命名模板'
  const fileType = normalizeText(getFileTypeFromName(resolvedName || localPath)).toLowerCase()
  return {
    id,
    templateId: id,
    fileName: resolvedName,
    localPath,
    fileType,
    typeText: String(fileType || 'file').toUpperCase(),
    stageTone: 'success',
    stageText: localPath ? '待上传' : '模板库',
    sourceText: localPath ? '来自微信文件选择' : '来自模板库',
  }
}

function buildTemplateCard(item, selectedTemplateId) {
  const id = normalizeText(item && (item.id || item.templateId))
  const fileName = normalizeFileName(item && (item.fileName || item.title || item.templateName)) || '未命名模板'
  const fileType = normalizeText((item && item.fileType) || getFileTypeFromName(fileName)).toLowerCase()

  return Object.assign({}, item, {
    id,
    templateId: id,
    fileName,
    fileType,
    typeText: String(fileType || 'file').toUpperCase(),
    fileTheme: fileType === 'xlsx' || fileType === 'xls' ? 'excel' : 'word',
    stageText: normalizeText(item && (item.parseStatusText || item.stageText || item.uploadStatusText)) || '模板可用',
    stageTone: 'success',
    sourceText: '来自模板库',
    authorText: normalizeText(item && (item.author || item.ownerName || item.userName || item.nickname)) || '系统用户',
    updatedAtText: formatDateTime(item && (item.updatedAt || item.createdAt)),
    fileSizeText: formatSize(item && item.fileSize),
    selected: id && String(id) === String(selectedTemplateId || ''),
  })
}

function buildSourceRuleDetails() {
  return {
    extensions: SOURCE_EXTENSIONS.map((item) => item.toUpperCase()).join(' / '),
    maxFileSize: '20MB',
    maxCount: '最多 10 份',
    retryText: '上传失败后可重新选择后再次上传。',
  }
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
    loadingSources: false,
    refreshingSources: false,
    uploadingSources: false,
    submitting: false,
    downloadingResult: false,
    sharingResult: false,
    remoteWebSupported: canUseRemoteWebAutofill(),
    remoteWebHost: getRemoteWebBaseUrl(),
    sourceSummary: { total: 0, ready: 0, parsing: 0, failed: 0, uploaded: 0 },
    sourceDocuments: [],
    selectedDocIds: [],
    selectedSourceDocs: [],
    selectionSummary: { total: 0, ready: 0, pending: 0, failed: 0 },
    sourceUploadRules: SOURCE_UPLOAD_RULES,
    sourceRuleDetails: buildSourceRuleDetails(),
    showSourceRules: false,
    showSourceDocPicker: false,
    pickerMaskVisible: false,
    pickerPanelVisible: false,
    pickerLoading: false,
    availableSourceDocs: [],
    selectedSourceDocIds: [],
    templateUploadRules: TEMPLATE_UPLOAD_RULES,
    uploadQueueRole: '',
    uploadProgressText: '',
    uploadErrorText: '',
    currentTemplate: null,
    templateName: '',
    templateLocalPath: '',
    selectedTemplateId: '',
    showTemplatePicker: false,
    templatePickerMaskVisible: false,
    templatePickerPanelVisible: false,
    templatePickerLoading: false,
    availableTemplateDocs: [],
    pendingTemplateId: '',
    userRequirement: '',
    requirementText: '',
    executionBlockedText: '请先选择至少 1 份数据源资料，再开始智能填表。',
    progressSteps: ['校验来源资料', '上传模板文件', '解析模板槽位', '执行智能填表'],
    currentProgressStep: -1,
    progressText: '',
    errorText: '',
    resultReady: false,
    result: null,
    decisionPreview: [],
    uploadPickerHint: getUploadPickerHint(),
    templatePickerHint: getUploadPickerHint(),
    privacyDialog: getDefaultPrivacyDialog(),
    stageItems: [
      { key: 'uploaded', title: '文件上传成功', desc: '资料已进入 DocAI，等待后台接管解析任务。', tone: 'plain' },
      { key: 'parsing', title: '文档解析中', desc: 'DocAI 正在提取正文与字段信息，这时先不要直接填表。', tone: 'warning' },
      { key: 'indexing', title: '建立知识索引中', desc: '内容已被读取，系统正在准备更稳定的检索与字段匹配。', tone: 'info' },
      { key: 'ready', title: '可填表', desc: '只有进入这一阶段，当前页面才会允许真正开始智能填表。', tone: 'success' },
    ],
  },

  onLoad() {
    this.lastSourceRetryFiles = []
    const app = getApp()
    if (app && app.bindPrivacyDialog) {
      app.bindPrivacyDialog(this)
    }
  },

  onShow() {
    if (!ensureLogin()) {
      return
    }

    this.syncLocalState()
    this.loadSourceDocuments()
  },

  onHide() {
    this.clearSourcePolling()
  },

  onUnload() {
    this.clearSourcePolling()
    const app = getApp()
    if (app && app.unbindPrivacyDialog) {
      app.unbindPrivacyDialog(this)
    }
  },

  syncLocalState() {
    const draft = loadAutofillDraft()
    const result = normalizeResultPayload(loadAutofillResultSession())
    const selectedTemplateId = draft.selectedTemplateId || ''
    const currentTemplate = draft.currentTemplate
      || buildCurrentTemplate(draft.templateName || '', draft.templateLocalPath || '', selectedTemplateId)

    this.setData({
      selectedDocIds: Array.isArray(draft.sourceDocIds) ? draft.sourceDocIds.map(String) : [],
      templateName: draft.templateName || '',
      templateLocalPath: draft.templateLocalPath || '',
      selectedTemplateId,
      userRequirement: draft.userRequirement || '',
      requirementText: draft.userRequirement || '',
      currentTemplate,
      resultReady: Boolean(result),
      result,
      decisionPreview: result ? (result.decisions || []).slice(0, 8) : [],
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

  runNativePrivacyApi(scene, action) {
    const app = getApp()
    if (app && typeof app.runNativePrivacyApi === 'function') {
      return app.runNativePrivacyApi(scene, action)
    }
    return typeof action === 'function' ? action() : Promise.resolve()
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

  clearSourcePolling() {
    if (this.sourcePollTimer) {
      clearTimeout(this.sourcePollTimer)
      this.sourcePollTimer = null
    }
  },

  scheduleSourcePolling() {
    this.clearSourcePolling()

    const shouldPoll = (this.data.sourceDocuments || []).some((item) => {
      return item.stageKey === 'uploaded' || item.stageKey === 'parsing' || item.stageKey === 'indexing'
    })

    if (!shouldPoll) {
      return
    }

    this.sourcePollTimer = setTimeout(() => {
      this.loadSourceDocuments({ silent: true })
    }, SOURCE_POLL_INTERVAL)
  },

  persistDraft(extraPatch) {
    updateAutofillDraft(Object.assign({
      sourceDocIds: this.data.selectedDocIds,
      sourceDocs: this.data.selectedSourceDocs,
      parsedReadyCount: this.data.selectionSummary.ready,
      templateLocalPath: this.data.templateLocalPath,
      templateName: this.data.templateName,
      selectedTemplateId: this.data.selectedTemplateId,
      currentTemplate: this.data.currentTemplate,
      userRequirement: this.data.userRequirement,
    }, extraPatch || {}))
  },

  async loadSourceDocuments(options) {
    const silent = Boolean(options && options.silent)
    const selectedDocIds = (this.data.selectedDocIds || []).slice()

    if (!silent) {
      this.setData({ loadingSources: true })
    }

    try {
      const res = await api.getSourceDocuments()
      const rawDocuments = Array.isArray(res.data) ? res.data : []
      const sourceDocuments = rawDocuments.map((item) => buildSourceCard(item, selectedDocIds))
      const selectedSourceDocs = sourceDocuments.filter((item) => item.selected)
      const nextSelectedDocIds = selectedSourceDocs.map((item) => item.id)
      const selectionSummary = summarizeSelection(selectedSourceDocs)

      this.setData({
        sourceSummary: buildSourceSummary(rawDocuments),
        sourceDocuments,
        selectedDocIds: nextSelectedDocIds,
        selectedSourceDocs,
        selectionSummary,
        executionBlockedText: buildExecutionBlockedText(selectionSummary),
      })

      this.persistDraft({
        sourceDocIds: nextSelectedDocIds,
        sourceDocs: selectedSourceDocs,
        parsedReadyCount: selectionSummary.ready,
      })

      this.scheduleSourcePolling()
    } catch (err) {
      this.setData({
        errorText: trimErrorMessage(err && err.message, '当前无法加载来源文档列表，请稍后重试。'),
      })
      this.clearSourcePolling()
    } finally {
      if (!silent) {
        this.setData({ loadingSources: false })
      }
    }
  },

  async refreshSourceStatuses() {
    if (!ensureLogin() || this.data.refreshingSources) {
      return
    }

    this.setData({
      refreshingSources: true,
      errorText: '',
    })

    try {
      await this.loadSourceDocuments({ silent: true })
    } finally {
      this.setData({ refreshingSources: false })
    }
  },

  toggleSourceSelection(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const sourceDocuments = this.data.sourceDocuments || []
    const targetDoc = sourceDocuments.find((item) => item.id === docId)
    if (!targetDoc) {
      return
    }

    if (targetDoc.selectDisabled && !targetDoc.selected) {
      wx.showToast({
        title: '处理失败的资料不能继续用于填表',
        icon: 'none',
      })
      return
    }

    const selectedDocIds = (this.data.selectedDocIds || []).slice()
    const currentIndex = selectedDocIds.indexOf(docId)

    if (currentIndex >= 0) {
      selectedDocIds.splice(currentIndex, 1)
    } else {
      selectedDocIds.push(docId)
    }

    const nextSourceDocuments = sourceDocuments.map((item) => buildSourceCard(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const selectionSummary = summarizeSelection(selectedSourceDocs)

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds,
      selectedSourceDocs,
      selectionSummary,
      executionBlockedText: buildExecutionBlockedText(selectionSummary),
      errorText: '',
    })

    this.persistDraft({
      sourceDocIds: selectedDocIds,
      sourceDocs: selectedSourceDocs,
      parsedReadyCount: selectionSummary.ready,
    })
  },

  removeSelectedSource(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const selectedDocIds = (this.data.selectedDocIds || []).filter((item) => item !== docId)
    const nextSourceDocuments = (this.data.sourceDocuments || []).map((item) => buildSourceCard(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const selectionSummary = summarizeSelection(selectedSourceDocs)

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds,
      selectedSourceDocs,
      selectionSummary,
      executionBlockedText: buildExecutionBlockedText(selectionSummary),
    })

    this.persistDraft({
      sourceDocIds: selectedDocIds,
      sourceDocs: selectedSourceDocs,
      parsedReadyCount: selectionSummary.ready,
    })
  },

  openSourcePicker() {
    this.goDocuments()
  },

  noop() {},

  toggleSourceRules() {
    this.setData({
      showSourceRules: !this.data.showSourceRules,
    })
  },

  async handleOpenSourceDocPicker() {
    if (!ensureLogin()) {
      return
    }

    const selectedSourceDocIds = (this.data.selectedDocIds || []).map(String)
    this.setData({
      showSourceDocPicker: true,
      pickerMaskVisible: false,
      pickerPanelVisible: false,
      pickerLoading: true,
      selectedSourceDocIds,
      availableSourceDocs: (this.data.sourceDocuments || []).map((item) => buildSourceCard(item, selectedSourceDocIds)),
    })

    setTimeout(() => {
      this.setData({
        pickerMaskVisible: true,
        pickerPanelVisible: true,
      })
    }, 20)

    try {
      const res = await api.getSourceDocuments()
      const rawDocuments = Array.isArray(res && res.data) ? res.data : []
      const availableSourceDocs = rawDocuments.map((item) => buildSourceCard(item, selectedSourceDocIds))

      this.setData({
        availableSourceDocs,
        pickerLoading: false,
      })
    } catch (err) {
      this.setData({ pickerLoading: false })
      wx.showToast({
        title: trimErrorMessage(err && err.message, '文档库加载失败'),
        icon: 'none',
      })
    }
  },

  handleCloseSourceDocPicker() {
    if (!this.data.showSourceDocPicker) {
      return
    }

    this.setData({
      pickerMaskVisible: false,
      pickerPanelVisible: false,
    })
    setTimeout(() => {
      this.setData({
        showSourceDocPicker: false,
        pickerLoading: false,
      })
    }, 220)
  },

  handleToggleSourceDoc(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const currentDoc = (this.data.availableSourceDocs || []).find((item) => item.id === docId)
    if (currentDoc && currentDoc.selectDisabled && !currentDoc.selected) {
      wx.showToast({
        title: '处理失败的资料不能继续用于填表',
        icon: 'none',
      })
      return
    }

    const selectedSourceDocIds = (this.data.selectedSourceDocIds || []).slice()
    const currentIndex = selectedSourceDocIds.indexOf(docId)
    if (currentIndex >= 0) {
      selectedSourceDocIds.splice(currentIndex, 1)
    } else {
      selectedSourceDocIds.push(docId)
    }

    this.setData({
      selectedSourceDocIds,
      availableSourceDocs: (this.data.availableSourceDocs || []).map((item) => buildSourceCard(item, selectedSourceDocIds)),
    })
  },

  handleConfirmSourceDocSelection() {
    const selectedDocIds = (this.data.selectedSourceDocIds || []).map(String)
    const nextSourceDocuments = (this.data.availableSourceDocs || []).map((item) => buildSourceCard(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const selectionSummary = summarizeSelection(selectedSourceDocs)

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds,
      selectedSourceDocs,
      selectionSummary,
      executionBlockedText: buildExecutionBlockedText(selectionSummary),
      errorText: '',
    })

    this.persistDraft({
      sourceDocIds: selectedDocIds,
      sourceDocs: selectedSourceDocs,
      parsedReadyCount: selectionSummary.ready,
    })
    this.handleCloseSourceDocPicker()
  },

  async handleOpenTemplatePicker() {
    if (!ensureLogin()) {
      return
    }

    const pendingTemplateId = this.data.selectedTemplateId || ''
    this.setData({
      showTemplatePicker: true,
      templatePickerMaskVisible: false,
      templatePickerPanelVisible: false,
      templatePickerLoading: true,
      pendingTemplateId,
      availableTemplateDocs: [],
    })

    setTimeout(() => {
      this.setData({
        templatePickerMaskVisible: true,
        templatePickerPanelVisible: true,
      })
    }, 20)

    try {
      const res = await api.listTemplateFiles()
      const rawTemplates = Array.isArray(res && res.data) ? res.data : []
      const availableTemplateDocs = rawTemplates
        .map((item) => buildTemplateCard(item, pendingTemplateId))
        .filter((item) => item.id && TEMPLATE_EXTENSIONS.indexOf(item.fileType) !== -1)

      this.setData({
        availableTemplateDocs,
        templatePickerLoading: false,
      })
    } catch (err) {
      this.setData({ templatePickerLoading: false })
      wx.showToast({
        title: trimErrorMessage(err && err.message, '模板库加载失败'),
        icon: 'none',
      })
    }
  },

  handleCloseTemplatePicker() {
    if (!this.data.showTemplatePicker) {
      return
    }

    this.setData({
      templatePickerMaskVisible: false,
      templatePickerPanelVisible: false,
    })
    setTimeout(() => {
      this.setData({
        showTemplatePicker: false,
        templatePickerLoading: false,
      })
    }, 220)
  },

  handleSelectTemplateDoc(e) {
    const templateId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!templateId) {
      return
    }

    this.setData({
      pendingTemplateId: templateId,
      availableTemplateDocs: (this.data.availableTemplateDocs || []).map((item) => buildTemplateCard(item, templateId)),
    })
  },

  handleConfirmTemplateSelection() {
    const pendingTemplateId = this.data.pendingTemplateId || ''
    const templateDoc = (this.data.availableTemplateDocs || []).find((item) => item.id === pendingTemplateId)

    if (!templateDoc) {
      wx.showToast({
        title: '请先选择 1 份模板',
        icon: 'none',
      })
      return
    }

    const currentTemplate = buildCurrentTemplate(templateDoc.fileName, '', templateDoc.templateId || templateDoc.id)

    this.setData({
      selectedTemplateId: templateDoc.templateId || templateDoc.id,
      templateName: templateDoc.fileName,
      templateLocalPath: '',
      currentTemplate,
      errorText: '',
    })
    this.persistDraft({
      selectedTemplateId: templateDoc.templateId || templateDoc.id,
      templateName: templateDoc.fileName,
      templateLocalPath: '',
      currentTemplate,
    })
    this.syncUploadFeedback('template', '已从模板库选择模板。', '')
    this.handleCloseTemplatePicker()
  },

  handleChooseTemplateFromWechat() {
    this.handleCloseTemplatePicker()
    return this.chooseTemplateFile()
  },

  syncUploadFeedback(role, progressText, errorText) {
    this.setData({
      uploadQueueRole: role || '',
      uploadProgressText: progressText || '',
      uploadErrorText: errorText || '',
    })
  },

  clearUploadFeedback(role) {
    if (role && this.data.uploadQueueRole && this.data.uploadQueueRole !== role) {
      return
    }

    this.syncUploadFeedback('', '', '')
  },

  handleTapOpenSourceLibrary() {
    this.openSourcePicker()
  },

  handleTapUploadSource() {
    return this.uploadSourceFiles()
  },

  handleTapChooseTemplate() {
    return this.handleOpenTemplatePicker()
  },

  async uploadSourceFiles() {
    if (!ensureLogin() || this.data.uploadingSources || this.sourceUploadFlowRunning) {
      return
    }

    this.sourceUploadFlowRunning = true

    try {
      const selection = await this.runNativePrivacyApi('autofill-source-upload', () => selectLocalFiles({
        count: 10,
        allowedExtensions: SOURCE_EXTENSIONS,
      }))
      const validFiles = selection.validFiles || []
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!validFiles.length) {
        this.lastSourceRetryFiles = []
        this.syncUploadFeedback('source', '', rejectedIssueTexts.join('\n'))
        wx.showToast({
          title: rejectedIssueTexts.length ? '所选资料不可上传' : '未选择资料',
          icon: 'none',
        })
        return
      }

      this.setData({ uploadingSources: true })
      this.syncUploadFeedback('source', '正在上传资料', rejectedIssueTexts.join('\n'))

      wx.showLoading({
        title: '正在上传资料',
        mask: true,
      })

      const uploadResult = await uploadSelectedFiles(validFiles, {
        beforeUpload: () => {
          this.syncUploadFeedback('source', '正在校验上传服务...', rejectedIssueTexts.join('\n'))
          return api.checkUploadConnection()
        },
        uploadOne: (file) => api.uploadDocument(file.path, file.name),
        failureMessage: '资料上传失败',
        onProgress: ({ index, total, file }) => {
          wx.showLoading({
            title: '上传 ' + (index + 1) + '/' + total,
            mask: true,
          })
          this.syncUploadFeedback(
            'source',
            '正在上传 ' + (index + 1) + '/' + total + '：' + (file && file.name ? file.name : ''),
            rejectedIssueTexts.join('\n')
          )
        },
      })

      const uploadedIds = uploadResult.successItems
        .map((item) => item && item.response && item.response.data && item.response.data.id)
        .filter((item) => item || item === 0)
        .map(String)

      if (uploadedIds.length > 0) {
        const selectedDocIds = (this.data.selectedDocIds || [])
          .concat(uploadedIds)
          .filter((item, index, list) => list.indexOf(item) === index)

        this.setData({ selectedDocIds })
      }

      this.lastSourceRetryFiles = uploadResult.failedItems.map((item) => item.file)
      const issueTexts = rejectedIssueTexts.concat(uploadResult.issueTexts || [])
      const progressText = uploadResult.successCount > 0
        ? '已成功上传 ' + uploadResult.successCount + ' 份资料，可继续用于本次填表。'
        : ''

      this.syncUploadFeedback('source', progressText, issueTexts.join('\n'))
      await this.loadSourceDocuments({ silent: true })

      wx.showToast({
        title: uploadResult.successCount > 0
          ? (issueTexts.length ? '资料已部分上传' : '资料上传成功')
          : '没有成功上传资料',
        icon: uploadResult.successCount > 0 ? 'success' : 'none',
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][autofill] uploadSourceFiles failed', err)
      this.syncUploadFeedback('source', '', getPickerErrorMessage(err, '资料上传失败'))
      wx.showToast({
        title: getPickerErrorMessage(err, '资料上传失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ uploadingSources: false })
      this.sourceUploadFlowRunning = false
    }
  },

  async chooseTemplateFile() {
    if (!ensureLogin()) {
      return
    }

    try {
      const selection = await this.runNativePrivacyApi('autofill-template-upload', () => selectLocalFiles({
        count: 1,
        allowedExtensions: TEMPLATE_EXTENSIONS,
      }))
      const file = (selection.validFiles || [])[0]
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!file) {
        this.syncUploadFeedback('template', '', rejectedIssueTexts.join('\n'))
        wx.showToast({
          title: rejectedIssueTexts.length ? '所选模板不可使用' : '未选择模板',
          icon: 'none',
        })
        return
      }

      const templateName = file.name || '未命名模板'
      const filePath = file.path || ''
      const currentTemplate = buildCurrentTemplate(templateName, filePath)

      this.setData({
        templateName,
        templateLocalPath: filePath,
        selectedTemplateId: '',
        currentTemplate,
      })

      this.persistDraft({
        templateLocalPath: filePath,
        templateName,
        selectedTemplateId: '',
        currentTemplate,
      })

      this.syncUploadFeedback(
        'template',
        '模板已选择，开始智能填表时会自动上传到 DocAI。',
        rejectedIssueTexts.join('\n')
      )
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][autofill] chooseTemplateFile failed', err)
      this.syncUploadFeedback('template', '', getPickerErrorMessage(err, '模板选择失败'))
      wx.showToast({
        title: getPickerErrorMessage(err, '模板选择失败'),
        icon: 'none',
      })
    }
  },

  clearTemplateFile() {
    this.setData({
      templateName: '',
      templateLocalPath: '',
      selectedTemplateId: '',
      currentTemplate: null,
    })

    this.persistDraft({
      templateLocalPath: '',
      templateName: '',
      selectedTemplateId: '',
      currentTemplate: null,
    })
    this.clearUploadFeedback('template')
  },

  clearCurrentTemplate() {
    this.clearTemplateFile()
  },

  handleRequirementInput(e) {
    const userRequirement = String((e.detail && e.detail.value) || '')
    this.setData({
      userRequirement,
      requirementText: userRequirement,
    })
    this.persistDraft({ userRequirement })
  },

  removeSelectedSourceDoc(e) {
    this.removeSelectedSource(e)
  },

  async showModalAsync(options) {
    return new Promise((resolve) => {
      wx.showModal(Object.assign({}, options, {
        success: resolve,
        fail: () => resolve({ confirm: false, cancel: true }),
      }))
    })
  },

  async confirmWebAutofillEntry() {
    if (wx.getStorageSync(WEB_AUTOFILL_NOTICE_KEY)) {
      return true
    }

    const res = await this.showModalAsync({
      title: '网页辅助模式',
      content: [
        '网页模式只负责打开现有 DocAI 网站版智能填表，不承担当前单页原生主流程。',
        '当前不会自动继承小程序登录态，首次进入可能仍需在网页端重新登录。',
      ].join('\n'),
      confirmText: '继续打开',
      cancelText: '先不进入',
    })

    if (!res.confirm) {
      return false
    }

    wx.setStorageSync(WEB_AUTOFILL_NOTICE_KEY, 1)
    return true
  },

  openWebAutofill() {
    if (!this.data.remoteWebSupported) {
      wx.showToast({
        title: '当前未配置可用的 HTTPS 网页入口',
        icon: 'none',
      })
      return
    }

    this.confirmWebAutofillEntry().then((confirmed) => {
      if (!confirmed) {
        return
      }

      wx.navigateTo({
        url: '/pages/docai/autofill-web/index',
      })
    })
  },

  async startAutofill() {
    if (!ensureLogin() || this.data.submitting) {
      return
    }

    const selectionSummary = summarizeSelection(this.data.selectedSourceDocs || [])
    if (!selectionSummary.total) {
      wx.showToast({
        title: '请先选择至少 1 份数据源文档',
        icon: 'none',
      })
      return
    }

    if (!this.data.templateLocalPath && !this.data.selectedTemplateId) {
      wx.showToast({
        title: '请先选择模板文件',
        icon: 'none',
      })
      return
    }

    this.setData({
      submitting: true,
      currentProgressStep: 0,
      progressText: '正在刷新并校验当前资料状态…',
      errorText: '',
    })

    try {
      await this.loadSourceDocuments({ silent: true })

      const latestSelectionSummary = summarizeSelection(this.data.selectedSourceDocs || [])
      if (latestSelectionSummary.failed > 0 || latestSelectionSummary.pending > 0) {
        throw new Error(buildExecutionBlockedText(latestSelectionSummary))
      }

      const selectedSourceDocs = this.data.selectedSourceDocs || []
      const sourceDocIds = selectedSourceDocs.map((item) => item.id)
      const startedAt = Date.now()

      this.setData({
        currentProgressStep: 1,
        progressText: this.data.templateLocalPath
          ? '资料校验完成，正在上传模板文件…'
          : '资料校验完成，正在准备模板库模板…',
      })

      let templateInfo = this.data.currentTemplate || {}
      let templateId = String(this.data.selectedTemplateId || '')

      if (this.data.templateLocalPath) {
        const uploadRes = await this.ensurePrivacyAuthorized(
          'autofill-template-upload',
          async () => {
            await api.checkUploadConnection()
            return api.uploadTemplateFile(this.data.templateLocalPath, this.data.templateName)
          }
        )
        templateInfo = (uploadRes && uploadRes.data) || uploadRes || {}
        templateId = String((templateInfo.id || templateInfo.templateId || ''))
      }

      if (!templateId) {
        throw new Error('模板未返回有效 ID')
      }

      this.setData({
        currentProgressStep: 2,
        progressText: '模板已就绪，正在解析模板槽位…',
      })

      const parseRes = await api.parseTemplateSlots(templateId)
      const slots = Array.isArray(parseRes && parseRes.data) ? parseRes.data : []

      this.setData({
        currentProgressStep: 3,
        progressText: '模板已解析，正在执行智能填表…',
      })

      const fillRes = await api.fillTemplate(
        templateId,
        sourceDocIds,
        this.data.userRequirement
      )
      const fillData = (fillRes && fillRes.data) || {}
      const outputFile = normalizeText(fillData.outputFile)
      const outputName = normalizeText(fillData.outputName) || (outputFile ? outputFile.split(/[\\/]/).pop() : '')

      let decisions = []
      try {
        const decisionRes = await api.getTemplateDecisions(templateId)
        decisions = Array.isArray(decisionRes && decisionRes.data) ? decisionRes.data : []
      } catch (decisionErr) {
        decisions = []
      }

      const createdAt = new Date().toISOString()
      const summaryText = [
        '本次智能填表已完成。',
        '已使用 ' + sourceDocIds.length + ' 份已就绪资料参与填充。',
        fillData.auditId ? '审计编号：' + fillData.auditId : '',
        outputName ? '结果文件：' + outputName : '',
        decisions.length ? '已生成 ' + decisions.length + ' 条填表决策记录。' : '本次没有返回可展示的决策记录。',
      ].filter(Boolean).join('\n')

      const storedResult = rememberAutofillResult({
        templateId,
        auditId: fillData.auditId || '',
        outputFile,
        outputName: outputName || this.data.templateName,
        templateName: templateInfo.fileName || this.data.templateName || '',
        createdAt,
        sourceCount: sourceDocIds.length,
        filledCount: fillData.filledCount || 0,
        blankCount: fillData.blankCount || 0,
        totalSlots: fillData.totalSlots || slots.length || 0,
        fillTimeMs: Date.now() - startedAt,
        summaryText,
      })

      const result = normalizeResultPayload({
        recordId: storedResult.recordId,
        templateId,
        auditId: fillData.auditId || '',
        templateName: templateInfo.fileName || this.data.templateName || '',
        outputName: outputName || this.data.templateName || '',
        outputFile,
        fileType: getFileTypeFromName(outputName || this.data.templateName),
        summaryText,
        filledCount: fillData.filledCount || 0,
        blankCount: fillData.blankCount || 0,
        totalSlots: fillData.totalSlots || slots.length || 0,
        fillTimeMs: Date.now() - startedAt,
        sourceCount: sourceDocIds.length,
        fileSizeText: formatSize(templateInfo.fileSize || 0),
        decisions,
        sourceDocs: selectedSourceDocs,
        createdAt,
      })

      saveAutofillResultSession(result)
      this.setData({
        resultReady: true,
        result,
        decisionPreview: (result.decisions || []).slice(0, 8),
        progressText: '填表完成，结果已经生成，可直接在当前页下载或转发。',
      })

      wx.showToast({
        title: '智能填表完成',
        icon: 'success',
      })
    } catch (err) {
      this.setData({
        errorText: trimErrorMessage(err && err.message, '智能填表失败，请稍后重试。'),
      })
      wx.showToast({
        title: '智能填表失败',
        icon: 'none',
      })
    } finally {
      this.setData({ submitting: false })
    }
  },

  async downloadResult(action) {
    const result = this.data.result
    if (!result || !result.templateId || this.data.downloadingResult) {
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({
        title: '登录已过期，请重新登录',
        icon: 'none',
      })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(result.templateId)
    if (!downloadUrl) {
      wx.showToast({
        title: '结果下载地址无效',
        icon: 'none',
      })
      return
    }

    this.setData({ downloadingResult: true })
    wx.showLoading({
      title: '正在下载结果',
      mask: true,
    })

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

      if (action === 'save') {
        const saveRes = await this.saveFileAsync(downloadRes.tempFilePath)
        updateAutofillResult(result.recordId, {
          savedFilePath: saveRes.savedFilePath || '',
          lastDownloadedAt: new Date().toISOString(),
        })
        wx.showToast({
          title: '已下载到本地',
          icon: 'success',
        })
      } else {
        updateAutofillResult(result.recordId, {
          lastDownloadedAt: new Date().toISOString(),
        })

        try {
          await this.openDocumentAsync(downloadRes.tempFilePath, result.fileType)
          wx.showToast({
            title: '已下载并打开',
            icon: 'success',
          })
        } catch (openErr) {
          wx.showToast({
            title: '已下载，可稍后打开',
            icon: 'none',
          })
        }
      }
    } catch (err) {
      wx.showToast({
        title: trimErrorMessage(err && err.message, '结果下载失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ downloadingResult: false })
    }
  },

  async shareResult() {
    const result = this.data.result
    if (!result || !result.templateId || this.data.sharingResult) {
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({
        title: '登录已过期，请重新登录',
        icon: 'none',
      })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(result.templateId)
    if (!downloadUrl) {
      wx.showToast({
        title: '结果下载地址无效',
        icon: 'none',
      })
      return
    }

    this.setData({ sharingResult: true })
    wx.showLoading({
      title: '正在准备转发',
      mask: true,
    })

    try {
      const downloadRes = await this.downloadFileAsync({
        url: downloadUrl,
        header: {
          Authorization: 'Bearer ' + token,
        },
        timeout: 120000,
      })

      if (!downloadRes || Number(downloadRes.statusCode) !== 200 || !downloadRes.tempFilePath) {
        throw new Error('结果文件下载失败')
      }

      try {
        await this.shareFileMessageAsync(downloadRes.tempFilePath, result.outputName)
        updateAutofillResult(result.recordId, {
          lastDownloadedAt: new Date().toISOString(),
        })
        wx.showToast({
          title: '已打开转发面板',
          icon: 'success',
        })
        return
      } catch (shareErr) {
        const message = String((shareErr && shareErr.errMsg) || (shareErr && shareErr.message) || '')
        if (message.indexOf('cancel') !== -1) {
          return
        }
      }

      await this.openDocumentAsync(downloadRes.tempFilePath, result.fileType)
      wx.showToast({
        title: '当前环境不支持直接转发，请在文档菜单中继续转发',
        icon: 'none',
      })
    } catch (err) {
      wx.showToast({
        title: trimErrorMessage(err && err.message, '转发准备失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ sharingResult: false })
    }
  },

  handleDownloadOpen() {
    this.downloadResult('open')
  },

  handleDownloadSave() {
    this.downloadResult('save')
  },

  handleShareResult() {
    this.shareResult()
  },

  resetCurrentFlow() {
    clearAutofillDraft()
    clearAutofillResultSession()
    this.setData({
      selectedDocIds: [],
      selectedSourceDocs: [],
      selectionSummary: { total: 0, ready: 0, pending: 0, failed: 0 },
      templateName: '',
      templateLocalPath: '',
      selectedTemplateId: '',
      currentTemplate: null,
      userRequirement: '',
      currentProgressStep: -1,
      progressText: '',
      errorText: '',
      resultReady: false,
      result: null,
      decisionPreview: [],
      executionBlockedText: '请先选择至少 1 份数据源资料，再开始智能填表。',
    })

    this.loadSourceDocuments()
  },

  goDocuments() {
    wx.setStorageSync(DOCUMENT_PICKER_CONTEXT_KEY, {
      returnUrl: '/pages/docai/autofill/index',
      createdAt: Date.now(),
      source: 'autofill',
    })
    wx.switchTab({
      url: '/pages/docai/documents/index',
    })
  },
})
