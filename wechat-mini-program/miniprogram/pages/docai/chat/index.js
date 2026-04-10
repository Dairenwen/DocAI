const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')
const { isDocumentReady } = require('../../../utils/document-stage')
const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
  removeCurrentUserCache,
} = require('../../../utils/storage')

const INPUT_MIN_HEIGHT = 44
const INPUT_MAX_HEIGHT = 108

const UI_TEXT = {
  navTitle: '智能问答',
  subtitleLinked: '已接入文档上下文',
  subtitleFree: '自由对话模式',
  badgeLinked: '已关联',
  badgeFree: '普通问答',
  noDocTitle: '还没有选择文档',
  contextLinkedPrefix: '已关联：',
  contextLinkedDesc: '可围绕该文档进行总结、提取、改写',
  contextFreeDesc: '可直接提问，也可先关联一份文档',
  chooseDocument: '选择文档',
  changeDocument: '更换',
  cancelAction: '取消',
  welcomeLinkedTitle: '已为你关联文档',
  welcomeFreeTitle: '你好，我随时可以帮你问答、总结和整理内容。',
  welcomeLinkedPrefix: '当前文档为《',
  welcomeLinkedSuffix: '》。你可以让我总结核心内容、提取负责人和时间节点、整理汇报要点，或继续围绕文档内容提问。',
  welcomeFreeDesc: '你可以直接提问，或先选择一份文档，再围绕材料进行定向分析。',
  inputLinked: '围绕该文档继续提问…',
  inputFree: '请输入你的问题',
  pendingText: '生成中',
  historyTitle: '历史会话',
  newConversation: '新对话',
  noHistory: '暂无历史会话',
  moreTitle: '更多操作',
  documentPickerTitle: '选择文档',
  loadingDocuments: '正在加载文档列表...',
  reload: '重新加载',
  noDocuments: '暂无可选文档',
  currentLinked: '当前关联',
  historyToday: '今天',
  historyRecent: '近7天',
  historyEarlier: '更早',
  untitledDoc: '未命名文档',
  answerCardTitle: '提取结果',
  serviceBusy: '服务繁忙',
  serviceBusyDesc: 'AI 正在排队处理，请稍后再试。',
  networkError: '网络异常',
  networkErrorDesc: '当前网络不稳定，请检查后重试。',
  docProcessing: '文档解析中',
  docProcessingDesc: '已连接文档，复杂问题可能需要稍等。',
  retry: '重试',
  unknownReply: '暂未获取到回复，请稍后重试。',
  quickSummary: '总结核心内容',
  quickSummaryDesc: '一键抓住材料重点',
  quickOwner: '提取负责人',
  quickOwnerDesc: '定位负责人和时间信息',
  quickReport: '整理汇报要点',
  quickReportDesc: '生成适合汇报的表达',
  quickTimeline: '找出关键时间节点',
  quickTimelineDesc: '梳理时间线和截止信息',
  quickExplain: '解释这段内容',
  quickExplainDesc: '适合自由问答和补充理解',
  quickFormal: '改写得更正式',
  quickFormalDesc: '调整语气，更适合正式场景',
  quickOutline: '整理成 3 条要点',
  quickOutlineDesc: '把长段内容变得更清晰',
  actionNewLabel: '新对话',
  actionNewDesc: '开启一个全新会话',
  actionClearLabel: '清空当前会话',
  actionClearDesc: '移除当前会话内容',
  actionHistoryLabel: '查看历史会话',
  actionHistoryDesc: '查看最近的聊天记录',
  actionChangeDocLabel: '更换文档',
  actionChangeDocDescLinked: '切换当前关联文档',
  actionChangeDocDescFree: '选择一份文档建立上下文',
  actionClearDocLabel: '取消关联文档',
  actionClearDocDesc: '回到普通问答模式',
  toastNewConversation: '已开启新对话',
  toastSwitchDoc: '已切换到新的关联文档',
  toastSwitchDocAndNew: '已切换文档并开启新对话',
  toastClearDoc: '已取消关联文档',
  clearConversationTitle: '清空当前会话',
  clearConversationContent: '当前会话将从历史记录中移除，是否继续？',
  clearDocumentTitle: '取消关联文档',
  clearDocumentContent: '为避免不同上下文混在一起，将为你开启一个新的普通问答会话。是否继续？',
  loadDocumentsFailed: '文档列表加载失败，请稍后重试',
}

let messageSeed = 0

function createMessageId() {
  messageSeed += 1
  return 'm_' + Date.now() + '_' + messageSeed
}

function getEmptyDoc() {
  return {
    id: '',
    title: '',
    uploadStatus: '',
    questionStageKey: '',
    questionStageText: '',
    questionStageDesc: '',
    canChat: false,
  }
}

