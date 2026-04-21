const {
  normalizeFileName,
} = require('./document-name')

function normalizeText(value) {
  return String(value || '').trim()
}

function toNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function formatFileSize(size) {
  const numericSize = toNumber(size)
  const kb = 1024
  const mb = kb * 1024

  if (!numericSize) {
    return ''
  }
  if (numericSize < kb) {
    return numericSize + ' B'
  }
  if (numericSize < mb) {
    return (numericSize / kb).toFixed(1) + ' KB'
  }

  return (numericSize / mb).toFixed(1) + ' MB'
}

function formatDurationText(durationMs) {
  const duration = toNumber(durationMs)
  if (!duration) {
    return '--'
  }

  const seconds = duration / 1000
  if (seconds >= 100) {
    return Math.round(seconds) + 's'
  }

  return seconds.toFixed(1).replace(/\.0$/, '') + 's'
}

function resolveResultFileName(result) {
  return normalizeFileName(
    result && (
      result.outputName
      || result.fileName
      || result.resultFileName
      || result.templateName
    )
  ) || '智能填表结果'
}

function resolveResultFileSizeText(result) {
  const explicitText = normalizeText(
    result && (
      result.resultFileSizeText
      || result.outputFileSizeText
      || result.fileSizeText
    )
  )

  const numericSize = toNumber(
    result && (
      result.resultFileSize
      || result.outputFileSize
      || result.localFileSize
      || result.fileSize
    )
  )

  return formatFileSize(numericSize) || explicitText || '--'
}

function buildResultSummary(result, options) {
  const sourceCount = Math.max(0, toNumber(result && result.sourceCount))

  return {
    fileName: resolveResultFileName(result),
    fileSizeText: resolveResultFileSizeText(result),
    sourceCountText: sourceCount + '份',
    durationText: formatDurationText(result && (result.durationMs || result.fillTimeMs)),
    downloadUrl: normalizeText(options && options.downloadUrl),
    localFilePath: normalizeText(
      result && (
        result.savedFilePath
        || result.localFilePath
      )
    ),
  }
}

function buildResultDetailItems(result) {
  const detailItems = []
  const templateName = normalizeText(result && result.templateName)
  const auditId = normalizeText(result && result.auditId)
  const filledCount = toNumber(result && result.filledCount)
  const blankCount = toNumber(result && result.blankCount)
  const totalSlots = toNumber(result && result.totalSlots)
  const summaryText = normalizeText(result && result.summaryText)

  if (templateName) {
    detailItems.push({
      label: '模板名称',
      value: templateName,
    })
  }

  if (auditId) {
    detailItems.push({
      label: '审计编号',
      value: auditId,
    })
  }

  if (filledCount || blankCount || totalSlots) {
    detailItems.push({
      label: '字段统计',
      value: '已填 ' + filledCount + '，待补 ' + blankCount + '，总字段 ' + totalSlots,
    })
  }

  if (summaryText) {
    detailItems.push({
      label: '详细处理说明',
      value: summaryText,
    })
  }

  return detailItems
}

module.exports = {
  formatFileSize,
  formatDurationText,
  resolveResultFileSizeText,
  buildResultSummary,
  buildResultDetailItems,
}
