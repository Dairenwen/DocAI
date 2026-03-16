<template>
  <div class="chat-page">
    <!-- 侧边栏 - 文档上下文 -->
    <div class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
        <el-icon><DArrowLeft v-if="!sidebarCollapsed" /><DArrowRight v-else /></el-icon>
      </div>
      <div class="sidebar-body" v-if="!sidebarCollapsed">
        <div class="sidebar-section">
          <h4>
            <el-icon :size="14"><Link /></el-icon> 关联文档
          </h4>
          <div v-if="currentDoc" class="linked-doc">
            <div class="linked-doc-icon">
              <el-icon :size="20"><Document /></el-icon>
            </div>
            <div class="linked-doc-info">
              <span class="linked-doc-name">{{ currentDoc.fileName }}</span>
              <el-tag size="small" type="success" effect="plain">已加载</el-tag>
            </div>
            <div class="linked-doc-actions">
              <el-tooltip content="下载文档">
                <el-button size="small" circle @click="downloadDoc">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="取消关联">
                <el-button size="small" circle @click="unlinkDoc">
                  <el-icon><Close /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <div v-else class="no-doc-tip">
            <el-button size="small" type="primary" plain class="select-doc-btn" @click="showDocPicker = true">
              <el-icon><FolderOpened /></el-icon> 选择文档关联
            </el-button>
            <p class="tip-text">关联文档后可基于文档内容进行问答和编辑</p>
          </div>
        </div>

        <!-- 文档操作 -->
        <div class="sidebar-section" v-if="currentDoc">
          <h4>
            <el-icon :size="14"><Setting /></el-icon> 文档操作
          </h4>
          <div class="doc-commands">
            <div class="cmd-btn" @click="sendCommand('总结这篇文档的核心内容，提炼关键信息')">
              <el-icon :size="14"><Memo /></el-icon><span>内容摘要</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('提取文档中所有关键数据，包括数字、日期、人名、机构等实体信息，以结构化形式输出')">
              <el-icon :size="14"><Search /></el-icon><span>信息提取</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请对文档内容进行润色和优化，使语言更规范流畅，格式更清晰，输出完整修改后的文档内容')">
              <el-icon :size="14"><EditPen /></el-icon><span>润色优化</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请调整文档的格式结构，优化标题层级、段落划分、列表格式等，输出完整修改后的文档内容')">
              <el-icon :size="14"><SetUp /></el-icon><span>格式调整</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请分析文档中的数据，给出趋势分析和关键发现')">
              <el-icon :size="14"><DataAnalysis /></el-icon><span>数据分析</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请删除文档中不必要的冗余内容和重复段落，精简文档，输出完整修改后的文档内容')">
              <el-icon :size="14"><Delete /></el-icon><span>删除冗余</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请为文档补充缺失的章节内容，完善文档结构，输出完整修改后的文档内容')">
              <el-icon :size="14"><Plus /></el-icon><span>内容补充</span>
            </div>
            <div class="cmd-btn" @click="sendCommand('请将文档翻译为英文，保持原文格式不变，输出完整翻译后的内容')">
              <el-icon :size="14"><Promotion /></el-icon><span>翻译文档</span>
            </div>
            <div class="cmd-btn" @click="exportAIResult">
              <el-icon :size="14"><Download /></el-icon><span>导出结果</span>
            </div>
          </div>
        </div>

        <div class="sidebar-section convo-section">
          <div class="convo-header">
            <h4>
              <el-icon :size="14"><ChatDotRound /></el-icon> 历史对话
            </h4>
            <el-button size="small" type="primary" @click="createNewConversation()">
              <el-icon><Plus /></el-icon> 新对话
            </el-button>
          </div>
          <el-input
            v-model="convoSearchKey"
            size="small"
            clearable
            placeholder="搜索历史对话"
            class="convo-search"
          />
          <div class="conversation-list">
            <el-empty v-if="filteredConversations.length === 0" :description="convoSearchKey ? '未找到匹配对话' : '暂无历史对话'" :image-size="50" />
            <div
              v-for="session in filteredConversations"
              :key="session.id"
              class="conversation-item"
              :class="{ active: session.id === activeConversationId }"
              @click="switchConversation(session.id)"
            >
              <div class="conversation-main">
                <div class="conversation-title">
                  <el-icon v-if="session.pinned" class="pin-mark"><Top /></el-icon>
                  <span>{{ session.title }}</span>
                </div>
                <div class="conversation-meta">
                  <span class="meta-time">{{ formatSessionTime(session.updatedAt) }}</span>
                  <span v-if="session.linkedDocName" class="meta-doc">{{ session.linkedDocName }}</span>
                </div>
              </div>
              <div class="conversation-actions" @click.stop>
                <el-tooltip content="删除">
                  <button class="convo-action-btn delete" @click="deleteConversation(session)">
                    <el-icon :size="14"><Delete /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip :content="session.pinned ? '取消置顶' : '置顶'">
                  <button class="convo-action-btn" :class="{ pinned: session.pinned }" @click="togglePinConversation(session)">
                    <el-icon :size="14"><Top /></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="chat-main">
      <!-- 顶部栏 -->
      <div class="chat-header">
        <div class="chat-title-row">
          <div class="chat-logo">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="11" stroke="var(--primary)" stroke-width="2"/>
              <circle cx="12" cy="12" r="4" fill="var(--primary)"/>
              <line x1="12" y1="1" x2="12" y2="5" stroke="var(--primary)" stroke-width="2"/>
              <line x1="12" y1="19" x2="12" y2="23" stroke="var(--primary)" stroke-width="2"/>
              <line x1="1" y1="12" x2="5" y2="12" stroke="var(--primary)" stroke-width="2"/>
              <line x1="19" y1="12" x2="23" y2="12" stroke="var(--primary)" stroke-width="2"/>
            </svg>
          </div>
          <div>
            <span class="chat-title-text">{{ activeConversation?.title || 'AI 智能对话' }}</span>
            <span class="chat-model-tag">{{ currentProviderLabel }}</span>
          </div>
        </div>
        <div class="chat-actions">
          <el-tooltip content="模型切换">
            <el-button text @click="showModelDialog = true">模型设置</el-button>
          </el-tooltip>
          <el-tooltip content="清空对话">
            <el-button text circle @click="clearChat">
              <el-icon :size="18"><Delete /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="messages-area" ref="chatArea">
        <!-- 欢迎卡片 -->
        <div class="welcome-card" v-if="messages.length <= 1">
          <div class="welcome-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="11" stroke="var(--primary)" stroke-width="1.5"/>
              <circle cx="12" cy="12" r="4" fill="var(--primary)"/>
              <line x1="12" y1="1" x2="12" y2="5" stroke="var(--primary)" stroke-width="1.5"/>
              <line x1="12" y1="19" x2="12" y2="23" stroke="var(--primary)" stroke-width="1.5"/>
              <line x1="1" y1="12" x2="5" y2="12" stroke="var(--primary)" stroke-width="1.5"/>
              <line x1="19" y1="12" x2="23" y2="12" stroke="var(--primary)" stroke-width="1.5"/>
            </svg>
          </div>
          <h3>欢迎使用 DocAI 智能助手</h3>
          <p>支持文档分析、内容总结、智能问答、文档编辑等能力</p>
          <div class="welcome-features">
            <div class="wf-item">
              <el-icon :size="16"><Memo /></el-icon>
              <span>文档内容理解与提问</span>
            </div>
            <div class="wf-item">
              <el-icon :size="16"><DataAnalysis /></el-icon>
              <span>数据分析与信息提取</span>
            </div>
            <div class="wf-item">
              <el-icon :size="16"><EditPen /></el-icon>
              <span>文档编辑、润色与格式调整</span>
            </div>
          </div>
        </div>

        <div
          class="message-wrapper"
          v-for="(msg, index) in messages"
          :key="index"
          :class="[msg.role]"
        >
          <!-- AI 消息 -->
          <template v-if="msg.role === 'ai'">
            <div class="ai-avatar">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" fill="var(--primary)" opacity="0.15"/>
                <circle cx="12" cy="12" r="4" fill="var(--primary)"/>
              </svg>
            </div>
            <div class="message-bubble ai-bubble" :class="{ 'streaming-bubble': msg._streaming }">
              <div class="bubble-text" v-html="renderMessageContent(msg)"></div>
              <div class="bubble-actions" v-if="!msg._isWelcome && !msg._streaming">
                <el-tooltip content="重新生成">
                  <button class="action-btn" @click="regenerateFromLastPrompt">
                    <el-icon :size="14"><RefreshRight /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="继续对话">
                  <button class="action-btn" @click="continueDialogue">
                    <el-icon :size="14"><Promotion /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="复制">
                  <button class="action-btn" @click="copyText(msg.content)">
                    <el-icon :size="14"><CopyDocument /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="预览">
                  <button class="action-btn" @click="previewContent(msg.content)">
                    <el-icon :size="14"><View /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="导出文档">
                  <button class="action-btn" @click="exportContentToWord(msg.content)">
                    <el-icon :size="14"><Document /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="下载文件" v-if="msg._meta?.modifiedExcelUrl">
                  <button class="action-btn" @click="downloadModifiedExcel(msg._meta.modifiedExcelUrl)">
                    <el-icon :size="14"><Download /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="发送至邮箱">
                  <button class="action-btn" @click="msg._meta?.modifiedExcelUrl ? sendResultToEmail(msg._meta.modifiedExcelUrl) : sendContentToEmail(msg.content)">
                    <el-icon :size="14"><Message /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="保存到文档" v-if="currentDoc">
                  <button class="action-btn save-btn" @click="saveToDocument(msg.content)">
                    <el-icon :size="14"><Upload /></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
          </template>

          <!-- 用户消息：消息在左，头像在右 -->
          <template v-else-if="msg.role === 'user'">
            <div class="message-bubble user-bubble">
              <div class="bubble-text">{{ msg.content }}</div>
            </div>
            <div class="user-avatar">{{ avatarChar }}</div>
          </template>
        </div>

        <!-- Loading -->
        <div class="message-wrapper ai" v-if="loading">
          <div class="ai-avatar">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" fill="var(--primary)" opacity="0.15"/>
              <circle cx="12" cy="12" r="4" fill="var(--primary)"/>
            </svg>
          </div>
          <div class="message-bubble ai-bubble loading-bubble">
            <div class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-box" :class="{ focused: inputFocused }">
          <el-input
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            @keydown.enter.exact.prevent="sendMessage"
            @focus="inputFocused = true"
            @blur="inputFocused = false"
            resize="none"
          />
          <div class="input-actions">
            <span class="char-count" v-if="inputText.length > 0">{{ inputText.length }}</span>
            <el-button
              v-if="loading || isStreaming"
              type="danger"
              circle
              @click="stopGeneration"
              class="stop-btn"
            >
              <el-icon><VideoPause /></el-icon>
            </el-button>
            <el-button
              v-else
              type="primary"
              circle
              :disabled="!inputText.trim()"
              @click="sendMessage"
              class="send-btn"
            >
              <el-icon><Position /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="input-footer">
          <span>当前在线模型：{{ currentProviderLabel }}，结果仅供参考</span>
        </div>
      </div>
    </div>

    <!-- 文档选择弹窗 -->
    <el-dialog v-model="showDocPicker" title="选择关联文档" width="600px" destroy-on-close>
      <div class="doc-picker-search">
        <el-input v-model="docSearchKey" placeholder="搜索文档..." clearable />
      </div>
      <div class="doc-picker-list" v-loading="loadingDocList">
        <el-empty v-if="filteredDocList.length === 0" description="暂无文档" :image-size="60" />
        <div
          v-for="doc in filteredDocList"
          :key="doc.fileId"
          class="doc-picker-item"
          @click="selectDoc(doc)"
        >
          <span class="dpi-icon"><el-icon :size="20"><Document /></el-icon></span>
          <div class="dpi-info">
            <span class="dpi-name">{{ doc.fileName }}</span>
            <span class="dpi-meta">{{ doc.fileExtension?.toUpperCase() || 'DOC' }}</span>
          </div>
          <el-tag v-if="doc.uploadStatus === 'parsed'" size="small" type="success" effect="plain">可关联</el-tag>
          <el-tag v-else-if="doc.uploadStatus === 'failed'" size="small" type="danger" effect="plain">提取失败</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">处理中</el-tag>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showModelDialog" title="大模型切换" width="520px">
      <el-form label-position="top">
        <el-form-item label="模型提供商">
          <el-select v-model="selectedProvider" placeholder="请选择模型提供商" style="width: 100%" :loading="modelLoading" @change="onProviderChange">
            <el-option
              v-for="item in llmProviders"
              :key="item.name"
              :label="`${item.name}${item.available ? '' : ' (不可用)'}`"
              :value="item.name"
              :disabled="!item.available"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型选择" v-if="availableModels.length > 0">
          <el-select v-model="selectedModel" placeholder="请选择模型" style="width: 100%">
            <el-option
              v-for="model in availableModels"
              :key="model"
              :label="model"
              :value="model"
            />
          </el-select>
        </el-form-item>
        <div class="model-info" v-if="selectedProvider">
          <el-tag effect="plain" size="small">当前：{{ currentProvider }}:{{ currentModel || '默认' }}</el-tag>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showModelDialog = false">取消</el-button>
        <el-button type="primary" :loading="modelLoading" @click="handleSwitchModel">切换</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="内容预览" width="80vw" top="3vh">
      <div class="preview-body" v-html="renderMarkdown(previewText)"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { aiChat, getSourceDocuments, getExcelFiles, downloadExcelFile, downloadSourceDocument, downloadBlob, getLlmProviders, getCurrentLlmProvider, switchLlmProvider, sendAiResultEmail, sendContentEmail, listConversations, createConversation, updateConversation, deleteConversationApi, getConversationMessages, addConversationMessage } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete, Position, CopyDocument, Document, Download, Close, FolderOpened,
  DArrowLeft, DArrowRight, Link, Setting, Memo, Search, EditPen, SetUp,
  DataAnalysis, Upload, ChatDotRound, Plus, Top, RefreshRight, Promotion, View, Message, VideoPause
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { startTask, getTask, clearTask, abortTask, subscribe as subscribeTask } from '../utils/chatTaskManager'

