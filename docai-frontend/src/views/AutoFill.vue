<template>
  <div class="autofill-page">
    <!-- 步骤条 -->
    <div class="steps-header card">
      <el-steps :active="currentStep" align-center finish-status="success">
        <el-step title="上传模板" description="上传Word/Excel模板文件" />
        <el-step title="智能填充" description="AI自动匹配数据库文档数据" />
        <el-step title="结果预览" description="预览/下载填充结果" />
      </el-steps>
    </div>

    <!-- Step 1: 上传模板 -->
    <div v-if="currentStep === 0" class="step-content step-content-with-tips">
      <div class="step-panel card">
        <div class="panel-header">
          <div>
            <h3><el-icon :size="18"><Document /></el-icon> 上传模板文件</h3>
            <p class="hint">上传模板后，系统将自动从数据库中匹配已上传的文档数据进行智能填充</p>
          </div>
        </div>

        <!-- 数据源提示 -->
        <div class="source-info" v-loading="loadingStats">
          <div class="source-info-inner">
            <el-icon :size="20" color="#4F46E5"><InfoFilled /></el-icon>
            <span>数据库中已有 <strong>{{ docCount }}</strong> 个文档可用作数据源（其中 <strong>{{ extractedCount }}</strong> 个已成功提取内容）</span>
            <el-button size="small" type="primary" plain class="manage-docs-btn" @click="openSourceDialog">选择数据源文档</el-button>
          </div>
          <div v-if="!loadingStats && extractedCount === 0" class="source-warning">
            <el-icon color="#E65100"><WarningFilled /></el-icon>
            <span>暂无已提取数据的文档，请先前往文档管理页面上传并提取文档内容</span>
          </div>
        </div>

        <div class="selected-sources" v-if="effectiveSourceCount > 0 || selectedSourceDocs.length > 0">
          <div class="selected-sources-header">
            <h4><el-icon><Document /></el-icon> 已选数据源文档（{{ selectedSourceDocs.length }}）</h4>
            <div class="selected-sources-actions">
              <el-button size="small" type="primary" @click="openSourceDialog">
                <el-icon><UploadFilled /></el-icon> 继续选择
              </el-button>
              <el-button size="small" type="danger" plain :disabled="selectedSourceDocs.length === 0" @click="clearSelectedSources">清空已选</el-button>
            </div>
          </div>
          <div v-if="selectedSourceDocs.length === 0" class="selected-sources-empty">
            当前未指定数据源文档，将默认使用全部已提取文档（{{ extractedCount }}）
          </div>
          <div v-else class="selected-sources-list">
            <el-tag
              v-for="doc in selectedSourceDocs"
              :key="doc.id"
              closable
              size="large"
              effect="plain"
              @close="removeSelectedSource(doc.id)"
            >
              {{ doc.fileName }}
            </el-tag>
          </div>
        </div>

        <div class="template-upload-area">
          <el-upload
            v-if="templateFiles.length === 0"
            ref="templateUploadRef"
            drag
            multiple
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleTemplateChange"
            accept=".docx,.xlsx"
            class="template-uploader"
          >
            <div class="upload-inner">
              <el-icon :size="48" color="#818CF8"><UploadFilled /></el-icon>
              <p class="upload-main-text">将模板文件拖到此处，或 <em>点击选择</em></p>
              <p class="upload-hint">支持 .docx / .xlsx 格式，可同时上传多个模板</p>
            </div>
          </el-upload>

          <div
            v-else
            class="selected-files-panel"
            @dragover.prevent
            @drop.prevent="handlePanelDrop"
          >
            <div class="selected-files-header">
              <h4>已选择模板文件（{{ templateFiles.length }}）</h4>
              <el-button text type="danger" @click="clearTemplateFiles">清空</el-button>
            </div>

            <div class="selected-files-list">
              <div class="selected-file-item" v-for="f in templateFiles" :key="f.uid">
                <div class="selected-file-meta">
                  <el-icon :size="18" :color="f.name.endsWith('.xlsx') ? '#10B981' : '#3B82F6'"><Document /></el-icon>
                  <span class="selected-file-name" :title="f.name">{{ f.name }}</span>
                  <span class="selected-file-size">{{ formatFileSize(f.size || 0) }}</span>
                </div>
                <el-button text type="danger" @click="removeTemplateFile(f)">移除</el-button>
              </div>
            </div>

            <el-upload
              multiple
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleTemplateChange"
              accept=".docx,.xlsx"
              class="add-more-uploader"
            >
              <el-button type="primary">
                <el-icon><UploadFilled /></el-icon> 继续添加模板
              </el-button>
            </el-upload>
          </div>
        </div>

        <div class="select-summary">
          <span>已选择 <strong>{{ templateFiles.length }}</strong> 个模板文件，数据源 <strong>{{ effectiveSourceCount }}</strong> 个文档</span>
          <el-button type="primary" :disabled="templateFiles.length === 0 || effectiveSourceCount === 0" @click="startFill">
            <el-icon><MagicStick /></el-icon> 开始智能填充
          </el-button>
        </div>
      </div>

      <div class="template-tips card" style="background: #FFFBEB; border-color: #FDE68A;">
        <h4><el-icon :size="14"><InfoFilled /></el-icon> 模板使用提示</h4>
        <ul>
          <li>Word模板：在需要填写的位置使用 <code v-pre>{{字段名}}</code> 占位符，或留空表格单元格</li>
          <li>Excel模板：第一行作为表头，下方空单元格将根据表头名称自动填充</li>
          <li>可在本页面自主选择一个或多个数据源文档；未选择时默认使用全部已提取文档</li>
        </ul>
      </div>
    </div>

    <!-- Step 2: 填充进度 -->
    <div v-if="currentStep === 1" class="step-content">
      <div class="step-panel card">
        <div class="panel-header">
          <h3><el-icon :size="18"><MagicStick /></el-icon> AI 正在智能填充...</h3>
        </div>
        <div class="filling-progress">
          <div class="progress-animation">
            <div class="pulse-ring"></div>
            <div class="pulse-core">
              <el-icon :size="40" color="white"><MagicStick /></el-icon>
            </div>
          </div>
          <div class="progress-steps">
            <div class="p-step" :class="{ done: fillPhase >= 1, active: fillPhase === 0 }">
              <div class="p-dot"></div><span>读取数据库文档</span>
            </div>
            <div class="p-step" :class="{ done: fillPhase >= 2, active: fillPhase === 1 }">
              <div class="p-dot"></div><span>分析模板结构</span>
            </div>
            <div class="p-step" :class="{ done: fillPhase >= 3, active: fillPhase === 2 }">
              <div class="p-dot"></div><span>AI提取匹配数据</span>
            </div>
            <div class="p-step" :class="{ done: fillPhase >= 4, active: fillPhase === 3 }">
              <div class="p-dot"></div><span>填充模板</span>
            </div>
          </div>
          <div class="progress-info">
            <el-progress :percentage="Math.min(fillProgress, 100)" :stroke-width="8" color="#4F46E5" :format="formatProgress" />
            <p class="progress-detail">数据源: {{ effectiveSourceCount }} 个文档 | 模板: {{ templateFiles.length }} 个</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Step 3: 结果 -->
    <div v-if="currentStep >= 2" class="step-content">
      <div class="step-panel card">
        <div class="panel-header">
          <div>
            <h3>{{ fillError ? '填充失败' : '填充完成' }}</h3>
          </div>
          <div class="header-actions">
            <el-button v-if="resultFiles.length > 0 && !fillError" type="primary" @click="downloadResult">
              <el-icon><Download /></el-icon> 下载结果
            </el-button>
            <el-button @click="resetAll">
              <el-icon><RefreshRight /></el-icon> 继续填表
            </el-button>
          </div>
        </div>
        <div class="result-area">
          <div class="result-success" v-if="fillResult && !fillError">
            <div class="result-summary">
              <div class="summary-item">
                <el-icon :size="32" color="#10B981"><SuccessFilled /></el-icon>
                <div>
                  <p class="summary-label">智能填表已完成</p>
                  <p class="summary-value">耗时 {{ fillTimeDisplay }}，数据源 {{ effectiveSourceCount }} 个文档</p>
                </div>
              </div>
              <div class="result-file-chips" v-if="resultFiles.length > 0">
                <div class="result-file-chip" v-for="(f, i) in resultFiles" :key="i" @click="downloadSingleResult(f)">
                  <el-icon :size="16" :color="f.name.endsWith('.xlsx') ? '#10B981' : '#3B82F6'"><Document /></el-icon>
                  <span>{{ f.name }}</span>
                  <el-icon :size="14"><Download /></el-icon>
                </div>
              </div>
            </div>

            <!-- Tab: 填充明细 / 分析结果 -->
            <el-tabs v-model="resultTab" class="result-tabs" v-if="decisionList.length > 0">
              <el-tab-pane label="填充明细" name="detail">
                <el-table :data="decisionList" stripe max-height="420" style="width:100%">
                  <el-table-column label="字段" prop="slotLabel" min-width="140" />
                  <el-table-column label="填入值" prop="finalValue" min-width="200">
                    <template #default="{ row }">
                      <span v-if="row.finalValue">{{ row.finalValue }}</span>
                      <el-tag v-else size="small" type="info">未填写</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="置信度" width="120" align="center">
                    <template #default="{ row }">
                      <el-progress
                        :percentage="Math.round((row.finalConfidence || 0) * 100)"
                        :color="row.finalConfidence >= 0.85 ? '#10B981' : row.finalConfidence >= 0.7 ? '#F59E0B' : '#EF4444'"
                        :stroke-width="6"
                        :show-text="true"
                        :format="() => ((row.finalConfidence || 0) * 100).toFixed(0) + '%'"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="决策方式" width="130" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.decisionMode === 'rule_only' ? 'success' : row.decisionMode === 'rule_plus_llm' ? 'warning' : 'info'" effect="plain">
                        {{ decisionModeLabel(row.decisionMode) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="原因" prop="reason" min-width="200" show-overflow-tooltip />
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="分析结果" name="analysis">
                <div class="analysis-charts">
                  <div class="chart-row">
                    <div class="chart-card">
                      <h4>置信度分布</h4>
                      <div ref="confidenceChartRef" class="chart-container"></div>
                    </div>
                    <div class="chart-card">
                      <h4>决策方式分布</h4>
                      <div ref="modeChartRef" class="chart-container"></div>
                    </div>
                  </div>
                  <div class="chart-row">
                    <div class="chart-card wide">
                      <h4>各字段置信度</h4>
                      <div ref="barChartRef" class="chart-container-wide"></div>
                    </div>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
          <div class="result-error" v-if="fillError">
            <div class="result-icon"><el-icon :size="64" color="#EF4444"><CircleCloseFilled /></el-icon></div>
            <h4>填充失败</h4>
            <p>{{ fillError }}</p>
            <el-button type="primary" @click="currentStep = 0">重新选择模板</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showSourceDialog" title="选择填表数据源文档" width="920px">
      <div class="source-dialog-toolbar">
        <div class="source-dialog-filters">
          <el-input
            v-model="sourceKeyword"
            placeholder="搜索文档名称"
            clearable
            style="width: 280px"
          />
          <el-select v-model="sourceType" placeholder="文件类型" clearable style="width: 130px;">
            <el-option label="Word" value="docx" />
            <el-option label="Excel" value="xlsx" />
            <el-option label="TXT" value="txt" />
            <el-option label="Markdown" value="md" />
          </el-select>
        </div>
        <div class="source-dialog-toolbar-actions">
          <el-button @click="selectAllFilteredSources" :disabled="filteredSourceDocs.length === 0">全选当前筛选结果</el-button>
          <el-button @click="clearDialogSelection" :disabled="sourceDialogSelectedIds.length === 0">清空当前勾选</el-button>
          <el-button @click="refreshSourceDocs">刷新列表</el-button>
        </div>
      </div>

      <el-table
        ref="sourceTableRef"
        :data="filteredSourceDocs"
        max-height="420"
        row-key="id"
        @selection-change="handleSourceSelectionChange"
      >
        <el-table-column type="selection" width="55" reserve-selection />
        <el-table-column label="文档名称" min-width="280">
          <template #default="{ row }">
            <div class="source-name-cell">
              <span class="source-name">{{ row.fileName }}</span>
              <span class="source-meta">{{ row.fileType?.toUpperCase() }} · {{ formatFileSize(row.fileSize || 0) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提取状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.uploadStatus === 'parsed' ? 'success' : row.uploadStatus === 'failed' ? 'danger' : 'info'" effect="plain">
              {{ row.uploadStatus === 'parsed' ? '可用' : (row.uploadStatus || '未提取') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="160" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="source-dialog-footer">
          <span>当前勾选 {{ sourceDialogSelectedIds.length }} 个，可多次追加选择</span>
          <div>
            <el-button @click="showSourceDialog = false">取消</el-button>
            <el-button type="primary" :disabled="sourceDialogSelectedIds.length === 0" @click="applySourceSelection">添加到已选数据源</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import { useDocumentStore } from '../store/documentStore'
import { storeToRefs } from 'pinia'
import {
  uploadTemplateFile,
  parseTemplateSlots,
  fillTemplate,
  downloadTemplateResult,
  getTemplateDecisions,
  downloadBlob
} from '../api'
import { ElMessage } from 'element-plus'
import {
  UploadFilled, MagicStick, Download, InfoFilled, WarningFilled, RefreshRight,
  Document, SuccessFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const docStore = useDocumentStore()

// Get reactive state and getters from the store
const { docCount, extractedCount, loading: loadingStats } = storeToRefs(docStore)

const ACCEPTED_EXTENSIONS = ['.docx', '.xlsx']

// State
const currentStep = ref(0)
const templateFiles = ref([])
const selectedSourceDocIds = ref([])

const showSourceDialog = ref(false)
const sourceKeyword = ref('')
const sourceType = ref('')
const sourceDialogSelectedIds = ref([])
const sourceTableRef = ref(null)

// Fill state
const fillProgress = ref(0)
const fillPhase = ref(0)
const progressText = ref('准备中...')
const fillResult = ref(null)
const fillError = ref('')
const fillTimeMs = ref(0)
const resultFiles = ref([])

// Result analysis
const resultTab = ref('detail')
const decisionList = ref([])
const templateIdsForDecisions = ref([])
const confidenceChartRef = ref(null)
const modeChartRef = ref(null)
const barChartRef = ref(null)
let chartInstances = []

const fillTimeDisplay = computed(() => {
  if (fillTimeMs.value < 1000) return fillTimeMs.value + 'ms'
  return (fillTimeMs.value / 1000).toFixed(1) + 's'
})

const availableSourceDocs = computed(() => {
  return (docStore.allDocuments || []).filter((doc) => doc.uploadStatus === 'parsed')
})

const sourceDocMap = computed(() => {
  return new Map(availableSourceDocs.value.map((doc) => [doc.id, doc]))
})

const selectedSourceDocs = computed(() => {
  return selectedSourceDocIds.value
    .map((id) => sourceDocMap.value.get(id))
    .filter(Boolean)
})

const filteredSourceDocs = computed(() => {
  const keyword = sourceKeyword.value.trim().toLowerCase()
  return availableSourceDocs.value.filter((doc) => {
    const typeMatch = !sourceType.value || doc.fileType === sourceType.value
    const keywordMatch = !keyword || (doc.fileName || '').toLowerCase().includes(keyword)
    return typeMatch && keywordMatch
  })
})

const effectiveSourceCount = computed(() => {
  return selectedSourceDocs.value.length > 0 ? selectedSourceDocs.value.length : extractedCount.value
})

const formatProgress = (percentage) => {
  return Math.floor(percentage) + '%'
}

// Methods
const loadStats = async () => {
  // Trigger fetch only if cache is invalid
  if (!docStore.isCacheValid) {
    await docStore.fetchDocuments({ size: 500 }) // Fetch a larger batch for accurate counting
  }
}

const refreshSourceDocs = async () => {
  await docStore.fetchDocuments({ size: 500, force: true })
  ElMessage.success('已刷新文档列表')
}

const openSourceDialog = async () => {
  if (!docStore.isCacheValid) {
    await docStore.fetchDocuments({ size: 500 })
  }
  showSourceDialog.value = true
  sourceDialogSelectedIds.value = [...selectedSourceDocIds.value]

  await nextTick()
  filteredSourceDocs.value.forEach((row) => {
    if (selectedSourceDocIds.value.includes(row.id)) {
      sourceTableRef.value?.toggleRowSelection(row, true)
    }
  })
}

const handleSourceSelectionChange = (rows) => {
  sourceDialogSelectedIds.value = rows.map((row) => row.id)
}

const clearDialogSelection = () => {
  sourceTableRef.value?.clearSelection()
  sourceDialogSelectedIds.value = []
}

const selectAllFilteredSources = async () => {
  if (filteredSourceDocs.value.length === 0) return
  sourceTableRef.value?.clearSelection()
  await nextTick()
  filteredSourceDocs.value.forEach((row) => {
    sourceTableRef.value?.toggleRowSelection(row, true)
  })
}

const applySourceSelection = () => {
  const merged = new Set([...selectedSourceDocIds.value, ...sourceDialogSelectedIds.value])
  selectedSourceDocIds.value = Array.from(merged)
  showSourceDialog.value = false
  ElMessage.success(`已添加 ${sourceDialogSelectedIds.value.length} 个数据源文档`)
}

const removeSelectedSource = (docId) => {
  selectedSourceDocIds.value = selectedSourceDocIds.value.filter((id) => id !== docId)
}

const clearSelectedSources = () => {
  selectedSourceDocIds.value = []
}

const handleTemplateChange = (file) => {
  if (!file?.raw) return
  if (!isTemplateType(file.raw.name)) {
    ElMessage.warning('仅支持 .docx / .xlsx 模板文件')
    return
  }
  if (isDuplicateRawFile(file.raw)) return
  templateFiles.value.push(file)
}

const handleTemplateRemove = (file) => {
  const idx = templateFiles.value.findIndex(f => f.uid === file.uid)
  if (idx >= 0) templateFiles.value.splice(idx, 1)
}

const removeTemplateFile = (file) => {
  handleTemplateRemove(file)
}

const clearTemplateFiles = () => {
  templateFiles.value = []
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const isTemplateType = (name = '') => {
  const lowerName = name.toLowerCase()
  return ACCEPTED_EXTENSIONS.some(ext => lowerName.endsWith(ext))
}

const isDuplicateRawFile = (rawFile) => {
  return templateFiles.value.some((f) => {
    const raw = f.raw
    return raw && raw.name === rawFile.name && raw.size === rawFile.size && raw.lastModified === rawFile.lastModified
  })
}

const handlePanelDrop = (event) => {
  const droppedFiles = Array.from(event.dataTransfer?.files || [])
  if (droppedFiles.length === 0) return

  let added = 0
  let invalid = 0

  droppedFiles.forEach((raw, index) => {
    if (!isTemplateType(raw.name)) {
      invalid += 1
      return
    }
    if (isDuplicateRawFile(raw)) return

    templateFiles.value.push({
      uid: `drop_${Date.now()}_${index}`,
      name: raw.name,
      size: raw.size,
      raw
    })
    added += 1
  })

  if (added > 0) {
    ElMessage.success(`已添加 ${added} 个模板文件`)
  }
  if (invalid > 0) {
    ElMessage.warning(`已忽略 ${invalid} 个非模板文件，仅支持 .docx / .xlsx`)
  }
}

const startFill = async () => {
  currentStep.value = 1
  fillProgress.value = 0
  fillPhase.value = 0
  fillResult.value = null
  fillError.value = ''
  resultFiles.value = []
  progressText.value = '正在读取数据库文档数据...'

  // 模拟进度
  const progressTimer = setInterval(() => {
    if (fillProgress.value < 90) {
      fillProgress.value += Math.random() * 6
      if (fillProgress.value > 20 && fillPhase.value < 1) { fillPhase.value = 1; progressText.value = '分析模板结构...' }
      if (fillProgress.value > 45 && fillPhase.value < 2) { fillPhase.value = 2; progressText.value = 'AI正在提取匹配数据...' }
      if (fillProgress.value > 70 && fillPhase.value < 3) { fillPhase.value = 3; progressText.value = '填充模板中...' }
    }
  }, 600)

  try {
    const docIds = selectedSourceDocIds.value.length > 0 ? [...selectedSourceDocIds.value] : []
    const finishedFiles = []
    const collectedTemplateIds = []
    const startedAt = Date.now()

    for (let i = 0; i < templateFiles.value.length; i++) {
      const tpl = templateFiles.value[i]
      const uploadRes = await uploadTemplateFile(tpl.raw)
      const templateId = uploadRes.data?.id
      if (!templateId) {
        throw new Error('模板上传失败，未返回 templateId')
      }

      await parseTemplateSlots(templateId)
      await fillTemplate(templateId, docIds)
      collectedTemplateIds.push(templateId)
      const fileRes = await downloadTemplateResult(templateId)

      finishedFiles.push({
        name: `filled_${tpl.name}`,
        blob: fileRes.data
      })

      fillProgress.value = Math.max(fillProgress.value, ((i + 1) / templateFiles.value.length) * 95)
    }

    clearInterval(progressTimer)
    fillProgress.value = 100
    fillPhase.value = 4
    progressText.value = '完成！'

    fillTimeMs.value = Date.now() - startedAt
    resultFiles.value = finishedFiles
    fillResult.value = finishedFiles[0]?.blob || null
    templateIdsForDecisions.value = collectedTemplateIds

    // Load fill decisions for result analysis
    await loadDecisions(collectedTemplateIds)

    setTimeout(() => { currentStep.value = 3 }, 800)

  } catch (e) {
    clearInterval(progressTimer)
    // Blob响应无法直接读取message，需要解析
    let errMsg = e.message || '填充失败，请重试'
    if (e.response?.data instanceof Blob) {
      try { errMsg = await e.response.data.text() } catch (_) {}
    } else if (e.response?.data?.message) {
      errMsg = e.response.data.message
    }
    fillError.value = errMsg
    fillProgress.value = 0
    currentStep.value = 2
    ElMessage.error('填充失败: ' + fillError.value)
  }
}

const downloadResult = () => {
  if (resultFiles.value.length === 0) return
  resultFiles.value.forEach((f) => {
    downloadBlob(new Blob([f.blob]), f.name)
  })
  ElMessage.success(`已下载 ${resultFiles.value.length} 个结果文件`)
}

const downloadSingleResult = (f) => {
  downloadBlob(new Blob([f.blob]), f.name)
  ElMessage.success('下载成功')
}

const decisionModeLabel = (mode) => {
  const map = { rule_only: '规则决策', rule_plus_llm: '规则+AI', fallback_blank: '拒填' }
  return map[mode] || mode || '未知'
}

const loadDecisions = async (templateIds) => {
  const allDecisions = []
  for (const tid of templateIds) {
    try {
      const res = await getTemplateDecisions(tid)
      if (Array.isArray(res.data)) {
        allDecisions.push(...res.data)
      }
    } catch (e) {
      console.warn('获取填充决策失败:', e)
    }
  }
  decisionList.value = allDecisions
}

const renderCharts = async () => {
  await nextTick()
  disposeCharts()
  if (decisionList.value.length === 0) return

  // Confidence distribution pie chart
  if (confidenceChartRef.value) {
    const high = decisionList.value.filter(d => (d.finalConfidence || 0) >= 0.85).length
    const medium = decisionList.value.filter(d => (d.finalConfidence || 0) >= 0.7 && (d.finalConfidence || 0) < 0.85).length
    const low = decisionList.value.filter(d => (d.finalConfidence || 0) > 0 && (d.finalConfidence || 0) < 0.7).length
    const blank = decisionList.value.filter(d => !d.finalConfidence || d.finalConfidence === 0).length

    const chart = echarts.init(confidenceChartRef.value)
    chartInstances.push(chart)
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        label: { show: false },
        data: [
          { value: high, name: '高置信(≥85%)', itemStyle: { color: '#10B981' } },
          { value: medium, name: '中置信(70-85%)', itemStyle: { color: '#F59E0B' } },
          { value: low, name: '低置信(<70%)', itemStyle: { color: '#EF4444' } },
          { value: blank, name: '未填写', itemStyle: { color: '#D1D5DB' } }
        ].filter(d => d.value > 0)
      }]
    })
  }

  // Decision mode pie chart
  if (modeChartRef.value) {
    const modeMap = {}
    decisionList.value.forEach(d => {
      const mode = d.decisionMode || 'unknown'
      modeMap[mode] = (modeMap[mode] || 0) + 1
    })
    const chart = echarts.init(modeChartRef.value)
    chartInstances.push(chart)
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        label: { show: false },
        data: Object.entries(modeMap).map(([mode, count]) => ({
          value: count,
          name: decisionModeLabel(mode),
          itemStyle: { color: mode === 'rule_only' ? '#10B981' : mode === 'rule_plus_llm' ? '#F59E0B' : '#D1D5DB' }
        }))
      }]
    })
  }

  // Bar chart: per-field confidence
  if (barChartRef.value) {
    const labels = decisionList.value.map(d => d.slotLabel || d.slotId || '?')
    const values = decisionList.value.map(d => Math.round((d.finalConfidence || 0) * 100))
    const colors = values.map(v => v >= 85 ? '#10B981' : v >= 70 ? '#F59E0B' : '#EF4444')

    const chart = echarts.init(barChartRef.value)
    chartInstances.push(chart)
    chart.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
      grid: { left: 100, right: 30, top: 10, bottom: 30 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', data: labels, axisLabel: { width: 80, overflow: 'truncate' } },
      series: [{
        type: 'bar',
        data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
        barWidth: 16
      }]
    })
  }
}

