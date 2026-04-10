const { normalizeFileName } = require('./document-name')

const RUNTIME_DEVTOOLS = 'devtools'
const RUNTIME_MOBILE = 'mobile'
const RUNTIME_DESKTOP_CLIENT = 'desktop-client'
const RUNTIME_UNKNOWN = 'unknown'

const PICKER_ERROR_CANCEL = 'picker_cancel'
const PICKER_ERROR_NOT_SUPPORTED = 'picker_not_supported'
const PICKER_ERROR_DEVTOOLS = 'picker_devtools_failed'
const PICKER_ERROR_MOBILE = 'picker_mobile_failed'
const PICKER_ERROR_DESKTOP = 'picker_desktop_failed'
const PICKER_ERROR_UNKNOWN = 'picker_unknown'

function normalizeText(value) {
  return String(value || '').trim()
}

function includesAny(text, list) {
  return (list || []).some((item) => String(text || '').indexOf(item) !== -1)
}

function getRawErrorMessage(error) {
  return normalizeText(
    (error && error.errMsg)
    || (error && error.message)
    || error
  )
}

function resolvePickedFilePath(file) {
  return normalizeText(file && (file.path || file.tempFilePath))
}

function resolvePickedFileName(file) {
  const normalizedName = normalizeFileName(file && file.name)
  if (normalizedName) {
    return normalizedName
  }

  const filePath = resolvePickedFilePath(file)
  return normalizeFileName(filePath.split(/[\\/]/).pop())
}

function normalizePickedFile(file) {
  const filePath = resolvePickedFilePath(file)
  const fileName = resolvePickedFileName(file)
  const numericSize = Number(file && file.size)

  return Object.assign({}, file, {
    name: fileName,
    path: filePath,
    tempFilePath: filePath,
    size: Number.isFinite(numericSize) ? numericSize : 0,
  })
}

function normalizePickedFiles(result) {
  const tempFiles = Array.isArray(result && result.tempFiles) ? result.tempFiles : []

  return tempFiles
    .map((file) => normalizePickedFile(file))
    .filter((file) => file.path)
}

function getPickerRuntime() {
  if (typeof wx === 'undefined' || !wx || typeof wx.getSystemInfoSync !== 'function') {
    return RUNTIME_UNKNOWN
  }

  try {
    const systemInfo = wx.getSystemInfoSync() || {}
    const platform = normalizeText(systemInfo.platform).toLowerCase()

    if (platform === 'devtools') {
      return RUNTIME_DEVTOOLS
    }

    if (platform === 'ios' || platform === 'android') {
      return RUNTIME_MOBILE
    }

    if (platform === 'windows' || platform === 'mac') {
      return RUNTIME_DESKTOP_CLIENT
    }
  } catch (err) {
    return RUNTIME_UNKNOWN
  }

  return RUNTIME_UNKNOWN
}

function canUseChooseMessageFile() {
  if (typeof wx === 'undefined' || !wx) {
    return false
  }

  if (typeof wx.chooseMessageFile === 'function') {
    return true
  }

  if (typeof wx.canIUse === 'function') {
    try {
      return Boolean(wx.canIUse('chooseMessageFile'))
    } catch (err) {
      return false
    }
  }

  return false
}

function buildPickerError(rawError, overrides) {
  const settings = Object.assign({
    code: PICKER_ERROR_UNKNOWN,
    message: '未能打开文件选择器，请稍后重试。',
    runtime: getPickerRuntime(),
  }, overrides || {})

  const error = new Error(settings.message)
  error.name = 'MessageFilePickerError'
  error.code = settings.code
  error.runtime = settings.runtime
  error.errMsg = getRawErrorMessage(rawError)
  error.rawError = rawError || null
  return error
}

function isPickerCancelError(error) {
  const code = normalizeText(error && error.code)
  if (code === PICKER_ERROR_CANCEL) {
    return true
  }

  const rawMessage = getRawErrorMessage(error).toLowerCase()
  return includesAny(rawMessage, ['cancel'])
}

function isDevtoolsPickerError(error) {
  return Boolean(
    error
    && (
      error.code === PICKER_ERROR_DEVTOOLS
      || normalizeText(error.runtime).toLowerCase() === RUNTIME_DEVTOOLS
    )
  )
}

