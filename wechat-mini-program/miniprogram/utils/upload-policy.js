const MB = 1024 * 1024

const SOURCE_FILE_EXTENSIONS = ['docx', 'xlsx', 'md', 'txt']
const TEMPLATE_FILE_EXTENSIONS = ['docx', 'xlsx']

const DOCUMENT_UPLOAD_POLICY = {
  extensions: SOURCE_FILE_EXTENSIONS.slice(),
  maxCount: 9,
  maxFileSize: 20 * MB,
}

const AUTOFILL_SOURCE_UPLOAD_POLICY = {
  extensions: SOURCE_FILE_EXTENSIONS.slice(),
  maxCount: 10,
  maxFileSize: 20 * MB,
}

const AUTOFILL_TEMPLATE_UPLOAD_POLICY = {
  extensions: TEMPLATE_FILE_EXTENSIONS.slice(),
  maxCount: 1,
  maxFileSize: 20 * MB,
}

function formatFileSize(bytes) {
  const value = Number(bytes) || 0
  if (value >= MB) {
    return (value / MB).toFixed(value % MB === 0 ? 0 : 1) + 'MB'
  }
  return Math.max(1, Math.round(value / 1024)) + 'KB'
}

function formatExtensions(extensions) {
  return (extensions || []).map((item) => String(item || '').toUpperCase()).join(' / ')
}

function getFileExtension(fileName) {
  const parts = String(fileName || '').split('.')
  if (parts.length < 2) {
    return ''
  }
  return String(parts.pop() || '').toLowerCase()
}

function resolveFileName(file, fileNameResolver) {
  if (typeof fileNameResolver === 'function') {
    return String(fileNameResolver(file) || '').trim()
  }
  return String((file && file.name) || '').trim()
}

function validateSelectedFiles(files, policy, fileNameResolver) {
  const selectedFiles = Array.isArray(files) ? files : []
  const normalizedPolicy = Object.assign({
    extensions: [],
    maxCount: selectedFiles.length,
    maxFileSize: 0,
  }, policy || {})

  const acceptedFiles = []
  const rejectedItems = []
  const limitedFiles = selectedFiles.slice(0, normalizedPolicy.maxCount)

  if (selectedFiles.length > normalizedPolicy.maxCount) {
    selectedFiles.slice(normalizedPolicy.maxCount).forEach((file) => {
      rejectedItems.push({
        name: resolveFileName(file, fileNameResolver),
        message: '单次最多上传 ' + normalizedPolicy.maxCount + ' 个文件，请分批处理',
        file: null,
      })
    })
  }

  limitedFiles.forEach((file) => {
    const fileName = resolveFileName(file, fileNameResolver)
    const filePath = String((file && (file.path || file.tempFilePath)) || '').trim()
    const fileExtension = getFileExtension(fileName)
    const fileSize = Number(file && file.size) || 0

    if (!fileName || !filePath) {
      rejectedItems.push({
        name: fileName,
        message: '文件读取失败，请重新选择',
        file: null,
      })
      return
    }

    if (normalizedPolicy.extensions.indexOf(fileExtension) === -1) {
      rejectedItems.push({
        name: fileName,
        message: '仅支持 ' + formatExtensions(normalizedPolicy.extensions) + ' 文件',
        file: null,
      })
      return
    }

    if (normalizedPolicy.maxFileSize > 0 && fileSize > normalizedPolicy.maxFileSize) {
      rejectedItems.push({
        name: fileName,
        message: '文件超过 ' + formatFileSize(normalizedPolicy.maxFileSize) + '，请压缩后重试',
        file: null,
      })
      return
    }

    acceptedFiles.push(file)
  })

  return {
    acceptedFiles,
    rejectedItems,
  }
}

function buildPolicyRules(policy, options) {
  const normalizedPolicy = Object.assign({
    extensions: [],
    maxCount: 0,
    maxFileSize: 0,
  }, policy || {})
  const settings = Object.assign({
    retryText: '上传失败后可在当前页面重新操作',
  }, options || {})

  return [
    {
      label: '支持格式',
      value: formatExtensions(normalizedPolicy.extensions),
    },
    {
      label: '单个文件',
      value: '不超过 ' + formatFileSize(normalizedPolicy.maxFileSize),
    },
    {
      label: '单次上传',
      value: '最多 ' + normalizedPolicy.maxCount + ' 个文件',
    },
    {
      label: '失败处理',
      value: settings.retryText,
    },
  ]
}

module.exports = {
  SOURCE_FILE_EXTENSIONS,
  TEMPLATE_FILE_EXTENSIONS,
  DOCUMENT_UPLOAD_POLICY,
  AUTOFILL_SOURCE_UPLOAD_POLICY,
  AUTOFILL_TEMPLATE_UPLOAD_POLICY,
  formatFileSize,
  formatExtensions,
  validateSelectedFiles,
  buildPolicyRules,
}
