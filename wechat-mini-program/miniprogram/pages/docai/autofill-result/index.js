const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')
const {
  loadAutofillResultSession,
  saveAutofillResultSession,
  clearAutofillResultSession,
  getFileTypeFromName,
} = require('../../../utils/autofill-draft')
const {
  listAutofillResults,
  updateAutofillResult,
} = require('../../../utils/autofill-result')
const {
  buildResultSummary,
  buildResultDetailItems,
  resolveResultFileSizeText,
} = require('../../../utils/autofill-result-view')

function normalizeText(value) {
  return String(value || '').trim()
}

function toNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function formatDateTime(value) {
  const timestamp = value ? new Date(value).getTime() : 0
  if (!timestamp) {
    return '--'
  }

  const date = new Date(timestamp)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return month + '-' + day + ' ' + hours + ':' + minutes
}

function normalizeDecision(item) {
  if (!item || typeof item !== 'object') {
    return null
  }

  const confidence = Number(item.finalConfidence || 0) || 0
  return {
    fieldName: normalizeText(item.fieldName || '未命名字段'),
    finalValue: normalizeText(item.finalValue || ''),
    confidenceText: Math.round(confidence * 100) + '%',
    decisionMode: normalizeText(item.decisionMode || 'default'),
    reason: normalizeText(item.reason || ''),
  }
}

function normalizeSourceDoc(item) {
  if (!item || typeof item !== 'object') {
    return null
  }

  return {
    id: String(item.id || ''),
    fileName: normalizeText(item.fileName || '未命名文档'),
  }
}

function normalizeResultPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }

  const outputName = normalizeText(payload.outputName || payload.fileName)
  const templateName = normalizeText(payload.templateName)
  const fileType = normalizeText(payload.fileType).toLowerCase() || getFileTypeFromName(outputName || templateName)

  return {
    recordId: normalizeText(payload.recordId),
    templateId: normalizeText(payload.templateId),
    auditId: normalizeText(payload.auditId),
    templateName: templateName || '未命名模板',
    outputName: outputName || templateName || '智能填表结果',
    outputFile: normalizeText(payload.outputFile),
    fileType,
    fileTheme: fileType === 'docx' || fileType === 'doc' ? 'word' : (fileType === 'xlsx' || fileType === 'xls' ? 'excel' : 'file'),
    typeText: String(fileType || 'file').toUpperCase(),
    summaryText: normalizeText(payload.summaryText),
    filledCount: toNumber(payload.filledCount),
    blankCount: toNumber(payload.blankCount),
    totalSlots: toNumber(payload.totalSlots),
    fillTimeMs: toNumber(payload.fillTimeMs),
    durationMs: toNumber(payload.durationMs || payload.fillTimeMs),
    sourceCount: toNumber(payload.sourceCount),
    fileSizeText: resolveResultFileSizeText(payload),
    userRequirement: normalizeText(payload.userRequirement),
    savedFilePath: normalizeText(payload.savedFilePath || payload.localFilePath),
    localFilePath: normalizeText(payload.localFilePath || payload.savedFilePath),
    localFileSize: toNumber(payload.localFileSize),
    createdAt: normalizeText(payload.createdAt),
    createdAtText: formatDateTime(payload.createdAt),
    decisions: (Array.isArray(payload.decisions) ? payload.decisions : []).map(normalizeDecision).filter(Boolean),
    sourceDocs: (Array.isArray(payload.sourceDocs) ? payload.sourceDocs : []).map(normalizeSourceDoc).filter(Boolean),
  }
}

function buildFallbackResult(record) {
  if (!record) {
    return null
  }

  return normalizeResultPayload({
    recordId: record.recordId,
    templateId: record.templateId,
    auditId: record.auditId,
    templateName: record.templateName,
    outputName: record.outputName || record.fileName,
    outputFile: record.outputFile,
    fileType: getFileTypeFromName(record.outputName || record.fileName || record.templateName),
    summaryText: record.summaryText,
    filledCount: record.filledCount,
    blankCount: record.blankCount,
    totalSlots: record.totalSlots,
    fillTimeMs: record.fillTimeMs,
    sourceCount: record.sourceCount,
    fileSizeText: record.fileSizeText,
    savedFilePath: record.savedFilePath,
    localFilePath: record.savedFilePath,
    localFileSize: record.localFileSize,
    createdAt: record.createdAt,
    decisions: [],
    sourceDocs: [],
  })
}