function normalizePickerError(rawError) {
  const runtime = getPickerRuntime()
  const rawMessage = getRawErrorMessage(rawError)
  const normalizedMessage = rawMessage.toLowerCase()

  if (isPickerCancelError(rawError)) {
    return buildPickerError(rawError, {
      code: PICKER_ERROR_CANCEL,
      runtime,
      message: '已取消选择文件。',
    })
  }

  if (
    !canUseChooseMessageFile()
    || includesAny(normalizedMessage, [
      'not support',
      'not supported',
      'no such api',
      'caniuse',
      'no permission',
    ])
  ) {
    return buildPickerError(rawError, {
      code: PICKER_ERROR_NOT_SUPPORTED,
      runtime,
      message: '当前环境不支持从微信会话选择文件。',
    })
  }

  if (runtime === RUNTIME_DEVTOOLS) {
    return buildPickerError(rawError, {
      code: PICKER_ERROR_DEVTOOLS,
      runtime,
      message: '该能力需在真机或 PC 微信验证。',
    })
  }

  if (runtime === RUNTIME_MOBILE) {
    return buildPickerError(rawError, {
      code: PICKER_ERROR_MOBILE,
      runtime,
      message: '请先把文件发到微信会话或文件传输助手。',
    })
  }

  if (runtime === RUNTIME_DESKTOP_CLIENT) {
    return buildPickerError(rawError, {
      code: PICKER_ERROR_DESKTOP,
      runtime,
      message: '请先把文件发到微信会话或文件传输助手。',
    })
  }

  return buildPickerError(rawError, {
    code: PICKER_ERROR_UNKNOWN,
    runtime,
    message: rawMessage || '未能打开文件选择器，请稍后重试。',
  })
}

function getPickerErrorMessage(error, fallbackMessage) {
  if (error && error.name === 'MessageFilePickerError' && error.message) {
    return error.message
  }

  return getRawErrorMessage(error) || fallbackMessage || '未能打开文件选择器，请稍后重试。'
}

function buildPickerOptionList(options) {
  const normalizedOptions = Object.assign({
    count: 1,
    type: 'file',
  }, options || {})
  const optionList = [normalizedOptions]
  const minimalCount = 1

  if (Array.isArray(normalizedOptions.extension) && normalizedOptions.extension.length) {
    optionList.push(Object.assign({}, normalizedOptions, {
      extension: undefined,
    }))
  }

  if (normalizedOptions.type === 'file') {
    optionList.push(Object.assign({}, normalizedOptions, {
      type: 'all',
    }))
  }

  if (
    normalizedOptions.type === 'file'
    && Array.isArray(normalizedOptions.extension)
    && normalizedOptions.extension.length
  ) {
    optionList.push(Object.assign({}, normalizedOptions, {
      type: 'all',
      extension: undefined,
    }))
  }

  optionList.push({
    count: minimalCount,
    type: 'file',
  })
  optionList.push({
    count: minimalCount,
    type: 'all',
  })
  optionList.push({
    count: minimalCount,
  })

  return optionList.filter((item, index, list) => {
    const signature = JSON.stringify({
      count: item.count || 0,
      type: item.type || '',
      extension: item.extension || [],
    })

    return list.findIndex((candidate) => JSON.stringify({
      count: candidate.count || 0,
      type: candidate.type || '',
      extension: candidate.extension || [],
    }) === signature) === index
  })
}

function shouldRetryPicker(error, normalizedError) {
  const rawMessage = getRawErrorMessage(error).toLowerCase()
  if (includesAny(rawMessage, [
    'invalid',
    'parameter',
    'option',
    'type',
    'extension',
    'not support',
    'not supported',
  ])) {
    return true
  }

  return Boolean(
    normalizedError
    && (
      normalizedError.code === PICKER_ERROR_DEVTOOLS
      || normalizedError.runtime === RUNTIME_DESKTOP_CLIENT
    )
  )
}

function chooseMessageFileAsync(options) {
  if (!canUseChooseMessageFile()) {
    return Promise.reject(normalizePickerError({
      errMsg: 'chooseMessageFile not supported',
    }))
  }

  const optionList = buildPickerOptionList(options)

  return new Promise((resolve, reject) => {
    const tryChoose = (index) => {
      const candidateOptions = optionList[index]
      if (!candidateOptions) {
        reject(normalizePickerError({
          errMsg: 'chooseMessageFile failed after retry',
        }))
        return
      }

      wx.chooseMessageFile(Object.assign({}, candidateOptions, {
        success: (result) => {
          const tempFiles = normalizePickedFiles(result)
          resolve(Object.assign({}, result, {
            tempFiles,
            tempFilePaths: tempFiles.map((file) => file.path),
          }))
        },
        fail: (error) => {
          const normalizedError = normalizePickerError(error)
          if (
            !isPickerCancelError(normalizedError)
            && shouldRetryPicker(error, normalizedError)
            && index < optionList.length - 1
          ) {
            tryChoose(index + 1)
            return
          }

          reject(normalizedError)
        },
      }))
    }

    tryChoose(0)
  })
}

function getUploadPickerHint() {
  if (!canUseChooseMessageFile()) {
    return '当前环境不支持会话文件选择。'
  }

  if (getPickerRuntime() === RUNTIME_DEVTOOLS) {
    return '微信开发者工具通常无法模拟该能力，请在真机或 PC 版微信中测试。'
  }

  return '先把文件发到微信会话或文件传输助手，再在小程序里选择上传。'
}

module.exports = {
  canUseChooseMessageFile,
  chooseMessageFileAsync,
  isDevtoolsPickerError,
  getPickerErrorMessage,
  getPickerRuntime,
  getUploadPickerHint,
  isPickerCancelError,
  normalizePickedFiles,
  normalizePickerError,
  resolvePickedFileName,
  resolvePickedFilePath,
}
