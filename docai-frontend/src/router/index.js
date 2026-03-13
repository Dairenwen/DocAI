import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', guest: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'DataAnalysis' }
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('../views/DocumentList.vue'),
        meta: { title: '文档管理', icon: 'FolderOpened' }
      },
      {
        path: 'autofill',
        name: 'AutoFill',
        component: () => import('../views/AutoFill.vue'),
        meta: { title: '智能填表', icon: 'Grid' }
      },
      {
        path: 'ai-chat',
        name: 'AIChat',
        component: () => import('../views/AIChat.vue'),
        meta: { title: 'AI 对话', icon: 'ChatDotRound' }
      },
      {
        path: 'ai-generate',
        name: 'AIGenerate',
        component: () => import('../views/AIGenerate.vue'),
        meta: { title: 'AI 写作', icon: 'EditPen' }
      },

    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.matched.some(r => r.meta.requiresAuth) && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    next('/')
  } else {
    next()
  }
})

export default router