// Configure marked for safe rendering
marked.setOptions({
  breaks: true,
  gfm: true
})

const route = useRoute()
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const chatArea = ref(null)
const inputFocused = ref(false)
const sidebarCollapsed = ref(false)

// Document state
const currentDoc = ref(null)
const parseDocId = (value) => {
  if (value === undefined || value === null || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : null
}
const currentDocId = ref(parseDocId(route.query.docId || route.query.fileId))
const showDocPicker = ref(false)
const docSearchKey = ref('')
const docList = ref([])
const loadingDocList = ref(false)
const lastAIContent = ref('')
const isStreaming = ref(false)
const sessionsLoading = ref(false)
const showModelDialog = ref(false)
const modelLoading = ref(false)
const llmProviders = ref([])
const selectedProvider = ref('dashscope')
const selectedModel = ref('')
const currentProvider = ref('dashscope')
const currentModel = ref('')
const previewVisible = ref(false)
const previewText = ref('')
const lastUserPrompt = ref('')
let _streamingTimer = null
let _unsubscribe = null

// 用户信息
const userId = localStorage.getItem('userId') || 'default'
const nickname = localStorage.getItem('nickname') || '用户'
const avatarChar = computed(() => nickname?.charAt(0) || 'U')
const conversations = ref([])
const activeConversationId = ref(null)
const convoSearchKey = ref('')

const toTimestamp = (value) => {
  const t = new Date(value || 0).getTime()
  return Number.isFinite(t) ? t : 0
}

const sortedConversations = computed(() => {
  return [...conversations.value].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return toTimestamp(b.updatedAt) - toTimestamp(a.updatedAt)
  })
})

