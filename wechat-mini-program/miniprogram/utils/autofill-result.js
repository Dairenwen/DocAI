const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
} = require('./storage')
const {
  resolveAutofillOutputName,
} = require('./autofill-name')
const {
  normalizeFileName,
} = require('./document-name')

const MAX_RECORD_COUNT = 80

function normalizeText(value) {
  return String(value || '').trim()
}

function toNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function getTimeValue(value) {
  const timestamp = value ? new Date(value).getTime() : 0
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function buildRandomId() {
  return Date.now() + '-' + Math.random().toString(36).slice(2, 8)
}

function buildRecordId(record) {
  const existingId = normalizeText(record && record.recordId)
  if (existingId) {
    return existingId
  }

  const templateId = normalizeText(record && record.templateId)
  const auditId = normalizeText(record && record.auditId)
  const createdAt = String(getTimeValue(record && record.createdAt) || Date.now())

  if (templateId || auditId) {
    return ['autofill', templateId || 'template', auditId || createdAt].join(':')
  }

  return 'autofill:' + buildRandomId()
}

function normalizeAutofillResult(record) {
  const createdAt = normalizeText(record && record.createdAt) || new Date().toISOString()
  const templateName = normalizeFileName(record && record.templateName)
  const summaryText = normalizeText(record && record.summaryText)
  const outputName = normalizeFileName(resolveAutofillOutputName(record))

  return {
    recordId: buildRecordId(record),
    templateId: normalizeText(record && record.templateId),
    auditId: normalizeText(record && record.auditId),
    outputFile: normalizeText(record && record.outputFile),
    outputName: outputName || templateName || '智能填表结果',
    fileName: outputName || templateName || '智能填表结果',
    templateName: templateName || '未命名模板',
    createdAt,
    sourceCount: toNumber(record && record.sourceCount),
    filledCount: toNumber(record && record.filledCount),
    blankCount: toNumber(record && record.blankCount),
    totalSlots: toNumber(record && record.totalSlots),
    fillTimeMs: toNumber(record && record.fillTimeMs),
    summaryText,
    savedFilePath: normalizeText(record && record.savedFilePath),
    lastDownloadedAt: normalizeText(record && record.lastDownloadedAt),
    localFileSize: toNumber(record && record.localFileSize),
  }
}

function sortRecords(list) {
  return list
    .slice()
    .sort((left, right) => {
      const rightTime = getTimeValue(right.createdAt)
      const leftTime = getTimeValue(left.createdAt)
      return rightTime - leftTime
    })
}

function saveRecords(list) {
  const normalizedList = sortRecords(
    (list || []).map(normalizeAutofillResult)
  ).slice(0, MAX_RECORD_COUNT)

  setCurrentUserCache(USER_CACHE_KEYS.autofillResults, normalizedList)
  return normalizedList
}

function listAutofillResults() {
  const rawList = getCurrentUserCache(USER_CACHE_KEYS.autofillResults, [])
  if (!Array.isArray(rawList)) {
    return []
  }

  return saveRecords(rawList)
}

function rememberAutofillResult(record) {
  const normalizedRecord = normalizeAutofillResult(record)
  const currentList = listAutofillResults().filter((item) => item.recordId !== normalizedRecord.recordId)
  const nextList = [normalizedRecord].concat(currentList)
  saveRecords(nextList)
  return normalizedRecord
}

function updateAutofillResult(recordId, patch) {
  const targetId = normalizeText(recordId)
  if (!targetId) {
    return null
  }

  let updatedRecord = null
  const nextList = listAutofillResults().map((item) => {
    if (item.recordId !== targetId) {
      return item
    }

    updatedRecord = normalizeAutofillResult(Object.assign({}, item, patch || {}))
    return updatedRecord
  })

  if (!updatedRecord) {
    return null
  }

  saveRecords(nextList)
  return updatedRecord
}

function removeAutofillResult(recordId) {
  const targetId = normalizeText(recordId)
  if (!targetId) {
    return listAutofillResults()
  }

  const nextList = listAutofillResults().filter((item) => item.recordId !== targetId)
  return saveRecords(nextList)
}

module.exports = {
  listAutofillResults,
  rememberAutofillResult,
  updateAutofillResult,
  removeAutofillResult,
}
