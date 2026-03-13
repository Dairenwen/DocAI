<template>
  <div class="layout-wrapper">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <!-- Logo -->
      <div class="sidebar-logo" @click="$router.push('/dashboard')">
        <div class="logo-icon-wrap">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="2" width="20" height="20" rx="4" fill="url(#logoGrad)" />
            <path d="M7 8h10M7 12h7M7 16h10" stroke="white" stroke-width="1.5" stroke-linecap="round" />
            <defs>
              <linearGradient id="logoGrad" x1="2" y1="2" x2="22" y2="22">
                <stop stop-color="#667eea" /><stop offset="1" stop-color="#764ba2" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <transition name="fade">
          <div v-if="!isCollapsed" class="logo-text">
            <span class="logo-title">DocAI</span>
            <span class="logo-subtitle">智能文档处理</span>
          </div>
        </transition>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <div class="nav-icon">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <transition name="fade">
            <span v-if="!isCollapsed" class="nav-label">{{ item.label }}</span>
          </transition>
          <transition name="fade">
            <span v-if="!isCollapsed && item.badge" class="nav-badge">{{ item.badge }}</span>
          </transition>
        </router-link>
      </nav>

      <!-- 模块分隔 -->
      <!-- <div v-if="!isCollapsed" class="sidebar-section-title">三大核心模块</div>
      <nav class="sidebar-nav modules">
        <div v-for="mod in moduleInfo" :key="mod.id" class="module-tag" :class="mod.color">
          <div class="module-dot"></div>
          <span v-if="!isCollapsed">{{ mod.label }}</span>
        </div>
      </nav> -->

      <!-- 浮动字符动画区域 -->
      <div class="float-chars-area" v-if="!isCollapsed">
        <span
          v-for="fc in floatChars"
          :key="fc.id"
          class="float-char"
          :style="{
            left: fc.left,
            animationDelay: fc.delay,
            animationDuration: fc.duration,
            transform: `rotate(${fc.rotate}deg)`,
            fontSize: fc.size
          }"
        >{{ fc.char }}</span>
      </div>

      <!-- 折叠按钮 -->
      <div class="sidebar-footer">
        <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <el-icon :size="16">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <transition name="fade">
            <span v-if="!isCollapsed" class="collapse-label">收起侧栏</span>
          </transition>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-area">
      <!-- 顶栏 -->
      <header class="topbar glass">
        <div class="topbar-left">
          <h2 class="page-title">{{ currentTitle }}</h2>
          <div class="breadcrumb-info">
            <el-tag size="small" effect="plain" round>{{ currentModule }}</el-tag>
          </div>
        </div>
        <div class="topbar-right">
          <el-tooltip content="系统状态" placement="bottom">
            <div class="status-indicator online">
              <span class="status-dot"></span>
              <span class="status-text">服务在线</span>
            </div>
          </el-tooltip>
          <el-dropdown trigger="click">
            <div class="user-area">
              <el-avatar :size="34" class="user-avatar">
                <span style="font-size: 14px;">{{ avatarChar }}</span>
              </el-avatar>
              <span class="user-name">{{ nickname }}</span>
              <el-icon class="arrow"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openChangePasswordDialog">修改密码</el-dropdown-item>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-dialog v-model="changePwdVisible" title="修改密码" width="440px">
      <el-form :model="changePwdForm" :rules="changePwdRules" ref="changePwdFormRef" label-position="top">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="changePwdForm.currentPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="changePwdForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="changePwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="changePwdLoading" @click="submitChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand, CaretBottom } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword } from '../api'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)

const floatChars = [
  { id: 1, char: 'D', left: '10%', delay: '0s', duration: '4s', rotate: -15, size: '14px' },
  { id: 2, char: 'o', left: '30%', delay: '0.8s', duration: '3.5s', rotate: 10, size: '12px' },
  { id: 3, char: 'c', left: '55%', delay: '1.5s', duration: '4.2s', rotate: -8, size: '13px' },
  { id: 4, char: 'A', left: '75%', delay: '0.3s', duration: '3.8s', rotate: 20, size: '15px' },
  { id: 5, char: 'I', left: '90%', delay: '2s', duration: '4.5s', rotate: -12, size: '11px' },
  { id: 6, char: 'N', left: '20%', delay: '2.5s', duration: '3.6s', rotate: 5, size: '12px' },
  { id: 7, char: 'L', left: '45%', delay: '1s', duration: '4.1s', rotate: -20, size: '14px' },
  { id: 8, char: 'P', left: '65%', delay: '3s', duration: '3.9s', rotate: 15, size: '13px' },
  { id: 9, char: 'e', left: '85%', delay: '1.8s', duration: '4.3s', rotate: -6, size: '11px' },
  { id: 10, char: 'X', left: '5%', delay: '3.5s', duration: '3.7s', rotate: 25, size: '12px' },
]

