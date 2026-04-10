const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
  removeCurrentUserCache,
} = require('./storage')
const {
  resolveAutofillOutputName,
} = require('./autofill-name')
const {
  normalizeDocRecord,
} = require('./document-role')

function normalizeText(value) {
  return String(value || '').trim()
}

function toNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function normalizeId(value) {
  if (value || value === 0) {
    return String(value)
  }

  return ''
}

function uniqueIds(list) {
  const result = []

  ;(Array.isArray(list) ? list : []).forEach((item) => {
    const value = normalizeId(item)
    if (!value || result.indexOf(value) !== -1) {
      return
    }

    result.push(value)
  })

  return result
}

function getFileTypeFromName(fileName) {
  const normalizedName = normalizeText(fileName)
  const match = normalizedName.match(/\.([^.]+)$/)
  return match ? String(match[1] || '').toLowerCase() : ''
}

function normalizeSourceDoc(doc) {
  const normalizedDoc = normalizeDocRecord(doc, {
    preferredRole: 'source',
  })
  if (!normalizedDoc || !normalizedDoc.id) {
    return null
  }

  return Object.assign({}, normalizedDoc, {
    questionStageText: normalizeText(doc.questionStageText),
    questionStageDesc: normalizeText(doc.questionStageDesc),
    questionStageTone: normalizeText(doc.questionStageTone).toLowerCase(),
    canChat: doc.canChat === true,
    localPath: normalizeText(doc.localPath),
  })
}

function normalizeTemplateDoc(doc) {
  if (!doc || typeof doc !== 'object') {
    return null
  }

  const normalizedDoc = normalizeDocRecord(doc, {
    preferredRole: 'template',
  }) || {}
  const fileName = normalizeText(
    normalizedDoc.fileName
    || doc.fileName
    || doc.title
    || doc.templateName
  )
  const localPath = normalizeText(doc.localPath || doc.templateLocalPath)

  if (!fileName && !localPath && !normalizeId(normalizedDoc.id || doc.templateId)) {
    return null
  }

  return Object.assign({}, normalizedDoc, {
    id: normalizeId(normalizedDoc.id || doc.templateId),
    templateId: normalizeId(normalizedDoc.id || doc.templateId),
    fileName: fileName || '未命名模板',
    title: fileName || '未命名模板',
    localPath,
    parseStatus: normalizeText(normalizedDoc.parseStatus || doc.parseStatus).toLowerCase() || 'uploaded',
  })
}

function normalizeDecision(item) {
  if (!item || typeof item !== 'object') {
    return null
  }

  return {
    fieldName: normalizeText(item.fieldName || item.slotName || item.placeholder || item.key),
    finalValue: normalizeText(item.finalValue || item.value || item.outputValue),
    finalConfidence: Number(item.finalConfidence || item.confidence || 0) || 0,
    decisionMode: normalizeText(item.decisionMode),
    reason: normalizeText(item.reason),
  }
}

function normalizeDraft(draft) {
  const sourceDocs = (Array.isArray(draft && draft.sourceDocs) ? draft.sourceDocs : [])
    .map(normalizeSourceDoc)
    .filter(Boolean)
  const currentTemplate = normalizeTemplateDoc(
    (draft && draft.currentTemplate)
    || (draft && draft.template)
    || {
      id: draft && draft.selectedTemplateId,
      templateId: draft && draft.selectedTemplateId,
      fileName: draft && draft.templateName,
      title: draft && draft.templateName,
      localPath: draft && draft.templateLocalPath,
      templateLocalPath: draft && draft.templateLocalPath,
    }
  )

  const sourceDocIds = uniqueIds(
    (draft && draft.sourceDocIds) || sourceDocs.map((item) => item.id)
  )

  const dedupedSourceDocs = sourceDocs.filter((item, index, list) => (
    list.findIndex((entry) => entry.id === item.id) === index
  ))

  const parsedReadyCount = dedupedSourceDocs.filter((item) => item.canChat === true).length

  return {
    sourceDocs: dedupedSourceDocs.filter((item) => sourceDocIds.indexOf(item.id) !== -1),
    sourceDocIds,
    currentTemplate,
    selectedTemplateId: normalizeId(
      (draft && draft.selectedTemplateId)
      || (currentTemplate && (currentTemplate.id || currentTemplate.templateId))
    ),
    templateLocalPath: normalizeText(
      (currentTemplate && currentTemplate.localPath)
      || (draft && draft.templateLocalPath)
    ),
    templateName: normalizeText(
      (currentTemplate && currentTemplate.fileName)
      || (draft && draft.templateName)
    ),
    parsedReadyCount: toNumber(draft && draft.parsedReadyCount) || parsedReadyCount,
    userRequirement: normalizeText(draft && draft.userRequirement),
    updatedAt: normalizeText(draft && draft.updatedAt) || new Date().toISOString(),
  }
}