function normalizeDoc(doc) {
  if (!doc || (!doc.id && doc.id !== 0)) {
    return getEmptyDoc()
  }

  return {
    id: String(doc.id),
    title: doc.title || doc.fileName || UI_TEXT.untitledDoc,
    uploadStatus: doc.uploadStatus || '',
    questionStageKey: doc.questionStageKey || '',
    questionStageText: doc.questionStageText || '',
    questionStageDesc: doc.questionStageDesc || '',
    canChat: typeof doc.canChat === 'boolean' ? doc.canChat : isDocumentReady(doc),
  }
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function getTimeValue(value) {
  const timestamp = value ? new Date(value).getTime() : 0
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function isSameDay(leftValue, rightValue) {
  const left = new Date(leftValue)
  const right = new Date(rightValue)
  return left.getFullYear() === right.getFullYear()
    && left.getMonth() === right.getMonth()
    && left.getDate() === right.getDate()
}

function formatClock(value) {
  const date = new Date(value || Date.now())
  return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0')
}

function formatMonthDayTime(value) {
  const date = new Date(value || Date.now())
  return String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0') + ' ' + formatClock(date)
}

function formatHistoryTime(value) {
  if (!value) {
    return '--'
  }

  if (isSameDay(value, Date.now())) {
    return formatClock(value)
  }

  return formatMonthDayTime(value)
}

function formatSize(size) {
  if (!size && size !== 0) {
    return '-'
  }

  const kb = 1024
  const mb = kb * 1024

  if (size < kb) {
    return size + ' B'
  }

  if (size < mb) {
    return (size / kb).toFixed(1) + ' KB'
  }

  return (size / mb).toFixed(1) + ' MB'
}

function splitParagraphs(content) {
  const text = String(content || '').replace(/\r\n/g, '\n').trim()
  if (!text) {
    return []
  }

  return text.split(/\n{1,2}/).map((item) => item.trim()).filter(Boolean)
}

function buildAiPresentation(content) {
  const text = String(content || '').trim()
  const paragraphs = splitParagraphs(text)
  const rows = []
  const plainLines = []

  text.replace(/\r\n/g, '\n').split('\n').map((item) => item.trim()).filter(Boolean).forEach((line) => {
    const match = line.match(/^([^：:]{1,18})[：:]\s*(.+)$/)
    if (match && match[2]) {
      rows.push({ label: match[1], value: match[2] })
      return
    }
    plainLines.push(line)
  })

  if (rows.length >= 2) {
    return {
      viewType: 'card',
      paragraphs,
      rows: rows.slice(0, 8),
      cardTitle: plainLines.length ? plainLines.shift() : UI_TEXT.answerCardTitle,
      cardTip: plainLines.join('\n'),
    }
  }

  return {
    viewType: 'text',
    paragraphs: paragraphs.length ? paragraphs : [text || UI_TEXT.unknownReply],
    rows: [],
    cardTitle: '',
    cardTip: '',
  }
}

function decorateMessage(item) {
  const role = item.role === 'user' ? 'user' : 'ai'
  const createdAt = getTimeValue(item.createdAt) || Date.now()
  const content = String(item.content || '').trim()
  const presentation = role === 'ai'
    ? buildAiPresentation(content)
    : { viewType: 'text', paragraphs: [content], rows: [], cardTitle: '', cardTip: '' }

  return {
    id: item.id ? String(item.id) : createMessageId(),
    role,
    content,
    createdAt,
    timeLabel: formatClock(createdAt),
    viewType: presentation.viewType,
    paragraphs: presentation.paragraphs,
    rows: presentation.rows,
    cardTitle: presentation.cardTitle,
    cardTip: presentation.cardTip,
  }
}

function buildMessage(role, content) {
  return decorateMessage({
    id: createMessageId(),
    role,
    content,
    createdAt: Date.now(),
  })
}

function hydrateMessages(list) {
  return (Array.isArray(list) ? list : []).map((item) => decorateMessage(item))
}

function normalizeSession(session) {
  if (!session || !session.id) {
    return null
  }

  return {
    id: String(session.id),
    title: session.title || UI_TEXT.newConversation,
    pinned: Boolean(session.pinned),
    updatedAt: getTimeValue(session.updatedAt) || Date.now(),
    currentDoc: normalizeDoc({
      id: session.linkedDocId,
      title: session.linkedDocName || '',
    }),
  }
}

function sortSessions(list) {
  return (Array.isArray(list) ? list : []).slice().sort((left, right) => {
    if (Boolean(left.pinned) !== Boolean(right.pinned)) {
      return left.pinned ? -1 : 1
    }

    return (right.updatedAt || 0) - (left.updatedAt || 0)
  })
}

function buildHistorySections(list) {
  const now = Date.now()
  const todayItems = []
  const recentItems = []
  const earlierItems = []

  ;(list || []).forEach((item) => {
    const updatedAt = Number(item.updatedAt) || now
    const dayDiff = Math.floor((now - updatedAt) / (24 * 60 * 60 * 1000))
    const historyItem = {
      id: item.id,
      title: item.title || UI_TEXT.newConversation,
      updatedLabel: formatHistoryTime(updatedAt),
    }

    if (isSameDay(updatedAt, now)) {
      todayItems.push(historyItem)
      return
    }

    if (dayDiff < 7) {
      recentItems.push(historyItem)
      return
    }

    earlierItems.push(historyItem)
  })

  return [
    { key: 'today', label: UI_TEXT.historyToday, items: todayItems },
    { key: 'recent', label: UI_TEXT.historyRecent, items: recentItems },
    { key: 'earlier', label: UI_TEXT.historyEarlier, items: earlierItems },
  ]
}

function getQuickPrompts(currentDoc) {
  if (currentDoc && currentDoc.id && currentDoc.canChat === false) {
    return []
  }

  if (currentDoc && currentDoc.id) {
    return [
      { key: 'summary', label: UI_TEXT.quickSummary, desc: UI_TEXT.quickSummaryDesc, prompt: UI_TEXT.quickSummary },
      { key: 'owner', label: UI_TEXT.quickOwner, desc: UI_TEXT.quickOwnerDesc, prompt: UI_TEXT.quickOwner },
      { key: 'report', label: UI_TEXT.quickReport, desc: UI_TEXT.quickReportDesc, prompt: UI_TEXT.quickReport },
      { key: 'timeline', label: UI_TEXT.quickTimeline, desc: UI_TEXT.quickTimelineDesc, prompt: UI_TEXT.quickTimeline },
    ]
  }

  return [
    { key: 'explain', label: UI_TEXT.quickExplain, desc: UI_TEXT.quickExplainDesc, prompt: UI_TEXT.quickExplain },
    { key: 'formal', label: UI_TEXT.quickFormal, desc: UI_TEXT.quickFormalDesc, prompt: UI_TEXT.quickFormal },
    { key: 'outline', label: UI_TEXT.quickOutline, desc: UI_TEXT.quickOutlineDesc, prompt: UI_TEXT.quickOutline },
    { key: 'summary', label: UI_TEXT.quickSummary, desc: UI_TEXT.quickSummaryDesc, prompt: UI_TEXT.quickSummary },
  ]
}

function canAskWithCurrentDoc(currentDoc) {
  return !(currentDoc && currentDoc.id) || currentDoc.canChat !== false
}

function buildContextView(currentDoc) {
  if (currentDoc && currentDoc.id) {
    const canAsk = canAskWithCurrentDoc(currentDoc)

    return {
      topSubtitle: canAsk ? UI_TEXT.subtitleLinked : (currentDoc.questionStageText || UI_TEXT.docProcessing),
      contextBadge: canAsk ? UI_TEXT.badgeLinked : '处理中',
      contextTitle: UI_TEXT.contextLinkedPrefix + currentDoc.title,
      contextDesc: canAsk ? UI_TEXT.contextLinkedDesc : (currentDoc.questionStageDesc || UI_TEXT.docProcessingDesc),
      primaryActionText: UI_TEXT.changeDocument,
      emptyTitle: canAsk ? UI_TEXT.welcomeLinkedTitle : (currentDoc.questionStageText || UI_TEXT.docProcessing),
      emptyDesc: canAsk
        ? UI_TEXT.welcomeLinkedPrefix + currentDoc.title + UI_TEXT.welcomeLinkedSuffix
        : (currentDoc.questionStageDesc || UI_TEXT.docProcessingDesc),
      quickPrompts: getQuickPrompts(currentDoc),
      inputPlaceholder: canAsk ? UI_TEXT.inputLinked : '当前文档准备完成后才可提问',
      canAskCurrentDoc: canAsk,
    }
  }

  return {
    topSubtitle: UI_TEXT.subtitleFree,
    contextBadge: UI_TEXT.badgeFree,
    contextTitle: UI_TEXT.noDocTitle,
    contextDesc: UI_TEXT.contextFreeDesc,
    primaryActionText: UI_TEXT.chooseDocument,
    emptyTitle: UI_TEXT.welcomeFreeTitle,
    emptyDesc: UI_TEXT.welcomeFreeDesc,
    quickPrompts: getQuickPrompts(getEmptyDoc()),
    inputPlaceholder: UI_TEXT.inputFree,
    canAskCurrentDoc: true,
  }
}

function getMoreActions(currentDoc) {
  return [
    { key: 'new', label: UI_TEXT.actionNewLabel, desc: UI_TEXT.actionNewDesc, disabled: false, danger: false },
    { key: 'clear', label: UI_TEXT.actionClearLabel, desc: UI_TEXT.actionClearDesc, disabled: false, danger: true },
    { key: 'history', label: UI_TEXT.actionHistoryLabel, desc: UI_TEXT.actionHistoryDesc, disabled: false, danger: false },
    {
      key: 'changeDoc',
      label: UI_TEXT.actionChangeDocLabel,
      desc: currentDoc && currentDoc.id ? UI_TEXT.actionChangeDocDescLinked : UI_TEXT.actionChangeDocDescFree,
      disabled: false,
      danger: false,
    },
    {
      key: 'clearDoc',
      label: UI_TEXT.actionClearDocLabel,
      desc: UI_TEXT.actionClearDocDesc,
      disabled: !(currentDoc && currentDoc.id),
      danger: false,
    },
  ]
}

function buildNotice(type) {
  const map = {
    'doc-processing': {
      type: 'doc-processing',
      title: UI_TEXT.docProcessing,
      desc: UI_TEXT.docProcessingDesc,
      actionText: '',
    },
    'network-error': {
      type: 'network-error',
      title: UI_TEXT.networkError,
      desc: UI_TEXT.networkErrorDesc,
      actionText: UI_TEXT.retry,
    },
    'service-busy': {
      type: 'service-busy',
      title: UI_TEXT.serviceBusy,
      desc: UI_TEXT.serviceBusyDesc,
      actionText: UI_TEXT.retry,
    },
  }

  return map[type] || map['service-busy']
}

function getReplyText(payload) {
  if (!payload) {
    return UI_TEXT.unknownReply
  }

  if (typeof payload === 'string') {
    return payload
  }

  return payload.reply
    || payload.content
    || payload.answer
    || payload.result
    || payload.message
    || UI_TEXT.unknownReply
}

function resolveErrorType(err) {
  const message = String(
    (err && (err.message || err.errMsg || err.msg))
      || (err && err.data && err.data.message)
      || ''
  ).toLowerCase()

  if (
    message.indexOf('network') !== -1
    || message.indexOf('timeout') !== -1
    || message.indexOf('fail') !== -1
    || message.indexOf('网络') !== -1
  ) {
    return 'network-error'
  }

  if (
    message.indexOf('busy') !== -1
    || message.indexOf('429') !== -1
    || message.indexOf('503') !== -1
    || message.indexOf('繁忙') !== -1
  ) {
    return 'service-busy'
  }

  return 'service-busy'
}

function isAuthError(err) {
  const statusCode = Number((err && err.statusCode) || (err && err.code) || 0)
  if (statusCode === 401) {
    return true
  }

  const message = String(
    (err && (err.message || err.errMsg || err.msg))
      || (err && err.data && err.data.message)
      || ''
  ).toLowerCase()

  return message.indexOf('login expired') !== -1
    || message.indexOf('token') !== -1
    || message.indexOf('令牌') !== -1
    || message.indexOf('登录') !== -1
}

function buildProgressNotice(progressEvent) {
  const detail = String(
    (progressEvent && (progressEvent.detail || progressEvent.reply || progressEvent.message))
      || ''
  ).trim()

  return {
    type: 'doc-processing',
    title: (progressEvent && progressEvent.message) || UI_TEXT.docProcessing,
    desc: detail || UI_TEXT.docProcessingDesc,
    actionText: '',
  }
}

function buildDocumentStageNotice(currentDoc) {
  const nextDoc = normalizeDoc(currentDoc)

  return {
    type: 'doc-processing',
    title: nextDoc.questionStageText || UI_TEXT.docProcessing,
    desc: nextDoc.questionStageDesc || UI_TEXT.docProcessingDesc,
    actionText: '',
  }
}

function buildErrorNotice(err) {
  const noticeType = resolveErrorType(err)
  const message = String(
    (err && (err.message || err.errMsg || err.msg))
      || (err && err.data && err.data.message)
      || ''
  ).trim()
  const notice = buildNotice(noticeType)

  if (!message || isAuthError(err)) {
    return notice
  }

  return Object.assign({}, notice, {
    desc: message,
  })
}

function buildDocumentOption(item) {
  const title = item.fileName || item.title || UI_TEXT.untitledDoc
  const fileType = String(item.fileType || '').toUpperCase()
  const modifiedAt = item.updatedAt || item.createdAt || ''
  const metaParts = []

  if (fileType) {
    metaParts.push(fileType)
  }

  metaParts.push(formatSize(Number(item.fileSize) || 0))

  if (modifiedAt) {
    metaParts.push(formatMonthDayTime(modifiedAt))
  }

  return {
    id: String(item.id),
    title,
    meta: metaParts.join(' / '),
    modifiedAtValue: getTimeValue(modifiedAt),
    uploadStatus: item.uploadStatus || '',
    questionStageKey: item.questionStageKey || '',
    questionStageText: item.questionStageText || '',
    questionStageDesc: item.questionStageDesc || '',
    questionStageTone: item.questionStageTone || '',
    canChat: typeof item.canChat === 'boolean' ? item.canChat : isDocumentReady(item),
  }
}

Page({
  data: {
    ui: UI_TEXT,
    statusBarHeight: 20,
    inputText: '',
    canSend: false,
    inputHeight: INPUT_MIN_HEIGHT,
    loading: false,
    pendingReply: false,
    messages: [],
    scrollIntoView: 'chat-anchor-top',
    currentDoc: getEmptyDoc(),
    currentSessionId: '',
    topSubtitle: UI_TEXT.subtitleFree,
    contextBadge: UI_TEXT.badgeFree,
    contextTitle: UI_TEXT.noDocTitle,
    contextDesc: UI_TEXT.contextFreeDesc,
    primaryActionText: UI_TEXT.chooseDocument,
    emptyTitle: UI_TEXT.welcomeFreeTitle,
    emptyDesc: UI_TEXT.welcomeFreeDesc,
    quickPrompts: getQuickPrompts(getEmptyDoc()),
    inputPlaceholder: UI_TEXT.inputFree,
    canAskCurrentDoc: true,
    showHistoryPopup: false,
    historySections: buildHistorySections([]),
    showMorePopup: false,
    moreActions: getMoreActions(getEmptyDoc()),
    showDocumentPopup: false,
    documentOptions: [],
    documentLoading: false,
    documentError: '',
    stateNotice: null,
  },

  onLoad() {
    const systemInfo = wx.getSystemInfoSync ? wx.getSystemInfoSync() : {}

    this.didInit = false
    this.noticeTimer = null
    this.currentDocPollTimer = null
    this.documentPickerPollTimer = null
    this.lastQuestion = ''
    this.sessionList = []

    this.setData({
      statusBarHeight: systemInfo.statusBarHeight || 20,
    })
  },

  async onShow() {
    if (!ensureLogin()) {
      return
    }

    if (!this.didInit) {
      this.didInit = true
      await this.bootstrapPage()
      return
    }

    await this.refreshRemoteHistory()
    await this.syncLinkedDocument()
  },

  onHide() {
    this.stopCurrentDocPolling()
    this.stopDocumentPickerPolling()
  },

  onUnload() {
    this.clearNoticeTimer()
    this.stopCurrentDocPolling()
    this.stopDocumentPickerPolling()
  },

  buildViewState(currentDoc, messages) {
    const nextDoc = normalizeDoc(currentDoc)

    return Object.assign({}, buildContextView(nextDoc), {
      moreActions: getMoreActions(nextDoc),
    })
  },

  setSessionList(list) {
    this.sessionList = sortSessions(list)
    this.refreshHistorySections()
  },

  refreshHistorySections() {
    this.setData({
      historySections: buildHistorySections(this.sessionList || []),
    })
  },

  findSessionById(sessionId) {
    return (this.sessionList || []).find((item) => item.id === String(sessionId || '')) || null
  },

  upsertSession(session) {
    if (!session) {
      return
    }

    const nextList = (this.sessionList || []).filter((item) => item.id !== session.id)
    nextList.unshift(session)
    this.setSessionList(nextList)
  },

  removeSessionFromList(sessionId) {
    this.setSessionList((this.sessionList || []).filter((item) => item.id !== String(sessionId || '')))
  },

  updateSessionMetaLocal(sessionId, patch) {
    const targetId = String(sessionId || '')
    const nextList = (this.sessionList || []).map((item) => {
      if (item.id !== targetId) {
        return item
      }

      const nextDoc = Object.prototype.hasOwnProperty.call(patch, 'currentDoc')
        ? normalizeDoc(patch.currentDoc)
        : item.currentDoc

      return Object.assign({}, item, patch, {
        currentDoc: nextDoc,
        updatedAt: patch.updatedAt || Date.now(),
      })
    })

    this.setSessionList(nextList)
  },

  scrollToAnchor(anchorId) {
    this.setData({ scrollIntoView: '' })
    const runner = () => {
      this.setData({ scrollIntoView: anchorId })
    }

    if (wx.nextTick) {
      wx.nextTick(runner)
      return
    }

    setTimeout(runner, 0)
  },

  async bootstrapPage() {
    await this.refreshRemoteHistory()
    await this.syncLinkedDocument()
  },

  async refreshRemoteHistory() {
    try {
      const res = await api.listConversations()
      const sessions = (res.data || []).map(normalizeSession).filter(Boolean)
      this.setSessionList(sessions)

      const currentSessionId = this.data.currentSessionId || String(
        getCurrentUserCache(USER_CACHE_KEYS.chatCurrentSessionId, '') || ''
      )
      const targetSession = this.findSessionById(currentSessionId)

      if (targetSession) {
        return
      }

      if (sessions.length) {
        await this.openSessionById(sessions[0].id)
        return
      }

      await this.startNewConversation({
        doc: getEmptyDoc(),
        silent: true,
      })
    } catch (err) {
      if (!this.data.currentSessionId) {
        await this.startNewConversation({
          doc: getEmptyDoc(),
          silent: true,
        })
      }
    }
  },

  async openSessionById(sessionId) {
    const targetSession = this.findSessionById(sessionId)
    if (!targetSession) {
      return false
    }

    let messages = []
    try {
      const res = await api.getConversationMessages(targetSession.id)
      messages = hydrateMessages(res.data || [])
    } catch (err) {
      messages = []
    }

    const currentDoc = await this.resolveCurrentDocState(targetSession.currentDoc)
    const viewState = this.buildViewState(currentDoc, messages)

    this.setData(Object.assign({
      currentSessionId: targetSession.id,
      currentDoc,
      messages,
      inputText: '',
      canSend: false,
      inputHeight: INPUT_MIN_HEIGHT,
      loading: false,
      pendingReply: false,
      stateNotice: null,
      showHistoryPopup: false,
      showMorePopup: false,
      showDocumentPopup: false,
    }, viewState))

    setCurrentUserCache(USER_CACHE_KEYS.chatCurrentSessionId, targetSession.id)
    this.syncCurrentDocStorage(currentDoc)
    this.scheduleCurrentDocPolling(currentDoc)
    if (currentDoc.id && !currentDoc.canChat) {
      this.showDocumentStageNotice(currentDoc)
    }
    this.scrollToAnchor(messages.length ? 'msg-' + messages[messages.length - 1].id : 'chat-anchor-top')
    return true
  },

  async startNewConversation(options) {
    const nextDoc = await this.resolveCurrentDocState((options && options.doc) || this.data.currentDoc || getEmptyDoc())

    try {
      const payload = {
        title: UI_TEXT.newConversation,
      }

      if (nextDoc.id) {
        payload.linkedDocId = Number(nextDoc.id)
        payload.linkedDocName = nextDoc.title
      }

      const res = await api.createConversation(payload)
      const session = normalizeSession(res.data || {})
      if (!session) {
        throw new Error('创建会话失败')
      }

      this.upsertSession(session)

      const viewState = this.buildViewState(nextDoc, [])
      this.clearNoticeTimer()
      this.setData(Object.assign({
        currentSessionId: session.id,
        currentDoc: nextDoc,
        messages: [],
        inputText: '',
        canSend: false,
        inputHeight: INPUT_MIN_HEIGHT,
        loading: false,
        pendingReply: false,
        stateNotice: null,
        showHistoryPopup: false,
        showMorePopup: false,
        showDocumentPopup: false,
      }, viewState))

      setCurrentUserCache(USER_CACHE_KEYS.chatCurrentSessionId, session.id)
      this.syncCurrentDocStorage(nextDoc)
      this.scrollToAnchor('chat-anchor-top')

      if (nextDoc.id && !nextDoc.canChat) {
        this.showDocumentStageNotice(nextDoc)
        this.scheduleCurrentDocPolling(nextDoc)
      } else if (options && options.noticeType) {
        this.showStateNotice(options.noticeType)
      } else {
        this.clearStateNotice()
      }

      if (options && options.toast && !options.silent) {
        wx.showToast({
          title: options.toast,
          icon: 'none',
        })
      }

      return session
    } catch (err) {
      if (!(options && options.silent)) {
        wx.showToast({
          title: (err && err.message) || '创建会话失败',
          icon: 'none',
        })
      }
      return null
    }
  },

  syncCurrentDocStorage(currentDoc) {
    const nextDoc = normalizeDoc(currentDoc)
    if (nextDoc.id) {
      setCurrentUserCache(USER_CACHE_KEYS.currentDoc, nextDoc)
      return
    }

    removeCurrentUserCache(USER_CACHE_KEYS.currentDoc)
  },

  async resolveCurrentDocState(currentDoc) {
    const nextDoc = normalizeDoc(currentDoc)
    if (!nextDoc.id) {
      return nextDoc
    }

    try {
      const res = await api.getDocument(nextDoc.id)
      return normalizeDoc(Object.assign({}, nextDoc, res.data || {}))
    } catch (err) {
      return nextDoc
    }
  },

  hasProcessingDocumentOptions() {
    return (this.data.documentOptions || []).some((item) => item.canChat === false && item.questionStageKey !== 'failed')
  },

  stopCurrentDocPolling() {
    if (this.currentDocPollTimer) {
      clearTimeout(this.currentDocPollTimer)
      this.currentDocPollTimer = null
    }
  },

  stopDocumentPickerPolling() {
    if (this.documentPickerPollTimer) {
      clearTimeout(this.documentPickerPollTimer)
      this.documentPickerPollTimer = null
    }
  },

  scheduleCurrentDocPolling(currentDoc) {
    const targetDoc = normalizeDoc(currentDoc || this.data.currentDoc)

    this.stopCurrentDocPolling()

    if (!targetDoc.id || canAskWithCurrentDoc(targetDoc) || targetDoc.questionStageKey === 'failed') {
      return
    }

    this.currentDocPollTimer = setTimeout(async () => {
      this.currentDocPollTimer = null

      if (!ensureLogin()) {
        return
      }

      const activeDocId = String(this.data.currentDoc.id || '')
      if (activeDocId !== targetDoc.id) {
        return
      }

      const wasBlocked = !canAskWithCurrentDoc(this.data.currentDoc)
      const latestDoc = await this.resolveCurrentDocState(targetDoc)

      if (String(this.data.currentDoc.id || '') !== targetDoc.id) {
        return
      }

      const viewState = this.buildViewState(latestDoc, this.data.messages)
      const nextNotice = latestDoc.id && !latestDoc.canChat
        ? buildDocumentStageNotice(latestDoc)
        : null

      this.setData(Object.assign({
        currentDoc: latestDoc,
        stateNotice: nextNotice,
      }, viewState))

      this.syncCurrentDocStorage(latestDoc)

      if (this.data.currentSessionId) {
        this.updateSessionMetaLocal(this.data.currentSessionId, {
          currentDoc: latestDoc,
        })
      }

      if (!latestDoc.canChat && latestDoc.questionStageKey !== 'failed') {
        this.scheduleCurrentDocPolling(latestDoc)
        return
      }

      if (wasBlocked && latestDoc.canChat) {
        wx.showToast({
          title: latestDoc.questionStageText || '\u6587\u6863\u5df2\u5c31\u7eea',
          icon: 'none',
        })
      }
    }, 3000)
  },

  scheduleDocumentPickerPolling() {
    this.stopDocumentPickerPolling()

    if (!this.data.showDocumentPopup || !this.hasProcessingDocumentOptions()) {
      return
    }

    this.documentPickerPollTimer = setTimeout(async () => {
      this.documentPickerPollTimer = null

      if (!ensureLogin() || !this.data.showDocumentPopup) {
        return
      }

      await this.ensureDocumentsLoaded(true)
    }, 3000)
  },

  async syncLinkedDocument() {
    const linkedDoc = await this.resolveCurrentDocState(
      getCurrentUserCache(USER_CACHE_KEYS.currentDoc, null)
    )
    const currentDoc = normalizeDoc(this.data.currentDoc)
    const hasMessages = this.data.messages.length > 0
    const docIdentityChanged = linkedDoc.id !== currentDoc.id || linkedDoc.title !== currentDoc.title
    const docStateChanged = linkedDoc.uploadStatus !== currentDoc.uploadStatus
      || linkedDoc.questionStageKey !== currentDoc.questionStageKey
      || linkedDoc.canChat !== currentDoc.canChat

    if (!linkedDoc.id) {
      if (!hasMessages && currentDoc.id) {
        await this.applyCurrentDoc(getEmptyDoc())
      }
      return
    }

    if (!docIdentityChanged && !docStateChanged) {
      this.scheduleCurrentDocPolling(linkedDoc)
      return
    }

    if (hasMessages && docIdentityChanged) {
      await this.startNewConversation({
        doc: linkedDoc,
        noticeType: 'doc-processing',
        toast: UI_TEXT.toastSwitchDoc,
      })
      return
    }

    await this.applyCurrentDoc(linkedDoc, {
      noticeType: 'doc-processing',
    })
  },

  async applyCurrentDoc(currentDoc, options) {
    const nextDoc = await this.resolveCurrentDocState(currentDoc)
    const viewState = this.buildViewState(nextDoc, this.data.messages)

    this.setData(Object.assign({
      currentDoc: nextDoc,
    }, viewState))

    this.syncCurrentDocStorage(nextDoc)

    const sessionId = this.data.currentSessionId
    if (sessionId) {
      const patch = {
        linkedDocId: nextDoc.id ? Number(nextDoc.id) : null,
        linkedDocName: nextDoc.id ? nextDoc.title : '',
      }

      this.updateSessionMetaLocal(sessionId, {
        currentDoc: nextDoc,
      })

      try {
        await api.updateConversation(sessionId, patch)
      } catch (err) {
        // keep UI state even if metadata sync fails
      }
    }

    if (nextDoc.id && !nextDoc.canChat) {
      this.showDocumentStageNotice(nextDoc)
      this.scheduleCurrentDocPolling(nextDoc)
      return
    }

    this.stopCurrentDocPolling()

    if (options && options.noticeType) {
      this.showStateNotice(options.noticeType)
      return
    }

    this.clearStateNotice()
  },

  clearNoticeTimer() {
    if (this.noticeTimer) {
      clearTimeout(this.noticeTimer)
      this.noticeTimer = null
    }
  },

  showStateNotice(type) {
    this.clearNoticeTimer()
    this.setData({
      stateNotice: buildNotice(type),
    })

    if (type === 'doc-processing') {
      this.noticeTimer = setTimeout(() => {
        this.setData({ stateNotice: null })
      }, 2400)
    }
  },

  showDocumentStageNotice(currentDoc) {
    this.clearNoticeTimer()
    this.setData({
      stateNotice: buildDocumentStageNotice(currentDoc),
    })
  },

  clearStateNotice() {
    this.clearNoticeTimer()
    this.setData({ stateNotice: null })
  },

  onInput(e) {
    const inputText = e.detail.value || ''
    this.setData({
      inputText,
      canSend: String(inputText).trim().length > 0,
    })
  },

  onInputLineChange(e) {
    const nextHeight = clamp(
      Math.round((e && e.detail && e.detail.height) || INPUT_MIN_HEIGHT),
      INPUT_MIN_HEIGHT,
      INPUT_MAX_HEIGHT
    )

    if (nextHeight !== this.data.inputHeight) {
      this.setData({ inputHeight: nextHeight })
    }
  },

  handleQuickPrompt(e) {
    const prompt = String(e.currentTarget.dataset.prompt || '').trim()
    if (!prompt) {
      return
    }

    this.submitText(prompt)
  },

  async openHistory() {
    await this.refreshRemoteHistory()
    this.stopDocumentPickerPolling()
    this.setData({
      showHistoryPopup: true,
      showMorePopup: false,
      showDocumentPopup: false,
    })
  },

  openMore() {
    this.stopDocumentPickerPolling()
    this.setData({
      showMorePopup: true,
      showHistoryPopup: false,
      showDocumentPopup: false,
      moreActions: getMoreActions(this.data.currentDoc),
    })
  },

  closeMask() {
    this.stopDocumentPickerPolling()
    this.setData({
      showHistoryPopup: false,
      showMorePopup: false,
      showDocumentPopup: false,
    })
  },

  noop() {},

  async handleMoreAction(e) {
    const key = e.currentTarget.dataset.key
    const disabled = e.currentTarget.dataset.disabled === true || e.currentTarget.dataset.disabled === 'true'

    if (disabled) {
      return
    }

    this.setData({ showMorePopup: false })

    if (key === 'new') {
      await this.startNewConversation({
        doc: this.data.currentDoc,
        toast: UI_TEXT.toastNewConversation,
      })
      return
    }

    if (key === 'clear') {
      this.handleClearConversation()
      return
    }

    if (key === 'history') {
      this.openHistory()
      return
    }

    if (key === 'changeDoc') {
      this.openDocumentPicker()
      return
    }

    if (key === 'clearDoc') {
      this.handleClearDocument()
    }
  },

  async clearCurrentConversationAndCreateNew(nextDoc, toastText) {
    const currentSessionId = this.data.currentSessionId
    if (currentSessionId) {
      try {
        await api.deleteConversationApi(currentSessionId)
      } catch (err) {
        // continue to create a new session even if delete fails
      }
      this.removeSessionFromList(currentSessionId)
    }

    await this.startNewConversation({
      doc: nextDoc,
      toast: toastText || UI_TEXT.toastNewConversation,
    })
  },

  handleClearConversation() {
    const run = async () => {
      await this.clearCurrentConversationAndCreateNew(this.data.currentDoc, UI_TEXT.toastNewConversation)
    }

    if (!this.data.messages.length) {
      run()
      return
    }

    wx.showModal({
      title: UI_TEXT.clearConversationTitle,
      content: UI_TEXT.clearConversationContent,
      success: (res) => {
        if (!res.confirm) {
          return
        }

        run()
      },
    })
  },

  handleClearDocument() {
    if (!this.data.currentDoc.id) {
      return
    }

    if (this.data.messages.length) {
      wx.showModal({
        title: UI_TEXT.clearDocumentTitle,
        content: UI_TEXT.clearDocumentContent,
        success: (res) => {
          if (!res.confirm) {
            return
          }

          this.clearCurrentConversationAndCreateNew(getEmptyDoc(), UI_TEXT.toastClearDoc)
        },
      })
      return
    }

    this.applyCurrentDoc(getEmptyDoc())
    wx.showToast({
      title: UI_TEXT.toastClearDoc,
      icon: 'none',
    })
  },

  async openDocumentPicker() {
    this.setData({
      showDocumentPopup: true,
      showHistoryPopup: false,
      showMorePopup: false,
    })

    await this.ensureDocumentsLoaded()
    this.scheduleDocumentPickerPolling()
  },

  async ensureDocumentsLoaded(force) {
    if (this.data.documentLoading) {
      return
    }

    if (this.data.documentOptions.length && !force) {
      return
    }

    this.setData({
      documentLoading: true,
      documentError: '',
    })

    try {
      const res = await api.getSourceDocuments()
      const documentOptions = (res.data || [])
        .map(buildDocumentOption)
        .sort((left, right) => right.modifiedAtValue - left.modifiedAtValue)
        .slice(0, 40)

      this.setData({
        documentOptions,
        documentError: '',
      })
    } catch (err) {
      this.setData({
        documentError: UI_TEXT.loadDocumentsFailed,
      })
    } finally {
      this.setData({
        documentLoading: false,
      })
      if (this.data.showDocumentPopup) {
        this.scheduleDocumentPickerPolling()
      } else {
        this.stopDocumentPickerPolling()
      }
    }
  },

  retryLoadDocuments() {
    this.ensureDocumentsLoaded(true)
  },

  async chooseDocument(e) {
    const targetId = String(e.currentTarget.dataset.id || '')
    const targetDoc = this.data.documentOptions.find((item) => item.id === targetId)

    if (!targetDoc) {
      return
    }

    if (targetDoc.canChat === false) {
      this.showDocumentStageNotice(targetDoc)
      this.scheduleDocumentPickerPolling()
      wx.showToast({
        title: targetDoc.questionStageText || UI_TEXT.docProcessing,
        icon: 'none',
      })
      return
    }

    this.stopDocumentPickerPolling()
    this.setData({ showDocumentPopup: false })

    const nextDoc = {
      id: targetDoc.id,
      title: targetDoc.title,
      uploadStatus: targetDoc.uploadStatus,
      questionStageKey: targetDoc.questionStageKey,
      questionStageText: targetDoc.questionStageText,
      questionStageDesc: targetDoc.questionStageDesc,
      canChat: targetDoc.canChat,
    }

    if (nextDoc.id === this.data.currentDoc.id && nextDoc.title === this.data.currentDoc.title) {
      return
    }

    if (this.data.messages.length) {
      await this.startNewConversation({
        doc: nextDoc,
        noticeType: 'doc-processing',
        toast: UI_TEXT.toastSwitchDocAndNew,
      })
      return
    }

    await this.applyCurrentDoc(nextDoc, {
      noticeType: 'doc-processing',
    })
  },

  async createNewConversation() {
    this.setData({ showHistoryPopup: false })
    await this.startNewConversation({
      doc: this.data.currentDoc,
      toast: UI_TEXT.toastNewConversation,
    })
  },

  async selectHistorySession(e) {
    const sessionId = String(e.currentTarget.dataset.id || '')
    this.setData({ showHistoryPopup: false })
    await this.openSessionById(sessionId)
  },

  retryLastRequest() {
    if (!this.lastQuestion || this.data.loading) {
      return
    }

    if (this.data.currentDoc.id && !this.data.canAskCurrentDoc) {
      this.showDocumentStageNotice(this.data.currentDoc)
      this.scheduleCurrentDocPolling(this.data.currentDoc)
      return
    }

    this.clearStateNotice()
    this.setData({
      loading: true,
      pendingReply: true,
    })
    this.scrollToAnchor('msg-loading')
    this.requestAiReply(this.lastQuestion)
  },

  sendMessage() {
    this.submitText(this.data.inputText)
  },

  generateConversationTitle(question) {
    const text = String(question || '').trim()
    if (!text) {
      return UI_TEXT.newConversation
    }

    return text.length > 22 ? text.slice(0, 22) + '...' : text
  },

  async saveConversationMessage(role, content) {
    const sessionId = this.data.currentSessionId
    if (!sessionId || !content) {
      return
    }

    try {
      await api.addConversationMessage(sessionId, {
        role,
        content,
      })
      this.updateSessionMetaLocal(sessionId, {})
    } catch (err) {
      // keep local UI usable even if message persistence fails
    }
  },

  async submitText(text) {
    const question = String(text || '').trim()
    if (!question || this.data.loading) {
      return
    }

    if (this.data.currentDoc.id && !this.data.canAskCurrentDoc) {
      this.showDocumentStageNotice(this.data.currentDoc)
      this.scheduleCurrentDocPolling(this.data.currentDoc)
      wx.showToast({
        title: this.data.currentDoc.questionStageText || UI_TEXT.docProcessing,
        icon: 'none',
      })
      return
    }

    if (!this.data.currentSessionId) {
      const created = await this.startNewConversation({
        doc: this.data.currentDoc,
        silent: true,
      })
      if (!created) {
        return
      }
    }

    this.lastQuestion = question

    const userMessage = buildMessage('user', question)
    const messages = this.data.messages.concat([userMessage])
    const viewState = this.buildViewState(this.data.currentDoc, messages)
    const title = this.generateConversationTitle(question)

    this.clearStateNotice()
    this.setData(Object.assign({}, viewState, {
      messages,
      inputText: '',
      canSend: false,
      inputHeight: INPUT_MIN_HEIGHT,
      loading: true,
      pendingReply: true,
    }))

    this.updateSessionMetaLocal(this.data.currentSessionId, {
      title,
    })

    try {
      await api.updateConversation(this.data.currentSessionId, {
        title,
      })
    } catch (err) {
      // ignore title sync failure
    }

    this.saveConversationMessage('user', question)
    this.scrollToAnchor('msg-loading')
    this.requestAiReply(question)
  },

  handleAiProgress(progressEvent) {
    if (!this.data.loading) {
      return
    }

    this.setData({
      stateNotice: buildProgressNotice(progressEvent),
    })
  },

  handleAuthExpired() {
    wx.showToast({
      title: '登录已过期，请重新登录',
      icon: 'none',
    })

    setTimeout(() => {
      wx.reLaunch({
        url: '/pages/docai/login/index',
      })
    }, 300)
  },

  async requestAiReply(question) {
    try {
      const payload = {
        message: question,
      }

      if (this.data.currentDoc.id) {
        payload.documentId = this.data.currentDoc.id
      }

      const res = await api.aiChat(payload, {
        onProgress: (progressEvent) => {
          this.handleAiProgress(progressEvent)
        },
      })
      const aiText = getReplyText((res && res.data) || res)
      const aiMessage = buildMessage('ai', aiText)
      const messages = this.data.messages.concat([aiMessage])
      const viewState = this.buildViewState(this.data.currentDoc, messages)

      this.setData(Object.assign({
        messages,
        loading: false,
        pendingReply: false,
        stateNotice: null,
      }, viewState))

      this.saveConversationMessage('ai', aiText)
      this.scrollToAnchor('msg-' + aiMessage.id)
    } catch (err) {
      const viewState = this.buildViewState(this.data.currentDoc, this.data.messages)

      if (isAuthError(err)) {
        this.setData(Object.assign({
          loading: false,
          pendingReply: false,
          stateNotice: null,
        }, viewState))
        this.handleAuthExpired()
        return
      }

      this.setData(Object.assign({
        loading: false,
        pendingReply: false,
        stateNotice: buildErrorNotice(err),
      }, viewState))
    }
  },
})
