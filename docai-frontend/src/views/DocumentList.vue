<template>
  <div class="documents-page">
    <!-- 工具栏 -->
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="keyword"
          placeholder="搜索文档名称..."
          clearable
          @clear="loadDocuments"
          @keyup.enter="loadDocuments"
          style="width: 300px;"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterType" placeholder="文件类型" clearable style="width: 130px;" @change="loadDocuments">
          <el-option label="Word" value="docx" />
          <el-option label="Excel" value="xlsx" />
          <el-option label="TXT" value="txt" />
          <el-option label="Markdown" value="md" />
        </el-select>
        <el-button @click="loadDocuments">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button type="warning" plain @click="batchDownload" :disabled="selectedIds.length === 0">
          <el-icon><Download /></el-icon> 下载已选 <span v-if="selectedIds.length">({{ selectedIds.length }})</span>
        </el-button>
        <el-button type="danger" plain @click="batchDelete" :disabled="selectedIds.length === 0">
          <el-icon><Delete /></el-icon> 删除已选 <span v-if="selectedIds.length">({{ selectedIds.length }})</span>
        </el-button>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><UploadFilled /></el-icon> 上传文档
        </el-button>
      </div>
    </div>

    <!-- 统计信息 -->
    <div class="stats-bar">
      <el-tag effect="plain" round>
        共 <strong>{{ total }}</strong> 个文档
      </el-tag>
      <el-tag v-if="selectedIds.length" type="success" effect="plain" round>
        已选 <strong>{{ selectedIds.length }}</strong> 个
      </el-tag>
      <el-tag v-if="keyword" type="info" closable @close="keyword = ''; loadDocuments()">
        搜索: {{ keyword }}
      </el-tag>
    </div>

    <!-- 上传队列显示 -->
    <div class="upload-queue-container card" v-if="docStore.uploadQueue.length > 0">
      <div class="queue-header">
        <div class="queue-title">
          <span v-if="docStore.isUploading" class="status-badge uploading">
            <el-icon class="is-loading"><Loading /></el-icon> 上传中
          </span>
          <span v-else class="status-badge completed">已完成</span>
          <span class="queue-count">{{ docStore.uploadQueue.length }} 个文件</span>
        </div>
        <el-button size="small" type="danger" plain @click="clearUploadQueue" v-if="!docStore.isUploading">
          <el-icon><Delete /></el-icon> 清空
        </el-button>
      </div>
      
      <div class="queue-items">
        <div class="queue-item" v-for="item in docStore.uploadQueue" :key="item.id">
          <div class="item-info">
            <el-icon class="file-icon">
              <Document />
            </el-icon>
            <div class="item-details">
              <div class="item-name">{{ item.fileName }}</div>
              <div class="item-size">{{ formatSize(item.fileSize) }}</div>
            </div>
          </div>
          <div class="item-progress">
            <el-progress 
              :percentage="item.progress" 
              :status="item.status === 'success' ? 'success' : item.status === 'failed' ? 'exception' : item.status === 'cancelled' ? 'warning' : undefined"
              :show-text="false"
              :stroke-width="6"
            />
            <span v-if="item.status === 'success'" class="status-text success">✓ 成功</span>
            <span v-else-if="item.status === 'failed'" class="status-text failed">✗ 失败</span>
            <span v-else-if="item.status === 'cancelled'" class="status-text cancelled">已取消</span>
            <span v-else class="progress-percent">{{ item.progress }}%</span>
            
            <el-button 
              size="small" 
              type="danger" 
              text 
              @click="cancelUploadItem(item.id)"
              v-if="item.status === 'uploading' || item.status === 'pending' || item.phase === 'extracting'"
              class="cancel-btn"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 文档表格 -->
    <div
      class="table-wrapper card"
      v-loading="loading"
    >
      <el-empty v-if="!loading && documents.length === 0" description="暂无文档，上传你的第一个文件吧" />
      <el-table
        v-else
        :data="documents"
        @selection-change="handleSelectionChange"
        style="width: 100%;"
        row-class-name="table-row"
        stripe
      >
        <el-table-column type="selection" width="50" />
        <el-table-column label="文档名称" min-width="280">
          <template #default="{ row }">
            <div class="doc-name-cell">
              <span class="doc-icon">
                <el-icon :size="20" v-if="row.fileType === 'docx'" color="#3B82F6"><Document /></el-icon>
                <el-icon :size="20" v-else-if="row.fileType === 'xlsx'" color="#10B981"><Grid /></el-icon>
                <el-icon :size="20" v-else-if="row.fileType === 'md'" color="#F59E0B"><EditPen /></el-icon>
                <el-icon :size="20" v-else color="#6B7280"><Document /></el-icon>
              </span>
              <div class="doc-name-wrap">
                <span class="doc-title">{{ row.fileName }}</span>
                <span v-if="row.docSummary" class="doc-preview">
                  {{ row.docSummary }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagMap[row.fileType]" effect="plain" round>
              {{ row.fileType?.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ formatSize(row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="140" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提取状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.uploadStatus === 'parsed' ? 'success' : row.uploadStatus === 'failed' ? 'danger' : 'info'" effect="plain" round>
              {{ row.uploadStatus === 'parsed' ? '已提取' : row.uploadStatus === 'failed' ? '提取失败' : row.uploadStatus === 'parsing' ? '提取中' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip content="文件预览" placement="top">
                <el-button size="small" class="preview-btn" plain round @click="viewContent(row)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="AI 对话" placement="top">
                <el-button size="small" type="success" plain round @click="goChat(row)" :disabled="row.uploadStatus !== 'parsed'">
                  <el-icon><ChatLineRound /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="下载" placement="top">
                <el-button size="small" type="warning" plain round @click="downloadDoc(row)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button size="small" type="danger" plain round @click="deleteDoc(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadDocuments"
          background
          small
        />
      </div>
    </div>

    <!-- 上传文档弹窗 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="560px" @close="onUploadDialogClose">
      <div class="upload-area">
        <el-upload
          ref="uploadRef"
          drag
          multiple
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          accept=".docx,.xlsx,.txt,.md"
          :file-list="uploadFileList"
        >
          <el-icon :size="48" class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 .docx / .xlsx / .txt / .md 四种格式，单个文件不超过200MB</div>
          </template>
        </el-upload>
      </div>
      <div class="upload-info" v-if="uploadFileList.length > 0">
        <p>已选择 <strong>{{ uploadFileList.length }}</strong> 个文件</p>
      </div>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="doUpload" :disabled="uploadFileList.length === 0">
          <el-icon><UploadFilled /></el-icon> 上传并提取信息
        </el-button>
      </template>
    </el-dialog>

    <!-- 文件原文预览弹窗 -->
    <el-dialog v-model="showContentDialog" :title="'文件预览 - ' + viewDocTitle" width="85vw" top="3vh">
      <div class="content-result" v-loading="previewLoading">
        <div class="content-body">
          <div v-if="previewType === 'html'" class="content-html" v-html="previewHtml"></div>
          <div v-else-if="previewType === 'markdown'" class="content-markdown" v-html="renderedMarkdown"></div>
          <pre v-else class="content-text">{{ viewDocContent }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useDocumentStore } from '../store/documentStore'
import { uploadSourceDocument, uploadExcelFile, getDocumentFields, downloadSourceDocument, downloadBlob } from '../api'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import * as XLSX from 'xlsx'
import mammoth from 'mammoth'
import { CancelToken } from '../api/request'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Search, UploadFilled, Download, Delete, ChatLineRound,
  Refresh, View, Document, Grid, EditPen, Loading, Close
} from '@element-plus/icons-vue'

const router = useRouter()
const docStore = useDocumentStore()

// Make state and getters reactive
const { documents, total, loading } = storeToRefs(docStore)

// Local state for UI controls
const keyword = ref('')
const filterType = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])

// 上传相关
const showUploadDialog = ref(false)
const uploadFileList = ref([])
const uploadRef = ref(null)
const dragActive = ref(false)
const dragDepth = ref(0)

// 查看内容
const showContentDialog = ref(false)
const viewDocTitle = ref('')
const viewDocContent = ref('')
const previewLoading = ref(false)
const previewType = ref('text') // 'text' or 'html' or 'markdown'
const previewBlobUrl = ref('')
const previewHtml = ref('')

const renderedMarkdown = computed(() => {
  if (!viewDocContent.value) return ''
  return DOMPurify.sanitize(marked.parse(viewDocContent.value, { breaks: true, gfm: true }))
})

const typeTagMap = { docx: 'primary', xlsx: 'success', txt: 'info', md: 'warning' }
const validExts = ['.docx', '.xlsx', '.txt', '.md']
const maxFileSize = 200 * 1024 * 1024
const extractTimerMap = new Map()

const loadDocuments = async (force = false) => {
  await docStore.fetchDocuments({
    page: currentPage.value,
    size: pageSize.value,
    keyword: keyword.value,
    fileType: filterType.value,
    force
  })
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadDocuments()
}

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

// 文件选择处理
const handleFileChange = (file, fileList) => {
  const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
  if (!validExts.includes(ext)) {
    ElMessage.error('不支持的格式: ' + ext)
    fileList.pop()
    return
  }
  if (file.size > maxFileSize) {
    ElMessage.error('文件不能超过200MB')
    fileList.pop()
    return
  }
  uploadFileList.value = fileList
}

const handleFileRemove = (file, fileList) => {
  uploadFileList.value = fileList
}

const onUploadDialogClose = () => {
  uploadFileList.value = []
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const isDuplicateUpload = (rawFile) => {
  return uploadFileList.value.some((item) => {
    const raw = item.raw
    return raw && raw.name === rawFile.name && raw.size === rawFile.size && raw.lastModified === rawFile.lastModified
  })
}

const onListDragEnter = () => {
  dragDepth.value += 1
  dragActive.value = true
}

const onListDragOver = () => {
  if (!dragActive.value) dragActive.value = true
}

const onListDragLeave = () => {
  dragDepth.value = Math.max(0, dragDepth.value - 1)
  if (dragDepth.value === 0) {
    dragActive.value = false
  }
}

const onListDrop = (event) => {
  dragDepth.value = 0
  dragActive.value = false

  const droppedFiles = Array.from(event.dataTransfer?.files || [])
  if (droppedFiles.length === 0) return

  let added = 0
  let invalidType = 0
  let oversize = 0
  let duplicate = 0

  droppedFiles.forEach((raw, index) => {
    const ext = raw.name.substring(raw.name.lastIndexOf('.')).toLowerCase()
    if (!validExts.includes(ext)) {
      invalidType += 1
      return
    }
    if (raw.size > maxFileSize) {
      oversize += 1
      return
    }
    if (isDuplicateUpload(raw)) {
      duplicate += 1
      return
    }

    uploadFileList.value.push({
      uid: `drop_${Date.now()}_${index}`,
      name: raw.name,
      size: raw.size,
      status: 'ready',
      raw
    })
    added += 1
  })

  if (added > 0) {
    showUploadDialog.value = true
    ElMessage.success(`已添加 ${added} 个文件，点击“上传并提取信息”开始处理`)
  }
  if (invalidType > 0) {
    ElMessage.warning(`已忽略 ${invalidType} 个不支持格式文件`)
  }
  if (oversize > 0) {
    ElMessage.warning(`已忽略 ${oversize} 个超过200MB的文件`)
  }
  if (duplicate > 0) {
    ElMessage.info(`已忽略 ${duplicate} 个重复文件`)
  }
}

// 执行上传
const doUpload = async () => {
  if (uploadFileList.value.length === 0) return
  
  // 立即关闭上传对话框
  showUploadDialog.value = false
  
  // 获取文件对象列表
  const filesToUpload = uploadFileList.value.map(item => item.raw || item)
  
  // 将文件加入上传队列
  docStore.addToUploadQueue(filesToUpload)
  
  // 清空选文件列表
  uploadFileList.value = []
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  
  // 开始异步上传处理
  startBackgroundUpload()
}

const goChat = (row) => {
  if (row.uploadStatus !== 'parsed') {
    ElMessage.warning('该文档尚未成功提取，无法关联AI对话')
    return
  }
  router.push({ path: '/ai-chat', query: { docId: row.id, docName: row.fileName } })
}

const downloadDoc = async (row) => {
  try {
    const res = await downloadSourceDocument(row.id)
    downloadBlob(new Blob([res.data]), row.fileName)
    ElMessage.success('下载成功')
  } catch (e) {
    ElMessage.error('下载失败，请重试')
  }
}

const batchDownload = async () => {
  if (selectedIds.value.length === 0) return
  const selectedDocs = documents.value.filter(d => selectedIds.value.includes(d.id))
  let success = 0
  let fail = 0
  for (const doc of selectedDocs) {
    try {
      const res = await downloadSourceDocument(doc.id)
      downloadBlob(new Blob([res.data]), doc.fileName)
      success++
    } catch (e) {
      fail++
    }
  }
  if (success > 0) ElMessage.success(`成功下载 ${success} 个文档`)
  if (fail > 0) ElMessage.warning(`${fail} 个文档下载失败`)
}

// 后台异步上传处理
const startBackgroundUpload = async () => {
  if (docStore.uploadQueue.length === 0) return
  
  docStore.setUploading(true)
  let successCount = 0
  let failCount = 0
  // 只处理还未开始的队列项，跳过已完成/已失败/已取消的
  const uploadIds = docStore.uploadQueue
    .filter(item => item.status === 'pending')
    .map(item => item.id)
  
  // 并发上传所有文件（或逐个上传，取决于后端能力）
  for (const uploadId of uploadIds) {
    // 跳过已取消或已完成的项
    const queueItem = docStore.uploadQueue.find(q => q.id === uploadId)
    if (!queueItem || queueItem.status === 'cancelled') continue
    
    // 更新状态为上传中
    docStore.updateUploadStatus(uploadId, 'uploading')
    docStore.updateUploadTransferProgress(uploadId, 0)
    
    try {
      // 创建取消令牌
      let cancelFunc
      const cancelToken = new CancelToken((c) => {
        cancelFunc = c
      })
      
      // 在store中注册取消函数
      docStore.registerCancelToken(uploadId, { cancel: cancelFunc })
      
      // 上传文件
      await uploadSourceDocument(queueItem.fileItem, (progressEvent) => {
        if (progressEvent.total > 0) {
          const uploadPercent = Math.min(100, Math.round((progressEvent.loaded / progressEvent.total) * 100))
          docStore.updateUploadTransferProgress(uploadId, uploadPercent)

          // 上传达到100%后，进入提取阶段(50%-100%)的模拟增长
          if (uploadPercent >= 100) {
            docStore.setExtracting(uploadId)
            ensureExtractSimulation(uploadId)
          }
        }
      }, cancelToken)
      
      stopExtractSimulation(uploadId)
      await smoothFinishExtraction(uploadId)

      // xlsx 文件同时上传到 files 服务，便于 AI 对话模块按 fileId 访问
      if ((queueItem.fileItem?.name || '').toLowerCase().endsWith('.xlsx')) {
        try {
          await uploadExcelFile(queueItem.fileItem)
        } catch (e) {
          console.warn('同步上传到 files 服务失败:', e)
        }
      }

      docStore.updateUploadStatus(uploadId, 'success')
      successCount++
      ElMessage.success(`${queueItem.fileName} 上传成功`)
    } catch (e) {
      stopExtractSimulation(uploadId)
      if (e.message === '用户取消上传') {
        // 用户取消，状态已在cancelUpload中设置
        ElMessage.info(`${queueItem.fileName} 已取消`)
      } else {
        docStore.updateUploadStatus(uploadId, 'failed', e.message)
        failCount++
        console.error('上传失败:', queueItem.fileName, e)
        ElMessage.error(`${queueItem.fileName} 上传失败`)
      }
    }
  }
  
  docStore.setUploading(false)
  
  // 所有上传完成后刷新文档列表
  if (successCount > 0) {
    await docStore.fetchDocuments({ ...docStore.lastFilters, force: true })
    // 启动轮询等待后台异步提取完成
    startParsingPoll()
  }

  // 上传全部完成/失败后，自动隐藏进度浮窗（延迟2秒让用户看到结果）
  setTimeout(() => {
    const allDone = docStore.uploadQueue.every(
      item => item.status === 'success' || item.status === 'failed' || item.status === 'cancelled'
    )
    if (allDone) {
      docStore.clearUploadQueue()
    }
  }, 2000)
}

const ensureExtractSimulation = (uploadId) => {
  if (extractTimerMap.has(uploadId)) return

  const timer = window.setInterval(() => {
    const item = docStore.uploadQueue.find(q => q.id === uploadId)
    if (!item) {
      stopExtractSimulation(uploadId)
      return
    }
    if (item.status === 'cancelled' || item.status === 'failed' || item.status === 'success') {
      stopExtractSimulation(uploadId)
      return
    }

    // 提取阶段平滑推进，逐步逼近100%，但在响应返回前不提前到100
    const current = item.extractProgress || 0
    const next = Math.min(98, current + Math.max(1, Math.ceil((98 - current) * 0.08)))
    docStore.updateExtractionProgress(uploadId, next)
  }, 400)

  extractTimerMap.set(uploadId, timer)
}

const smoothFinishExtraction = async (uploadId) => {
  const item = docStore.uploadQueue.find(q => q.id === uploadId)
  if (!item || item.status === 'cancelled' || item.status === 'failed') return

  docStore.setExtracting(uploadId)

  await new Promise((resolve) => {
    let current = item.extractProgress || 0
    if (current >= 100) {
      resolve()
      return
    }

    const timer = window.setInterval(() => {
      const latest = docStore.uploadQueue.find(q => q.id === uploadId)
      if (!latest || latest.status === 'cancelled' || latest.status === 'failed') {
        clearInterval(timer)
        resolve()
        return
      }

      current = Math.min(100, current + (current < 90 ? 3 : 1))
      docStore.updateExtractionProgress(uploadId, current)

      if (current >= 100) {
        clearInterval(timer)
        resolve()
      }
    }, 35)
  })
}

const stopExtractSimulation = (uploadId) => {
  const timer = extractTimerMap.get(uploadId)
  if (timer) {
    clearInterval(timer)
    extractTimerMap.delete(uploadId)
  }
}

// 取消单个上传
const cancelUploadItem = (uploadId) => {
  stopExtractSimulation(uploadId)
  docStore.cancelUpload(uploadId)
  ElMessage.info('已取消上传')
}

// 清空上传队列
const clearUploadQueue = () => {
  for (const uploadId of extractTimerMap.keys()) {
    stopExtractSimulation(uploadId)
  }
  docStore.clearUploadQueue()
}

// 查看已提取的文档内容 -> 改为预览原文件
const viewContent = async (row) => {
  viewDocTitle.value = row.fileName
  viewDocContent.value = ''
  previewType.value = 'text'
  previewHtml.value = ''
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value)
    previewBlobUrl.value = ''
  }
  showContentDialog.value = true
  previewLoading.value = true

  try {
    const res = await downloadSourceDocument(row.id)
    const blob = new Blob([res.data])
    const ext = (row.fileType || '').toLowerCase()

    if (ext === 'txt') {
      const text = await blob.text()
      viewDocContent.value = text
      previewType.value = 'text'
    } else if (ext === 'md') {
      const text = await blob.text()
      viewDocContent.value = text
      previewType.value = 'markdown'
    } else if (ext === 'xlsx') {
      const arrayBuffer = await blob.arrayBuffer()
      const workbook = XLSX.read(arrayBuffer, { type: 'array' })
      let html = ''
      workbook.SheetNames.forEach(name => {
        const sheet = workbook.Sheets[name]
        html += `<h4 style="margin:12px 0 8px;color:#4F46E5;">${name}</h4>`
        html += XLSX.utils.sheet_to_html(sheet, { editable: false })
      })
      previewHtml.value = DOMPurify.sanitize(html)
      previewType.value = 'html'
    } else if (ext === 'docx') {
      const arrayBuffer = await blob.arrayBuffer()
      const result = await mammoth.convertToHtml({ arrayBuffer })
      previewHtml.value = DOMPurify.sanitize(result.value)
      previewType.value = 'html'
    } else {
      viewDocContent.value = '不支持该格式的预览，请下载后查看'
      previewType.value = 'text'
    }
  } catch (e) {
    viewDocContent.value = '文件预览加载失败，请尝试下载后查看'
    previewType.value = 'text'
  } finally {
    previewLoading.value = false
  }
}

const deleteDoc = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.fileName}」？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await docStore.deleteDocument(row.id)
  } catch (e) { /* cancelled */ }
}

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个文档？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '全部删除',
      cancelButtonText: '取消'
    })
    // Use the store action
    await docStore.batchDeleteDocuments(selectedIds.value)
    selectedIds.value = []
    // The list will refresh automatically
  } catch (e) { /* cancelled */ }
}

