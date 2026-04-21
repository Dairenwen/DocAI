const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')
const {
  getFileTypeFromName,
  loadAutofillDraft,
  updateAutofillDraft,
  clearAutofillDraft,
  saveAutofillResultSession,
} = require('../../../utils/autofill-draft')
const { rememberAutofillResult } = require('../../../utils/autofill-result')
const {
  buildTemplateDraftPatch,
  resolveDraftTemplate,
  hasSelectedTemplate,
} = require('../../../utils/template-selection')

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

function buildMissingDoc(id, fallbackDoc) {
  const fileName = String((fallbackDoc && fallbackDoc.fileName) || '').trim()

  return {
    id: String(id),
    fileName: fileName || '已不存在的资料',
    fileType: String((fallbackDoc && fallbackDoc.fileType) || '').toLowerCase(),
    uploadStatus: 'missing',
    questionStageKey: 'failed',
    questionStageText: '资料不存在',
    questionStageDesc: '该文档当前已无法从 DocAI 列表中读取，请先移出后再继续。',
    questionStageTone: 'danger',
    canChat: false,
  }
}

function buildRunDoc(doc) {
  const stageKey = toStageKey(doc)
  const typeText = String((doc.fileType || '').toUpperCase() || 'FILE')
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

  return Object.assign({}, doc, {
    id: String(doc.id),
    stageKey,
    stageTone,
    stageText: doc.questionStageText || '待处理',
    stageDesc: doc.questionStageDesc || '当前资料还没有可用的处理说明。',
    typeText,
  })
}

