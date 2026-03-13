<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-content">
        <div class="welcome-tag">Intelligent Document Processing</div>
        <h1 class="welcome-title">DocAI 智能文档处理系统</h1>
        <p class="welcome-desc">整合 AI 大语言模型，实现文档智能编辑、信息自动提取、表格一键填写</p>
        <div class="welcome-actions">
          <el-button type="primary" size="large" @click="$router.push('/autofill')">
            <el-icon><Grid /></el-icon> 开始智能填表
          </el-button>
          <el-button size="large" plain @click="$router.push('/documents')">
            <el-icon><UploadFilled /></el-icon> 上传文档
          </el-button>
          <el-button size="large" plain @click="$router.push('/ai-chat')">
            <el-icon><ChatDotRound /></el-icon> AI 对话
          </el-button>
        </div>
      </div>
      <div class="welcome-illustration">
        <div class="illustration-orbit">
          <div class="orbit-ring ring-1"></div>
          <div class="orbit-ring ring-2"></div>
          <div class="orbit-dot dot-1"></div>
          <div class="orbit-dot dot-2"></div>
          <div class="orbit-dot dot-3"></div>
          <div class="orbit-center">AI</div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card" v-for="stat in statCards" :key="stat.label">
        <div class="stat-icon" :style="{ background: stat.bg }">
          <el-icon :size="22" :color="stat.color"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stat.value }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
      </div>
    </div>

    <!-- 快速入门指南 -->
    <div class="section-title">
      <h3>快速入门 Quick Start</h3>
      <p>按照以下步骤即可快速开始使用系统完成文档处理工作</p>
    </div>
    <div class="guide-steps">
      <div class="guide-step" v-for="(step, idx) in guideSteps" :key="idx" @click="$router.push(step.route)">
        <div class="guide-step-num">{{ idx + 1 }}</div>
        <div class="guide-step-connector" v-if="idx < guideSteps.length - 1"></div>
        <div class="guide-step-body">
          <div class="guide-step-icon" :style="{ background: step.iconBg }">
            <el-icon :size="20" :color="step.iconColor"><component :is="step.icon" /></el-icon>
          </div>
          <h5 class="guide-step-title">{{ step.title }}</h5>
          <p class="guide-step-desc">{{ step.desc }}</p>
          <div class="guide-step-tip">{{ step.tip }}</div>
        </div>
      </div>
    </div>

    <!-- 三大核心模块 -->
    <div class="section-title">
      <h3>核心功能模块 Core Modules</h3>
      <p>覆盖从文档理解到数据提取、自动填表的完整工作流</p>
    </div>
    <div class="modules-grid">
      <div class="module-card" v-for="mod in modules" :key="mod.id" @click="$router.push(mod.route)">
        <div class="module-header">
          <div class="module-number">{{ mod.id }}</div>
          <el-tag :type="mod.tagType" effect="plain" size="small" round>{{ mod.status }}</el-tag>
        </div>
        <h4 class="module-name">{{ mod.name }}</h4>
        <p class="module-desc">{{ mod.desc }}</p>
        <div class="module-features">
          <span v-for="f in mod.features" :key="f" class="feature-tag">{{ f }}</span>
        </div>
        <div class="module-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 支持格式与操作提示 -->
    <div class="info-row">
      <div class="info-card formats-card">
        <h4><el-icon :size="16"><Document /></el-icon> 支持的文档格式</h4>
        <div class="format-list">
          <div class="format-item" v-for="fmt in formats" :key="fmt.ext">
            <span class="format-ext" :style="{ color: fmt.color, background: fmt.bg }">{{ fmt.ext }}</span>
            <span class="format-name">{{ fmt.name }}</span>
          </div>
        </div>
      </div>
      <div class="info-card tips-card">
        <h4><el-icon :size="16"><InfoFilled /></el-icon> 使用提示</h4>
        <ul class="tips-list">
          <li>上传文档后系统自动进行结构化信息提取，提取结果用于后续自动填表</li>
          <li>模板文件需包含 <code>&#123;&#123;字段名&#125;&#125;</code> 占位符或留空的表格单元格</li>
          <li>AI 对话中可关联已上传文档进行深度分析、编辑、润色</li>
          <li>智能写作支持通知、报告、请示等 8 种公文类型</li>
          <li>批量上传模板可一次性填充并下载 ZIP 压缩包</li>
        </ul>
      </div>
    </div>

    <!-- 工作流程图解 -->
    <div class="section-title">
      <h3>核心工作流 Workflow</h3>
      <p>从文档上传到表格自动填写的完整流程</p>
    </div>
    <div class="workflow-bar">
      <div class="wf-node" v-for="(wf, i) in workflow" :key="i">
        <div class="wf-icon" :style="{ background: wf.bg }">
          <el-icon :size="18" :color="wf.color"><component :is="wf.icon" /></el-icon>
        </div>
        <span class="wf-label">{{ wf.label }}</span>
      </div>
      <div class="wf-arrow" v-if="i < workflow.length - 1" v-for="i in workflow.length - 1" :key="'a'+i">
        <el-icon :size="14"><Right /></el-icon>
      </div>
    </div>

    <!-- 模型信息 -->
    <div class="model-bar">
      <div class="model-info">
        <span class="model-dot"></span>
        <span>AI Engine: GLM-4.7-Flash</span>
        <el-tag size="small" effect="plain" round>200K Context</el-tag>
        <el-tag size="small" type="success" effect="plain" round>Free Tier</el-tag>
      </div>
      <span class="model-powered">Powered by ZHIPU AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocumentStats } from '../api'
