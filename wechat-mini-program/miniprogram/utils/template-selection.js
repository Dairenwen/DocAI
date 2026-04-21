const {
  normalizeDocRecord,
} = require('./document-role')
const {
  normalizeFileName,
} = require('./document-name')

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
  const normalizedName = normalizeFileName(fileName)
  const match = normalizedName.match(/\.([^.]+)$/)
  return match ? String(match[1] || '').toLowerCase() : ''
}

function buildTemplateSelection(template) {
  if (!template || typeof template !== 'object') {
    return null
  }

  const localPath = normalizeText(template.localPath || template.templateLocalPath)
  const normalizedDoc = normalizeDocRecord(Object.assign({}, template, {
    id: template.id || template.templateId,
    templateId: template.templateId || template.id,
    fileName: template.fileName || template.title || template.templateName,
    title: template.title || template.fileName || template.templateName,
    localPath,
  }), {
    preferredRole: 'template',
  }) || {}

  const id = normalizeId(normalizedDoc.id || template.templateId)
  const fileName = normalizeFileName(
    normalizedDoc.fileName
    || template.fileName
    || template.title
    || template.templateName
  )

  if (!id && !localPath && !fileName) {
    return null
  }

  const fileType = normalizeText(
    normalizedDoc.fileType || getFileTypeFromName(fileName || localPath)
  ).toLowerCase()
  const parseStatus = normalizeText(
    normalizedDoc.parseStatus
    || normalizedDoc.questionStageKey
    || normalizedDoc.uploadStatus
    || template.parseStatus
  ).toLowerCase() || (id ? 'ready' : 'uploaded')

  let stageTone = 'success'
  let stageText = '待上传'
  let sourceText = '待上传模板'

  if (localPath) {
    sourceText = '来自微信会话文件，将在执行时上传'
  } else if (id) {
    sourceText = '来自模板库，可直接复用'
    stageText = '可直接使用'

    if (parseStatus === 'failed') {
      stageTone = 'danger'
      stageText = '处理失败'
    } else if (
      parseStatus === 'uploaded'
      || parseStatus === 'parsing'
      || parseStatus === 'indexing'
    ) {
      stageTone = 'warning'
      stageText = '处理中'
    }
  }

  return {
    id,
    templateId: id,
    fileName: fileName || '未命名模板',
    localPath,
    fileType,
    typeText: String(fileType || 'file').toUpperCase(),
    stageTone,
    stageText,
    sourceText,
    parseStatus,
  }
}

function buildTemplateDraftPatch(template) {
  const currentTemplate = template ? buildTemplateSelection(template) : null

  return {
    currentTemplate: currentTemplate
      ? {
        id: currentTemplate.id,
        templateId: currentTemplate.templateId,
        fileName: currentTemplate.fileName,
        title: currentTemplate.fileName,
        localPath: currentTemplate.localPath,
        templateLocalPath: currentTemplate.localPath,
        fileType: currentTemplate.fileType,
        parseStatus: currentTemplate.parseStatus,
      }
      : null,
    selectedTemplateId: currentTemplate ? currentTemplate.id : '',
    templateLocalPath: currentTemplate ? currentTemplate.localPath : '',
    templateName: currentTemplate ? currentTemplate.fileName : '',
  }
}

function resolveDraftTemplate(draft) {
  return buildTemplateSelection(
    (draft && draft.currentTemplate)
    || {
      id: draft && draft.selectedTemplateId,
      templateId: draft && draft.selectedTemplateId,
      fileName: draft && draft.templateName,
      title: draft && draft.templateName,
      localPath: draft && draft.templateLocalPath,
      templateLocalPath: draft && draft.templateLocalPath,
    }
  )
}

function hasSelectedTemplate(template) {
  const normalizedTemplate = buildTemplateSelection(template)
  return Boolean(normalizedTemplate && (normalizedTemplate.id || normalizedTemplate.localPath))
}

module.exports = {
  buildTemplateSelection,
  buildTemplateDraftPatch,
  resolveDraftTemplate,
  hasSelectedTemplate,
}