const activeConversation = computed(() => {
  return conversations.value.find(item => item.id === activeConversationId.value) || null
})

const filteredConversations = computed(() => {
  const keyword = convoSearchKey.value.trim().toLowerCase()
  if (!keyword) return sortedConversations.value
  return sortedConversations.value.filter(item => item.title?.toLowerCase().includes(keyword))
})

const currentProviderLabel = computed(() => {
  const model = currentModel.value || '默认'
  return `${currentProvider.value || 'dashscope'}:${model}`
})

const availableModels = computed(() => {
  const provider = llmProviders.value.find(p => p.name === selectedProvider.value)
  return provider?.models || []
})

const onProviderChange = () => {
  const provider = llmProviders.value.find(p => p.name === selectedProvider.value)
  selectedModel.value = provider?.defaultModel || ''
}

const createSession = (linkedDocId = null, linkedDocName = '') => {
  const now = new Date().toISOString()
  return {
    id: null, // Will be set after API call
    title: '新对话',
    pinned: false,
    createdAt: now,
    updatedAt: now,
    linkedDocId,
    linkedDocName,
    messages: []
  }
}

const normalizeSession = (session) => {
  if (!session || typeof session !== 'object') return null
  return {
    id: session.id,
    title: typeof session.title === 'string' && session.title.trim() ? session.title.trim() : '新对话',
    pinned: Boolean(session.pinned),
    createdAt: session.createdAt || new Date().toISOString(),
    updatedAt: session.updatedAt || new Date().toISOString(),
    linkedDocId: parseDocId(session.linkedDocId),
    linkedDocName: typeof session.linkedDocName === 'string' ? session.linkedDocName : '',
    messages: Array.isArray(session.messages) ? session.messages.filter(m => m?.role && typeof m?.content === 'string').map(m => ({ role: m.role, content: m.content })) : []
  }
}

const saveConversations = () => {
  // No-op: conversations are now persisted server-side
}

const updateConversationMeta = async (sessionId, patch = {}) => {
  const target = conversations.value.find(item => item.id === sessionId)
  if (!target) return
  Object.assign(target, patch)
  try {
    await updateConversation(sessionId, patch)
  } catch (e) {
    console.warn('更新会话失败', e)
  }
}

const persistActiveMessages = () => {
  // No-op: messages are persisted via addConversationMessage on send/receive
}

const generateConversationTitle = (text) => {
  const cleaned = (text || '').replace(/\s+/g, ' ').trim()
  if (!cleaned) return '新对话'
  const topicMap = [
    { key: /(总结|摘要|概括|大纲)/, title: '文档总结与大纲' },
    { key: /(提取|信息|字段|实体)/, title: '信息提取分析' },
    { key: /(统计|趋势|对比|分析)/, title: '数据统计分析' },
    { key: /(润色|优化|改写|编辑|修改)/, title: '文档编辑优化' },
    { key: /(表格|填表|模板)/, title: '智能填表处理' }
  ]
  const matched = topicMap.find(item => item.key.test(cleaned))
  if (matched) return matched.title
  return cleaned.length > 16 ? `${cleaned.slice(0, 16)}...` : cleaned
}