function normalizeResultSession(result) {
  const templateName = normalizeText(result && result.templateName)
  const outputName = resolveAutofillOutputName(result)
  const sourceDocs = (Array.isArray(result && result.sourceDocs) ? result.sourceDocs : [])
    .map(normalizeSourceDoc)
    .filter(Boolean)
  const decisions = (Array.isArray(result && result.decisions) ? result.decisions : [])
    .map(normalizeDecision)
    .filter(Boolean)

  return {
    recordId: normalizeText(result && result.recordId),
    templateId: normalizeText(result && result.templateId),
    auditId: normalizeText(result && result.auditId),
    templateName: templateName || '未命名模板',
    outputName: outputName || templateName || '智能填表结果',
    outputFile: normalizeText(result && result.outputFile),
    fileType: normalizeText(result && result.fileType).toLowerCase() || getFileTypeFromName(outputName),
    summaryText: normalizeText(result && result.summaryText),
    filledCount: toNumber(result && result.filledCount),
    blankCount: toNumber(result && result.blankCount),
    totalSlots: toNumber(result && result.totalSlots),
    fillTimeMs: toNumber(result && result.fillTimeMs),
    slotCount: toNumber(result && result.slotCount),
    sourceCount: toNumber(result && result.sourceCount) || sourceDocs.length,
    userRequirement: normalizeText(result && result.userRequirement),
    fileSizeText: normalizeText(result && result.fileSizeText),
    savedFilePath: normalizeText(result && result.savedFilePath),
    localFilePath: normalizeText(result && result.localFilePath),
    localFileSize: toNumber(result && result.localFileSize),
    createdAt: normalizeText(result && result.createdAt) || new Date().toISOString(),
    decisions,
    sourceDocs,
  }
}

function saveDraft(draft) {
  const normalizedDraft = normalizeDraft(draft)
  setCurrentUserCache(USER_CACHE_KEYS.autofillDraft, normalizedDraft)
  return normalizedDraft
}

function loadAutofillDraft() {
  const rawValue = getCurrentUserCache(USER_CACHE_KEYS.autofillDraft, null)
  if (!rawValue || typeof rawValue !== 'object') {
    return normalizeDraft({})
  }

  return normalizeDraft(rawValue)
}

function updateAutofillDraft(patch) {
  const currentDraft = loadAutofillDraft()
  return saveDraft(Object.assign({}, currentDraft, patch || {}, {
    updatedAt: new Date().toISOString(),
  }))
}

function clearAutofillDraft() {
  removeCurrentUserCache(USER_CACHE_KEYS.autofillDraft)
}

function saveAutofillResultSession(result) {
  const normalizedResult = normalizeResultSession(result)
  setCurrentUserCache(USER_CACHE_KEYS.autofillResultSession, normalizedResult)
  return normalizedResult
}

function loadAutofillResultSession() {
  const rawValue = getCurrentUserCache(USER_CACHE_KEYS.autofillResultSession, null)
  if (!rawValue || typeof rawValue !== 'object') {
    return null
  }

  return normalizeResultSession(rawValue)
}

function clearAutofillResultSession() {
  removeCurrentUserCache(USER_CACHE_KEYS.autofillResultSession)
}

module.exports = {
  getFileTypeFromName,
  loadAutofillDraft,
  updateAutofillDraft,
  clearAutofillDraft,
  saveAutofillResultSession,
  loadAutofillResultSession,
  clearAutofillResultSession,
}
