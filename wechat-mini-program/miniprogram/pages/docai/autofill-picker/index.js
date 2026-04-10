const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')
const {
  getPickerErrorMessage,
  isDevtoolsPickerError,
  isPickerCancelError,
} = require('../../../utils/message-file-picker')
const {
  selectLocalFiles,
  uploadSelectedFiles,
} = require('../../../utils/upload-workflow')
const {
  loadAutofillDraft,
  updateAutofillDraft,
} = require('../../../utils/autofill-draft')
const {
  getDocaiEntryHint,
} = require('../../../utils/docai-entry')
const {
  buildTemplateSelection,
  buildTemplateDraftPatch,
  resolveDraftTemplate,
  hasSelectedTemplate,
} = require('../../../utils/template-selection')
const {
  AUTOFILL_SOURCE_UPLOAD_POLICY,
  formatExtensions,
  formatFileSize,
} = require('../../../utils/upload-policy')
const {
  normalizeDocRecord,
  isResultDoc,
  isSelectableSourceDoc,
  isSelectableTemplateDoc,
} = require('../../../utils/document-role')

const SOURCE_EXTENSIONS = ['docx', 'xlsx', 'txt', 'md']
const TEMPLATE_EXTENSIONS = ['docx', 'xlsx']
const SOURCE_POLL_INTERVAL = 4000
const SOURCE_DOC_PICKER_ANIMATION_DURATION = 220
const SOURCE_UPLOAD_RULES = [
  {
    label: '支持格式',
    value: formatExtensions(AUTOFILL_SOURCE_UPLOAD_POLICY.extensions),
  },
  {
    label: '单文件大小',
    value: '不超过 ' + formatFileSize(AUTOFILL_SOURCE_UPLOAD_POLICY.maxFileSize),
  },
  {
    label: '单次上传数量',
    value: '最多 ' + AUTOFILL_SOURCE_UPLOAD_POLICY.maxCount + ' 个文件',
  },
  {
    label: '失败处理',
    value: '上传失败后可重新选择并再次上传。',
  },
]
const FILE_TYPE_META = {
  doc: { badge: 'W', theme: 'word' },
  docx: { badge: 'W', theme: 'word' },
  xls: { badge: 'X', theme: 'excel' },
  xlsx: { badge: 'X', theme: 'excel' },
  md: { badge: 'M', theme: 'markdown' },
  txt: { badge: 'T', theme: 'text' },
}

function normalizeSourceDoc(doc) {
  return normalizeDocRecord(doc, {
    preferredRole: 'source',
  }) || doc || {}
}