function summarizeDocs(list) {
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

function trimErrorMessage(message, fallback) {
  const text = String(message || '').replace(/\s+/g, ' ').trim()
  return text || fallback
}

Page({
  data: {
    loading: false,
    refreshing: false,
    submitting: false,
    flowReady: false,
    sourceDocs: [],
    selectionSummary: {
      total: 0,
      ready: 0,
      pending: 0,
      failed: 0,
    },
    currentTemplate: null,
    selectedTemplateId: '',
    templateName: '',
    templateLocalPath: '',
    templateSourceText: '',
    userRequirement: '',
    executionBlockedText: '',
    progressSteps: [
      '校验来源资料',
      '上传模板文件',
      '解析模板槽位',
      '执行智能填表',
    ],
    currentProgressStep: -1,
    progressText: '',
    errorText: '',
  },

  onLoad() {
    if (!ensureLogin()) {
      return
    }

    this.loadDraftState()
  },

  onShow() {
    if (!ensureLogin()) {
      return
    }

    this.loadDraftState({ silent: true })
  },

  ensurePrivacyAuthorized(scene, action) {
    const app = getApp()
    if (app && typeof app.ensurePrivacyAuthorized === 'function') {
      return app.ensurePrivacyAuthorized(scene, action)
    }

    return typeof action === 'function' ? action() : Promise.resolve()
  },

  buildExecutionBlockedText(selectionSummary) {
    if (!selectionSummary.total) {
      return '当前还没有可执行的数据源，请先返回上一步选择资料。'
    }

    if (selectionSummary.failed > 0) {
      return '当前选择里有 ' + selectionSummary.failed + ' 份失败资料，请先移出后再继续。'
    }

    if (selectionSummary.pending > 0) {
      return '当前仍有 ' + selectionSummary.pending + ' 份资料未就绪，请刷新等待。'
    }

    return '所有资料已就绪，可以开始执行智能填表。'
  },

  async loadDraftState(options) {
    const silent = Boolean(options && options.silent)
    const draft = loadAutofillDraft()
    const currentTemplate = resolveDraftTemplate(draft)
    const sourceDocIds = Array.isArray(draft.sourceDocIds) ? draft.sourceDocIds : []

    if (!silent) {
      this.setData({ loading: true })
    }

    if (sourceDocIds.length <= 0 || !hasSelectedTemplate(currentTemplate)) {
      const selectionSummary = summarizeDocs(draft.sourceDocs || [])
      this.setData({
        flowReady: false,
        sourceDocs: (draft.sourceDocs || []).map(buildRunDoc),
        selectionSummary,
        currentTemplate,
        selectedTemplateId: draft.selectedTemplateId || '',
        templateName: draft.templateName || '',
        templateLocalPath: draft.templateLocalPath || '',
        templateSourceText: currentTemplate ? currentTemplate.sourceText : '',
        userRequirement: draft.userRequirement || '',
        executionBlockedText: this.buildExecutionBlockedText(selectionSummary),
      })
      if (!silent) {
        this.setData({ loading: false })
      }
      return
    }

    await this.refreshSelectedSources({
      silent,
      preserveRequirement: true,
    })
  },

  async refreshSelectedSources(options) {
    const silent = Boolean(options && options.silent)
    const preserveRequirement = Boolean(options && options.preserveRequirement)
    const draft = loadAutofillDraft()
    const currentTemplate = resolveDraftTemplate(draft)
    const sourceDocIds = Array.isArray(draft.sourceDocIds) ? draft.sourceDocIds.map(String) : []

    if (!silent) {
      this.setData({ refreshing: true })
    }

    try {
      const res = await api.getDocumentStatuses()
      const allDocuments = Array.isArray(res.data) ? res.data : []
      const selectedDocs = sourceDocIds.map((id) => {
        const matchedDoc = allDocuments.find((item) => String(item.id) === String(id))
        if (matchedDoc) {
          return matchedDoc
        }

        const fallbackDoc = (draft.sourceDocs || []).find((item) => String(item.id) === String(id))
        return buildMissingDoc(id, fallbackDoc)
      })
      const runDocs = selectedDocs.map(buildRunDoc)
      const selectionSummary = summarizeDocs(runDocs)

      updateAutofillDraft({
        sourceDocIds: runDocs.map((item) => item.id),
        sourceDocs: runDocs,
        parsedReadyCount: selectionSummary.ready,
        userRequirement: preserveRequirement ? draft.userRequirement : this.data.userRequirement,
      })

      this.setData({
        flowReady: runDocs.length > 0 && hasSelectedTemplate(currentTemplate),
        sourceDocs: runDocs,
        selectionSummary,
        currentTemplate,
        selectedTemplateId: draft.selectedTemplateId || '',
        templateName: draft.templateName || '',
        templateLocalPath: draft.templateLocalPath || '',
        templateSourceText: currentTemplate ? currentTemplate.sourceText : '',
        userRequirement: preserveRequirement ? (draft.userRequirement || '') : this.data.userRequirement,
        executionBlockedText: this.buildExecutionBlockedText(selectionSummary),
      })
    } catch (err) {
      this.setData({
        errorText: trimErrorMessage(err && err.message, '当前无法刷新来源资料状态，请稍后再试。'),
      })
    } finally {
      if (!silent) {
        this.setData({ refreshing: false })
      }
    }
  },

  handleRequirementInput(e) {
    const value = String((e.detail && e.detail.value) || '')
    this.setData({
      userRequirement: value,
    })

    updateAutofillDraft({
      userRequirement: value,
    })
  },

  async refreshBeforeRun() {
    await this.refreshSelectedSources({
      silent: false,
      preserveRequirement: false,
    })
  },

  removeSourceDoc(e) {
    const docId = String((e.currentTarget.dataset && e.currentTarget.dataset.id) || '')
    if (!docId) {
      return
    }

    const nextDocs = (this.data.sourceDocs || []).filter((item) => String(item.id) !== docId)
    const selectionSummary = summarizeDocs(nextDocs)

    updateAutofillDraft({
      sourceDocIds: nextDocs.map((item) => item.id),
      sourceDocs: nextDocs,
      parsedReadyCount: selectionSummary.ready,
      userRequirement: this.data.userRequirement,
    })

    this.setData({
      sourceDocs: nextDocs,
      selectionSummary,
      flowReady: nextDocs.length > 0 && hasSelectedTemplate(this.data.currentTemplate),
      executionBlockedText: this.buildExecutionBlockedText(selectionSummary),
    })
  },

  goPickSources() {
    wx.navigateTo({
      url: '/pages/docai/autofill-picker/index?mode=source',
    })
  },

  goPickTemplate() {
    wx.navigateTo({
      url: '/pages/docai/autofill-picker/index?mode=template',
    })
  },

  async startAutofill() {
    if (!ensureLogin() || this.data.submitting) {
      return
    }

    const sourceDocs = this.data.sourceDocs || []
    const selectionSummary = summarizeDocs(sourceDocs)
    if (!selectionSummary.total) {
      wx.showToast({
        title: '请先补充数据源文档',
        icon: 'none',
      })
      return
    }

    if (selectionSummary.failed > 0 || selectionSummary.pending > 0) {
      wx.showToast({
        title: '当前仍有未就绪资料，请先刷新处理状态',
        icon: 'none',
      })
      return
    }

    if (!hasSelectedTemplate(this.data.currentTemplate)) {
      wx.showToast({
        title: '请先返回上一步选择模板',
        icon: 'none',
      })
      return
    }

    await this.refreshSelectedSources({
      silent: true,
      preserveRequirement: false,
    })

    const refreshedSummary = summarizeDocs(this.data.sourceDocs || [])
    if (refreshedSummary.failed > 0 || refreshedSummary.pending > 0) {
      wx.showToast({
        title: '刚刷新后仍有未就绪资料，请稍后再试',
        icon: 'none',
      })
      return
    }

    this.setData({
      submitting: true,
      errorText: '',
      currentProgressStep: 0,
      progressText: '资料校验完成，正在上传模板文件…',
    })

    try {
      const startedAt = Date.now()
      const draft = loadAutofillDraft()
      const sourceDocIds = (draft.sourceDocIds || []).map(String)
      const templateDraftState = buildTemplateDraftPatch(this.data.currentTemplate)
      const latestSourceDocs = this.data.sourceDocs || []

      let templateInfo = this.data.currentTemplate || {}
      let templateId = templateDraftState.selectedTemplateId || ''

      if (templateDraftState.templateLocalPath) {
        const uploadRes = await this.ensurePrivacyAuthorized(
          'autofill-template-upload',
          () => api.uploadTemplateFile(
            templateDraftState.templateLocalPath,
            templateDraftState.templateName
          )
        )
        templateInfo = (uploadRes && uploadRes.data) || uploadRes || {}
        templateId = String((templateInfo && (templateInfo.id || templateInfo.templateId)) || '')
      } else {
        this.setData({
          currentProgressStep: 1,
          progressText: '已复用模板库中的模板，正在解析模板槽位…',
        })
      }
      if (!templateId) {
        throw new Error('模板上传成功，但未返回模板 ID')
      }

      this.setData({
        currentProgressStep: 1,
        progressText: templateDraftState.templateLocalPath
          ? '模板已上传，正在解析模板槽位…'
          : '模板已就绪，正在解析模板槽位…',
      })

      const parseRes = await api.parseTemplateSlots(templateId)
      const slots = Array.isArray(parseRes && parseRes.data) ? parseRes.data : []

      this.setData({
        currentProgressStep: 2,
        progressText: '模板已解析，正在执行智能填表…',
      })

      const fillRes = await api.fillTemplate(
        templateId,
        sourceDocIds,
        this.data.userRequirement
      )
      const fillData = (fillRes && fillRes.data) || {}
      const outputFile = String(fillData.outputFile || '')
      const outputName = String(fillData.outputName || '') || (outputFile ? outputFile.split(/[\\/]/).pop() : '')

      let decisions = []
      try {
        const decisionRes = await api.getTemplateDecisions(templateId)
        decisions = Array.isArray(decisionRes && decisionRes.data) ? decisionRes.data : []
      } catch (decisionErr) {
        decisions = []
      }

      this.setData({
        currentProgressStep: 3,
        progressText: '结果已生成，正在整理结果摘要…',
      })

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
        createdAt: new Date().toISOString(),
        sourceCount: sourceDocIds.length,
        filledCount: fillData.filledCount || 0,
        blankCount: fillData.blankCount || 0,
        totalSlots: fillData.totalSlots || slots.length || 0,
        fillTimeMs: Date.now() - startedAt,
        summaryText,
      })

      saveAutofillResultSession({
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
        slotCount: slots.length || 0,
        fillTimeMs: Date.now() - startedAt,
        sourceCount: sourceDocIds.length,
        userRequirement: this.data.userRequirement,
        fileSizeText: formatSize(templateInfo.fileSize || 0),
        decisions,
        sourceDocs: latestSourceDocs,
        createdAt: new Date().toISOString(),
      })

      clearAutofillDraft()

      wx.navigateTo({
        url: '/pages/docai/autofill-result/index?recordId=' + encodeURIComponent(storedResult.recordId),
      })
    } catch (err) {
      this.setData({
        errorText: trimErrorMessage(err && err.message, '智能填表失败，请稍后重试。'),
      })
    } finally {
      this.setData({
        submitting: false,
      })
    }
  },
})