const formatSessionTime = (value) => {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const formatDate = (d) => d ? d.split('T')[0] : '-'

const filteredDocList = computed(() => {
  if (!docSearchKey.value) return docList.value
  const k = docSearchKey.value.toLowerCase()
  return docList.value.filter(d => d.fileName?.toLowerCase().includes(k))
})

const buildWelcomeMessage = (doc) => {
  if (doc) {
    return `您好，我已加载文档"${doc.fileName}"。\n\n我可以帮您：\n- 总结文档核心内容\n- 查询与筛选信息\n- 编辑、修改、增删文档内容\n- 润色优化、格式调整\n- 翻译文档\n- 下载修改结果或发送至邮箱\n\n您可以直接提问，或使用左侧快捷操作。`
  }
  return '您好！我是 DocAI 智能助手。您可以直接进行日常 AI 对话；关联文档后，还可执行文档编辑、内容增删、润色翻译、信息提取、统计分析等操作，并可导出结果或发送至邮箱。'
}

const createNewConversation = async (linkedDocId = null, linkedDocName = '') => {
  if (loading.value || isStreaming.value || sessionsLoading.value) {
    ElMessage.warning('操作进行中，请稍后再试')
    return
  }
  try {
    const res = await createConversation({
      title: '新对话',
      linkedDocId: linkedDocId || undefined,
      linkedDocName: linkedDocName || undefined
    })
    if (!res || !res.data) {
      throw new Error('服务器返回数据为空')
    }
    const newSession = normalizeSession(res.data)
    if (!newSession || !newSession.id) {
      throw new Error('返回的会话数据无效')
    }
    conversations.value.unshift(newSession)
    activeConversationId.value = newSession.id
    currentDoc.value = null
    currentDocId.value = linkedDocId
    if (linkedDocId) {
      await loadDocument(linkedDocId)
      await updateConversationMeta(newSession.id, { linkedDocId, linkedDocName: currentDoc.value?.fileName || linkedDocName || '' })
    }
    messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
    lastAIContent.value = ''
  } catch (e) {
    const errMsg = e?.response?.data?.message || e?.message || '创建对话失败，请刷新页面后重试'
    const errUrl = e?.config?.url || 'unknown'
    const errMethod = e?.config?.method || 'unknown'
    console.error('创建对话失败', errMsg, errUrl, errMethod, e)
    ElMessage.error(`[${errMethod} ${errUrl}] ${errMsg}`)
  }
}

const switchConversation = async (sessionId) => {
  if (loading.value || isStreaming.value) {
    ElMessage.warning('当前正在生成回复，请稍后切换对话')
    return
  }
  if (sessionId === activeConversationId.value) return
  const target = conversations.value.find(item => item.id === sessionId)
  if (!target) return
  activeConversationId.value = sessionId
  currentDocId.value = parseDocId(target.linkedDocId)
  if (currentDocId.value) {
    await loadDocument(currentDocId.value)
  } else {
    currentDoc.value = null
  }
  // Load messages from server
  try {
    const res = await getConversationMessages(sessionId)
    const serverMsgs = res.data || []
    if (serverMsgs.length > 0) {
      messages.value = serverMsgs.map(m => ({ role: m.role, content: m.content }))
    } else {
      messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
    }
  } catch (e) {
    messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
  }
  lastAIContent.value = ''
  await scrollToBottom()
}

const deleteConversation = async (session) => {
  try {
    await ElMessageBox.confirm(`确认删除对话「${session.title}」吗？`, '删除对话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }

  try {
    await deleteConversationApi(session.id)
  } catch (e) {
    ElMessage.error('删除对话失败')
    return
  }

  const idx = conversations.value.findIndex(item => item.id === session.id)
  if (idx !== -1) conversations.value.splice(idx, 1)

  if (activeConversationId.value === session.id) {
    if (conversations.value.length === 0) {
      await createNewConversation()
    } else {
      await switchConversation(sortedConversations.value[0].id)
    }
  }
  ElMessage.success('已删除该对话')
}

const togglePinConversation = (session) => {
  updateConversationMeta(session.id, { pinned: !session.pinned })
}

const loadSessions = async () => {
  sessionsLoading.value = true
  try {
    let parsedSessions = []
    try {
      const res = await listConversations()
      const serverSessions = res.data || []
      parsedSessions = serverSessions.map(normalizeSession).filter(Boolean)
    } catch (e) {
      console.warn('加载会话列表失败', e)
    }

    conversations.value = parsedSessions

    const routeDocId = parseDocId(route.query.docId || route.query.fileId)
    let targetSessionId = null
    if (routeDocId) {
      const matched = conversations.value.find(item => parseDocId(item.linkedDocId) === routeDocId)
      if (matched) {
        targetSessionId = matched.id
      } else {
        try {
          const res = await createConversation({ linkedDocId: routeDocId })
          const newSession = normalizeSession(res.data)
          if (newSession && newSession.id) {
            conversations.value.unshift(newSession)
            targetSessionId = newSession.id
          }
        } catch (e) {
          console.warn('创建关联对话失败', e)
        }
      }
    }

    if (!targetSessionId) {
      targetSessionId = conversations.value[0]?.id
    }

    if (!targetSessionId) {
      try {
        const res = await createConversation({ title: '新对话' })
        const newSession = normalizeSession(res.data)
        if (newSession && newSession.id) {
          conversations.value.push(newSession)
          targetSessionId = newSession.id
        }
      } catch (e) {
        console.warn('创建默认对话失败', e)
        ElMessage.warning('会话初始化失败，请点击"新对话"按钮重试')
      }
    }

    activeConversationId.value = targetSessionId
    const active = conversations.value.find(item => item.id === targetSessionId)
    currentDocId.value = parseDocId(active?.linkedDocId)
    if (currentDocId.value) {
      await loadDocument(currentDocId.value)
    } else {
      currentDoc.value = null
    }

    // Load messages from server
    if (targetSessionId) {
      try {
        const res = await getConversationMessages(targetSessionId)
        const serverMsgs = res.data || []
        if (serverMsgs.length > 0) {
          messages.value = serverMsgs.map(m => ({ role: m.role, content: m.content }))
        } else {
          messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
        }
      } catch (e) {
        messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
      }
    } else {
      messages.value = [{ role: 'ai', content: buildWelcomeMessage(currentDoc.value), _isWelcome: true }]
    }

    saveConversations()
  } finally {
    sessionsLoading.value = false
  }
}

watch(messages, () => {
  if (isStreaming.value) return
  persistActiveMessages()
}, { deep: true })

watch(() => route.query.docId || route.query.fileId, async (newDocId) => {
  const parsed = parseDocId(newDocId)
  if (!parsed) return
  if (currentDocId.value === parsed) return
  await createNewConversation(parsed)
})

// Load document by ID (from source documents)
const loadDocument = async (id) => {
  if (!id || !Number.isFinite(id)) return
  try {
    const res = await getSourceDocuments()
    const docs = res.data || []
    const found = docs.find(d => Number(d.id) === Number(id))
    if (!found) {
      throw new Error('文件不存在或无权限')
    }
    currentDoc.value = { fileId: found.id, fileName: found.fileName, fileExtension: found.fileType, uploadStatus: found.uploadStatus }
    currentDocId.value = id
    if (found.uploadStatus !== 'parsed') {
      ElMessage.warning(`文档"${found.fileName}"提取未完成，AI对话功能可能受限`)
    }
  } catch (e) {
    ElMessage.error('文档加载失败: ' + (e.message || '未知错误'))
    currentDoc.value = null
    currentDocId.value = null
  }
}

// Load document list for picker (from source documents)
const loadDocList = async () => {
  loadingDocList.value = true
  try {
    const res = await getSourceDocuments()
    const docs = res.data || []
    docList.value = docs.map(d => ({
      fileId: d.id,
      fileName: d.fileName,
      fileExtension: d.fileType,
      uploadStatus: d.uploadStatus
    }))
  } catch (e) {
    console.error('加载文档列表失败', e)
  } finally {
    loadingDocList.value = false
  }
}

const selectDoc = async (doc) => {
  showDocPicker.value = false
  currentDoc.value = doc
  currentDocId.value = doc.fileId
  if (activeConversationId.value) {
    updateConversationMeta(activeConversationId.value, { linkedDocId: doc.fileId, linkedDocName: doc.fileName })
  }
  if (doc.uploadStatus && doc.uploadStatus !== 'parsed') {
    messages.value.push({
      role: 'ai',
      content: `已选择文档"${doc.fileName}"，但该文档提取未成功，关联功能可能受限。`,
      _isWelcome: true
    })
  } else {
    messages.value.push({
      role: 'ai',
      content: `已关联文档"${doc.fileName}"，进入历史对话时会自动恢复当前关联文档。`,
      _isWelcome: true
    })
  }
  await scrollToBottom()
}

const unlinkDoc = () => {
  currentDoc.value = null
  currentDocId.value = null
  if (activeConversationId.value) {
    updateConversationMeta(activeConversationId.value, { linkedDocId: null, linkedDocName: '' })
  }
  messages.value.push({
    role: 'ai',
    content: '已取消文档关联。您可以继续自由对话，或重新选择文档。',
    _isWelcome: true
  })
}

const downloadDoc = () => {
  if (!currentDocId.value) return
  downloadSourceDocument(currentDocId.value)
    .then((res) => {
      const name = currentDoc.value?.fileName || `file_${currentDocId.value}`
      downloadBlob(new Blob([res.data]), name)
      ElMessage.success('下载成功')
    })
    .catch((e) => {
      ElMessage.error('下载失败: ' + (e.message || '未知错误'))
    })
}

onMounted(async () => {
  loadDocList()
  await loadModelProviders()
  await loadSessions()
  await scrollToBottom()

  // 恢复后台AI任务
  const pendingTask = getTask()
  if (pendingTask) {
    if (pendingTask.status === 'pending') {
      loading.value = true
    }
    attachToTask(pendingTask)
  }
})

const loadModelProviders = async () => {
  modelLoading.value = true
  try {
    const [providersRes, currentRes] = await Promise.all([
      getLlmProviders(),
      getCurrentLlmProvider()
    ])
    llmProviders.value = providersRes.data || []
    currentProvider.value = currentRes.data?.currentProvider || 'dashscope'
    currentModel.value = currentRes.data?.currentModel || ''
    selectedProvider.value = currentProvider.value
    selectedModel.value = currentModel.value || (llmProviders.value.find(p => p.name === currentProvider.value)?.defaultModel || '')
  } catch (e) {
    ElMessage.error('加载模型列表失败')
  } finally {
    modelLoading.value = false
  }
}

const handleSwitchModel = async () => {
  if (!selectedProvider.value) {
    ElMessage.warning('请先选择模型提供商')
    return
  }
  modelLoading.value = true
  try {
    // 发送 "provider:model" 格式
    const switchValue = selectedModel.value
      ? `${selectedProvider.value}:${selectedModel.value}`
      : selectedProvider.value
    const res = await switchLlmProvider(switchValue)
    currentProvider.value = res.data?.currentProvider || selectedProvider.value
    currentModel.value = res.data?.currentModel || selectedModel.value
    showModelDialog.value = false
    ElMessage.success(`已切换到 ${currentProvider.value}:${currentModel.value}`)
  } catch (e) {
    // axios interceptor already shows error message
  } finally {
    modelLoading.value = false
  }
}

onBeforeUnmount(() => {
  // 停止打字动画，提交完整内容
  if (_streamingTimer) {
    clearTimeout(_streamingTimer)
    _streamingTimer = null
  }
  if (isStreaming.value) {
    messages.value.forEach(m => {
      if (m._streaming && m._fullContent) {
        m.content = m._fullContent
        m._streaming = false
        delete m._fullContent
      }
    })
    isStreaming.value = false
    lastAIContent.value = messages.value[messages.value.length - 1]?.content || ''
  }
  if (_unsubscribe) {
    _unsubscribe()
    _unsubscribe = null
  }
  persistActiveMessages()
})

const sendCommand = (text) => {
  inputText.value = text
  sendMessage()
}

const submitPrompt = async (userMsg) => {
  if (!userMsg || loading.value || isStreaming.value) return
  lastUserPrompt.value = userMsg

  const isFirstUserMessage = !messages.value.some(m => m.role === 'user')
  messages.value.push({ role: 'user', content: userMsg })
  if (isFirstUserMessage && activeConversationId.value && (activeConversation.value?.title || '新对话') === '新对话') {
    updateConversationMeta(activeConversationId.value, { title: generateConversationTitle(userMsg) })
  }

  // Persist user message to server
  if (activeConversationId.value) {
    addConversationMessage(activeConversationId.value, { role: 'user', content: userMsg }).catch(() => {})
  }

  loading.value = true
  await scrollToBottom()

  const linkedDocId = Number.isFinite(currentDocId.value) ? currentDocId.value : null
  const task = startTask((abortController) => requestAIChatWithFallback(userMsg, linkedDocId, abortController.signal))
  attachToTask(task)
}

const sendMessage = async () => {
  if (!inputText.value.trim()) return
  const userMsg = inputText.value.trim()
  inputText.value = ''
  await submitPrompt(userMsg)
}

// 停止AI生成
const stopGeneration = () => {
  // Phase A: abort the backend fetch
  abortTask()

  // Phase B: stop typewriter animation, show content immediately
  if (_streamingTimer) {
    clearTimeout(_streamingTimer)
    _streamingTimer = null
  }
  if (isStreaming.value) {
    const streamingMsg = messages.value.find(m => m._streaming)
    if (streamingMsg) {
      const full = streamingMsg._fullContent || streamingMsg.content
      streamingMsg.content = full
      streamingMsg._streaming = false
      delete streamingMsg._fullContent
      lastAIContent.value = full
      // Persist whatever we have
      if (activeConversationId.value && full) {
        addConversationMessage(activeConversationId.value, { role: 'ai', content: full }).catch(() => {})
      }
    }
    isStreaming.value = false
  }
  loading.value = false
  clearTask()
  if (_unsubscribe) { _unsubscribe(); _unsubscribe = null }

  // Remove the loading indicator message if present by just scrolling
  scrollToBottom()
}

// 订阅后台AI任务结果
const attachToTask = (task) => {
  if (_unsubscribe) { _unsubscribe(); _unsubscribe = null }
  _unsubscribe = subscribeTask(task, (type, data) => {
    if (type === 'complete') {
      loading.value = false
      const payload = typeof data === 'string' ? { reply: data } : (data || {})
      beginTypewriter(payload.reply || '', payload)
    } else if (type === 'aborted') {
      // User stopped generation during fetch phase
      loading.value = false
      clearTask()
      messages.value.push({ role: 'ai', content: '已停止生成。', _isWelcome: true })
      scrollToBottom()
    } else {
      loading.value = false
      const errMsg = data?.message || ''
      let displayMsg
      if (errMsg.includes('超时')) {
        displayMsg = errMsg
      } else if (errMsg.includes('Failed to fetch') || errMsg.includes('Network Error') || errMsg.includes('Load failed')) {
        displayMsg = '网络连接失败，请检查后端服务是否启动或网络是否正常。'
      } else if (errMsg.includes('429') || errMsg.includes('繁忙')) {
        displayMsg = '服务繁忙，请稍后重试。'
      } else {
        displayMsg = '抱歉，服务暂时繁忙，请稍后再试。如果问题持续，请检查网络连接或后端服务是否启动。'
      }
      messages.value.push({ role: 'ai', content: displayMsg })
      clearTask()
      scrollToBottom()
    }
  })
}

// 逐字打字效果
const beginTypewriter = (fullText, payload = {}) => {
  const msg = {
    role: 'ai',
    content: '',
    _streaming: true,
    _fullContent: fullText,
    _meta: {
      modifiedExcelUrl: payload.modifiedExcelUrl || ''
    }
  }
  messages.value.push(msg)
  isStreaming.value = true
  loading.value = false

  const target = messages.value[messages.value.length - 1]
  let idx = 0
  const len = fullText.length
  const step = Math.max(1, Math.ceil(len / 200))

  const tick = () => {
    if (idx >= len) {
      target.content = fullText
      target._streaming = false
      delete target._fullContent
      isStreaming.value = false
      lastAIContent.value = fullText
      _streamingTimer = null
      clearTask()
      // Persist AI message to server
      if (activeConversationId.value) {
        addConversationMessage(activeConversationId.value, { role: 'ai', content: fullText }).catch(() => {})
      }
      scrollToBottom()
      return
    }
    idx = Math.min(idx + step, len)
    target.content = fullText.substring(0, idx)
    scrollToBottom()
    _streamingTimer = setTimeout(tick, 16)
  }

  tick()
}

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const isTransientChatError = (err) => {
  const status = err?.response?.status
  if (status === 429 || status === 503) return true
  const msg = err?.message || ''
  if (msg.includes('Network Error') || msg.includes('Failed to fetch') || msg.includes('Load failed')) return true
  return err?.code === 'ECONNABORTED' || err?.name === 'TypeError'
}

const sendChatOnce = async (message, documentId, signal) => {
  return aiChat({ message, documentId, signal })
}

const requestAIChatWithFallback = async (message, documentId, signal) => {
  try {
    return await sendChatOnce(message, documentId, signal)
  } catch (err) {
    if (!isTransientChatError(err)) {
      throw err
    }
  }

  // 临时连接问题进行一次重试
  await sleep(700)
  return await sendChatOnce(message, documentId, signal)
}

const regenerateFromLastPrompt = async () => {
  const last = [...messages.value].reverse().find(m => m.role === 'user')
  if (!last?.content) {
    ElMessage.warning('暂无可重新生成的提问')
    return
  }
  await submitPrompt(last.content)
}

const continueDialogue = async () => {
  await submitPrompt('请继续上一次回答，补充未完成的内容并给出可执行建议。')
}

const previewContent = (content) => {
  previewText.value = content || ''
  previewVisible.value = true
}

const downloadModifiedExcel = (url) => {
  if (!url) {
    ElMessage.warning('暂无可下载的在线文件')
    return
  }
  window.open(url, '_blank')
}

const sendResultToEmail = async (url) => {
  if (!url) {
    ElMessage.warning('暂无可发送的文档结果')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入接收邮箱', '发送至邮箱', {
      inputPlaceholder: 'name@example.com',
      inputPattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      inputErrorMessage: '请输入有效邮箱地址',
      confirmButtonText: '发送',
      cancelButtonText: '取消'
    })
    await sendAiResultEmail({ email: value.trim(), excelUrl: url })
    ElMessage.success('邮件发送成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('邮件发送失败')
    }
  }
}

const exportAIResult = async () => {
  if (!lastAIContent.value) {
    ElMessage.warning('暂无AI生成内容可导出')
    return
  }
  const ext = currentDoc.value?.fileExtension?.toLowerCase() || 'txt'
  const baseName = currentDoc.value ? currentDoc.value.fileName.replace(/\.\w+$/, '') + '_AI结果' : 'AI生成内容'
  const exportExt = (ext === 'md') ? '.md' : '.txt'
  try {
    downloadBlob(new Blob([lastAIContent.value], { type: 'text/plain;charset=utf-8' }), `${baseName}${exportExt}`)
    ElMessage.success('已导出文档')
  } catch (e) {
    ElMessage.error('导出失败: ' + (e.message || '未知错误'))
  }
}

const exportContentToWord = async (content) => {
  const ext = currentDoc.value?.fileExtension?.toLowerCase() || 'txt'
  const baseName = currentDoc.value ? currentDoc.value.fileName.replace(/\.\w+$/, '') + '_AI导出' : 'AI_内容导出'
  const exportExt = (ext === 'md') ? '.md' : '.txt'
  try {
    downloadBlob(new Blob([content], { type: 'text/plain;charset=utf-8' }), `${baseName}${exportExt}`)
    ElMessage.success('已导出文档')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const sendContentToEmail = async (content) => {
  if (!content) {
    ElMessage.warning('暂无可发送的内容')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入接收邮箱', '发送至邮箱', {
      inputPlaceholder: 'name@example.com',
      inputPattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      inputErrorMessage: '请输入有效邮箱地址',
      confirmButtonText: '发送',
      cancelButtonText: '取消'
    })
    const subject = currentDoc.value
      ? `DocAI - ${currentDoc.value.fileName} AI处理结果`
      : 'DocAI - AI生成内容'
    await sendContentEmail({ email: value.trim(), content, subject })
    ElMessage.success('邮件发送成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('邮件发送失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }
}

const saveToDocument = async (content) => {
  ElMessage.info('当前后端未提供文档回写接口，请使用导出功能保存结果')
}

const clearChat = async () => {
  // 中止进行中的AI任务和打字动画
  if (_streamingTimer) { clearTimeout(_streamingTimer); _streamingTimer = null }
  isStreaming.value = false
  loading.value = false
  clearTask()
  if (_unsubscribe) { _unsubscribe(); _unsubscribe = null }

  messages.value = []
  lastAIContent.value = ''

  // Delete old conversation and create a fresh one
  if (activeConversationId.value) {
    const oldLinkedDocId = currentDocId.value
    const oldLinkedDocName = currentDoc.value?.fileName || ''
    try {
      await deleteConversationApi(activeConversationId.value)
      conversations.value = conversations.value.filter(c => c.id !== activeConversationId.value)
    } catch (e) { /* ignore */ }
    await createNewConversation(oldLinkedDocId, oldLinkedDocName)
  } else {
    messages.value.push({
      role: 'ai',
      content: '对话已重置。请问有什么可以帮您？',
      _isWelcome: true
    })
  }
}

const copyText = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  })
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}

