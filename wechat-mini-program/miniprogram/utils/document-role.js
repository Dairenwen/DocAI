const {
  normalizeFileName,
} = require('./document-name')

const SOURCE_FILE_TYPES = ['docx', 'xlsx', 'md', 'txt']
const TEMPLATE_FILE_TYPES = ['docx', 'xlsx']
const RESULT_NAME_PATTERN = /(filled|result|output|成表|结果)/i

function normalizeText(value) {
  return String(value || '').trim()
}

function normalizeId(value) {
  if (value || value === 0) {
    return String(value)
  }
  return ''
}

function getFileTypeFromName(fileName) {
  const match = normalizeFileName(fileName).match(/\.([^.]+)$/)
  return match ? String(match[1] || '').toLowerCase() : ''
}

function resolveFileName(doc) {
  return normalizeFileName(
    doc && (
      doc.fileName
      || doc.title
      || doc.name
      || doc.originalFileName
      || doc.outputName
      || doc.templateName
    )
  )
}

function getExplicitRole(doc) {
  const role = normalizeText(doc && (doc.docRole || doc.role || doc.kind)).toLowerCase()
  if (role === 'source' || role === 'template' || role === 'result') {
    return role
  }
  return ''
}

function isResultDoc(doc) {
  const explicitRole = getExplicitRole(doc)
  if (explicitRole === 'result') {
    return true
  }

  const fileName = resolveFileName(doc)
  if (fileName && RESULT_NAME_PATTERN.test(fileName)) {
    return true
  }

  return Boolean(
    normalizeText(doc && doc.recordId)
    || normalizeText(doc && doc.auditId)
    || normalizeText(doc && doc.outputFile)
  )
}

function normalizeParseStatus(doc) {
  const explicitStatus = normalizeText(
    doc && (
      doc.parseStatus
      || doc.questionStageKey
      || doc.uploadStatus
      || doc.status
    )
  ).toLowerCase()

  if (explicitStatus === 'ready' || explicitStatus === 'parsed' || explicitStatus === 'success') {
    return 'ready'
  }
  if (explicitStatus === 'indexing') {
    return 'indexing'
  }
  if (explicitStatus === 'parsing' || explicitStatus === 'processing') {
    return 'parsing'
  }
  if (explicitStatus === 'failed' || explicitStatus === 'error') {
    return 'failed'
  }

  if (doc && (doc.canChat === true || normalizeText(doc.docSummary) || normalizeText(doc.contentText))) {
    return 'ready'
  }

  return explicitStatus || 'uploaded'
}

function inferDocRole(doc, preferredRole) {
  const explicitRole = getExplicitRole(doc)
  if (explicitRole) {
    return explicitRole
  }

  if (isResultDoc(doc)) {
    return 'result'
  }

  if (doc && doc.canUseAsTemplate === true && doc.canUseAsSource !== true) {
    return 'template'
  }
  if (doc && doc.canUseAsSource === true && doc.canUseAsTemplate !== true) {
    return 'source'
  }

  if (preferredRole === 'template') {
    return 'template'
  }

  return 'source'
}

function normalizeDocRecord(doc, options) {
  if (!doc || typeof doc !== 'object') {
    return null
  }

  const preferredRole = normalizeText(options && options.preferredRole).toLowerCase()
  const fileName = resolveFileName(doc)
  const fileType = normalizeText(doc.fileType || getFileTypeFromName(fileName)).toLowerCase()
  const docRole = inferDocRole(doc, preferredRole)
  const parseStatus = normalizeParseStatus(doc)
  const canUseAsSource = doc.canUseAsSource === true || (
    docRole === 'source'
    && !isResultDoc(doc)
    && SOURCE_FILE_TYPES.indexOf(fileType) !== -1
  )
  const canUseAsTemplate = doc.canUseAsTemplate === true || (
    docRole === 'template'
    && !isResultDoc(doc)
    && TEMPLATE_FILE_TYPES.indexOf(fileType) !== -1
  )

  return Object.assign({}, doc, {
    id: normalizeId(doc.id || doc.templateId || doc.docId || doc.recordId),
    fileName: fileName || '未命名文档',
    title: fileName || '未命名文档',
    fileType,
    docRole,
    canUseAsSource,
    canUseAsTemplate,
    parseStatus,
    uploadStatus: normalizeText(doc.uploadStatus).toLowerCase() || parseStatus,
    questionStageKey: normalizeText(doc.questionStageKey).toLowerCase() || parseStatus,
    docSummary: normalizeText(doc.docSummary || doc.contentText),
  })
}

function isSelectableSourceDoc(doc) {
  const normalizedDoc = normalizeDocRecord(doc, { preferredRole: 'source' })
  if (!normalizedDoc || !normalizedDoc.id || isResultDoc(normalizedDoc)) {
    return false
  }

  if (normalizedDoc.canUseAsSource === true) {
    return true
  }

  return normalizedDoc.docRole === 'source'
    && SOURCE_FILE_TYPES.indexOf(normalizedDoc.fileType) !== -1
}

function isSelectableTemplateDoc(doc) {
  const normalizedDoc = normalizeDocRecord(doc, { preferredRole: 'template' })
  if (!normalizedDoc || !normalizedDoc.id || isResultDoc(normalizedDoc)) {
    return false
  }

  if (normalizedDoc.canUseAsTemplate === true) {
    return true
  }

  return normalizedDoc.docRole === 'template'
    && TEMPLATE_FILE_TYPES.indexOf(normalizedDoc.fileType) !== -1
}

module.exports = {
  SOURCE_FILE_TYPES,
  TEMPLATE_FILE_TYPES,
  RESULT_NAME_PATTERN,
  getFileTypeFromName,
  normalizeDocRecord,
  isResultDoc,
  isSelectableSourceDoc,
  isSelectableTemplateDoc,
}
