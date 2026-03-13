<template>
  <div class="generate-page">
    <!-- 左侧配置面板 -->
    <div class="config-panel card">
      <div class="panel-head">
        <div class="panel-icon"><el-icon :size="28" color="var(--primary)"><EditPen /></el-icon></div>
        <div>
          <h3>智能写作</h3>
          <p class="panel-desc">AI 驱动的公文生成与润色</p>
        </div>
      </div>

      <el-form label-position="top" class="gen-form">
        <!-- 公文类型 -->
        <el-form-item label="公文类型">
          <div class="type-grid">
            <div
              v-for="t in docTypes"
              :key="t.value"
              class="type-card"
              :class="{ active: docType === t.value }"
              @click="docType = t.value"
            >
              <span class="type-icon"><el-icon :size="20"><component :is="t.icon" /></el-icon></span>
              <span class="type-label">{{ t.value }}</span>
            </div>
          </div>
        </el-form-item>

        <!-- 标题 -->
        <el-form-item label="标题">
          <el-input
            v-model="title"
            placeholder="例：关于开展XX工作的通知"
            clearable
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <!-- 需求描述 -->
        <el-form-item label="需求描述">
          <el-input
            v-model="requirement"
            type="textarea"
            :rows="6"
            placeholder="详细描述公文的主题、背景、目的和核心内容要点..."
            resize="none"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <!-- 生成按钮 -->
        <el-button
          type="primary"
          size="large"
          class="gen-btn"
          @click="generate"
          :loading="generating"
        >
          <el-icon v-if="!generating"><MagicStick /></el-icon>
          {{ generating ? '正在生成...' : '立即生成' }}
        </el-button>
      </el-form>
    </div>

    <!-- 右侧预览区 -->
    <div class="preview-panel">
      <!-- 空状态 -->
      <div class="empty-preview" v-if="!generatedContent && !generating">
        <div class="empty-anim">
          <div class="pen-icon"><el-icon :size="48" color="var(--text-muted)"><EditPen /></el-icon></div>
          <div class="paper-lines">
            <div class="pline"></div>
            <div class="pline"></div>
            <div class="pline"></div>
          </div>
        </div>
        <h3>AI 智能写作</h3>
        <p>在左侧填写需求，一键生成规范公文</p>
      </div>

      <!-- 加载状态 -->
      <div class="loading-preview" v-if="generating">
        <div class="loading-anim">
          <div class="loader-bar"></div>
          <div class="loader-bar delay1"></div>
          <div class="loader-bar delay2"></div>
        </div>
        <p class="loading-tip">AI 正在构思内容...</p>
      </div>

      <!-- 生成结果 -->
      <div class="result-area" v-if="generatedContent && !generating">
        <!-- 操作栏 -->
        <div class="result-toolbar">
          <div class="result-info">
            <el-tag type="success" effect="plain" round size="small">已生成</el-tag>
            <span class="word-count">约 {{ generatedContent.length }} 字</span>
          </div>
          <div class="result-actions">
            <el-tooltip content="复制内容">
              <el-button size="small" plain @click="copyContent">
                <el-icon><CopyDocument /></el-icon> 复制
              </el-button>
            </el-tooltip>
            <el-tooltip content="AI润色优化">
              <el-button size="small" type="warning" plain @click="polishContent" :loading="polishing">
                <el-icon><Brush /></el-icon> 润色
              </el-button>
            </el-tooltip>
            <el-tooltip content="下载Word文档">
              <el-button size="small" type="success" plain @click="downloadWord">
                <el-icon><Download /></el-icon> 下载
              </el-button>
            </el-tooltip>
          </div>
        </div>

        <!-- A4 纸效果 -->
        <div class="paper-container">
          <div class="a4-paper">
            <div class="paper-header">
              <div class="red-line"></div>
            </div>
            <h1 class="paper-title">{{ title || '公文标题' }}</h1>
            <div class="paper-body">
              <el-input
                v-model="generatedContent"
                type="textarea"
                autosize
                class="paper-textarea"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { aiGenerate, aiPolish } from '../api'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import {
  MagicStick, CopyDocument, Brush, Download, EditPen,
  Bell, DataLine, Message, Promotion, Notebook, ChatLineSquare, CircleCheck, Document
} from '@element-plus/icons-vue'

const docType = ref('通知')
const title = ref('')
const requirement = ref('')
const generatedContent = ref('')
const generating = ref(false)
const polishing = ref(false)

const docTypes = [
  { value: '通知', icon: 'Bell' },
  { value: '报告', icon: 'DataLine' },
  { value: '请示', icon: 'Message' },
  { value: '函',   icon: 'Promotion' },
  { value: '纪要', icon: 'Notebook' },
  { value: '通报', icon: 'ChatLineSquare' },
  { value: '批复', icon: 'CircleCheck' },
  { value: '其他', icon: 'Document' }
]

