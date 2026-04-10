const api = require('../../../api/docai')
const config = require('../../../config')
const { ensureLogin } = require('../../../utils/auth')
const {
  chooseMessageFileAsync,
  getPickerErrorMessage,
  isPickerCancelError,
  resolvePickedFileName,
  resolvePickedFilePath,
} = require('../../../utils/message-file-picker')
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
  getDocaiEntryHint,
  showDocaiEntryActionSheet,
} = require('../../../utils/docai-entry')
const {
  buildTemplateSelection,
  buildTemplateDraftPatch,
  resolveDraftTemplate,
  hasSelectedTemplate,
} = require('../../../utils/template-selection')
const {
  normalizeDocRecord,
  isResultDoc,
  isSelectableSourceDoc,
  isSelectableTemplateDoc,
} = require('../../../utils/document-role')
const {
  AUTOFILL_SOURCE_UPLOAD_POLICY,
  AUTOFILL_TEMPLATE_UPLOAD_POLICY,
  buildPolicyRules,
} = require('../../../utils/upload-policy')

const SOURCE_EXTENSIONS = ['docx', 'xlsx', 'txt', 'md']
const TEMPLATE_EXTENSIONS = ['docx', 'xlsx']
const SOURCE_POLL_INTERVAL = 4000
const SOURCE_PICKER_ANIMATION_DURATION = 220
const FILE_THEME_MAP = {
  doc: 'word',
  docx: 'word',
  xls: 'excel',
  xlsx: 'excel',
  md: 'markdown',
  txt: 'text',
}
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
    fileName: normalizeText(item.fileName || item.title) || '未命名文档',
    fileType,
    typeText: String(fileType || 'file').toUpperCase(),
    stageKey,
    stageTone,
    stageText: normalizeText(item.questionStageText) || '待处理',
    stageDesc: normalizeText(item.questionStageDesc) || '当前资料还没有可用的处理说明。',
    selected,
    selectDisabled: !selected && stageKey === 'failed',
    actionText: selected ? '移出本次填表' : (stageKey === 'failed' ? '不可选择' : '加入本次填表'),
  })
}

function uniqueIdList(list) {
  const result = []
  ;(Array.isArray(list) ? list : []).forEach((item) => {
    const normalizedId = normalizeText(item)
    if (normalizedId && result.indexOf(normalizedId) === -1) {
      result.push(normalizedId)
    }
  })
  return result
}

function getStageDisplayText(stageKey) {
  if (stageKey === 'ready') {
    return '已就绪'
  }
  if (stageKey === 'failed') {
    return '失败'
  }
  return '处理中'
}

function getFileTheme(fileType) {
  return FILE_THEME_MAP[normalizeText(fileType).toLowerCase()] || 'text'
}

function resolveAuthorText(item) {
  const authorText = [
    item && item.authorName,
    item && item.author,
    item && item.nickname,
    item && item.userName,
    item && item.username,
    item && item.ownerName,
    item && item.creatorName,
    item && item.createdBy,
  ].map(normalizeText).find(Boolean)

  return authorText || '系统用户'
}

function resolveFileSizeText(item) {
  const fileSize = Number(
    (item && (
      item.fileSize
      || item.size
      || item.fileBytes
      || item.fileLength
      || item.contentLength
    )) || 0
  )

  return fileSize > 0 ? formatSize(fileSize) : '--'
}

function resolveUpdatedAt(item) {
  return item && (
    item.updatedAt
    || item.updateTime
    || item.modifiedAt
    || item.modifiedTime
    || item.createdAt
    || item.createTime
    || item.uploadedAt
    || item.uploadTime
  )
}

function getSortTimestamp(item) {
  const updatedAt = resolveUpdatedAt(item)
  const timestamp = updatedAt ? new Date(updatedAt).getTime() : 0
  return Number.isFinite(timestamp) ? timestamp : 0
}