const formatSize = (size) => {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

const formatDate = (d) => d ? d.replace('T', ' ').substring(0, 16) : '-'

let parsingPollTimer = null

const startParsingPoll = () => {
  if (parsingPollTimer) return
  parsingPollTimer = window.setInterval(async () => {
    const hasParsing = documents.value?.some(d => d.uploadStatus === 'parsing')
    if (hasParsing) {
      await loadDocuments(true)
    } else {
      stopParsingPoll()
    }
  }, 5000)
}

const stopParsingPoll = () => {
  if (parsingPollTimer) {
    window.clearInterval(parsingPollTimer)
    parsingPollTimer = null
  }
}

onMounted(() => {
  loadDocuments()
  // 启动轮询检查解析中的文档
  startParsingPoll()
})

onBeforeUnmount(() => {
  stopParsingPoll()
  for (const uploadId of extractTimerMap.keys()) {
    stopExtractSimulation(uploadId)
  }
})
</script>

<style scoped>
.documents-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.table-wrapper {
  flex: 1;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(79, 70, 229, 0.12);
  border: 2px dashed var(--primary);
  color: var(--primary);
  font-size: 16px;
  font-weight: 600;
  pointer-events: none;
}

.table-wrapper :deep(.el-table) {
  flex: 1;
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.doc-icon {
  font-size: 24px;
  width: 32px;
  text-align: center;
  flex-shrink: 0;
}

.doc-name-wrap {
  min-width: 0;
}

.doc-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-preview {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.text-muted {
  color: var(--text-muted);
  font-size: 13px;
}

.table-row {
  cursor: default;
}

.pagination-wrap {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-light);
}

/* Upload dialog */
.upload-area {
  padding: 10px 0;
}

.upload-info {
  padding: 12px 0 0;
  font-size: 14px;
  color: var(--text-secondary);
}

/* Action buttons */
.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.action-buttons .el-button {
  padding: 6px 10px;
}
.preview-btn {
  color: #409eff !important;
  border-color: #a0cfff !important;
  background-color: #ecf5ff !important;
}
.preview-btn:hover {
  color: #fff !important;
  background-color: #409eff !important;
  border-color: #409eff !important;
}

/* Content dialog */
.content-result {
  min-height: 200px;
}

.content-section {
  margin-bottom: 16px;
}

.content-section h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.content-body {
  max-height: 80vh;
  overflow-y: auto;
}

.content-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
  background: var(--bg-base);
  padding: 20px;
  border-radius: var(--radius-md);
}

.content-markdown {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
  padding: 20px;
  max-height: 80vh;
  overflow-y: auto;
}

.content-markdown :deep(h1),
.content-markdown :deep(h2),
.content-markdown :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 700;
}