const generate = async () => {
  if (!requirement.value.trim()) {
    return ElMessage.warning('请填写需求描述')
  }
  ElMessage.info('当前后端未开放 AI 写作生成接口，请使用 AI 对话模块进行内容处理')
  return
  generating.value = true
  generatedContent.value = ''
  try {
    const res = await aiGenerate({
      docType: docType.value,
      requirement: requirement.value
    })
    generatedContent.value = res.data?.content || res.data
    ElMessage.success('生成成功')
  } catch (e) {
    ElMessage.error('生成失败，请重试')
  } finally {
    generating.value = false
  }
}

const polishContent = async () => {
  if (!generatedContent.value.trim()) return
  ElMessage.info('当前后端未开放 AI 润色接口，请在 AI 对话中使用“润色优化”指令')
  return
  polishing.value = true
  try {
    const res = await aiPolish({ text: generatedContent.value })
    generatedContent.value = res.data?.polished || res.data
    ElMessage.success('润色完成')
  } catch (e) {
    ElMessage.error('润色失败')
  } finally {
    polishing.value = false
  }
}

const downloadWord = async () => {
  ElMessage.info('当前后端未开放 Word 导出接口，请先复制内容后手动保存')
  return
  try {
    const response = await axios.post(
      '/api/ai/generate-word',
      { title: title.value || '公文', content: generatedContent.value },
      { responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = (title.value || '公文') + '.docx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

const copyContent = () => {
  navigator.clipboard.writeText(generatedContent.value)
  ElMessage.success('已复制到剪贴板')
}
</script>

<style scoped>
.generate-page {
  display: flex;
  gap: 24px;
  height: 100%;
}

/* 配置面板 */
.config-panel {
  width: 380px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}

.panel-icon {
  font-size: 32px;
}

.panel-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.panel-desc {
  margin: 2px 0 0 0;
  font-size: 13px;
  color: var(--text-muted);
}

.gen-form {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 公文类型选择网格 */
.type-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  border: 2px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
  background: var(--bg-base);
}

.type-card:hover {
  border-color: var(--primary);
  background: rgba(79, 70, 229, 0.04);
}

.type-card.active {
  border-color: var(--primary);
  background: rgba(79, 70, 229, 0.08);
}

.type-icon { font-size: 20px; }

.type-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.type-card.active .type-label {
  color: var(--primary);
}

.gen-btn {
  margin-top: auto;
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 700;
  border-radius: var(--radius-md);
  box-shadow: 0 4px 16px rgba(79, 70, 229, 0.35);
}

/* 预览面板 */
.preview-panel {
  flex: 1;
  background: var(--bg-base);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* 空状态 */
.empty-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.empty-anim {
  position: relative;
  margin-bottom: 20px;
}

.pen-icon {
  font-size: 48px;
  animation: penBob 2s ease-in-out infinite;
}

@keyframes penBob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.paper-lines {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}

.pline {
  height: 3px;
  background: var(--border-light);
  border-radius: 2px;
}

.pline:nth-child(1) { width: 80px; }
.pline:nth-child(2) { width: 60px; }
.pline:nth-child(3) { width: 40px; }

.empty-preview h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
  color: var(--text-secondary);
}

.empty-preview p {
  margin: 0;
  font-size: 14px;
}

/* 加载状态 */
.loading-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
}

.loading-anim {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.loader-bar {
  height: 6px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--primary), rgba(79, 70, 229, 0.2));
  animation: loadBar 1.5s ease-in-out infinite;
}

.loader-bar:nth-child(1) { width: 120px; }
.loader-bar:nth-child(2) { width: 90px; animation-delay: 0.2s; }
.loader-bar:nth-child(3) { width: 60px; animation-delay: 0.4s; }

@keyframes loadBar {
  0% { opacity: 0.3; transform: scaleX(0.5); transform-origin: left; }
  50% { opacity: 1; transform: scaleX(1); }
  100% { opacity: 0.3; transform: scaleX(0.5); }
}

.loading-tip {
  color: var(--text-muted);
  font-size: 14px;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* 结果区域 */
.result-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.result-toolbar {
  padding: 14px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-card);
  position: sticky;
  top: 0;
  z-index: 5;
}

.result-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.word-count {
  font-size: 12px;
  color: var(--text-muted);
}

.result-actions {
  display: flex;
  gap: 8px;
}

/* A4 纸 */
.paper-container {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 32px;
}

.a4-paper {
  width: 100%;
  max-width: 794px;
  min-height: 600px;
  background: white;
  padding: 60px 80px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.06);
  border-radius: 2px;
}

.paper-header {
  margin-bottom: 32px;
}

.red-line {
  height: 3px;
  background: linear-gradient(90deg, #e74c3c, #c0392b);
  border-radius: 2px;
}

.paper-title {
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 32px 0;
  color: #1a1a1a;
  letter-spacing: 2px;
}

.paper-body {
  font-size: 15px;
  line-height: 1.8;
  color: #333;
}

.paper-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: transparent;
  padding: 0;
  font-size: 15px;
  line-height: 1.8;
  color: #333;
  resize: none;
}
</style>
