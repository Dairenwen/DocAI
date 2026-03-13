import { defineStore } from 'pinia'
import { getDocuments, deleteDocument as apiDeleteDocument, batchDeleteDocuments as apiBatchDelete } from '../api'
import { ElMessage } from 'element-plus'

const CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

// 存储所有上传请求的取消令牌
const uploadCancelTokens = new Map()

export const useDocumentStore = defineStore('documents', {
  state: () => ({
    allDocuments: [],
    documents: [],
    total: 0,
    loading: false,
    lastLoaded: 0, // Timestamp of last successful load
    // Store filter/pagination state to detect if a full reload is needed
    lastFilters: {
      page: 1,
      size: 20,
      keyword: '',
      fileType: ''
    },
    // 上传队列相关状态
    uploadQueue: [], // 上传队列：{ id, fileName, fileSize, progress, status, fileItem }
    isUploading: false,
    uploadCancelTokens: uploadCancelTokens
  }),

  getters: {
    docCount: (state) => state.total,
    extractedCount: (state) => {
      return state.allDocuments.filter(d => d.uploadStatus === 'parsed').length
    },
    isCacheValid: (state) => {
      return state.lastLoaded > 0 && (Date.now() - state.lastLoaded < CACHE_DURATION)
    }
  },

  actions: {
    // The main action to fetch documents with caching logic
    async fetchDocuments(filters = {}) {
      const { force = false, ...filterParams } = filters
      const params = {
        page: filterParams.page || 1,
        size: filterParams.size || 20,
        keyword: filterParams.keyword || '',
        fileType: filterParams.fileType || ''
      }

      // Check if we can use cache
      const isNewQuery = JSON.stringify(params) !== JSON.stringify(this.lastFilters)
      if (!force && this.isCacheValid && !isNewQuery) {
        console.log('Using cached documents.')
        return
      }

      this.loading = true
      try {
        const res = await getDocuments()
        let docs = res.data || []

        if (params.keyword) {
          const keyword = params.keyword.trim().toLowerCase()
          docs = docs.filter(d => (d.fileName || '').toLowerCase().includes(keyword))
        }

        if (params.fileType) {
          docs = docs.filter(d => d.fileType === params.fileType)
        }

        docs.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())

        const start = (params.page - 1) * params.size
        const end = start + params.size

        this.allDocuments = docs
        this.total = docs.length
        this.documents = docs.slice(start, end)
        this.lastLoaded = Date.now()
        this.lastFilters = params
        console.log('Fetched and updated documents.')
      } catch (e) {
        ElMessage.error('加载文档列表失败')
        // In case of error, invalidate cache to allow retry
        this.lastLoaded = 0
      } finally {
        this.loading = false
      }
    },

    // Action to delete a single document with optimistic UI
    async deleteDocument(docId) {
      const docIndex = this.documents.findIndex(d => d.id === docId)
      if (docIndex === -1) return

      // Optimistically remove the document from the state
      const removedDoc = this.documents.splice(docIndex, 1)[0]
      this.total -= 1

      try {
        await apiDeleteDocument(docId)
        ElMessage.success('删除成功')
      } catch (e) {
        this.documents.splice(docIndex, 0, removedDoc)
        this.total += 1
        ElMessage.error('删除失败，请重试')
        throw e // Re-throw for the component to know about the failure if needed
      }
    },

    // Action to delete multiple documents with optimistic UI
    async batchDeleteDocuments(docIds) {
      const originalDocs = [...this.documents]
      const removedDocs = []
      
      // Optimistically remove documents
      this.documents = this.documents.filter(doc => {
        if (docIds.includes(doc.id)) {
          removedDocs.push(doc)
          return false
        }
        return true
      })
      const removedCount = removedDocs.length
      this.total -= removedCount

      try {
        await apiBatchDelete(docIds)
        ElMessage.success(`成功删除 ${removedCount} 个文档`)
      } catch (e) {
        this.documents = originalDocs
        this.total += removedCount
        ElMessage.error('批量删除失败，请重试')
        throw e
      }
    },

    // Call this after a successful upload to refresh the list
    async handleUploadSuccess() {
      ElMessage.success('上传成功，正在刷新列表...')
      await this.fetchDocuments({ ...this.lastFilters, force: true })
    },

    // Invalidate cache manually
    invalidateCache() {
      this.lastLoaded = 0
      console.log('Document cache invalidated.')
    },

    // ==================== 上传队列管理 ====================
    
    // 添加文件到上传队列
    addToUploadQueue(fileItems) {
      const newItems = fileItems.map((fileItem, index) => ({
        id: `upload_${Date.now()}_${index}`,
        fileName: fileItem.name,
        fileSize: fileItem.size,
        progress: 0,
        uploadProgress: 0,
        extractProgress: 0,
        phase: 'pending', // pending, uploading, extracting, completed, failed, cancelled
        status: 'pending', // pending, uploading, success, failed, cancelled
        fileItem: fileItem,
        error: null
      }))
      this.uploadQueue.push(...newItems)
      return newItems.map(item => item.id)
    },

    // 更新第1阶段(0-50%)上传进度
    updateUploadTransferProgress(uploadId, progress) {
      const item = this.uploadQueue.find(q => q.id === uploadId)
      if (item) {
        const safeProgress = Math.min(100, Math.max(0, progress))
        item.uploadProgress = safeProgress
        item.phase = 'uploading'
        item.progress = Math.round((item.uploadProgress * 0.5) + (item.extractProgress * 0.5))
      }
    },

    // 切换到第2阶段(50-100%)提取进度
    setExtracting(uploadId) {
      const item = this.uploadQueue.find(q => q.id === uploadId)
      if (item) {
        item.phase = 'extracting'
        if (item.uploadProgress < 100) item.uploadProgress = 100
        if (item.progress < 50) item.progress = 50
      }
    },

    // 更新第2阶段(50-100%)提取进度
    updateExtractionProgress(uploadId, progress) {
      const item = this.uploadQueue.find(q => q.id === uploadId)
      if (item) {
        const safeProgress = Math.min(100, Math.max(0, progress))
        item.extractProgress = safeProgress
        item.phase = 'extracting'
        item.progress = Math.round((item.uploadProgress * 0.5) + (item.extractProgress * 0.5))
      }
    },

    // 更新上传状态
    updateUploadStatus(uploadId, status, error = null) {
      const item = this.uploadQueue.find(q => q.id === uploadId)
      if (item) {
        item.status = status
        if (error) item.error = error
        if (status === 'success') {
          item.phase = 'completed'
          item.uploadProgress = 100
          item.extractProgress = 100
          item.progress = 100
        } else if (status === 'failed') {
          item.phase = 'failed'
        } else if (status === 'cancelled') {
          item.phase = 'cancelled'
        }
      }
    },

    // 取消单个上传
    cancelUpload(uploadId) {
      const item = this.uploadQueue.find(q => q.id === uploadId)
      if (item) {
        item.status = 'cancelled'
        item.phase = 'cancelled'
        // 取消axios请求
        const cancelToken = this.uploadCancelTokens.get(uploadId)
        if (cancelToken) {
          cancelToken.cancel('用户取消上传')
          this.uploadCancelTokens.delete(uploadId)
        }
      }
    },

    // 取消所有上传
    cancelAllUploads() {
      this.uploadQueue.forEach(item => {
        if (item.status === 'uploading' || item.status === 'pending') {
          item.status = 'cancelled'
          item.phase = 'cancelled'
          const cancelToken = this.uploadCancelTokens.get(item.id)
          if (cancelToken) {
            cancelToken.cancel('用户取消上传')
            this.uploadCancelTokens.delete(item.id)
          }
        }
      })
    },

    // 清空上传队列
    clearUploadQueue() {
      // 先取消所有正在进行的上传
      this.cancelAllUploads()
      // 清空队列
      this.uploadQueue = []
      // 清空所有取消令牌
      this.uploadCancelTokens.clear()
      this.isUploading = false
    },

    // 移除已完成/失败的上传项
    removeUploadItem(uploadId) {
      const index = this.uploadQueue.findIndex(q => q.id === uploadId)
      if (index > -1) {
        this.uploadQueue.splice(index, 1)
      }
      this.uploadCancelTokens.delete(uploadId)
    },

    // 将上传状态设为上传中
    setUploading(value) {
      this.isUploading = value
    },

    // 注册上传取消令牌
    registerCancelToken(uploadId, cancelToken) {
      this.uploadCancelTokens.set(uploadId, cancelToken)
    }
  }
})
