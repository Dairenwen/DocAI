const {
  normalizeFileName,
} = require('./document-name')
const {
  buildUploadIssueText,
  getFriendlyErrorMessage,
} = require('./feedback')
const {
  chooseMessageFileAsync,
  resolvePickedFileName,
  resolvePickedFilePath,
} = require('./message-file-picker')

function normalizeText(value) {
  return String(value || '').trim()
}

function normalizeExtensionList(extensions) {
  return (Array.isArray(extensions) ? extensions : [])
    .map((item) => normalizeText(item).replace(/^\./, '').toLowerCase())
    .filter(Boolean)
}

function getFileExtension(fileName) {
  const normalizedFileName = normalizeFileName(fileName)
  const parts = normalizedFileName.split('.')
  if (parts.length < 2) {
    return ''
  }

  return normalizeText(parts.pop()).toLowerCase()
}

function formatSupportedExtensions(extensions) {
  const normalizedExtensions = normalizeExtensionList(extensions)
  if (!normalizedExtensions.length) {
    return ''
  }

  return normalizedExtensions.map((item) => item.toUpperCase()).join(' / ')
}

function buildUnsupportedFileMessage(extensions) {
  const extensionText = formatSupportedExtensions(extensions)
  return extensionText
    ? '仅支持 ' + extensionText + ' 文件'
    : '当前文件类型暂不支持'
}

function isAllowedExtension(fileName, extensions) {
  const normalizedExtensions = normalizeExtensionList(extensions)
  if (!normalizedExtensions.length) {
    return true
  }

  return normalizedExtensions.indexOf(getFileExtension(fileName)) !== -1
}

function normalizeSelectedFile(file) {
  const fileName = normalizeFileName(resolvePickedFileName(file))
  const filePath = normalizeText(resolvePickedFilePath(file))
  const fileSize = Number(file && file.size)

  return Object.assign({}, file, {
    name: fileName,
    path: filePath,
    tempFilePath: filePath,
    size: Number.isFinite(fileSize) ? fileSize : 0,
    extension: getFileExtension(fileName),
  })
}

function buildRejectedEntry(file, reason) {
  return {
    file,
    reason: normalizeText(reason) || '文件暂不可用',
    issueText: buildUploadIssueText(file && file.name, reason),
  }
}

async function selectLocalFiles(options) {
  const settings = Object.assign({
    count: 1,
    type: 'file',
    allowedExtensions: [],
  }, options || {})

  const pickRes = await chooseMessageFileAsync({
    count: settings.count,
    type: settings.type,
    extension: settings.allowedExtensions,
  })
  const pickedFiles = (Array.isArray(pickRes && pickRes.tempFiles) ? pickRes.tempFiles : [])
    .map((item) => normalizeSelectedFile(item))

  const validFiles = []
  const rejectedFiles = []
  const unsupportedReason = buildUnsupportedFileMessage(settings.allowedExtensions)

  pickedFiles.forEach((file) => {
    if (!file.name || !file.path) {
      rejectedFiles.push(buildRejectedEntry(file, '未能读取该文件，请重新选择'))
      return
    }

    if (!isAllowedExtension(file.name, settings.allowedExtensions)) {
      rejectedFiles.push(buildRejectedEntry(file, unsupportedReason))
      return
    }

    validFiles.push(file)
  })

  return {
    pickedFiles,
    validFiles,
    rejectedFiles,
    issueTexts: rejectedFiles.map((item) => item.issueText),
    unsupportedReason,
    totalPickedCount: pickedFiles.length,
  }
}

async function uploadSelectedFiles(files, options) {
  const settings = Object.assign({
    uploadOne: null,
    beforeUpload: null,
    onProgress: null,
    failureMessage: '上传失败，请稍后重试',
  }, options || {})
  const selectedFiles = Array.isArray(files) ? files : []

  if (typeof settings.uploadOne !== 'function') {
    throw new Error('missing uploadOne implementation')
  }

  if (typeof settings.beforeUpload === 'function') {
    await settings.beforeUpload(selectedFiles)
  }

  const successItems = []
  const failedItems = []

  for (let index = 0; index < selectedFiles.length; index += 1) {
    const file = selectedFiles[index]

    if (typeof settings.onProgress === 'function') {
      settings.onProgress({
        index,
        total: selectedFiles.length,
        file,
      })
    }

    try {
      const response = await settings.uploadOne(file, index, selectedFiles.length)
      successItems.push({
        file,
        response,
      })
    } catch (error) {
      const message = getFriendlyErrorMessage(error, settings.failureMessage)
      failedItems.push({
        file,
        error,
        message,
        issueText: buildUploadIssueText(file && file.name, message),
      })
    }
  }

  return {
    successItems,
    failedItems,
    successCount: successItems.length,
    failedCount: failedItems.length,
    totalCount: selectedFiles.length,
    issueTexts: failedItems.map((item) => item.issueText),
  }
}

module.exports = {
  formatSupportedExtensions,
  selectLocalFiles,
  uploadSelectedFiles,
}