Page({
  data: {
    missingResult: false,
    result: null,
    resultSummary: null,
    resultDetailItems: [],
    resultDetailExpanded: false,
    downloading: false,
    sharing: false,
  },

  onLoad(options) {
    if (!ensureLogin()) {
      return
    }

    this.loadResult(options && options.recordId)
  },

  loadResult(recordId) {
    const sessionResult = normalizeResultPayload(loadAutofillResultSession())
    let result = sessionResult

    if (!result || (recordId && result.recordId !== String(recordId))) {
      const record = listAutofillResults().find((item) => item.recordId === String(recordId || ''))
      result = buildFallbackResult(record)
    }

    if (!result) {
      this.setData({
        missingResult: true,
        result: null,
        resultSummary: null,
        resultDetailItems: [],
        resultDetailExpanded: false,
      })
      return
    }

    this.pendingRecordId = result.recordId
    this.applyResultView(result)
  },

  chooseDownloadAction() {
    return new Promise((resolve) => {
      wx.showActionSheet({
        itemList: ['下载并打开', '仅下载到本地'],
        success: (res) => {
          resolve(res.tapIndex === 0 ? 'open' : 'save')
        },
        fail: () => resolve(''),
      })
    })
  },

  downloadFileAsync(options) {
    return new Promise((resolve, reject) => {
      wx.downloadFile(Object.assign({}, options, {
        success: resolve,
        fail: reject,
      }))
    })
  },

  saveFileAsync(tempFilePath) {
    return new Promise((resolve, reject) => {
      wx.saveFile({
        tempFilePath,
        success: resolve,
        fail: reject,
      })
    })
  },

  getFileInfoAsync(filePath) {
    return new Promise((resolve, reject) => {
      wx.getFileInfo({
        filePath,
        success: resolve,
        fail: reject,
      })
    })
  },

  openDocumentAsync(filePath, fileType) {
    return new Promise((resolve, reject) => {
      wx.openDocument({
        filePath,
        fileType: fileType || undefined,
        showMenu: true,
        success: resolve,
        fail: reject,
      })
    })
  },

  shareFileMessageAsync(filePath, fileName) {
    return new Promise((resolve, reject) => {
      if (typeof wx.shareFileMessage !== 'function') {
        reject(new Error('当前微信环境不支持直接转发文件'))
        return
      }

      wx.shareFileMessage({
        filePath,
        fileName: fileName || '智能填表结果',
        success: resolve,
        fail: reject,
      })
    })
  },

  applyResultView(result, options) {
    const nextResult = result ? normalizeResultPayload(result) : null
    this.setData(Object.assign({
      missingResult: !nextResult,
      result: nextResult,
      resultSummary: nextResult ? buildResultSummary(nextResult, {
        downloadUrl: api.buildTemplateResultDownloadUrl(nextResult.templateId),
      }) : null,
      resultDetailItems: nextResult ? buildResultDetailItems(nextResult) : [],
      resultDetailExpanded: false,
    }, options || {}))
  },

  toggleResultDetail() {
    if (!(this.data.resultDetailItems || []).length) {
      return
    }

    this.setData({
      resultDetailExpanded: !this.data.resultDetailExpanded,
    })
  },

  async downloadResult(action) {
    const result = this.data.result
    if (!result || !result.templateId || this.data.downloading) {
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({
        title: '登录已过期，请重新登录',
        icon: 'none',
      })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(result.templateId)
    if (!downloadUrl) {
      wx.showToast({
        title: '结果下载地址无效',
        icon: 'none',
      })
      return
    }

    this.setData({ downloading: true })
    wx.showLoading({
      title: '正在下载结果',
      mask: true,
    })

    try {
      const downloadRes = await this.downloadFileAsync({
        url: downloadUrl,
        header: {
          Authorization: 'Bearer ' + token,
        },
        timeout: 120000,
      })

      if (!downloadRes || Number(downloadRes.statusCode) !== 200 || !downloadRes.tempFilePath) {
        if (Number(downloadRes && downloadRes.statusCode) === 401) {
          throw new Error('登录已过期，请重新登录')
        }
        throw new Error('结果文件下载失败')
      }

      let localFileSize = this.data.result && this.data.result.localFileSize
      try {
        const fileInfo = await this.getFileInfoAsync(downloadRes.tempFilePath)
        localFileSize = toNumber(fileInfo && fileInfo.size) || localFileSize
      } catch (fileInfoErr) {
        localFileSize = this.data.result && this.data.result.localFileSize
      }

      if (action === 'save') {
        const saveRes = await this.saveFileAsync(downloadRes.tempFilePath)
        const nextPatch = {
          savedFilePath: saveRes.savedFilePath || '',
          localFilePath: saveRes.savedFilePath || '',
          localFileSize,
          lastDownloadedAt: new Date().toISOString(),
        }
        updateAutofillResult(result.recordId, nextPatch)
        const nextResult = Object.assign({}, this.data.result, nextPatch)
        saveAutofillResultSession(nextResult)
        this.applyResultView(nextResult, {
          resultDetailExpanded: this.data.resultDetailExpanded,
        })
        wx.showToast({
          title: '已下载到本地',
          icon: 'success',
        })
        return
      }

      const nextPatch = {
        localFileSize,
        lastDownloadedAt: new Date().toISOString(),
      }
      updateAutofillResult(result.recordId, nextPatch)
      const nextResult = Object.assign({}, this.data.result, nextPatch)
      saveAutofillResultSession(nextResult)
      this.applyResultView(nextResult, {
        resultDetailExpanded: this.data.resultDetailExpanded,
      })

      try {
        await this.openDocumentAsync(downloadRes.tempFilePath, result.fileType)
        wx.showToast({
          title: '已下载并打开',
          icon: 'success',
        })
      } catch (openErr) {
        wx.showToast({
          title: '已下载，可稍后打开',
          icon: 'none',
        })
      }
    } catch (err) {
      wx.showToast({
        title: normalizeText(err && err.message) || '结果下载失败',
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ downloading: false })
    }
  },

  async shareResult() {
    const result = this.data.result
    if (!result || !result.templateId || this.data.sharing) {
      return
    }

    const token = wx.getStorageSync('token') || ''
    if (!token) {
      wx.showToast({
        title: '登录已过期，请重新登录',
        icon: 'none',
      })
      return
    }

    const downloadUrl = api.buildTemplateResultDownloadUrl(result.templateId)
    if (!downloadUrl) {
      wx.showToast({
        title: '结果下载地址无效',
        icon: 'none',
      })
      return
    }

    this.setData({ sharing: true })
    wx.showLoading({
      title: '正在准备转发',
      mask: true,
    })

    try {
      const downloadRes = await this.downloadFileAsync({
        url: downloadUrl,
        header: {
          Authorization: 'Bearer ' + token,
        },
        timeout: 120000,
      })

      if (!downloadRes || Number(downloadRes.statusCode) !== 200 || !downloadRes.tempFilePath) {
        if (Number(downloadRes && downloadRes.statusCode) === 401) {
          throw new Error('登录已过期，请重新登录')
        }
        throw new Error('结果文件下载失败')
      }

      let localFileSize = this.data.result && this.data.result.localFileSize
      try {
        const fileInfo = await this.getFileInfoAsync(downloadRes.tempFilePath)
        localFileSize = toNumber(fileInfo && fileInfo.size) || localFileSize
      } catch (fileInfoErr) {
        localFileSize = this.data.result && this.data.result.localFileSize
      }

      try {
        await this.shareFileMessageAsync(downloadRes.tempFilePath, result.outputName)
        const nextPatch = {
          localFileSize,
          lastDownloadedAt: new Date().toISOString(),
        }
        updateAutofillResult(result.recordId, nextPatch)
        const nextResult = Object.assign({}, this.data.result, nextPatch)
        saveAutofillResultSession(nextResult)
        this.applyResultView(nextResult, {
          resultDetailExpanded: this.data.resultDetailExpanded,
        })
        wx.showToast({
          title: '已打开转发面板',
          icon: 'success',
        })
        return
      } catch (shareErr) {
        const errorMessage = String((shareErr && shareErr.errMsg) || (shareErr && shareErr.message) || '')
        if (errorMessage.indexOf('cancel') !== -1) {
          return
        }
      }

      await this.openDocumentAsync(downloadRes.tempFilePath, result.fileType)
      wx.showToast({
        title: '当前环境不支持直接转发，请在文档菜单中继续转发',
        icon: 'none',
      })
    } catch (err) {
      wx.showToast({
        title: normalizeText(err && err.message) || '转发准备失败',
        icon: 'none',
      })
    } finally {
      wx.hideLoading()
      this.setData({ sharing: false })
    }
  },

  handleDownloadOpen() {
    this.downloadResult('open')
  },

  handleDownloadSave() {
    this.downloadResult('save')
  },

  handleShare() {
    this.shareResult()
  },

  goDocuments() {
    clearAutofillResultSession()
    wx.switchTab({
      url: '/pages/docai/documents/index',
    })
  },

  restartAutofill() {
    clearAutofillResultSession()
    wx.redirectTo({
      url: '/pages/docai/autofill/index',
    })
  },
})