function toStageKey(doc) {
  const uploadStatus = String((doc && doc.uploadStatus) || '').toLowerCase()
  const questionStageKey = String((doc && doc.questionStageKey) || '').toLowerCase()

  if (questionStageKey) {
    return questionStageKey
  }

  if (uploadStatus === 'failed') {
    return 'failed'
  }

  if (uploadStatus === 'parsing') {
    return 'parsing'
  }

  if (doc && (doc.canChat === true || uploadStatus === 'parsed' || doc.docSummary)) {
    return 'ready'
  }

  return 'uploaded'
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

function getStageTone(stageKey) {
  if (stageKey === 'ready') {
    return 'success'
  }

  if (stageKey === 'failed') {
    return 'danger'
  }

  return 'warning'
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

function getFileTypeMeta(fileType) {
  const normalizedType = String(fileType || '').toLowerCase()
  return FILE_TYPE_META[normalizedType] || {
    badge: (normalizedType || 'F').slice(0, 1).toUpperCase(),
    theme: 'default',
  }
}

function formatDateText(value) {
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
}

function getFileSizeText(doc) {
  const fileSize = Number(doc && doc.fileSize)
  if (fileSize > 0) {
    return formatFileSize(fileSize)
  }

  return '大小未知'
}

function getDocTimestamp(doc) {
  const date = new Date(
    (doc && (doc.updatedAt || doc.createdAt || doc.uploadedAt || doc.createTime)) || 0
  )
  const timestamp = date.getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function resolveAuthorText(doc) {
  return String(
    (doc && (
      doc.author
      || doc.ownerName
      || doc.userName
      || doc.username
      || doc.nickname
    )) || '系统用户'
  ).trim()
}

// 后端不同接口的文档角色字段并不完全一致，这里先做归一化，
// 再复用现有结果文档判定逻辑，排除成表和已填充输出文件。
function isGeneratedResultDocByFields(doc) {
  return isResultDoc(normalizeSourceDoc(doc))
}

function buildMissingDoc(id, fallbackDoc) {
  const fileName = String((fallbackDoc && fallbackDoc.fileName) || '').trim()

  return {
    id: String(id),
    fileName: fileName || '已不存在的数据源',
    fileType: String((fallbackDoc && fallbackDoc.fileType) || '').toLowerCase(),
    uploadStatus: 'missing',
    questionStageKey: 'failed',
    questionStageText: '资料不存在',
    questionStageDesc: '该文档已无法从 DocAI 当前列表中获取，请先将它移出选择。',
    questionStageTone: 'danger',
    canChat: false,
  }
}

function buildSourceCard(doc, selectedIds) {
  const normalizedDoc = normalizeSourceDoc(doc)
  const stageKey = toStageKey(normalizedDoc)
  const isSelected = (selectedIds || []).indexOf(String(normalizedDoc.id)) !== -1
  const stageDisplayText = getStageDisplayText(stageKey)
  const selectDisabled = !isSelected && stageKey === 'failed'

  return Object.assign({}, normalizedDoc, {
    id: String(normalizedDoc.id),
    stageKey,
    stageTone: getStageTone(stageKey),
    stageText: normalizedDoc.questionStageText || stageDisplayText,
    stageDisplayText,
    stageDesc: normalizedDoc.questionStageDesc || '当前资料还没有可用的处理说明。',
    selected: isSelected,
    selectDisabled,
    actionText: isSelected ? '移出已选' : (selectDisabled ? '不可选择' : '加入选择'),
    typeText: String((normalizedDoc.fileType || '').toUpperCase() || 'FILE'),
    fileSizeText: getFileSizeText(normalizedDoc),
  })
}

function buildSelectableSourceDoc(doc, selectedIds) {
  const normalizedDoc = normalizeSourceDoc(doc)
  const stageKey = toStageKey(normalizedDoc)
  const fileTypeMeta = getFileTypeMeta(normalizedDoc.fileType)
  const docId = String(normalizedDoc.id || '')

  return Object.assign({}, normalizedDoc, {
    id: docId,
    fileBadge: fileTypeMeta.badge,
    fileTheme: fileTypeMeta.theme,
    authorText: resolveAuthorText(normalizedDoc),
    fileSizeText: getFileSizeText(normalizedDoc),
    timeText: formatDateText(normalizedDoc.updatedAt || normalizedDoc.createdAt),
    stageKey,
    stageTone: getStageTone(stageKey),
    stageDisplayText: getStageDisplayText(stageKey),
    selected: (selectedIds || []).indexOf(docId) !== -1,
  })
}

function mergeSelectedDocs(list, selectedIds, draftDocs) {
  const documents = Array.isArray(list) ? list : []
  const fallbackDocs = Array.isArray(draftDocs) ? draftDocs : []

  return (selectedIds || []).map((id) => {
    const matchedDoc = documents.find((item) => String(item.id) === String(id))
    if (matchedDoc) {
      return matchedDoc
    }

    const fallbackDoc = fallbackDocs.find((item) => String(item.id) === String(id))
    return buildMissingDoc(id, fallbackDoc)
  })
}

function summarizeSelection(sourceDocs) {
  const docs = Array.isArray(sourceDocs) ? sourceDocs : []

  return docs.reduce((summary, item) => {
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

function buildSourceSelectionState(list, selectedIds, draftDocs) {
  const normalizedIds = uniqueIdList((selectedIds || []).map(String))
  const documents = (Array.isArray(list) ? list : []).map((item) => buildSourceCard(item, normalizedIds))
  const selectedDocs = mergeSelectedDocs(documents, normalizedIds, draftDocs)
    .map((item) => buildSourceCard(item, normalizedIds))
  const selectionSummary = summarizeSelection(selectedDocs)

  return {
    documents,
    selectedDocs,
    selectedDocIds: selectedDocs.map((item) => item.id),
    selectionSummary,
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
    mode: 'source',
    loading: false,
    uploading: false,
    documents: [],
    selectedSourceDocs: [],
    selectedDocIds: [],
    selectionSummary: {
      total: 0,
      ready: 0,
      pending: 0,
      failed: 0,
    },
    sourceUploadRules: SOURCE_UPLOAD_RULES,
    showSourceRules: false,
    showSourceDocPicker: false,
    pickerMaskVisible: false,
    pickerPanelVisible: false,
    pickerLoading: false,
    availableSourceDocs: [],
    pickerSelectedDocIds: [],
    currentTemplate: null,
    selectedTemplateId: '',
    templateName: '',
    templateLocalPath: '',
    templateSourceText: '',
    templateLibraryDocs: [],
    templateLibraryLoading: false,
    sourcePreview: [],
    sourceCount: 0,
    uploadPickerHint: getDocaiEntryHint({ role: 'source' }),
    templatePickerHint: getDocaiEntryHint({ role: 'template' }),
    privacyDialog: getDefaultPrivacyDialog(),
  },

  onLoad(options) {
    const app = getApp()
    if (app && app.bindPrivacyDialog) {
      app.bindPrivacyDialog(this)
    }

    const mode = options && options.mode === 'template' ? 'template' : 'source'
    this.setData({ mode })

    wx.setNavigationBarTitle({
      title: mode === 'source' ? '智能填表' : '选择模板',
    })

    if (!ensureLogin()) {
      return
    }

    if (mode === 'source') {
      this.loadSourceMode()
      return
    }

    this.loadTemplateMode()
  },

  onShow() {
    if (!ensureLogin()) {
      return
    }

    if (this.data.mode === 'template') {
      this.loadTemplateMode()
    }
  },

  onHide() {
    this.clearSourcePolling()
    this.clearSourceDocPickerTimer()
  },

  onUnload() {
    this.clearSourcePolling()
    this.clearSourceDocPickerTimer()

    const app = getApp()
    if (app && app.unbindPrivacyDialog) {
      app.unbindPrivacyDialog(this)
    }
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

  clearSourcePolling() {
    if (this.sourcePollTimer) {
      clearTimeout(this.sourcePollTimer)
      this.sourcePollTimer = null
    }
  },

  clearSourceDocPickerTimer() {
    if (this.sourceDocPickerTimer) {
      clearTimeout(this.sourceDocPickerTimer)
      this.sourceDocPickerTimer = null
    }
  },

  scheduleSourcePolling() {
    this.clearSourcePolling()

    const shouldPoll = (this.data.selectedSourceDocs || []).some((item) => {
      const stageKey = item.stageKey || toStageKey(item)
      return stageKey === 'uploaded' || stageKey === 'parsing' || stageKey === 'indexing'
    })

    if (!shouldPoll) {
      return
    }

    this.sourcePollTimer = setTimeout(() => {
      this.loadSourceMode({ silent: true })
    }, SOURCE_POLL_INTERVAL)
  },

  isGeneratedResultDoc(doc) {
    return isGeneratedResultDocByFields(doc)
  },

  getSelectableSourceDocs(list, selectedIds) {
    const normalizedIds = uniqueIdList((selectedIds || []).map(String))

    return (Array.isArray(list) ? list : [])
      .map((item) => normalizeSourceDoc(item))
      .filter((item) => !this.isGeneratedResultDoc(item))
      .filter((item) => isSelectableSourceDoc(item))
      .filter((item) => {
        const docId = String(item.id || '')
        const alreadySelected = normalizedIds.indexOf(docId) !== -1
        return alreadySelected || toStageKey(item) !== 'failed'
      })
      .map((item) => buildSelectableSourceDoc(item, normalizedIds))
      .sort((left, right) => {
        if (left.selected !== right.selected) {
          return left.selected ? -1 : 1
        }

        return getDocTimestamp(right) - getDocTimestamp(left)
      })
  },

  buildPickerDocList(documents, selectedIds) {
    return this.getSelectableSourceDocs(documents, selectedIds)
  },

  toggleSourceRules() {
    this.setData({
      showSourceRules: !this.data.showSourceRules,
    })
  },

  noop() {},

  async handleOpenSourceDocPicker() {
    if (!ensureLogin()) {
      return
    }

    const pickerSelectedDocIds = uniqueIdList(this.data.selectedDocIds || [])
    this.clearSourceDocPickerTimer()
    this.setData({
      showSourceDocPicker: true,
      pickerMaskVisible: false,
      pickerPanelVisible: false,
      pickerLoading: true,
      pickerSelectedDocIds,
      availableSourceDocs: this.buildPickerDocList(this.data.documents || [], pickerSelectedDocIds),
    })

    this.sourceDocPickerTimer = setTimeout(() => {
      this.setData({
        pickerMaskVisible: true,
        pickerPanelVisible: true,
      })
    }, 20)

    try {
      await this.loadSourceMode({ silent: true })
    } finally {
      const latestSelectedIds = uniqueIdList(this.data.selectedDocIds || [])
      this.setData({
        pickerLoading: false,
        pickerSelectedDocIds: latestSelectedIds,
        availableSourceDocs: this.buildPickerDocList(this.data.documents || [], latestSelectedIds),
      })
    }
  },

  handleCloseSourceDocPicker() {
    if (!this.data.showSourceDocPicker) {
      return
    }

    this.clearSourceDocPickerTimer()
    this.setData({
      pickerMaskVisible: false,
      pickerPanelVisible: false,
    })

    this.sourceDocPickerTimer = setTimeout(() => {
      const selectedDocIds = uniqueIdList(this.data.selectedDocIds || [])
      this.setData({
        showSourceDocPicker: false,
        pickerLoading: false,
        pickerSelectedDocIds: selectedDocIds,
        availableSourceDocs: this.buildPickerDocList(this.data.documents || [], selectedDocIds),
      })
    }, SOURCE_DOC_PICKER_ANIMATION_DURATION)
  },

  handleToggleSourceDoc(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const nextSelectedIds = uniqueIdList(this.data.pickerSelectedDocIds || [])
    const currentIndex = nextSelectedIds.indexOf(docId)

    if (currentIndex >= 0) {
      nextSelectedIds.splice(currentIndex, 1)
    } else {
      nextSelectedIds.push(docId)
    }

    this.setData({
      pickerSelectedDocIds: nextSelectedIds,
      availableSourceDocs: this.buildPickerDocList(this.data.documents || [], nextSelectedIds),
    })
  },

  handleConfirmSourceDocSelection() {
    const draft = loadAutofillDraft()
    const sourceState = buildSourceSelectionState(
      this.data.documents || [],
      this.data.pickerSelectedDocIds || [],
      draft.sourceDocs
    )

    updateAutofillDraft({
      sourceDocIds: sourceState.selectedDocIds,
      sourceDocs: sourceState.selectedDocs,
      parsedReadyCount: sourceState.selectionSummary.ready,
    })

    this.setData({
      documents: sourceState.documents,
      selectedSourceDocs: sourceState.selectedDocs,
      selectedDocIds: sourceState.selectedDocIds,
      selectionSummary: sourceState.selectionSummary,
      pickerSelectedDocIds: sourceState.selectedDocIds,
      availableSourceDocs: this.buildPickerDocList(sourceState.documents, sourceState.selectedDocIds),
    })

    this.scheduleSourcePolling()
    this.handleCloseSourceDocPicker()
  },

  removeSelectedSourceDoc(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const nextSelectedIds = uniqueIdList((this.data.selectedDocIds || []).filter((item) => String(item) !== docId))
    const nextPickerSelectedIds = uniqueIdList(
      (this.data.pickerSelectedDocIds || []).filter((item) => String(item) !== docId)
    )
    const draft = loadAutofillDraft()
    const sourceState = buildSourceSelectionState(
      this.data.documents || [],
      nextSelectedIds,
      draft.sourceDocs
    )

    updateAutofillDraft({
      sourceDocIds: sourceState.selectedDocIds,
      sourceDocs: sourceState.selectedDocs,
      parsedReadyCount: sourceState.selectionSummary.ready,
    })

    this.setData({
      documents: sourceState.documents,
      selectedSourceDocs: sourceState.selectedDocs,
      selectedDocIds: sourceState.selectedDocIds,
      selectionSummary: sourceState.selectionSummary,
      pickerSelectedDocIds: nextPickerSelectedIds,
      availableSourceDocs: this.buildPickerDocList(
        sourceState.documents,
        this.data.showSourceDocPicker ? nextPickerSelectedIds : sourceState.selectedDocIds
      ),
    })

    this.scheduleSourcePolling()
  },

  async loadSourceMode(options) {
    const silent = Boolean(options && options.silent)
    const draft = loadAutofillDraft()
    const selectedDocIds = (draft.sourceDocIds || []).map(String)

    if (!silent) {
      this.setData({ loading: true })
    }

    try {
      const res = await api.getSourceDocuments()
      const rawDocuments = Array.isArray(res.data) ? res.data : []
      const sourceState = buildSourceSelectionState(rawDocuments, selectedDocIds, draft.sourceDocs)
      const activePickerIds = this.data.showSourceDocPicker
        ? uniqueIdList(
          (this.data.pickerSelectedDocIds || []).length
            ? this.data.pickerSelectedDocIds
            : sourceState.selectedDocIds
        )
        : sourceState.selectedDocIds

      updateAutofillDraft({
        sourceDocIds: sourceState.selectedDocIds,
        sourceDocs: sourceState.selectedDocs,
        parsedReadyCount: sourceState.selectionSummary.ready,
      })

      this.setData({
        documents: sourceState.documents,
        selectedSourceDocs: sourceState.selectedDocs,
        selectedDocIds: sourceState.selectedDocIds,
        selectionSummary: sourceState.selectionSummary,
        availableSourceDocs: this.buildPickerDocList(sourceState.documents, activePickerIds),
      })

      this.scheduleSourcePolling()
    } catch (err) {
      console.error('[docai][autofill-picker] loadSourceMode failed', err)

      const fallbackState = buildSourceSelectionState([], selectedDocIds, draft.sourceDocs)
      this.setData({
        documents: fallbackState.documents,
        selectedSourceDocs: fallbackState.selectedDocs,
        selectedDocIds: fallbackState.selectedDocIds,
        selectionSummary: fallbackState.selectionSummary,
        availableSourceDocs: [],
      })
      this.clearSourcePolling()
    } finally {
      if (!silent) {
        this.setData({ loading: false })
      }
    }
  },

  async loadTemplateMode() {
    const draft = loadAutofillDraft()
    const sourceDocs = Array.isArray(draft.sourceDocs) ? draft.sourceDocs : []
    const currentTemplate = resolveDraftTemplate(draft)

    this.setData({
      currentTemplate,
      selectedTemplateId: currentTemplate ? currentTemplate.id : (draft.selectedTemplateId || ''),
      templateName: draft.templateName || '',
      templateLocalPath: draft.templateLocalPath || '',
      templateSourceText: currentTemplate ? currentTemplate.sourceText : '',
      templateLibraryLoading: true,
      sourceCount: sourceDocs.length,
      sourcePreview: sourceDocs.slice(0, 3).map((item) => ({
        id: item.id,
        fileName: item.fileName,
      })),
    })

    await this.loadTemplateLibrary({ silent: true })
  },

  async loadTemplateLibrary(options) {
    this.setData({ templateLibraryLoading: true })

    try {
      const res = await api.listTemplateFiles()
      const templateLibraryDocs = (Array.isArray(res.data) ? res.data : [])
        .filter((item) => isSelectableTemplateDoc(item))
        .map((item) => {
          const template = buildTemplateSelection(item)
          if (!template || !template.id) {
            return null
          }

          return Object.assign({}, template, {
            selected: String(template.id) === String(this.data.selectedTemplateId || ''),
          })
        })
        .filter(Boolean)

      this.setData({ templateLibraryDocs })
      return templateLibraryDocs
    } catch (err) {
      if (!(options && options.silent)) {
        wx.showToast({
          title: getPickerErrorMessage(err, '模板库加载失败'),
          icon: 'none',
        })
      }
      return []
    } finally {
      this.setData({ templateLibraryLoading: false })
    }
  },

  refreshTemplateLibrary() {
    return this.loadTemplateLibrary()
  },

  applyTemplateSelection(template) {
    const currentTemplate = buildTemplateSelection(template)
    const draftPatch = buildTemplateDraftPatch(currentTemplate)
    const templateLibraryDocs = (this.data.templateLibraryDocs || []).map((item) => {
      return Object.assign({}, item, {
        selected: String(item.id) === String(draftPatch.selectedTemplateId || ''),
      })
    })

    updateAutofillDraft(draftPatch)
    this.setData({
      currentTemplate,
      selectedTemplateId: draftPatch.selectedTemplateId,
      templateName: draftPatch.templateName,
      templateLocalPath: draftPatch.templateLocalPath,
      templateSourceText: currentTemplate ? currentTemplate.sourceText : '',
      templateLibraryDocs,
    })
  },

  selectTemplateFromLibrary(e) {
    const templateId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!templateId) {
      return
    }

    const template = (this.data.templateLibraryDocs || []).find((item) => String(item.id) === templateId)
    if (!template) {
      return
    }

    this.applyTemplateSelection(template)
    wx.showToast({
      title: '已从模板库选择模板',
      icon: 'success',
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

  async uploadSourceFiles() {
    if (!ensureLogin() || this.data.uploading) {
      return
    }

    try {
      this.setData({ uploading: true })

      const selection = await this.ensurePrivacyAuthorized('autofill-source-upload', () => selectLocalFiles({
        count: 10,
        allowedExtensions: SOURCE_EXTENSIONS,
      }))
      const validFiles = selection.validFiles || []
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!validFiles.length) {
        wx.showToast({
          title: rejectedIssueTexts.length ? '所选文件不符合规则' : '未选择文件',
          icon: 'none',
        })
        return
      }

      wx.showLoading({
        title: '正在上传',
        mask: true,
      })

      const uploadResult = await uploadSelectedFiles(validFiles, {
        beforeUpload: () => api.checkUploadConnection(),
        uploadOne: (file) => api.uploadDocument(file.path, file.name),
        failureMessage: '资料上传失败',
        onProgress: ({ index, total }) => {
          wx.showLoading({
            title: '上传 ' + (index + 1) + '/' + total,
            mask: true,
          })
        },
      })

      const uploadedDocs = uploadResult.successItems
        .map((item) => (item && item.response && item.response.data) || null)
        .filter(Boolean)

      const currentDraft = loadAutofillDraft()
      const selectedDocIds = uniqueIdList(
        (this.data.selectedDocIds || []).concat(uploadedDocs.map((item) => String(item.id)))
      )
      const selectedDocs = mergeSelectedDocs(
        (this.data.documents || []).concat(uploadedDocs).map((item) => buildSourceCard(item, selectedDocIds)),
        selectedDocIds,
        currentDraft.sourceDocs
      )
      const selectionSummary = summarizeSelection(selectedDocs)

      updateAutofillDraft({
        sourceDocIds: selectedDocIds,
        sourceDocs: selectedDocs,
        parsedReadyCount: selectionSummary.ready,
      })

      await this.loadSourceMode({ silent: true })

      wx.showToast({
        title: uploadResult.successCount > 0 ? '已上传 ' + uploadResult.successCount + ' 份' : '上传失败',
        icon: uploadResult.successCount > 0 ? 'success' : 'none',
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      if (isDevtoolsPickerError(err) || (err && err.name === 'MessageFilePickerError')) {
        this.showNativeUploadOnlyHint(err, '资料上传')
        return
      }

      console.error('[docai][autofill-picker] uploadSourceFiles failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '资料上传失败'),
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ uploading: false })
    }
  },

  continueToTemplate() {
    const selectionSummary = this.data.selectionSummary || {}
    if (!selectionSummary.total) {
      wx.showToast({
        title: '请至少选择 1 份数据源',
        icon: 'none',
      })
      return
    }

    wx.redirectTo({
      url: '/pages/docai/autofill-picker/index?mode=template',
    })
  },

  goBackToSource() {
    wx.redirectTo({
      url: '/pages/docai/autofill-picker/index?mode=source',
    })
  },

  async chooseTemplateFile() {
    if (!ensureLogin()) {
      return
    }

    try {
      const selection = await this.ensurePrivacyAuthorized('autofill-template-upload', () => selectLocalFiles({
        count: 1,
        allowedExtensions: TEMPLATE_EXTENSIONS,
      }))
      const file = (selection.validFiles || [])[0]
      const rejectedIssueTexts = (selection.rejectedFiles || []).map((item) => item.issueText)

      if (!file) {
        wx.showToast({
          title: rejectedIssueTexts.length ? '所选模板不符合要求' : '未选择模板',
          icon: 'none',
        })
        return
      }

      const templateName = file.name || '未命名模板'
      const filePath = file.path || ''

      this.applyTemplateSelection({
        fileName: templateName,
        templateName,
        title: templateName,
        localPath: filePath,
        templateLocalPath: filePath,
      })
    } catch (err) {
      if (isPickerCancelError(err)) {
        return
      }

      if (isDevtoolsPickerError(err) || (err && err.name === 'MessageFilePickerError')) {
        this.showNativeUploadOnlyHint(err, '模板选择')
        return
      }

      console.error('[docai][autofill-picker] chooseTemplateFile failed', err)
      wx.showToast({
        title: getPickerErrorMessage(err, '模板选择失败'),
        icon: 'none',
      })
    }
  },

  clearTemplateFile() {
    const draftPatch = buildTemplateDraftPatch(null)

    updateAutofillDraft(draftPatch)

    this.setData({
      currentTemplate: null,
      selectedTemplateId: '',
      templateLocalPath: '',
      templateName: '',
      templateSourceText: '',
      templateLibraryDocs: (this.data.templateLibraryDocs || []).map((item) => Object.assign({}, item, {
        selected: false,
      })),
    })
  },

  continueToRun() {
    const draft = loadAutofillDraft()
    if ((draft.sourceDocIds || []).length <= 0) {
      wx.showToast({
        title: '请先补充数据源',
        icon: 'none',
      })
      return
    }

    if (!hasSelectedTemplate(resolveDraftTemplate(draft))) {
      wx.showToast({
        title: '请先选择模板文件',
        icon: 'none',
      })
      return
    }

    wx.navigateTo({
      url: '/pages/docai/autofill-run/index',
    })
  },
})