const disposeCharts = () => {
  chartInstances.forEach(c => { try { c.dispose() } catch (_) {} })
  chartInstances = []
}

// Watch for tab switch to render charts
watch(resultTab, async (tab) => {
  if (tab === 'analysis' && decisionList.value.length > 0) {
    await renderCharts()
  }
})

onBeforeUnmount(disposeCharts)

const resetAll = () => {
  currentStep.value = 0
  templateFiles.value = []
  fillResult.value = null
  fillError.value = ''
  fillProgress.value = 0
  fillPhase.value = 0
  resultFiles.value = []
  resultTab.value = 'detail'
  decisionList.value = []
  templateIdsForDecisions.value = []
  disposeCharts()
  sourceKeyword.value = ''
  sourceType.value = ''
  sourceDialogSelectedIds.value = []
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(loadStats)
</script>

<style scoped>
.autofill-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
}

.steps-header {
  padding: 24px 40px;
}

.step-content {
  flex: 1;
}

.step-content-with-tips {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-content-with-tips .step-panel {
  height: auto;
  flex: 1;
  min-height: 0;
}

.step-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.panel-header {
  padding: 24px 28px 16px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-light);
}

.panel-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.hint {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* Source info banner */
.source-info {
  margin: 16px 28px 0;
  padding: 14px 18px;
  background: rgba(79, 70, 229, 0.04);
  border: 1px solid rgba(79, 70, 229, 0.15);
  border-radius: var(--radius-md);
}

.source-info-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.source-info-inner strong {
  color: var(--primary);
  font-weight: 700;
}

.source-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(230, 81, 0, 0.3);
  font-size: 13px;
  color: #E65100;
}

