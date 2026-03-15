<template>
  <div class="auth-page" @mousemove="onMouseMove">
    <!-- Dynamic background -->
    <div class="auth-bg">
      <div class="bg-grid"></div>
      <div class="bg-glow" :style="glowStyle"></div>
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
      <div class="bg-shape shape-4"></div>
    </div>

    <!-- Floating keywords -->
    <div class="floating-words">
      <span class="fw" v-for="(w, i) in floatingWords" :key="i"
        :style="{
          animationDelay: w.delay, left: w.left, top: w.top, fontSize: w.size,
          opacity: getCharOpacity(w),
          color: getCharColor(w)
        }">
        {{ w.text }}
      </span>
    </div>

    <div class="auth-container">
      <!-- Left branding panel -->
      <div class="auth-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none">
              <rect x="2" y="2" width="20" height="20" rx="4" fill="url(#lGrad)" />
              <path d="M7 8h10M7 12h7M7 16h10" stroke="white" stroke-width="1.5" stroke-linecap="round" />
              <defs>
                <linearGradient id="lGrad" x1="2" y1="2" x2="22" y2="22">
                  <stop stop-color="#818CF8" /><stop offset="1" stop-color="#6366F1" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h1 class="brand-title">DocAI</h1>
          <p class="brand-slogan">智能文档处理系统</p>
          <p class="brand-slogan-cn">AI 驱动 · 高效办公 · 一键填表</p>
          <div class="brand-divider"></div>
          <div class="brand-features">
            <div class="bf-item">
              <div class="bf-dot"></div>
              <div class="bf-text">
                <span class="bf-main">AI 智能对话</span>
                <span class="bf-desc">基于大语言模型，与文档深度交互</span>
              </div>
            </div>
            <div class="bf-item">
              <div class="bf-dot"></div>
              <div class="bf-text">
                <span class="bf-main">文档信息提取</span>
                <span class="bf-desc">自动识别并提取关键字段与结构化数据</span>
              </div>
            </div>
            <div class="bf-item">
              <div class="bf-dot"></div>
              <div class="bf-text">
                <span class="bf-main">表格一键填写</span>
                <span class="bf-desc">智能匹配数据，批量填充模板并导出</span>
              </div>
            </div>
            <div class="bf-item">
              <div class="bf-dot"></div>
              <div class="bf-text">
                <span class="bf-main">智能写作生成</span>
                <span class="bf-desc">支持通知、报告、请示等公文一键生成</span>
              </div>
            </div>
            <div class="bf-item">
              <div class="bf-dot"></div>
              <div class="bf-text">
                <span class="bf-main">多格式兼容</span>
                <span class="bf-desc">支持 Word、Excel、TXT、Markdown 等格式</span>
              </div>
            </div>
          </div>
          <div class="brand-tech">
            <span class="tech-tag">大语言模型</span>
            <span class="tech-tag">微服务架构</span>
            <span class="tech-tag">智能办公</span>
          </div>
        </div>
      </div>

      <!-- Right form panel -->
      <div class="auth-form-panel">
        <div class="form-header">
          <h2>{{ isLogin ? '欢迎回来' : '创建账户' }}</h2>
          <p>{{ isLogin ? '登录您的账户，开始智能文档处理' : '创建新账户，开始智能文档处理之旅' }}</p>
        </div>

        <!-- Login form -->
        <div v-if="isLogin" class="auth-form">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" @submit.prevent="handleLogin">
            <div class="form-label">用户名</div>
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                prefix-icon="User"
                @keydown.enter="handleLogin"
              />
            </el-form-item>
            <div class="form-label">密码</div>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                prefix-icon="Lock"
                show-password
                @keydown.enter="handleLogin"
              />
            </el-form-item>
            <el-button
              type="primary"
              size="large"
              class="auth-submit-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form>
          <div class="extra-actions">
            <a @click="openEmailAuthDialog">邮箱验证码登录/注册</a>
            <a @click="openResetDialog">忘记密码</a>
          </div>
          <div class="auth-switch">
            <span>还没有账户？</span>
            <a @click="isLogin = false">立即注册</a>
          </div>
        </div>

        <!-- Register form -->
        <div v-else class="auth-form">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" @submit.prevent="handleRegister">
            <div class="form-label">用户名 <span class="form-hint">（3-20位）</span></div>
            <el-form-item prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                size="large"
                prefix-icon="User"
              />
            </el-form-item>
            <div class="form-label">昵称 <span class="form-hint">（选填）</span></div>
            <el-form-item prop="nickname">
              <el-input
                v-model="registerForm.nickname"
                placeholder="请输入昵称(可选)"
                size="large"
                prefix-icon="UserFilled"
              />
            </el-form-item>
            <div class="form-label">密码 <span class="form-hint">（至少6位）</span></div>
            <el-form-item prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <div class="form-label">确认密码</div>
            <el-form-item prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                size="large"
                prefix-icon="Lock"
                show-password
                @keydown.enter="handleRegister"
              />
            </el-form-item>
            <el-button
              type="primary"
              size="large"
              class="auth-submit-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form>
          <div class="auth-switch">
            <span>已有账户？</span>
            <a @click="isLogin = true">立即登录</a>
          </div>
          <div class="extra-actions">
            <a @click="openEmailAuthDialog">邮箱验证码注册/登录</a>
          </div>
        </div>

        <div class="auth-footer">
          <span>DocAI — 智能文档处理系统</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="emailAuthDialogVisible" title="邮箱验证码登录/注册" width="460px">
      <el-form :model="emailAuthForm" :rules="emailAuthRules" ref="emailAuthFormRef" label-position="top">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="emailAuthForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="验证码" prop="verificationCode">
          <div class="verify-row">
            <el-input v-model="emailAuthForm.verificationCode" placeholder="请输入验证码" />
            <el-button :disabled="codeSending || codeCountDown > 0" @click="sendCodeForEmailAuth">
              {{ codeCountDown > 0 ? `${codeCountDown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="注册用户名（可选）" prop="username">
          <el-input v-model="emailAuthForm.username" placeholder="新用户可填写用户名，不填则系统生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailAuthDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleEmailAuth">确认登录/注册</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialogVisible" title="忘记密码（邮箱重置）" width="460px">
      <el-form :model="resetForm" :rules="resetRules" ref="resetFormRef" label-position="top">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="resetForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="验证码" prop="verificationCode">
          <div class="verify-row">
            <el-input v-model="resetForm.verificationCode" placeholder="请输入验证码" />
            <el-button :disabled="codeSending || codeCountDown > 0" @click="sendCodeForReset">
              {{ codeCountDown > 0 ? `${codeCountDown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleResetPassword">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { authLogin, authRegister, authByEmailCode, sendVerificationCode, resetPasswordByEmail } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const isLogin = ref(true)
const loading = ref(false)
const codeSending = ref(false)
const codeCountDown = ref(0)
let codeTimer = null
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const emailAuthFormRef = ref(null)
const resetFormRef = ref(null)
const emailAuthDialogVisible = ref(false)
const resetDialogVisible = ref(false)

// Mouse glow effect
const mouseX = ref(50)
const mouseY = ref(50)
const glowStyle = computed(() => ({
  background: `radial-gradient(600px circle at ${mouseX.value}px ${mouseY.value}px, rgba(0, 0, 0, 0.18), transparent 60%)`
}))

const onMouseMove = (e) => {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
}

// Floating bilingual characters - many more spread across background
const floatingWords = [
  { text: '智', delay: '0s', left: '3%', top: '8%', size: '22px', opacity: 0.14 },
  { text: '能', delay: '1s', left: '80%', top: '6%', size: '20px', opacity: 0.13 },
  { text: '文', delay: '2s', left: '15%', top: '85%', size: '21px', opacity: 0.12 },
  { text: '档', delay: '3s', left: '70%', top: '82%', size: '20px', opacity: 0.14 },
  { text: '处', delay: '0.5s', left: '92%', top: '45%', size: '19px', opacity: 0.11 },
  { text: '理', delay: '1.5s', left: '2%', top: '50%', size: '20px', opacity: 0.13 },
  { text: '抽', delay: '2.5s', left: '60%', top: '92%', size: '18px', opacity: 0.10 },
  { text: '取', delay: '4s', left: '93%', top: '18%', size: '20px', opacity: 0.12 },
  { text: '分', delay: '3.5s', left: '25%', top: '95%', size: '19px', opacity: 0.11 },
  { text: '析', delay: '1.2s', left: '50%', top: '3%', size: '18px', opacity: 0.10 },
  { text: '数', delay: '0.8s', left: '38%', top: '12%', size: '19px', opacity: 0.12 },
  { text: '据', delay: '2.2s', left: '88%', top: '70%', size: '18px', opacity: 0.10 },
  { text: '填', delay: '1.8s', left: '8%', top: '30%', size: '22px', opacity: 0.13 },
  { text: '表', delay: '3.2s', left: '45%', top: '75%', size: '19px', opacity: 0.11 },
  { text: '模', delay: '0.3s', left: '72%', top: '25%', size: '20px', opacity: 0.12 },
  { text: '板', delay: '2.8s', left: '55%', top: '15%', size: '18px', opacity: 0.11 },
  { text: '提', delay: '1.6s', left: '18%', top: '68%', size: '21px', opacity: 0.14 },
  { text: '交', delay: '3.8s', left: '85%', top: '55%', size: '17px', opacity: 0.10 },
  { text: '编', delay: '0.6s', left: '32%', top: '40%', size: '20px', opacity: 0.13 },
  { text: '辑', delay: '4.2s', left: '95%', top: '88%', size: '18px', opacity: 0.09 },
  { text: '识', delay: '2.0s', left: '5%', top: '75%', size: '21px', opacity: 0.14 },
  { text: '别', delay: '3.0s', left: '62%', top: '58%', size: '18px', opacity: 0.10 },
  { text: '匹', delay: '1.0s', left: '78%', top: '38%', size: '19px', opacity: 0.12 },
  { text: '配', delay: '4.5s', left: '42%', top: '48%', size: '17px', opacity: 0.09 },
  { text: '写', delay: '0.9s', left: '28%', top: '22%', size: '20px', opacity: 0.11 },
  { text: '作', delay: '3.6s', left: '68%', top: '65%', size: '18px', opacity: 0.10 },
  { text: '生', delay: '2.4s', left: '12%', top: '58%', size: '20px', opacity: 0.13 },
  { text: '成', delay: '1.4s', left: '52%', top: '88%', size: '19px', opacity: 0.11 },
  { text: '润', delay: '4.0s', left: '90%', top: '32%', size: '20px', opacity: 0.12 },
  { text: '色', delay: '0.2s', left: '35%', top: '55%', size: '17px', opacity: 0.09 },
  { text: '公', delay: '1.3s', left: '10%', top: '15%', size: '19px', opacity: 0.11 },
  { text: '办', delay: '2.6s', left: '65%', top: '10%', size: '18px', opacity: 0.10 },
  { text: '自', delay: '0.7s', left: '82%', top: '78%', size: '20px', opacity: 0.12 },
  { text: '动', delay: '3.3s', left: '20%', top: '45%', size: '19px', opacity: 0.11 },
  { text: '解', delay: '1.9s', left: '48%', top: '35%', size: '18px', opacity: 0.10 },
  { text: '构', delay: '4.3s', left: '75%', top: '50%', size: '17px', opacity: 0.09 },
  { text: '导', delay: '2.1s', left: '58%', top: '72%', size: '20px', opacity: 0.12 },
  { text: '出', delay: '0.4s', left: '40%', top: '62%', size: '18px', opacity: 0.10 },
  { text: '语', delay: '3.9s', left: '7%', top: '90%', size: '19px', opacity: 0.11 },
  { text: '义', delay: '1.7s', left: '87%', top: '15%', size: '17px', opacity: 0.09 },
]

// Proximity-based character color/opacity: chars near mouse glow brighter and change color
const getCharOpacity = (w) => {
  const el = document.querySelector('.auth-page')
  if (!el) return w.opacity
  const rect = el.getBoundingClientRect()
  const charX = (parseFloat(w.left) / 100) * rect.width
  const charY = (parseFloat(w.top) / 100) * rect.height
  const dx = mouseX.value - rect.left - charX
  const dy = mouseY.value - rect.top - charY
  const dist = Math.sqrt(dx * dx + dy * dy)
  const radius = 220
  if (dist < radius) {
    return Math.min(0.95, w.opacity + (1 - dist / radius) * 0.8)
  }
  return w.opacity
}

const getCharColor = (w) => {
  const el = document.querySelector('.auth-page')
  if (!el) return 'rgba(0, 0, 0, 0.35)'
  const rect = el.getBoundingClientRect()
  const charX = (parseFloat(w.left) / 100) * rect.width
  const charY = (parseFloat(w.top) / 100) * rect.height
  const dx = mouseX.value - rect.left - charX
  const dy = mouseY.value - rect.top - charY
  const dist = Math.sqrt(dx * dx + dy * dy)
  const radius = 220
  if (dist < radius) {
    const t = 1 - dist / radius
    // Transition from rgba(0,0,0,0.35) to a vivid indigo #4F46E5
    const r = Math.round(0 + t * 79)
    const g = Math.round(0 + t * 70)
    const b = Math.round(0 + t * 229)
    const a = 0.35 + t * 0.65
    return `rgba(${r}, ${g}, ${b}, ${a})`
  }
  return 'rgba(0, 0, 0, 0.35)'
}

const loginForm = reactive({ username: '', password: '' })

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const emailAuthForm = reactive({
  email: '',
  verificationCode: '',
  username: ''
})

const resetForm = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需为3-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const emailAuthRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  verificationCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const resetRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  verificationCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const startCodeCountDown = () => {
  codeCountDown.value = 60
  if (codeTimer) clearInterval(codeTimer)
  codeTimer = setInterval(() => {
    codeCountDown.value -= 1
    if (codeCountDown.value <= 0) {
      clearInterval(codeTimer)
      codeTimer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  if (codeTimer) {
    clearInterval(codeTimer)
    codeTimer = null
  }
})

const sendCode = async (email) => {
  if (!email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  codeSending.value = true
  try {
    const res = await sendVerificationCode(email)
    if (res.data?.deliveryMode === 'noop') {
      ElMessage.warning('验证码流程已触发，但当前 SMTP 未配置，邮件不会真正发出')
    } else {
      ElMessage.success('验证码已发送，请注意查收邮箱')
    }
    startCodeCountDown()
  } finally {
    codeSending.value = false
  }
}

const sendCodeForEmailAuth = () => sendCode(emailAuthForm.email)
const sendCodeForReset = () => sendCode(resetForm.email)

const openEmailAuthDialog = () => {
  emailAuthDialogVisible.value = true
}

const openResetDialog = () => {
  resetDialogVisible.value = true
}

const handleEmailAuth = async () => {
  if (!emailAuthFormRef.value) return
  const valid = await emailAuthFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authByEmailCode({
      email: emailAuthForm.email,
      verificationCode: emailAuthForm.verificationCode,
      username: emailAuthForm.username || undefined
    })
    const data = res.data
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('username', data.userName)
    localStorage.setItem('nickname', data.userName)
    ElMessage.success('邮箱登录成功，欢迎 ' + (data.userName || '用户'))
    emailAuthDialogVisible.value = false
    router.push('/')
  } finally {
    loading.value = false
  }
}

const handleResetPassword = async () => {
  if (!resetFormRef.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await resetPasswordByEmail({
      email: resetForm.email,
      verificationCode: resetForm.verificationCode,
      newPassword: resetForm.newPassword,
      confirmPassword: resetForm.confirmPassword
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    resetDialogVisible.value = false
    isLogin.value = true
    loginForm.username = resetForm.email
    loginForm.password = ''
  } finally {
    loading.value = false
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authLogin({
      username: loginForm.username,
      password: loginForm.password
    })
    const data = res.data
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('username', data.userName)
    localStorage.setItem('nickname', data.userName)

    ElMessage.success('登录成功，欢迎 ' + (data.userName || '用户'))
    router.push('/')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authRegister({
      username: registerForm.username,
      password: registerForm.password
    })
    ElMessage.success('注册并登录能力已开通，使用账号密码直接登录即可')
    isLogin.value = true
    loginForm.username = registerForm.username
    loginForm.password = ''
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.extra-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.extra-actions a {
  color: #4f46e5;
  cursor: pointer;
}

.extra-actions a:hover {
  text-decoration: underline;
}

.verify-row {
  width: 100%;
  display: flex;
  gap: 8px;
}

.verify-row .el-input {
  flex: 1;
}

.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  position: relative;
  overflow: hidden;
}

/* Dynamic background */
.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

.bg-glow {
  position: absolute;
  inset: 0;
  transition: background 0.3s ease;
  pointer-events: none;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
}

.shape-1 {
  width: 500px;
  height: 500px;
  top: -200px;
  right: -100px;
  background: rgba(79, 70, 229, 0.06);
  animation: float-slow 20s infinite ease-in-out;
}

.shape-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  left: -100px;
  background: rgba(139, 92, 246, 0.05);
  animation: float-slow 25s infinite ease-in-out reverse;
}

.shape-3 {
  width: 300px;
  height: 300px;
  top: 40%;
  left: 40%;
  background: rgba(79, 70, 229, 0.04);
  animation: float-slow 18s infinite ease-in-out;
}

.shape-4 {
  width: 200px;
  height: 200px;
  top: 20%;
  left: 15%;
  background: rgba(124, 58, 237, 0.03);
  animation: float-slow 22s infinite ease-in-out reverse;
}

@keyframes float-slow {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

/* Floating words */
.floating-words {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.fw {
  position: absolute;
  color: rgba(0, 0, 0, 0.35);
  font-weight: 600;
  letter-spacing: 0.1em;
  animation: fw-drift 30s infinite linear;
  transition: color 0.15s ease, opacity 0.15s ease, text-shadow 0.15s ease;
  will-change: color, opacity;
  text-shadow: none;
}

@keyframes fw-drift {
  0% { transform: translateY(0); }
  50% { transform: translateY(-20px); }
  100% { transform: translateY(0); }
}

/* Main container */
.auth-container {
  display: flex;
  width: 900px;
  max-width: 95vw;
  min-height: 480px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  overflow: hidden;
  position: relative;
  z-index: 1;
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.08), 0 0 0 1px rgba(0, 0, 0, 0.03);
}

/* Left brand panel */
.auth-brand {
  width: 380px;
  background: linear-gradient(160deg, #f8f7ff 0%, #f0eeff 100%);
  border-right: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
  overflow: hidden;
}

.auth-brand::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 40%, rgba(79, 70, 229, 0.04) 0%, transparent 50%);
}

.brand-content {
  position: relative;
  z-index: 1;
}

.brand-logo {
  width: 56px;
  height: 56px;
  background: rgba(79, 70, 229, 0.08);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  border: 1px solid rgba(79, 70, 229, 0.12);
}

.brand-title {
  font-size: 32px;
  font-weight: 800;
  color: #1a1a2e;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.brand-slogan {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 4px;
  letter-spacing: 0.02em;
}

.brand-slogan-cn {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 24px;
}

.brand-divider {
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, #4F46E5, transparent);
  margin-bottom: 24px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 28px;
}

.bf-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  transition: all 0.3s;
  cursor: default;
}

.bf-item:hover {
  transform: translateX(4px);
}

.bf-item:hover .bf-dot {
  background: #4F46E5;
  box-shadow: 0 0 10px rgba(79, 70, 229, 0.3);
}

.bf-item:hover .bf-main {
  color: #1a1a2e;
}

.bf-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(79, 70, 229, 0.3);
  flex-shrink: 0;
  transition: all 0.3s;
  margin-top: 6px;
}

.bf-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bf-main {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
  transition: color 0.3s;
}

.bf-desc {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.4;
}

.brand-tech {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tech-tag {
  padding: 3px 10px;
  font-size: 10px;
  color: #6b7280;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  font-weight: 500;
  letter-spacing: 0.03em;
}

/* Right form panel */
.auth-form-panel {
  flex: 1;
  padding: 28px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header {
  margin-bottom: 16px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 6px;
  letter-spacing: -0.02em;
}

.form-header p {
  font-size: 13px;
  color: #9ca3af;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.form-hint {
  font-weight: 400;
  color: #d1d5db;
}

.auth-form {
  margin-bottom: 10px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.auth-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 12px;
  background: #f9fafb !important;
  border: 1px solid #e5e7eb;
  box-shadow: none !important;
  transition: all 0.3s;
}

.auth-form :deep(.el-input__wrapper:hover) {
  border-color: #c7c3f0;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  border-color: #4F46E5 !important;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.08) !important;
  background: #ffffff !important;
}

.auth-form :deep(.el-input__inner) {
  color: #1a1a2e !important;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: #c7c3f0 !important;
}

.auth-form :deep(.el-input__prefix .el-icon) {
  color: #9ca3af;
}

.auth-form :deep(.el-input__suffix .el-icon) {
  color: #9ca3af;
}

.auth-submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  margin-top: 4px;
  background: linear-gradient(135deg, #4F46E5, #7C3AED) !important;
  border: none !important;
  color: white !important;
  letter-spacing: 0.03em;
  transition: all 0.3s !important;
}

.auth-submit-btn:hover {
  background: linear-gradient(135deg, #4338CA, #6D28D9) !important;
  box-shadow: 0 4px 20px rgba(79, 70, 229, 0.3);
  transform: translateY(-1px);
}

.auth-switch {
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.auth-switch a {
  color: #4F46E5;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s;
}

.auth-switch a:hover {
  color: #7C3AED;
  text-decoration: underline;
}

.auth-footer {
  text-align: center;
  margin-top: auto;
  padding-top: 16px;
  font-size: 11px;
  color: #d1d5db;
  letter-spacing: 0.05em;
}

/* Responsive */
@media (max-width: 768px) {
  .auth-container {
    flex-direction: column;
    width: 95vw;
    min-height: auto;
  }
  .auth-brand {
    width: 100%;
    padding: 30px;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }
  .brand-features { display: none; }
  .auth-form-panel {
    padding: 30px;
  }
}
</style>