import {
  UploadFilled, Grid, ChatDotRound, EditPen, ArrowRight, FolderOpened,
  Document, DataAnalysis, Right, InfoFilled, Search, Download, Setting
} from '@element-plus/icons-vue'

const stats = ref({ total: 0, docx: 0, xlsx: 0, txt: 0, md: 0 })

const statCards = ref([
  { label: '总文档数', value: '0', icon: 'FolderOpened', color: '#4F46E5', bg: '#EEF2FF' },
  { label: 'Word 文档', value: '0', icon: 'Document', color: '#3B82F6', bg: '#EFF6FF' },
  { label: 'Excel 表格', value: '0', icon: 'Grid', color: '#10B981', bg: '#ECFDF5' },
  { label: '文本文件', value: '0', icon: 'EditPen', color: '#F59E0B', bg: '#FFFBEB' }
])

const guideSteps = [
  {
    title: '上传文档',
    desc: '将 Word/Excel/TXT/Markdown 等格式文档上传到系统',
    tip: '支持拖放上传和批量上传',
    route: '/documents',
    icon: 'UploadFilled',
    iconBg: '#EEF2FF',
    iconColor: '#4F46E5'
  },
  {
    title: '自动信息提取',
    desc: '系统自动解析文档并通过 AI 提取关键数据和结构化信息',
    tip: '上传后自动执行，无需额外操作',
    route: '/documents',
    icon: 'Search',
    iconBg: '#ECFDF5',
    iconColor: '#10B981'
  },
  {
    title: '上传模板',
    desc: '在智能填表页面上传包含占位符或空表格的模板文件',
    tip: '支持 Word 和 Excel 模板格式',
    route: '/autofill',
    icon: 'Grid',
    iconBg: '#FEF3C7',
    iconColor: '#D97706'
  },
  {
    title: '一键填充并下载',
    desc: 'AI 自动匹配数据填充模板，完成后直接下载结果文件',
    tip: '批量模板会打包为 ZIP 下载',
    route: '/autofill',
    icon: 'Download',
    iconBg: '#FEE2E2',
    iconColor: '#DC2626'
  }
]

const modules = [
  {
    id: 1,
    name: '文档智能编辑操作交互',
    desc: '通过自然语言指令与 AI 进行对话，完成文档的分析、编辑、排版、格式调整和内容提取等操作',
    route: '/ai-chat',
    tagType: 'primary',
    status: '已就绪',
    features: ['AI对话', '智能写作', '文本润色', '格式调整', 'Word导出']
  },
  {
    id: 2,
    name: '非结构化文档信息提取',
    desc: '支持 Word/Excel/TXT/Markdown 多格式文档解析，AI 自动提取关键实体、数值、日期等结构化信息并存入数据库',
    route: '/documents',
    tagType: 'success',
    status: '已就绪',
    features: ['多格式解析', 'AI信息提取', '实体识别', '数据库存储', '批量上传']
  },
  {
    id: 3,
    name: '表格自定义数据填写',
    desc: '上传模板文件，系统从已提取的文档库中自动搜索匹配数据，AI 智能填充表格并输出结果文件',
    route: '/autofill',
    tagType: 'warning',
    status: '核心功能',
    features: ['模板识别', '语义匹配', '自动填充', '批量处理', 'ZIP打包']
  }
]