const nickname = ref(localStorage.getItem('nickname') || '用户')
const avatarChar = computed(() => nickname.value?.charAt(0) || 'U')
const changePwdVisible = ref(false)
const changePwdLoading = ref(false)
const changePwdFormRef = ref(null)
const changePwdForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const changePwdRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== changePwdForm.value.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const menuItems = [
  { path: '/dashboard', label: '工作台', icon: 'DataAnalysis' },
  { path: '/documents', label: '文档管理', icon: 'FolderOpened' },
  { path: '/autofill', label: '智能填表', icon: 'Grid' },
  { path: '/ai-chat', label: 'AI 对话', icon: 'ChatDotRound' }
]

const moduleInfo = [
  { id: 1, label: '文档智能编辑交互', color: 'mod-blue' },
  { id: 2, label: '非结构化信息提取', color: 'mod-purple' },
  { id: 3, label: '表格自动数据填写', color: 'mod-green' }
]

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')

const titleMap = {
  '/dashboard': '工作台',
  '/documents': '文档管理',
  '/autofill': '智能填表',
  '/ai-chat': 'AI 智能对话'
}

const moduleMap = {
  '/dashboard': '系统总览',
  '/documents': '模块二：非结构化文档信息提取',
  '/autofill': '模块三：表格自动数据填写',
  '/ai-chat': '模块一：文档智能编辑交互'
}

const currentTitle = computed(() => titleMap[route.path] || 'DocAI')
const currentModule = computed(() => moduleMap[route.path] || '智能文档处理系统')

const openChangePasswordDialog = () => {
  changePwdVisible.value = true
}

const submitChangePassword = async () => {
  if (!changePwdFormRef.value) return
  const valid = await changePwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  changePwdLoading.value = true
  try {
    await changePassword(changePwdForm.value)
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    changePwdVisible.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
    router.push('/login')
  } finally {
    changePwdLoading.value = false
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
    ElMessage.success('已退出登录')
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  nickname.value = localStorage.getItem('nickname') || '用户'
})
</script>

<style scoped>
.layout-wrapper {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

/* ============ 侧边栏 ============ */
.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-white);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-slow);
  z-index: 20;
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed);
}

/* Logo */
.sidebar-logo {
  height: 72px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  flex-shrink: 0;
}

.sidebar-logo:hover {
  background: var(--primary-lighter);
}

.logo-icon-wrap {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.logo-subtitle {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
}

/* 导航 */
.sidebar-nav {
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all var(--transition-fast);
  position: relative;
  white-space: nowrap;
}

.nav-item:hover {
  background: var(--bg-base);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-lighter);
  color: var(--primary);
}

.nav-item.active .nav-icon {
  color: var(--primary);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--primary);
  border-radius: 0 4px 4px 0;
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-badge {
  margin-left: auto;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 600;
  background: var(--primary-gradient);
  color: white;
  border-radius: var(--radius-full);
}

/* 模块信息 */
.sidebar-section-title {
  padding: 16px 24px 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.sidebar-nav.modules {
  padding-top: 0;
}

.module-tag {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.module-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mod-blue .module-dot { background: #3B82F6; }
.mod-purple .module-dot { background: #8B5CF6; }
.mod-green .module-dot { background: #10B981; }

/* 折叠按钮 */
.sidebar-footer {
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--border-light);
  flex-shrink: 0;
}

.collapse-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--text-muted);
  transition: all var(--transition-fast);
  font-size: 13px;
}

.collapse-label {
  white-space: nowrap;
  font-weight: 500;
}

.collapse-btn:hover {
  background: var(--primary-lighter);
  color: var(--primary);
}

.collapsed .collapse-btn {
  justify-content: center;
  padding: 10px;
}

/* 浮动字符动画 */
.float-chars-area {
  position: relative;
  flex: 1;
  min-height: 60px;
  overflow: hidden;
  flex-shrink: 1;
  pointer-events: none;
}

.float-char {
  position: absolute;
  bottom: -20px;
  font-weight: 700;
  font-family: 'Consolas', 'Monaco', monospace;
  opacity: 0;
  animation: floatUp 4s ease-out infinite;
}

@keyframes floatUp {
  0% {
    opacity: 0;
    bottom: -20px;
    color: #4F46E5;
  }
  15% {
    opacity: 0.7;
    color: #4F46E5;
  }
  60% {
    opacity: 0.5;
    color: #9b96e3;
  }
  90% {
    opacity: 0.15;
    color: #e8e8ec;
  }
  100% {
    opacity: 0;
    bottom: 100%;
    color: #ffffff;
  }
}

/* ============ 主内容区 ============ */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* 顶栏 */
.topbar {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  flex-shrink: 0;
  z-index: 10;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
}

.status-indicator.online {
  background: #ECFDF5;
  color: #059669;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-dot 2s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.user-area:hover {
  background: var(--bg-base);
}

.user-avatar {
  background: var(--primary-gradient) !important;
  color: white;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.arrow {
  font-size: 12px;
  color: var(--text-muted);
}

/* 页面内容 */
.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  background: var(--bg-base);
}
</style>