.selected-sources {
  margin: 12px 28px 0;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: #fff;
}

.selected-sources-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.selected-sources-header h4 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-primary);
}

.selected-sources-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-sources-empty {
  margin-top: 10px;
  font-size: 13px;
  color: var(--text-muted);
}

.selected-sources-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.source-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.source-dialog-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.source-dialog-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  color: var(--text-secondary);
  font-size: 13px;
}

.source-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source-name {
  font-size: 14px;
  color: var(--text-primary);
}

.source-meta,
.text-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.select-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px 20px;
  border-top: 1px solid var(--border-light);
  margin-top: 12px;
  font-size: 14px;
  color: var(--text-secondary);
}

/* Template upload */
.template-upload-area {
  padding: 24px 28px;
}

.template-uploader :deep(.el-upload-dragger) {
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  padding: 48px;
  transition: all var(--transition-base);
  background: var(--bg-base);
}

.template-uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--primary-light);
  background: var(--primary-lighter);
}

.upload-inner {
  text-align: center;
}

.upload-main-text {
  font-size: 15px;
  color: var(--text-secondary);
  margin-top: 12px;
}

.upload-main-text em {
  color: var(--primary);
  font-style: normal;
  font-weight: 600;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.selected-files-panel {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-base);
  padding: 14px 16px;
}