const formats = [
  { ext: '.docx', name: 'Word 文档', color: '#2563EB', bg: '#DBEAFE' },
  { ext: '.xlsx', name: 'Excel 表格', color: '#059669', bg: '#D1FAE5' },
  { ext: '.txt', name: '纯文本文件', color: '#7C3AED', bg: '#EDE9FE' },
  { ext: '.md', name: 'Markdown', color: '#EA580C', bg: '#FED7AA' }
]

const workflow = [
  { label: '上传文档', icon: 'UploadFilled', bg: '#EEF2FF', color: '#4F46E5' },
  { label: '文档解析', icon: 'Setting', bg: '#F3E8FF', color: '#7C3AED' },
  { label: 'AI信息提取', icon: 'Search', bg: '#ECFDF5', color: '#10B981' },
  { label: '上传模板', icon: 'Document', bg: '#FEF3C7', color: '#D97706' },
  { label: '智能匹配', icon: 'DataAnalysis', bg: '#FEE2E2', color: '#DC2626' },
  { label: '自动填充', icon: 'Grid', bg: '#DBEAFE', color: '#2563EB' },
  { label: '下载结果', icon: 'Download', bg: '#D1FAE5', color: '#059669' }
]

onMounted(async () => {
  try {
    const res = await getDocumentStats()
    if (res.data) {
      stats.value = res.data
      statCards.value[0].value = String(res.data.total || 0)
      statCards.value[1].value = String(res.data.docx || 0)
      statCards.value[2].value = String(res.data.xlsx || 0)
      statCards.value[3].value = String((res.data.txt || 0) + (res.data.md || 0))
    }
  } catch (e) {
    // silent
  }
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
}

/* Welcome Banner */
.welcome-banner {
  background: var(--primary-gradient);
  border-radius: var(--radius-xl);
  padding: 40px 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: white;
  position: relative;
  overflow: hidden;
}

.welcome-banner::before {
  content: '';
  position: absolute;
  top: -60%;
  right: -10%;
  width: 400px;
  height: 400px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 50%;
}

.welcome-content {
  position: relative;
  z-index: 1;
  max-width: 600px;
}

.welcome-tag {
  display: inline-block;
  padding: 4px 14px;
  background: rgba(255,255,255,0.15);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.05em;
  margin-bottom: 14px;
  backdrop-filter: blur(4px);
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: -0.02em;
}

.welcome-desc {
  font-size: 15px;
  opacity: 0.85;
  margin-bottom: 24px;
  line-height: 1.6;
}

.welcome-actions {
  display: flex;
  gap: 12px;
}

.welcome-actions .el-button--primary {
  background: white !important;
  color: var(--primary) !important;
  border-color: white !important;
  font-weight: 600;
}

.welcome-actions .el-button--primary:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.welcome-actions .el-button:not(.el-button--primary) {
  color: white !important;
  border-color: rgba(255, 255, 255, 0.5) !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.welcome-actions .el-button:not(.el-button--primary):hover {
  background: rgba(255, 255, 255, 0.25) !important;
  border-color: rgba(255, 255, 255, 0.8) !important;
}

.welcome-actions .el-button {
  min-width: 140px;
}

/* Illustration - orbital animation */
.welcome-illustration {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.illustration-orbit {
  width: 180px;
  height: 180px;
  position: relative;
}

.orbit-ring {
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid rgba(255,255,255,0.15);
}

.ring-1 {
  width: 140px;
  height: 140px;
  top: 20px;
  left: 20px;
  animation: orbit-spin 12s linear infinite;
}

.ring-2 {
  width: 180px;
  height: 180px;
  top: 0;
  left: 0;
  animation: orbit-spin 20s linear infinite reverse;
}

.orbit-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255,255,255,0.7);
}

.dot-1 {
  top: 10px;
  left: 90px;
  animation: orbit-move-1 12s linear infinite;
}

.dot-2 {
  top: 160px;
  left: 50px;
  animation: orbit-move-2 20s linear infinite;
}

.dot-3 {
  top: 90px;
  left: 170px;
  animation: orbit-move-3 16s linear infinite;
  width: 7px;
  height: 7px;
}

.orbit-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 56px;
  height: 56px;
  background: rgba(255,255,255,0.18);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: white;
  backdrop-filter: blur(6px);
  border: 1.5px solid rgba(255,255,255,0.3);
}