function buildDisplaySourceDoc(item, selectedIds) {
  const normalizedDoc = normalizeDocRecord(item, { preferredRole: 'source' }) || item || {}
  const sourceCard = buildSourceCard(normalizedDoc, selectedIds)
  const stageDisplayText = getStageDisplayText(sourceCard.stageKey)

  return Object.assign({}, sourceCard, {
    fileTheme: getFileTheme(sourceCard.fileType),
    fileSizeText: resolveFileSizeText(normalizedDoc),
    updatedAtText: formatDateTime(resolveUpdatedAt(normalizedDoc)),
    authorText: resolveAuthorText(normalizedDoc),
    stageText: normalizeText(sourceCard.stageText) || stageDisplayText,
    stageDisplayText,
    sortTimestamp: getSortTimestamp(normalizedDoc),
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

function buildCurrentTemplate(templateName, templateLocalPath) {
  return buildTemplateSelection({
    fileName: templateName,
    templateName,
    localPath: templateLocalPath,
    templateLocalPath,
  })
}

function buildSourceRuleDetails() {
  return {
    extensions: SOURCE_EXTENSIONS.map((item) => String(item || '').toUpperCase()).join(' / '),
    maxFileSize: '不超过 ' + formatSize(AUTOFILL_SOURCE_UPLOAD_POLICY.maxFileSize),
    maxCount: '最多 ' + AUTOFILL_SOURCE_UPLOAD_POLICY.maxCount + ' 个文件',
    retryText: '上传失败后可重新选择后再次上传。',
  }
}

function buildTemplatePickerDoc(item, selectedTemplateId) {
  const template = buildTemplateSelection(item)
  if (!template || !template.id) {
    return null
  }

  return Object.assign({}, template, {
    selected: String(template.id) === String(selectedTemplateId || ''),
  })
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
    showSourceRules: false,
    showSourceDocPicker: false,
    pickerMaskVisible: false,
    pickerPanelVisible: false,
    pickerLoading: false,
    availableSourceDocs: [],
    selectedSourceDocIds: [],
    sourceUploadRules: SOURCE_UPLOAD_RULES,
    sourceRuleDetails: buildSourceRuleDetails(),
    templateUploadRules: TEMPLATE_UPLOAD_RULES,
    uploadQueueRole: '',
    uploadProgressText: '',
    uploadErrorText: '',
    currentTemplate: null,
    selectedTemplateId: '',
    templateName: '',
    templateLocalPath: '',
    templatePickerHint: getDocaiEntryHint({ role: 'template' }),
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
    uploadPickerHint: getDocaiEntryHint({ role: 'source' }),
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
    this.clearSourcePickerTimer()
  },

  onUnload() {
    this.clearSourcePolling()
    this.clearSourcePickerTimer()
    const app = getApp()
    if (app && app.unbindPrivacyDialog) {
      app.unbindPrivacyDialog(this)
    }
  },

  syncLocalState() {
    const draft = loadAutofillDraft()
    const result = normalizeResultPayload(loadAutofillResultSession())
    const currentTemplate = resolveDraftTemplate(draft)

    this.setData({
      selectedDocIds: Array.isArray(draft.sourceDocIds) ? draft.sourceDocIds.map(String) : [],
      templateName: draft.templateName || '',
      templateLocalPath: draft.templateLocalPath || '',
      selectedTemplateId: currentTemplate ? currentTemplate.id : (draft.selectedTemplateId || ''),
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

  clearSourcePickerTimer() {
    if (this.sourcePickerTimer) {
      clearTimeout(this.sourcePickerTimer)
      this.sourcePickerTimer = null
    }
  },

  scheduleSourcePolling() {
    this.clearSourcePolling()

    const shouldPoll = (this.data.selectedSourceDocs || []).some((item) => {
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
      currentTemplate: this.data.currentTemplate,
      selectedTemplateId: this.data.selectedTemplateId,
      templateLocalPath: this.data.templateLocalPath,
      templateName: this.data.templateName,
      userRequirement: this.data.userRequirement,
    }, extraPatch || {}))
  },

  noop() {},

  toggleSourceRules() {
    this.setData({
      showSourceRules: !this.data.showSourceRules,
    })
  },

  isGeneratedResultDoc(item) {
    // 后端不同接口返回的文档角色字段并不完全一致，这里先做归一化，再复用现有结果文档判定逻辑，排除成表和已填充输出文件。
    const normalizedDoc = normalizeDocRecord(item, { preferredRole: 'source' })
    return !normalizedDoc || isResultDoc(normalizedDoc)
  },

  getSelectableSourceDocs(list, selectedIds) {
    const normalizedIds = uniqueIdList(selectedIds)
    return (Array.isArray(list) ? list : [])
      .map((item) => buildDisplaySourceDoc(item, normalizedIds))
      .filter((item) => !this.isGeneratedResultDoc(item))
      .filter((item) => isSelectableSourceDoc(item))
      .filter((item) => item.selected || item.stageKey !== 'failed')
      .sort((left, right) => {
        if (left.selected !== right.selected) {
          return left.selected ? -1 : 1
        }

        return (right.sortTimestamp || 0) - (left.sortTimestamp || 0)
      })
  },

  async loadSourceDocuments(options) {
    const silent = Boolean(options && options.silent)
    const selectedDocIds = uniqueIdList(this.data.selectedDocIds || [])

    if (!silent) {
      this.setData({ loadingSources: true })
    }

    try {
      const res = await api.getSourceDocuments()
      const rawDocuments = Array.isArray(res.data) ? res.data : []
      const sourceDocuments = rawDocuments.map((item) => buildDisplaySourceDoc(item, selectedDocIds))
      const selectedSourceDocs = sourceDocuments.filter((item) => item.selected)
      const nextSelectedDocIds = uniqueIdList(selectedSourceDocs.map((item) => item.id))
      const selectionSummary = summarizeSelection(selectedSourceDocs)
      const pickerSelectedDocIds = this.data.showSourceDocPicker
        ? uniqueIdList(this.data.selectedSourceDocIds || nextSelectedDocIds)
        : uniqueIdList(this.data.selectedSourceDocIds || [])

      this.setData({
        sourceSummary: buildSourceSummary(rawDocuments),
        sourceDocuments,
        selectedDocIds: nextSelectedDocIds,
        selectedSourceDocs,
        availableSourceDocs: this.getSelectableSourceDocs(sourceDocuments, pickerSelectedDocIds),
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

    const selectedDocIds = uniqueIdList(this.data.selectedDocIds || [])
    const currentIndex = selectedDocIds.indexOf(docId)

    if (currentIndex >= 0) {
      selectedDocIds.splice(currentIndex, 1)
    } else {
      selectedDocIds.push(docId)
    }

    const nextSourceDocuments = sourceDocuments.map((item) => buildDisplaySourceDoc(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const selectionSummary = summarizeSelection(selectedSourceDocs)

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds,
      selectedSourceDocs,
      availableSourceDocs: this.data.showSourceDocPicker
        ? this.getSelectableSourceDocs(nextSourceDocuments, this.data.selectedSourceDocIds)
        : this.data.availableSourceDocs,
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

    const selectedDocIds = uniqueIdList((this.data.selectedDocIds || []).filter((item) => item !== docId))
    const nextSourceDocuments = (this.data.sourceDocuments || []).map((item) => buildDisplaySourceDoc(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const selectionSummary = summarizeSelection(selectedSourceDocs)
    const nextPickerSelectedDocIds = uniqueIdList((this.data.selectedSourceDocIds || []).filter((item) => item !== docId))

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds,
      selectedSourceDocs,
      selectedSourceDocIds: nextPickerSelectedDocIds,
      availableSourceDocs: this.data.showSourceDocPicker
        ? this.getSelectableSourceDocs(nextSourceDocuments, nextPickerSelectedDocIds)
        : this.data.availableSourceDocs,
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
    if (!ensureLogin() || this.data.showSourceDocPicker) {
      return
    }

    const selectedSourceDocIds = uniqueIdList(this.data.selectedDocIds || [])

    this.clearSourcePickerTimer()
    this.setData({
      showSourceDocPicker: true,
      pickerMaskVisible: false,
      pickerPanelVisible: false,
      pickerLoading: true,
      selectedSourceDocIds,
      availableSourceDocs: this.getSelectableSourceDocs(this.data.sourceDocuments || [], selectedSourceDocIds),
    })

    this.sourcePickerTimer = setTimeout(() => {
      this.setData({
        pickerMaskVisible: true,
        pickerPanelVisible: true,
      })
      this.clearSourcePickerTimer()
    }, 16)

    this.loadSourceDocuments({ silent: true }).finally(() => {
      if (!this.data.showSourceDocPicker) {
        return
      }

      this.setData({
        pickerLoading: false,
        availableSourceDocs: this.getSelectableSourceDocs(
          this.data.sourceDocuments || [],
          this.data.selectedSourceDocIds || selectedSourceDocIds
        ),
      })
    })
  },

  handleOpenSourceDocPicker() {
    return this.openSourcePicker()
  },

  handleCloseSourceDocPicker() {
    if (!this.data.showSourceDocPicker) {
      return
    }

    this.clearSourcePickerTimer()
    this.setData({
      pickerMaskVisible: false,
      pickerPanelVisible: false,
    })

    this.sourcePickerTimer = setTimeout(() => {
      this.setData({
        showSourceDocPicker: false,
        pickerLoading: false,
        availableSourceDocs: [],
        selectedSourceDocIds: [],
      })
      this.clearSourcePickerTimer()
    }, SOURCE_PICKER_ANIMATION_DURATION)
  },

  handleToggleSourceDoc(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const targetDoc = (this.data.availableSourceDocs || []).find((item) => item.id === docId)
    if (!targetDoc) {
      return
    }

    if (targetDoc.selectDisabled && !targetDoc.selected) {
      wx.showToast({
        title: '处理失败的资料暂时不能加入本次填表',
        icon: 'none',
      })
      return
    }

    const selectedSourceDocIds = uniqueIdList(this.data.selectedSourceDocIds || [])
    const currentIndex = selectedSourceDocIds.indexOf(docId)
    if (currentIndex >= 0) {
      selectedSourceDocIds.splice(currentIndex, 1)
    } else {
      selectedSourceDocIds.push(docId)
    }

    this.setData({
      selectedSourceDocIds,
      availableSourceDocs: this.getSelectableSourceDocs(this.data.sourceDocuments || [], selectedSourceDocIds),
    })
  },

  handleConfirmSourceDocSelection() {
    const selectedDocIds = uniqueIdList(this.data.selectedSourceDocIds || [])
    const nextSourceDocuments = (this.data.sourceDocuments || []).map((item) => buildDisplaySourceDoc(item, selectedDocIds))
    const selectedSourceDocs = nextSourceDocuments.filter((item) => item.selected)
    const nextSelectedDocIds = uniqueIdList(selectedSourceDocs.map((item) => item.id))
    const selectionSummary = summarizeSelection(selectedSourceDocs)

    this.setData({
      sourceDocuments: nextSourceDocuments,
      selectedDocIds: nextSelectedDocIds,
      selectedSourceDocs,
      availableSourceDocs: this.getSelectableSourceDocs(nextSourceDocuments, nextSelectedDocIds),
      selectionSummary,
      executionBlockedText: buildExecutionBlockedText(selectionSummary),
      errorText: '',
    })

    this.persistDraft({
      sourceDocIds: nextSelectedDocIds,
      sourceDocs: selectedSourceDocs,
      parsedReadyCount: selectionSummary.ready,
    })

    this.scheduleSourcePolling()
    this.handleCloseSourceDocPicker()
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

  async uploadSourceFilesLegacy() {
    if (!ensureLogin() || this.data.uploadingSources) {
      return
    }

    try {
      await this.ensurePrivacyAuthorized('autofill-source-upload', async () => {
        const pickRes = await chooseMessageFileAsync({
          count: 10,
          type: 'file',
          extension: SOURCE_EXTENSIONS,
        })
        const files = Array.isArray(pickRes && pickRes.tempFiles) ? pickRes.tempFiles : []
        if (files.length <= 0) {
          return
        }

        this.setData({ uploadingSources: true })
        wx.showLoading({
          title: '正在上传资料',
          mask: true,
        })

        let successCount = 0
        const uploadedIds = []

        for (let index = 0; index < files.length; index += 1) {
          const file = files[index] || {}
          const fileName = resolvePickedFileName(file)
          const filePath = resolvePickedFilePath(file)
          if (!fileName || !filePath) {
            continue
          }

          try {
            const res = await api.uploadDocument(filePath, fileName)
            const uploadedDoc = (res && res.data) || res || {}
            if (uploadedDoc && (uploadedDoc.id || uploadedDoc.id === 0)) {
              uploadedIds.push(String(uploadedDoc.id))
              successCount += 1
            }
          } catch (err) {
            // continue uploading remaining files
          }
        }

        if (uploadedIds.length > 0) {
          const selectedDocIds = (this.data.selectedDocIds || [])
            .concat(uploadedIds)
            .filter((item, index, list) => list.indexOf(item) === index)

          this.setData({ selectedDocIds })
        }

        await this.loadSourceDocuments({ silent: true })

        wx.showToast({
          title: successCount > 0 ? '已上传 ' + successCount + ' 份资料' : '没有成功上传资料',
          icon: successCount > 0 ? 'success' : 'none',
        })
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][autofill] uploadSourceFiles failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '资料上传失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ uploadingSources: false })
    }
  },

  handleTapChooseTemplate() {
    return showDocaiEntryActionSheet({
      includeLibrary: true,
      includeNative: true,
      libraryLabel: '从模板库选择模板',
      nativeLabel: '从微信会话选择模板',
    }).then((action) => {
      if (action === 'library') {
        return this.chooseTemplateFromLibrary()
      }
      if (action === 'native') {
        return this.chooseTemplateFile()
      }
      return undefined
    })
  },

  applyCurrentTemplateSelection(template) {
    const currentTemplate = buildTemplateSelection(template)
    const draftPatch = buildTemplateDraftPatch(currentTemplate)

    this.setData({
      currentTemplate,
      selectedTemplateId: draftPatch.selectedTemplateId,
      templateName: draftPatch.templateName,
      templateLocalPath: draftPatch.templateLocalPath,
    })

    this.persistDraft(draftPatch)
  },

  async chooseTemplateFromLibrary() {
    if (!ensureLogin()) {
      return
    }

    try {
      this.syncUploadFeedback('template', '正在加载模板库…', '')

      const res = await api.listTemplateFiles()
      const templateDocs = (Array.isArray(res.data) ? res.data : [])
        .filter((item) => isSelectableTemplateDoc(item))
        .map((item) => buildTemplatePickerDoc(item, this.data.selectedTemplateId))
        .filter(Boolean)

      if (!templateDocs.length) {
        this.syncUploadFeedback('template', '', '')
        wx.showToast({
          title: '模板库暂无可用模板',
          icon: 'none',
        })
        return
      }

      const selectedIndex = await new Promise((resolve) => {
        wx.showActionSheet({
          itemList: templateDocs.map((item) => (
            item.stageText ? (item.fileName + ' · ' + item.stageText) : item.fileName
          )),
          success: (actionRes) => resolve(Number(actionRes.tapIndex)),
          fail: () => resolve(-1),
        })
      })

      if (selectedIndex < 0 || !templateDocs[selectedIndex]) {
        this.syncUploadFeedback('template', '', '')
        return
      }

      this.applyCurrentTemplateSelection(templateDocs[selectedIndex])
      this.syncUploadFeedback('template', '已从模板库选择模板，执行时将直接复用。', '')
    } catch (err) {
      console.error('[docai][autofill] chooseTemplateFromLibrary failed', err)
      this.syncUploadFeedback('template', '', getPickerErrorMessage(err, '模板库加载失败'))
      wx.showToast({
        title: getPickerErrorMessage(err, '模板库加载失败'),
        icon: 'none',
      })
    }
  },

  async chooseTemplateFileLegacy() {
    if (!ensureLogin()) {
      return
    }

    try {
      await this.ensurePrivacyAuthorized('autofill-template-upload', async () => {
        const pickRes = await chooseMessageFileAsync({
          count: 1,
          type: 'file',
          extension: TEMPLATE_EXTENSIONS,
        })
        const file = (pickRes.tempFiles || [])[0]
        const fileName = resolvePickedFileName(file)
        const filePath = resolvePickedFilePath(file)
        if (!fileName || !filePath) {
          return
        }

        const templateName = fileName || '未命名模板'
        this.setData({
          templateName,
          templateLocalPath: filePath,
          currentTemplate: buildCurrentTemplate(templateName, filePath),
        })

        this.persistDraft({
          templateLocalPath: filePath,
          templateName,
        })
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      console.error('[docai][autofill] chooseTemplateFile failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '模板选择失败'),
        icon: 'none',
      })
    }
  },

  async uploadSourceFiles() {
    if (!ensureLogin() || this.data.uploadingSources) {
      return
    }

    try {
      this.setData({ uploadingSources: true })
      this.syncUploadFeedback('source', '姝ｅ湪鎵撳紑璧勬枡閫夋嫨鈥?', '')

      const selection = await this.ensurePrivacyAuthorized('autofill-source-upload', () => selectLocalFiles({
        count: 10,
        allowedExtensions: SOURCE_EXTENSIONS,
      }))
      const validFiles = selection.validFiles || []
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!validFiles.length) {
        this.lastSourceRetryFiles = []
        this.syncUploadFeedback('source', '', rejectedIssueTexts.join('\n'))
        wx.showToast({
          title: rejectedIssueTexts.length ? '鎵€閫夎祫鏂欎笉鍙笂浼?' : '鏈€夋嫨璧勬枡',
          icon: 'none',
        })
        return
      }

      wx.showLoading({
        title: '姝ｅ湪涓婁紶璧勬枡',
        mask: true,
      })

      const uploadResult = await uploadSelectedFiles(validFiles, {
        beforeUpload: () => {
          this.syncUploadFeedback('source', '姝ｅ湪鏍￠獙涓婁紶鏈嶅姟鈥?', rejectedIssueTexts.join('\n'))
          return api.checkUploadConnection()
        },
        uploadOne: (file) => api.uploadDocument(file.path, file.name),
        failureMessage: '璧勬枡涓婁紶澶辫触',
        onProgress: ({ index, total, file }) => {
          wx.showLoading({
            title: '涓婁紶 ' + (index + 1) + '/' + total,
            mask: true,
          })
          this.syncUploadFeedback(
            'source',
            '姝ｅ湪涓婁紶 ' + (index + 1) + '/' + total + '锛?' + (file && file.name ? file.name : ''),
            rejectedIssueTexts.join('\n')
          )
        },
      })

      const uploadedIds = uploadResult.successItems
        .map((item) => {
          const response = item && item.response
          if (!response) {
            return null
          }

          return response.data ? response.data.id : response.id
        })
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
        ? '宸叉垚鍔熶笂浼? ' + uploadResult.successCount + ' 浠借祫鏂欙紝鍙户缁敤浜庢湰娆″～琛ㄣ€?'
        : ''

      this.syncUploadFeedback('source', progressText, issueTexts.join('\n'))
      await this.loadSourceDocuments({ silent: true })

      wx.showToast({
        title: uploadResult.successCount > 0
          ? (issueTexts.length ? '璧勬枡宸查儴鍒嗕笂浼?' : '璧勬枡涓婁紶鎴愬姛')
          : '娌℃湁鎴愬姛涓婁紶璧勬枡',
        icon: uploadResult.successCount > 0 ? 'success' : 'none',
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      if (err && err.name === 'MessageFilePickerError') {
        this.showNativeUploadOnlyHint(err, '资料上传')
        return
      }

      console.error('[docai][autofill] uploadSourceFiles failed', err)
      this.syncUploadFeedback('source', '', getPickerErrorMessage(err, '璧勬枡涓婁紶澶辫触'))
      wx.showToast({
        title: getPickerErrorMessage(err, '璧勬枡涓婁紶澶辫触'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ uploadingSources: false })
    }
  },

  async chooseTemplateFile() {
    if (!ensureLogin()) {
      return
    }

    try {
      this.syncUploadFeedback('template', '正在打开模板选择…', '')

      const selection = await this.ensurePrivacyAuthorized('autofill-template-upload', () => selectLocalFiles({
        count: 1,
        allowedExtensions: TEMPLATE_EXTENSIONS,
      }))
      const file = (selection.validFiles || [])[0]
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!file) {
        this.syncUploadFeedback('template', '', rejectedIssueTexts.join('\n'))
        wx.showToast({
          title: rejectedIssueTexts.length ? '鎵€閫夋ā鏉夸笉鍙娇鐢?' : '鏈€夋嫨妯℃澘',
          icon: 'none',
        })
        return
      }

      const templateName = file.name || '未命名模板'
      const filePath = file.path || ''

      this.applyCurrentTemplateSelection({
        fileName: templateName,
        templateName,
        title: templateName,
        localPath: filePath,
        templateLocalPath: filePath,
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

      if (err && err.name === 'MessageFilePickerError') {
        this.showNativeUploadOnlyHint(err, '模板选择')
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
    const draftPatch = buildTemplateDraftPatch(null)

    this.setData({
      templateName: '',
      templateLocalPath: '',
      currentTemplate: null,
      selectedTemplateId: '',
    })

    this.persistDraft(draftPatch)
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

  showNativeUploadOnlyHint(err, actionText) {
    const actionLabel = actionText || '文件选择'
    wx.showModal({
      title: actionLabel + '暂不可用',
      content: getPickerErrorMessage(err, '请先把文件发到微信会话或文件传输助手。') + ' 当前流程只支持在小程序内选择微信会话文件，不会跳转网页。',
      showCancel: false,
    })
  },

  async confirmWebAutofillEntry() {
    return false
  },

  openWebAutofill() {
    wx.showModal({
      title: '当前流程仅支持小程序内上传',
      content: '请先把文件发到微信会话或文件传输助手，再在小程序里选择文件或模板。',
      showCancel: false,
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

    if (!hasSelectedTemplate(this.data.currentTemplate)) {
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
      const templateDraftState = buildTemplateDraftPatch(this.data.currentTemplate)

      this.setData({
        currentProgressStep: 1,
        progressText: templateDraftState.templateLocalPath
          ? '资料校验完成，正在上传模板文件…'
          : '资料校验完成，正在复用模板库中的模板…',
      })

      let templateInfo = this.data.currentTemplate || {}
      let templateId = templateDraftState.selectedTemplateId || ''

      if (templateDraftState.templateLocalPath) {
        const uploadRes = await this.ensurePrivacyAuthorized(
          'autofill-template-upload',
          async () => {
            await api.checkUploadConnection()
            return api.uploadTemplateFile(templateDraftState.templateLocalPath, templateDraftState.templateName)
          }
        )
        templateInfo = (uploadRes && uploadRes.data) || uploadRes || {}
        templateId = String((templateInfo.id || templateInfo.templateId || ''))
      }

      if (!templateId) {
        throw new Error('模板上传成功，但未返回模板 ID')
      }

      this.setData({
        currentProgressStep: 2,
        progressText: templateDraftState.templateLocalPath
          ? '模板已上传，正在解析模板槽位…'
          : '模板已就绪，正在解析模板槽位…',
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
    this.clearSourcePickerTimer()
    this.setData({
      selectedDocIds: [],
      selectedSourceDocs: [],
      selectionSummary: { total: 0, ready: 0, pending: 0, failed: 0 },
      showSourceRules: false,
      showSourceDocPicker: false,
      pickerMaskVisible: false,
      pickerPanelVisible: false,
      pickerLoading: false,
      availableSourceDocs: [],
      selectedSourceDocIds: [],
      currentTemplate: null,
      selectedTemplateId: '',
      templateName: '',
      templateLocalPath: '',
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
})