const renderMessageContent = (msg) => {
  const html = renderMarkdown(msg.content || '')
  if (msg._streaming) {
    const cursor = '<span class="stream-cursor">▍</span>'
    const lastClose = html.lastIndexOf('</')
    return lastClose > 0 ? html.substring(0, lastClose) + cursor + html.substring(lastClose) : html + cursor
  }
  return html
}

const scrollToBottom = async () => {
  await nextTick()
  await nextTick()
  if (chatArea.value) {
    chatArea.value.scrollTop = chatArea.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-page { display: flex; height: 100%; gap: 0; background: var(--bg-base); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-md); }
.chat-sidebar { width: 280px; background: var(--bg-card); border-right: 1px solid var(--border-light); display: flex; flex-direction: column; position: relative; transition: width 0.3s ease; }
.chat-sidebar.collapsed { width: 0; border-right: none; }
.sidebar-toggle { position: absolute; right: -14px; top: 50%; transform: translateY(-50%); width: 28px; height: 28px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 10; transition: all 0.2s; }
.sidebar-toggle:hover { background: var(--primary); color: white; border-color: var(--primary); }
.sidebar-body { padding: 20px; overflow-y: auto; flex: 1; display: flex; flex-direction: column; }
.sidebar-section { margin-bottom: 24px; flex-shrink: 0; }
.sidebar-section h4 { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin: 0 0 12px 0; display: flex; align-items: center; gap: 6px; }
.linked-doc { display: flex; gap: 10px; align-items: center; padding: 12px; background: rgba(79, 70, 229, 0.05); border: 1px solid rgba(79, 70, 229, 0.15); border-radius: var(--radius-md); flex-wrap: wrap; }
.linked-doc-icon { font-size: 24px; color: var(--primary); }
.linked-doc-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }
.linked-doc-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linked-doc-actions { display: flex; gap: 4px; }
.no-doc-tip { text-align: center; padding: 16px 0; }
.select-doc-btn { background: rgba(79, 70, 229, 0.1) !important; border-color: var(--primary) !important; color: var(--primary) !important; font-weight: 500; }
.select-doc-btn:hover { background: rgba(79, 70, 229, 0.2) !important; }
.doc-commands { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
.cmd-btn { display: flex; align-items: center; gap: 6px; padding: 10px 10px; font-size: 12px; color: var(--text-secondary); background: var(--bg-base); border: 1px solid var(--border-light); border-radius: var(--radius-md); cursor: pointer; transition: all 0.2s; }
.cmd-btn:hover { background: rgba(79, 70, 229, 0.06); border-color: var(--primary); color: var(--primary); }
.quick-questions { display: flex; flex-direction: column; gap: 8px; }
.quick-q { padding: 10px 12px; font-size: 12px; color: var(--text-secondary); background: var(--bg-base); border: 1px solid var(--border-light); border-radius: var(--radius-md); cursor: pointer; transition: all 0.2s; line-height: 1.4; }
.quick-q:hover { background: rgba(79, 70, 229, 0.06); border-color: var(--primary); color: var(--primary); }
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-header { padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-light); background: var(--bg-card); }
.chat-title-row { display: flex; align-items: center; gap: 12px; }
.chat-logo { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: rgba(79, 70, 229, 0.08); border-radius: var(--radius-md); }
.chat-title-text { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.chat-model-tag { display: inline-block; font-size: 11px; padding: 2px 8px; background: linear-gradient(135deg, var(--primary), var(--primary-hover)); color: white; border-radius: 10px; margin-left: 8px; font-weight: 500; }
.messages-area { flex: 1; overflow-y: auto; padding: 24px; scroll-behavior: smooth; }
.welcome-card { text-align: center; padding: 40px 20px; margin-bottom: 20px; }
.welcome-icon { margin-bottom: 12px; display: flex; justify-content: center; }
.welcome-card h3 { font-size: 20px; font-weight: 700; color: var(--text-primary); margin: 0 0 8px 0; }
.welcome-card > p { color: var(--text-muted); font-size: 14px; margin: 0 0 24px 0; }
.welcome-features { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; }
.wf-item { display: flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-md); font-size: 13px; color: var(--text-secondary); }
.message-wrapper { display: flex; gap: 12px; margin-bottom: 20px; align-items: flex-start; }
.message-wrapper.user { flex-direction: row; justify-content: flex-end; }
.ai-avatar { width: 36px; height: 36px; border-radius: 50%; background: rgba(79, 70, 229, 0.08); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.user-avatar { width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), var(--primary-hover)); color: white; font-size: 14px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; order: 2; }
.message-wrapper.user .message-bubble { order: 1; }
.message-bubble { max-width: 72%; padding: 14px 18px; border-radius: 16px; font-size: 14px; line-height: 1.7; position: relative; word-break: break-word; min-width: 40px; }
.ai-bubble { background: var(--bg-card); color: var(--text-primary); border: 1px solid var(--border-light); border-radius: 4px 16px 16px 16px; }
.user-bubble { background: linear-gradient(135deg, var(--primary), var(--primary-hover)); color: white; border-radius: 16px 4px 16px 16px; }
.bubble-text :deep(code) { background: rgba(0, 0, 0, 0.06); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.bubble-text :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 14px 16px; border-radius: 8px; overflow-x: auto; margin: 8px 0; font-size: 13px; line-height: 1.5; }
.bubble-text :deep(pre code) { background: none; padding: 0; color: inherit; font-size: 13px; }
.bubble-text :deep(strong) { font-weight: 700; }
.bubble-text :deep(li) { margin-left: 16px; list-style: disc; }
.bubble-text :deep(ol li) { list-style: decimal; }
.bubble-text :deep(h1), .bubble-text :deep(h2), .bubble-text :deep(h3), .bubble-text :deep(h4) { margin: 12px 0 6px 0; font-weight: 700; }
.bubble-text :deep(h1) { font-size: 20px; }
.bubble-text :deep(h2) { font-size: 17px; }
.bubble-text :deep(h3) { font-size: 15px; }
.bubble-text :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 13px; }
.bubble-text :deep(th), .bubble-text :deep(td) { border: 1px solid var(--border-light); padding: 6px 10px; text-align: left; }
.bubble-text :deep(th) { background: var(--bg-base); font-weight: 600; }
.bubble-text :deep(blockquote) { border-left: 3px solid var(--primary); padding: 4px 12px; margin: 8px 0; color: var(--text-secondary); background: rgba(79, 70, 229, 0.03); border-radius: 0 4px 4px 0; }
.bubble-text :deep(p) { margin: 4px 0; }
.bubble-text :deep(a) { color: var(--primary); text-decoration: underline; }
.bubble-actions { display: flex; gap: 4px; margin-top: 6px; padding: 4px 0; opacity: 0; transition: opacity 0.25s ease; }
.message-bubble:hover .bubble-actions { opacity: 1; }
.action-btn { background: transparent; border: 1px solid transparent; border-radius: var(--radius-sm); padding: 4px 8px; cursor: pointer; color: var(--text-muted); transition: all 0.2s; font-size: 12px; }
.action-btn:hover { background: rgba(79, 70, 229, 0.08); color: var(--primary); border-color: rgba(79, 70, 229, 0.2); }
.action-btn.save-btn { color: var(--primary); border-color: rgba(79, 70, 229, 0.3); }
.action-btn.save-btn:hover { background: rgba(79, 70, 229, 0.1); color: var(--primary); border-color: var(--primary); }
.loading-bubble { min-width: 60px; }
.streaming-bubble { border-color: rgba(79, 70, 229, 0.25); }
.bubble-text :deep(.stream-cursor) { color: var(--primary); font-weight: normal; animation: cursor-blink 0.8s step-end infinite; }
@keyframes cursor-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.typing-dots { display: flex; gap: 4px; align-items: center; height: 20px; }
.typing-dots span { width: 7px; height: 7px; background: var(--primary); border-radius: 50%; opacity: 0.4; animation: typingDot 1.4s infinite ease-in-out; }
.typing-dots span:nth-child(1) { animation-delay: 0s; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typingDot { 0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); } 30% { opacity: 1; transform: scale(1.1); } }
.input-area { padding: 16px 24px; background: var(--bg-card); border-top: 1px solid var(--border-light); }
.input-box { display: flex; align-items: flex-end; background: var(--bg-base); border: 2px solid var(--border-light); border-radius: var(--radius-lg); padding: 8px; transition: all 0.3s; }
.input-box.focused { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1); }
.input-box :deep(.el-textarea__inner) { border: none; box-shadow: none; background: transparent; padding: 8px 12px; resize: none; }
.input-actions { display: flex; align-items: center; gap: 8px; padding: 0 8px 4px 0; }
.char-count { font-size: 11px; color: var(--text-muted); }
.send-btn { width: 36px; height: 36px; }
.stop-btn { width: 36px; height: 36px; animation: pulse-stop 1.5s ease-in-out infinite; }
@keyframes pulse-stop { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
.input-footer { text-align: center; padding: 8px 0 0 0; font-size: 11px; color: var(--text-muted); }
.preview-body { max-height: 80vh; overflow-y: auto; line-height: 1.7; color: var(--text-primary); }
.preview-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 12px; border-radius: 8px; overflow-x: auto; }
.doc-picker-search { margin-bottom: 16px; }
.doc-picker-list { max-height: 400px; overflow-y: auto; }
.doc-picker-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: var(--radius-md); cursor: pointer; transition: all 0.2s; border: 1px solid transparent; }
.doc-picker-item:hover { background: rgba(79, 70, 229, 0.05); border-color: rgba(79, 70, 229, 0.15); }
.dpi-icon { color: var(--primary); }
.dpi-info { flex: 1; min-width: 0; }
.dpi-name { display: block; font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dpi-meta { font-size: 12px; color: var(--text-muted); }
.tip-text { font-size: 12px; color: var(--text-muted); margin-top: 8px; }
.convo-section { border-top: 1px solid var(--border-light); padding-top: 14px; flex: 1; display: flex; flex-direction: column; min-height: 0; margin-bottom: 0; }
.convo-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.convo-search { margin-bottom: 10px; }
.conversation-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; min-height: 0; }
.conversation-item { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 6px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--bg-base); cursor: pointer; transition: all 0.2s; }
.conversation-item:hover { border-color: var(--primary); background: rgba(79, 70, 229, 0.06); }
.conversation-item.active { border-color: var(--primary); background: rgba(79, 70, 229, 0.1); }
.conversation-main { min-width: 0; flex: 1; }
.conversation-title { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-primary); font-weight: 600; }
.conversation-title span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pin-mark { color: #f59e0b; }
.conversation-meta { margin-top: 2px; display: flex; gap: 6px; font-size: 10px; color: var(--text-muted); }
.meta-time { flex-shrink: 0; }
.meta-doc { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-actions { display: flex; align-items: center; gap: 2px; opacity: 0; pointer-events: none; transition: opacity 0.2s; }
.conversation-item:hover .conversation-actions,
.conversation-item.active .conversation-actions { opacity: 1; pointer-events: auto; }
.convo-action-btn { width: 24px; height: 24px; border: 1px solid var(--border-light); border-radius: 6px; background: var(--bg-card); color: var(--text-muted); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; }
.convo-action-btn:hover { color: var(--primary); border-color: var(--primary); }
.convo-action-btn.pinned { color: #f59e0b; border-color: rgba(245, 158, 11, 0.6); background: rgba(245, 158, 11, 0.08); }
.convo-action-btn.pinned:hover { color: #d97706; border-color: #d97706; }
.convo-action-btn.delete:hover { color: #ef4444; border-color: #ef4444; }
.model-info { margin-top: 8px; }

@media (max-width: 1024px) {
  .conversation-list { min-height: 100px; }
}
</style>
