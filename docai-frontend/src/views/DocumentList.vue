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
              {{ row.uploadStatus || 'unknown' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip content="文件预览" placement="top">
                <el-button size="small" type="primary" plain round @click="viewContent(row)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="AI 对话" placement="top">
                <el-button size="small" type="success" plain round @click="goChat(row)">
                  <el-icon><ChatLineRound /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="下载" placement="top">
                <el-button size="small" type="warning" plain round @click="downloadDoc(row)">
                  <el-icon><Download /></el-icon>
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
          accept=".docx,.xlsx,.txt,.md,.pdf,.doc,.xls,.pptx,.ppt,.csv,.html"
          :file-list="uploadFileList"
        >
          <el-icon :size="48" class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 .docx / .xlsx / .txt / .md / .pdf / .doc / .pptx / .csv 等格式，单个文件不超过100MB</div>
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

    <!-- 查看提取内容弹窗 -->
    <el-dialog v-model="showContentDialog" title="文档内容预览" width="750px" top="5vh">
      <div class="content-result">
        <div class="content-section" v-if="viewDocTitle">
          <h4>{{ viewDocTitle }}</h4>
        </div>
        <div class="content-body">
          <pre class="content-text">{{ viewDocContent }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useDocumentStore } from '../store/documentStore'
import { uploadDocument, uploadExcelFile, getDocumentFields } from '../api'
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

const typeTagMap = { docx: 'primary', xlsx: 'success', txt: 'info', md: 'warning' }
const validExts = ['.docx', '.xlsx', '.txt', '.md']
const maxFileSize = 100 * 1024 * 1024
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
    ElMessage.error('文件不能超过100MB')
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
    ElMessage.warning(`已忽略 ${oversize} 个超过100MB的文件`)
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

const goChat = () => router.push('/ai-chat')

const downloadDoc = () => {
  ElMessage.info('源文档下载接口暂未开放，可在服务器文件存储中获取原文件')
}

// 后台异步上传处理
const startBackgroundUpload = async () => {
  if (docStore.uploadQueue.length === 0) return
  
  docStore.setUploading(true)
  let successCount = 0
  let failCount = 0
  const uploadIds = docStore.uploadQueue.map(item => item.id)
  
  // 并发上传所有文件（或逐个上传，取决于后端能力）
  for (const uploadId of uploadIds) {
    // 跳过已取消或已完成的项
    const queueItem = docStore.uploadQueue.find(q => q.id === uploadId)
    if (!queueItem || queueItem.status === 'cancelled') continue
    
    // 更新状态为上传中
    docStore.updateUploadStatus(uploadId, 'uploading')
    docStore.updateUploadTransferProgress(uploadId, 0)
    
    try {
      const formData = new FormData()
      formData.append('file', queueItem.fileItem)
      
      // 创建取消令牌
      let cancelFunc
      const cancelToken = new CancelToken((c) => {
        cancelFunc = c
      })
      
      // 在store中注册取消函数
      docStore.registerCancelToken(uploadId, { cancel: cancelFunc })
      
      // 上传文件
      await uploadDocument(formData, (progressEvent) => {
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
  }
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

// 查看已提取的文档内容
const viewContent = (row) => {
  viewDocTitle.value = row.fileName
  getDocumentFields(row.id).then((res) => {
    const fields = res.data || []
    if (fields.length === 0) {
      viewDocContent.value = row.docSummary || '暂无抽取结果'
    } else {
      viewDocContent.value = fields
        .map((f, idx) => `${idx + 1}. ${f.fieldName || f.fieldKey}: ${f.fieldValue || ''}`)
        .join('\n')
    }
    showContentDialog.value = true
  }).catch(() => {
    viewDocContent.value = row.docSummary || '暂无抽取结果'
    showContentDialog.value = true
  })
}

const deleteDoc = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.title}」？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    // Use the store action
    await docStore.deleteDocument(row.id)
    // The list will refresh automatically
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

onMounted(() => {
  // Initial load when component is mounted
  loadDocuments()
})

onBeforeUnmount(() => {
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
  max-height: 60vh;
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
