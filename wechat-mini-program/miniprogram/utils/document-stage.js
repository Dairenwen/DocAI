const {
  USER_CACHE_KEYS,
  getCurrentUserCache,
  setCurrentUserCache,
} = require('./storage')

function normalizeUploadStatus(status) {
  return String(status || '').trim().toLowerCase()
}

function toDocumentId(docOrId) {
  if (docOrId && typeof docOrId === 'object') {
    if (docOrId.id || docOrId.id === 0) {
      return String(docOrId.id)
    }
    return ''
  }

  if (docOrId || docOrId === 0) {
    return String(docOrId)
  }

  return ''
}

function loadStageTracker() {
  const value = getCurrentUserCache(USER_CACHE_KEYS.documentStageTracker, {}) || {}
  return value && typeof value === 'object' ? value : {}
}

function saveStageTracker(tracker) {
  setCurrentUserCache(
    USER_CACHE_KEYS.documentStageTracker,
    tracker && typeof tracker === 'object' ? tracker : {}
  )
}

function removeTrackerEntry(tracker, docId) {
  if (!docId || !tracker[docId]) {
    return false
  }

  delete tracker[docId]
  return true
}

function getStageMeta(stageKey, summary) {
  if (stageKey === 'ready') {
    return {
      questionStageKey: 'ready',
      questionStageText: '已就绪，可提问',
      questionStageDesc: '文档已完成解析，可用于 AI 对话与总结。',
      questionStageTone: 'success',
      canChat: true,
    }
  }

  if (stageKey === 'indexing') {
    return {
      questionStageKey: 'indexing',
      questionStageText: '建立知识索引中',
      questionStageDesc: summary || '文档内容已提取，正在准备问答上下文，请稍后再试。',
      questionStageTone: 'warning',
      canChat: false,
    }
  }

  if (stageKey === 'parsing') {
    return {
      questionStageKey: 'parsing',
      questionStageText: '文档解析中',
      questionStageDesc: summary || '文档正在解析与抽取字段，暂时还不能发起问答。',
      questionStageTone: 'warning',
      canChat: false,
    }
  }

  if (stageKey === 'failed') {
    return {
      questionStageKey: 'failed',
      questionStageText: '处理失败',
      questionStageDesc: summary || '文档处理失败，请重新上传后再试。',
      questionStageTone: 'danger',
      canChat: false,
    }
  }

  return {
    questionStageKey: 'uploaded',
    questionStageText: '已上传',
    questionStageDesc: summary || '文件上传成功，后台正在准备解析任务。',
    questionStageTone: 'plain',
    canChat: false,
  }
}

function isDocumentReady(doc) {
  if (!doc) {
    return false
  }

  if (doc.canChat === true || doc.questionStageKey === 'ready') {
    return true
  }

  const uploadStatus = normalizeUploadStatus(doc.uploadStatus)
  return uploadStatus === 'parsed' || (!uploadStatus && Boolean(doc.docSummary))
}

function decorateDocumentsWithStage(list) {
  const tracker = loadStageTracker()
  let trackerChanged = false

  const nextList = (Array.isArray(list) ? list : []).map((item) => {
    const doc = item ? Object.assign({}, item) : {}
    const docId = toDocumentId(doc)
    const uploadStatus = normalizeUploadStatus(doc.uploadStatus)
    const trackerEntry = docId ? Object.assign({}, tracker[docId] || {}) : {}
    let stageKey = 'uploaded'

    if (isDocumentReady(doc)) {
      stageKey = 'ready'
      if (docId && removeTrackerEntry(tracker, docId)) {
        trackerChanged = true
      }
    } else if (uploadStatus === 'failed') {
      stageKey = 'failed'
      if (docId && removeTrackerEntry(tracker, docId)) {
        trackerChanged = true
      }
    } else if (uploadStatus === 'parsing') {
      const parsingSeenCount = Number(trackerEntry.parsingSeenCount || 0)

      if (trackerEntry.phase === 'uploaded' && parsingSeenCount <= 0) {
        stageKey = 'uploaded'
      } else if (parsingSeenCount <= 1) {
        stageKey = 'parsing'
      } else {
        stageKey = 'indexing'
      }

      if (docId) {
        tracker[docId] = {
          phase: stageKey,
          parsingSeenCount: parsingSeenCount + 1,
          uploadedAt: trackerEntry.uploadedAt || Date.now(),
          updatedAt: Date.now(),
        }
        trackerChanged = true
      }
    } else if (docId && trackerEntry.phase) {
      stageKey = trackerEntry.phase
    }

    return Object.assign(doc, getStageMeta(stageKey, doc.docSummary || ''))
  })

  if (trackerChanged) {
    saveStageTracker(tracker)
  }

  return nextList
}

function decorateDocumentWithStage(doc) {
  return decorateDocumentsWithStage(doc ? [doc] : [])[0] || doc
}

function markDocumentsUploaded(docIds) {
  const tracker = loadStageTracker()
  let changed = false

  ;(Array.isArray(docIds) ? docIds : [docIds]).forEach((docId) => {
    const normalizedId = toDocumentId(docId)
    if (!normalizedId) {
      return
    }

    tracker[normalizedId] = {
      phase: 'uploaded',
      parsingSeenCount: 0,
      uploadedAt: Date.now(),
      updatedAt: Date.now(),
    }
    changed = true
  })

  if (changed) {
    saveStageTracker(tracker)
  }
}

function clearDocumentStage(docId) {
  const tracker = loadStageTracker()
  const normalizedId = toDocumentId(docId)
  if (!normalizedId) {
    return
  }

  if (removeTrackerEntry(tracker, normalizedId)) {
    saveStageTracker(tracker)
  }
}

module.exports = {
  normalizeUploadStatus,
  isDocumentReady,
  decorateDocumentsWithStage,
  decorateDocumentWithStage,
  markDocumentsUploaded,
  clearDocumentStage,
}