.selected-files-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.selected-files-header h4 {
  margin: 0;
  font-size: 14px;
  color: var(--text-primary);
}

.selected-files-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  margin-bottom: 12px;
}

.selected-file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: #fff;
}

.selected-file-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.selected-file-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-file-size {
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.template-tips {
  margin: 0;
  padding: 16px 20px;
  border-radius: var(--radius-md);
}

.template-tips h4 {
  font-size: 14px;
  margin-bottom: 8px;
}

.template-tips ul {
  font-size: 13px;
  color: var(--text-secondary);
  padding-left: 20px;
  line-height: 1.8;
}

.template-tips code {
  background: rgba(245, 158, 11, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #D97706;
}

/* Filling progress */
.filling-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 28px;
  gap: 32px;
}

.progress-animation {
  position: relative;
  width: 100px;
  height: 100px;
}

.pulse-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid var(--primary-light);
  animation: pulse-ring-anim 1.5s infinite;
}

.pulse-core {
  position: absolute;
  inset: 15px;
  border-radius: 50%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse-core-anim 1.5s infinite;
}

@keyframes pulse-ring-anim {
  0% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.2); opacity: 0; }
  100% { transform: scale(1); opacity: 0.6; }
}

@keyframes pulse-core-anim {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(0.95); }
}

