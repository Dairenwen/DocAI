const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')

const WORKBENCH_TOOLS = [
  {
    key: 'documents',
    title: '上传资料',
    subtitle: '文档',
    iconText: '上',
    tone: 'blue',
    action: 'goDocuments',
  },
  {
    key: 'autofill',
    title: '智能填表',
    subtitle: '模板',
    iconText: '填',
    tone: 'deep',
    action: 'goAutofill',
  },
  {
    key: 'chat',
    title: 'AI 对话',
    subtitle: '问答',
    iconText: 'AI',
    tone: 'slate',
    action: 'goChat',
  },
  {
    key: 'profile',
    title: '我的',
    subtitle: '账号',
    iconText: '我',
    tone: 'gold',
    action: 'goProfile',
  },
]

function formatTime(ts) {
  const date = ts ? new Date(ts) : new Date()
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return hours + ':' + minutes
}

function isParsedDocument(item) {
  const status = String((item && item.uploadStatus) || '').toLowerCase()
  return status === 'parsed' || (!status && item && item.docSummary)
}

function buildSourceSummary(list) {
  const summary = {
    total: 0,
    parsed: 0,
    parsing: 0,
    failed: 0,
    pending: 0,
  }

  ;(list || []).forEach((item) => {
    summary.total += 1
    const status = String((item && item.uploadStatus) || '').toLowerCase()

    if (isParsedDocument(item)) {
      summary.parsed += 1
      return
    }

    if (status === 'parsing') {
      summary.parsing += 1
      return
    }

    if (status === 'failed') {
      summary.failed += 1
      return
    }

    summary.pending += 1
  })

  return summary
}

function buildAutofillState(summary) {
  if (summary.parsed > 0) {
    return {
      tone: 'success',
      statusText: '可开始',
      readyText: summary.parsed + ' 份资料已就绪',
      hintText: summary.parsing > 0
        ? '另有 ' + summary.parsing + ' 份资料仍在解析中'
        : '可以直接选择模板继续填表',
      ctaText: '选择模板',
      targetAction: 'goAutofill',
    }
  }

  if (summary.parsing > 0) {
    return {
      tone: 'warning',
      statusText: '解析中',
      readyText: '暂无可直接填表的资料',
      hintText: '已有 ' + summary.parsing + ' 份资料在后台处理中',
      ctaText: '去文档页',
      targetAction: 'goDocuments',
    }
  }

  if (summary.total > 0) {
    return {
      tone: 'plain',
      statusText: '待处理',
      readyText: '文档已上传，等待进一步整理',
      hintText: '可继续补充资料，或稍后下拉刷新',
      ctaText: '去文档页',
      targetAction: 'goDocuments',
    }
  }

  return {
    tone: 'plain',
    statusText: '未上传',
    readyText: '还没有可用于填表的资料',
    hintText: '先上传文件，再选择模板生成结果',
    ctaText: '去上传文件',
    targetAction: 'goDocuments',
  }
}

function trimErrorMessage(message, fallbackMessage) {
  const text = String(message || '').replace(/\s+/g, ' ').trim()
  if (!text) {
    return fallbackMessage
  }

  if (text.length <= 88) {
    return text
  }

  return text.slice(0, 88) + '...'
}

Page({
  data: {
    tools: WORKBENCH_TOOLS,
    userName: '团队成员',
    avatarText: '团',
    workspaceRefreshing: false,
    backendStatusTone: 'warn',
    backendStatusText: '系统离线',
    backendNotice: '',
    lastUpdated: '--:--',
    sourceSummary: {
      total: 0,
      parsed: 0,
      parsing: 0,
      failed: 0,
      pending: 0,
    },
    stats: {
      total: 0,
      docx: 0,
      xlsx: 0,
      txt: 0,
      md: 0,
    },
    docBreakdownText: 'Word 0 / Excel 0 / 文本 0',
    autofillState: {
      tone: 'plain',
      statusText: '未上传',
      readyText: '还没有可用于填表的资料',
      hintText: '先上传文件，再选择模板生成结果',
      ctaText: '去上传文件',
      targetAction: 'goDocuments',
    },
  },

  async onShow() {
    if (!ensureLogin()) {
      return
    }

    this.loadUser()
    await this.refreshWorkspace()
  },

  async onPullDownRefresh() {
    if (!ensureLogin()) {
      wx.stopPullDownRefresh()
      return
    }

    this.loadUser()
    const success = await this.refreshWorkspace()
    wx.stopPullDownRefresh()

    wx.showToast({
      title: success ? '工作台已更新' : '刷新失败',
      icon: success ? 'success' : 'none',
    })
  },

  loadUser() {
    const app = getApp()
    const appUser = (app && app.globalData && app.globalData.user) || null
    const cachedUser = wx.getStorageSync('user') || null
    const user = appUser || cachedUser || {}
    const userName = user.nickname || user.username || user.userName || '团队成员'
    const avatarText = String(userName || '工').trim().slice(0, 1).toUpperCase() || '工'

    this.setData({
      userName,
      avatarText,
    })
  },

  markBackendConnected() {
    this.setData({
      backendStatusTone: 'success',
      backendStatusText: '系统在线',
      backendNotice: '',
    })
  },

  markBackendOffline(err) {
    this.setData({
      backendStatusTone: 'warn',
      backendStatusText: '系统离线',
      backendNotice: trimErrorMessage(
        err && err.message,
        '当前服务暂时不可用，请检查网络后重试。'
      ),
    })
  },

  async refreshWorkspace() {
    if (this.data.workspaceRefreshing) {
      return false
    }

    this.setData({ workspaceRefreshing: true })

    let lastError = null
    let hasSuccess = false

    try {
      await this.loadStats()
      hasSuccess = true
    } catch (err) {
      lastError = err
    }

    try {
      await this.loadSourceSummary()
      hasSuccess = true
    } catch (err) {
      lastError = err
    }

    if (hasSuccess) {
      this.markBackendConnected()
      this.setData({
        lastUpdated: formatTime(Date.now()),
      })
    } else {
      this.markBackendOffline(lastError)
    }

    this.setData({ workspaceRefreshing: false })
    return hasSuccess
  },

  async loadStats() {
    const res = await api.getDocumentStats()
    const stats = res.data || this.data.stats
    const textDocCount = Number(stats.txt || 0) + Number(stats.md || 0)

    this.setData({
      stats,
      docBreakdownText: 'Word ' + (stats.docx || 0) + ' / Excel ' + (stats.xlsx || 0) + ' / 文本 ' + textDocCount,
    })

    return res
  },

  async loadSourceSummary() {
    const res = await api.getSourceDocuments()
    const summary = buildSourceSummary(res.data || [])

    this.setData({
      sourceSummary: summary,
      autofillState: buildAutofillState(summary),
    })

    return res
  },

  handleQuickAction(e) {
    const action = e.currentTarget.dataset.action
    if (action && typeof this[action] === 'function') {
      this[action]()
    }
  },

  handleAutofillEntry() {
    const action = this.data.autofillState.targetAction
    if (action && typeof this[action] === 'function') {
      this[action]()
    }
  },

  goDocuments() {
    wx.switchTab({ url: '/pages/docai/documents/index' })
  },

  goChat() {
    wx.switchTab({ url: '/pages/docai/chat/index' })
  },

  goAutofill() {
    wx.navigateTo({ url: '/pages/docai/autofill/index' })
  },

  goProfile() {
    wx.switchTab({ url: '/pages/docai/profile/index' })
  },
})
