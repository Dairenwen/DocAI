const {
  normalizeFileName,
} = require('./document-name')

function normalizeText(value) {
  return String(value || '').trim()
}

function getFileExtension(fileName) {
  const normalizedName = normalizeFileName(fileName)
  const match = normalizedName.match(/\.([^.\\/]+)$/)
  return match ? String(match[1] || '').toLowerCase() : ''
}

function stripExtension(fileName) {
  const normalizedName = normalizeFileName(fileName)
  return normalizedName.replace(/\.[^.\\/]+$/, '')
}

function truncateText(value, maxLength) {
  const normalizedValue = normalizeText(value)
  if (!normalizedValue || normalizedValue.length <= maxLength) {
    return normalizedValue
  }

  return normalizedValue.slice(0, maxLength)
}

function hasChinese(text) {
  return /[\u4e00-\u9fff]/.test(String(text || ''))
}

function looksMachineGeneratedName(fileName) {
  const baseName = stripExtension(fileName)
  if (!baseName) {
    return true
  }

  if (baseName.indexOf('\ufffd') !== -1) {
    return true
  }

  const tokenList = baseName.split(/[\s_-]+/).filter(Boolean)
  const asciiOnly = /^[A-Za-z0-9._-]+$/.test(baseName)

  const hasOpaqueToken = tokenList.some((token) => {
    return /^[A-Fa-f0-9]{8,}$/.test(token)
      || /^[A-Za-z0-9]{24,}$/.test(token)
      || ((/[A-Za-z]/.test(token) && /\d/.test(token)) && token.length >= 12)
  })

  if (hasOpaqueToken) {
    return true
  }

  if (asciiOnly && baseName.length >= 32) {
    return true
  }

  if (
    asciiOnly
    && /(?:^|[_-])(filled|result|output|export|final)(?:$|[_-])/i.test(baseName)
    && tokenList.length >= 2
  ) {
    return true
  }

  return false
}

function cleanLabel(value) {
  let nextValue = stripExtension(value)
  if (!nextValue) {
    return ''
  }

  nextValue = nextValue
    .replace(/[A-Fa-f0-9]{16,}/g, ' ')
    .replace(/[A-Za-z0-9]{24,}/g, ' ')
    .replace(/\b(filled|fill|result|output|export|final|docai)\b/ig, ' ')
    .replace(/[_]+/g, ' ')
    .replace(/[-]{2,}/g, '-')
    .replace(/\s+/g, ' ')
    .trim()

  if (!nextValue) {
    return ''
  }

  if (!hasChinese(nextValue) && looksMachineGeneratedName(nextValue)) {
    return ''
  }

  return truncateText(nextValue, 32)
}

function isUsableDecisionValue(value) {
  const normalizedValue = cleanLabel(value)
  if (!normalizedValue) {
    return false
  }

  if (normalizedValue.length < 2 || normalizedValue.length > 32) {
    return false
  }

  if (/^\d+$/.test(normalizedValue)) {
    return false
  }

  return !/^(是|否|无|空|未知|未填写|未命名)$/i.test(normalizedValue)
}

function pickDecisionLabel(decisions) {
  const normalizedDecisions = Array.isArray(decisions) ? decisions : []
  const preferredPatterns = [
    /名称|标题|主题|事项|项目|合同|案由|表名|姓名|申请人|单位|公司|企业|客户|部门|学校/i,
    /name|title|subject|project|contract|company|client|department/i,
  ]

  for (let patternIndex = 0; patternIndex < preferredPatterns.length; patternIndex += 1) {
    const pattern = preferredPatterns[patternIndex]
    for (let index = 0; index < normalizedDecisions.length; index += 1) {
      const item = normalizedDecisions[index] || {}
      const fieldName = normalizeText(item.fieldName || item.slotName || item.placeholder || item.key)
      const finalValue = normalizeText(item.finalValue || item.value || item.outputValue)
      if (pattern.test(fieldName) && isUsableDecisionValue(finalValue)) {
        return cleanLabel(finalValue)
      }
    }
  }

  for (let index = 0; index < normalizedDecisions.length; index += 1) {
    const item = normalizedDecisions[index] || {}
    const finalValue = normalizeText(item.finalValue || item.value || item.outputValue)
    if (isUsableDecisionValue(finalValue)) {
      return cleanLabel(finalValue)
    }
  }

  return ''
}

function buildSourceLabel(sourceDocs) {
  const normalizedSourceDocs = Array.isArray(sourceDocs) ? sourceDocs : []
  if (normalizedSourceDocs.length <= 0) {
    return ''
  }

  const firstDoc = normalizedSourceDocs[0] || {}
  const firstName = cleanLabel(normalizeFileName(firstDoc.fileName || firstDoc.title || firstDoc.name))
  if (!firstName) {
    return normalizedSourceDocs.length > 1 ? normalizedSourceDocs.length + '份资料' : ''
  }

  if (normalizedSourceDocs.length === 1) {
    return firstName
  }

  return truncateText(firstName, 18) + '等' + normalizedSourceDocs.length + '份资料'
}

function joinUniqueParts(parts) {
  const result = []

  ;(parts || []).forEach((item) => {
    const value = normalizeText(item)
    if (!value) {
      return
    }

    if (result.some((part) => part === value || part.indexOf(value) !== -1 || value.indexOf(part) !== -1)) {
      return
    }

    result.push(value)
  })

  return result
}

function buildFriendlyBaseName(record) {
  const decisionLabel = pickDecisionLabel(record && record.decisions)
  const sourceLabel = buildSourceLabel(record && record.sourceDocs)
  const templateLabel = cleanLabel(normalizeFileName(record && record.templateName))
  const rawLabel = cleanLabel(normalizeFileName(record && (record.outputName || record.fileName)))

  const parts = joinUniqueParts([
    decisionLabel || sourceLabel,
    templateLabel,
  ])

  let baseName = parts.join('-')
  if (!baseName) {
    baseName = rawLabel || templateLabel || sourceLabel || '智能填表结果'
  }

  if (!/(结果|成表|填表|表单|表格|申请表|登记表|清单|台账|名册|报告|报表)/.test(baseName)) {
    baseName += '-填表结果'
  }

  return truncateText(baseName, 40)
}

function resolveAutofillOutputName(record) {
  const rawOutputName = normalizeFileName(record && (record.outputName || record.fileName))
  const fileExtension = normalizeText(
    (record && record.fileType)
    || getFileExtension(rawOutputName)
    || getFileExtension(record && record.outputFile)
    || getFileExtension(normalizeFileName(record && record.templateName))
    || 'xlsx'
  ).toLowerCase()

  if (rawOutputName && !looksMachineGeneratedName(rawOutputName)) {
    return rawOutputName
  }

  const baseName = buildFriendlyBaseName(record)
  return fileExtension ? baseName + '.' + fileExtension : baseName
}

module.exports = {
  resolveAutofillOutputName,
  looksMachineGeneratedName,
}