@keyframes orbit-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes orbit-move-1 {
  0% { transform: translate(0, 0); }
  25% { transform: translate(30px, 40px); }
  50% { transform: translate(-10px, 60px); }
  75% { transform: translate(-30px, 20px); }
  100% { transform: translate(0, 0); }
}

@keyframes orbit-move-2 {
  0% { transform: translate(0, 0); }
  33% { transform: translate(40px, -50px); }
  66% { transform: translate(-20px, -30px); }
  100% { transform: translate(0, 0); }
}

@keyframes orbit-move-3 {
  0% { transform: translate(0, 0); }
  50% { transform: translate(-40px, 30px); }
  100% { transform: translate(0, 0); }
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: var(--bg-white);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--border-light);
  transition: all var(--transition-base);
  cursor: default;
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}

.stat-label {
  font-size: 13px;
  color: var(--text-muted);
}

/* Guide Steps */
.guide-steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.guide-step {
  position: relative;
  cursor: pointer;
}

.guide-step-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.guide-step-connector {
  position: absolute;
  top: 12px;
  left: 36px;
  right: -12px;
  height: 2px;
  background: linear-gradient(90deg, var(--primary) 0%, var(--border-light) 100%);
}

.guide-step-body {
  background: var(--bg-white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
  transition: all var(--transition-base);
}

.guide-step:hover .guide-step-body {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-md), var(--shadow-glow);
  transform: translateY(-2px);
}

.guide-step-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.guide-step-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.guide-step-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
}

.guide-step-tip {
  font-size: 11px;
  color: var(--primary);
  background: var(--primary-lighter);
  padding: 4px 10px;
  border-radius: var(--radius-full);
  display: inline-block;
}

/* Section Title */
.section-title {
  padding-top: 4px;
}

.section-title h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.section-title p {
  font-size: 13px;
  color: var(--text-muted);
}

/* Module Cards */
.modules-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.module-card {
  background: var(--bg-white);
  border-radius: var(--radius-lg);
  padding: 28px;
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all var(--transition-base);
  position: relative;
  overflow: hidden;
}

.module-card:hover {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-lg), var(--shadow-glow);
  transform: translateY(-4px);
}

.module-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.module-number {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--primary-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}

.module-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.module-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 16px;
}

.module-features {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feature-tag {
  padding: 3px 10px;
  font-size: 11px;
  background: var(--primary-lighter);
  color: var(--primary);
  border-radius: var(--radius-full);
  font-weight: 500;
}

.module-arrow {
  position: absolute;
  bottom: 24px;
  right: 24px;
  color: var(--text-muted);
  transition: all var(--transition-fast);
}

.module-card:hover .module-arrow {
  color: var(--primary);
  transform: translateX(4px);
}

/* Info Row (formats + tips) */
.info-row {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 20px;
}

.info-card {
  background: var(--bg-white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
}

.info-card h4 {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.format-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.format-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-base);
  border-radius: var(--radius-md);
}

.format-ext {
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.format-name {
  font-size: 13px;
  color: var(--text-secondary);
}

.tips-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tips-list li {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  padding-left: 16px;
  position: relative;
}

.tips-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
}

.tips-list code {
  background: var(--primary-lighter);
  color: var(--primary);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Menlo', 'Consolas', monospace;
}

/* Workflow bar */
.workflow-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: var(--bg-white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  flex-wrap: wrap;
}

.wf-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.wf-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wf-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.wf-arrow {
  color: var(--text-muted);
  margin: 0 4px;
  margin-bottom: 20px;
}

/* Model bar */
.model-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--bg-white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
}

.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.model-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10B981;
  animation: pulse-dot 2s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.model-powered {
  font-size: 12px;
  color: var(--text-muted);
}

/* Responsive */
@media (max-width: 1200px) {
  .modules-grid { grid-template-columns: 1fr; }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .guide-steps { grid-template-columns: repeat(2, 1fr); }
  .guide-step-connector { display: none; }
  .info-row { grid-template-columns: 1fr; }
}
</style>