.content-markdown :deep(p) {
  margin-bottom: 8px;
}

.content-markdown :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.content-markdown :deep(pre) {
  background: #f6f8fa;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin-bottom: 12px;
}

.content-markdown :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 12px;
}

.content-markdown :deep(th),
.content-markdown :deep(td) {
  border: 1px solid var(--border-light);
  padding: 8px 12px;
  text-align: left;
}

.content-markdown :deep(blockquote) {
  border-left: 4px solid var(--primary);
  padding-left: 12px;
  color: var(--text-secondary);
  margin: 8px 0;
}

.content-markdown :deep(ul),
.content-markdown :deep(ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}

.content-html {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  padding: 20px;
  max-height: 70vh;
  overflow: auto;
}

.content-html :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 16px;
}

.content-html :deep(th),
.content-html :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 10px;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}

.content-html :deep(th) {
  background: #f5f5f5;
  font-weight: 600;
}

.content-html :deep(tr:nth-child(even)) {
  background: #fafafa;
}

.content-html :deep(p) {
  margin-bottom: 8px;
}

/* Upload Queue Styles */
.upload-queue-container {
  padding: 16px 24px;
  border: 1px solid var(--border-light);
  background: var(--bg-elevated);
  border-radius: var(--radius-md);
}

.queue-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.queue-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

.status-badge.uploading {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
}

.status-badge.completed {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.queue-count {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 400;
}

.queue-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: var(--bg-base);
  border-radius: 8px;
  border: 1px solid var(--border-light);
}

.item-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
}

.file-icon {
  font-size: 24px;
  color: var(--primary);
  flex-shrink: 0;
}

.item-details {
  min-width: 0;
}

.item-name {
  font-weight: 500;
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.item-size {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.item-progress {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-progress :deep(.el-progress) {
  flex: 1;
  min-width: 150px;
}

.progress-percent {
  font-size: 12px;
  color: var(--text-secondary);
  min-width: 45px;
  text-align: right;
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  min-width: 45px;
  text-align: right;
}

.status-text.success {
  color: #10B981;
}

.status-text.failed {
  color: #EF4444;
}

.status-text.cancelled {
  color: #F59E0B;
}

.cancel-btn {
  padding: 4px 8px !important;
  min-width: auto !important;
  color: #EF4444;
}

.cancel-btn:hover {
  color: #DC2626 !important;
}

</style>