.progress-info {
  width: 100%;
  max-width: 500px;
  text-align: center;
}

.progress-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 12px;
}

.progress-detail {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

.progress-steps {
  display: flex;
  gap: 32px;
}

.p-step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.p-step.active {
  color: var(--primary);
  font-weight: 600;
}

.p-step.done {
  color: var(--success);
}

.p-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.p-step.active .p-dot {
  animation: pulse-dot 1s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* Result */
.result-area {
  padding: 20px 28px;
  text-align: left;
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: rgba(16, 185, 129, 0.06);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: var(--radius-md);
  margin-bottom: 20px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-label {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.summary-value {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 4px 0 0;
}

.result-file-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.result-file-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--bg-base);
  border: 1px solid var(--border-light);
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.result-file-chip:hover {
  border-color: var(--primary);
  background: rgba(79, 70, 229, 0.05);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.result-tabs {
  margin-top: 8px;
}

.analysis-charts {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 12px 0;
}

.chart-row {
  display: flex;
  gap: 16px;
}

.chart-card {
  flex: 1;
  padding: 16px;
  background: var(--bg-base);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
}

.chart-card.wide {
  flex: 1;
}

.chart-card h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.chart-container {
  height: 260px;
}

.chart-container-wide {
  height: 300px;
}

.result-icon {
  font-size: 64px;
  margin-bottom: 16px;
  text-align: center;
}

.result-success h4,
.result-error h4 {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 8px;
}

.result-error {
  text-align: center;
  padding: 40px 0;
}

.result-error p {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.manage-docs-btn {
  background: rgba(79, 70, 229, 0.1) !important;
  border-color: var(--primary) !important;
  color: var(--primary) !important;
  font-weight: 500;
}

.manage-docs-btn:hover {
  background: rgba(79, 70, 229, 0.2) !important;
}
</style>
