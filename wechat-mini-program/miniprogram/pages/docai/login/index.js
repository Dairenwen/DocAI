const api = require('../../../api/docai')

function getDefaultPrivacyDialog() {
  return {
    visible: false,
    scene: 'general',
    sceneText: '继续当前操作',
    referrer: '',
    agreeButtonId: 'docai-privacy-agree-btn',
  }
}

function getModeMeta(isLogin) {
  if (isLogin) {
    return {
      modeTitle: '欢迎回来',
      modeDesc: '使用 DocAI 账号进入工作台，继续上传资料、发起问答或进行智能填表。',
      submitText: '进入工作台',
      modeTip: '登录成功后会安全保存当前账号的会话信息。',
    }
  }

  return {
    modeTitle: '创建 DocAI 账号',
    modeDesc: '注册成功后会自动登录，并可直接进入小程序开始使用。',
    submitText: '注册并进入',
    modeTip: '请设置至少 6 位密码，注册成功后可直接开始使用。',
  }
}

Page({
  data: Object.assign(
    {
      isLogin: true,
      loading: false,
      username: '',
      password: '',
      confirmPassword: '',
      agreedToPolicies: false,
      privacyDialog: getDefaultPrivacyDialog(),
    },
    getModeMeta(true)
  ),

  onLoad() {
    const app = getApp()
    if (app && app.bindPrivacyDialog) {
      app.bindPrivacyDialog(this)
    }
  },

  onUnload() {
    const app = getApp()
    if (app && app.unbindPrivacyDialog) {
      app.unbindPrivacyDialog(this)
    }
  },

  onShow() {
    const token = wx.getStorageSync('token') || ''
    if (token) {
      wx.switchTab({ url: '/pages/docai/dashboard/index' })
    }
  },

  switchMode(e) {
    const isLogin = String(e.currentTarget.dataset.login) === 'true'
    this.setData(Object.assign({
      isLogin,
      password: '',
      confirmPassword: '',
    }, getModeMeta(isLogin)))
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value.trim() })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  onConfirmPasswordInput(e) {
    this.setData({ confirmPassword: e.detail.value })
  },

  togglePolicyAgreement() {
    this.setData({
      agreedToPolicies: !this.data.agreedToPolicies,
    })
  },

  openPrivacyPolicy() {
    const app = getApp()
    if (app && app.openPrivacyContract) {
      app.openPrivacyContract()
      return
    }

    wx.navigateTo({
      url: '/pages/legal/privacy-policy/index',
    })
  },

  openUserAgreement() {
    const app = getApp()
    if (app && app.openUserAgreement) {
      app.openUserAgreement()
      return
    }

    wx.navigateTo({
      url: '/pages/legal/user-agreement/index',
    })
  },

  handlePrivacyAgree(e) {
    const app = getApp()
    if (app && app.handlePrivacyAgree) {
      app.handlePrivacyAgree(e)
    }
  },

  handlePrivacyDisagree() {
    const app = getApp()
    if (app && app.handlePrivacyDisagree) {
      app.handlePrivacyDisagree()
    }
  },

  applyAuth(data) {
    const token = data.token || ''
    if (!token) {
      return false
    }

    const app = getApp()
    if (app && app.setAuth) {
      app.setAuth(token, {
        id: data.userId,
        username: data.userName || data.username || '',
        nickname: data.nickname || data.userName || data.username || '',
        email: data.email || '',
      })
    }
    return true
  },

  async executeSubmit(username, password) {
    if (this.data.isLogin) {
      const res = await api.authLogin({ username, password })
      const data = res.data || {}
      if (!this.applyAuth(data)) {
        throw new Error('登录失败，未获取到登录令牌')
      }
      wx.showToast({ title: '登录成功', icon: 'success' })
      wx.switchTab({ url: '/pages/docai/dashboard/index' })
      return
    }

    if (password.length < 6) {
      throw new Error('密码至少需要 6 位')
    }

    if (password !== this.data.confirmPassword) {
      throw new Error('两次输入的密码不一致')
    }

    const res = await api.authRegister({
      username,
      password,
    })
    const data = res.data || {}

    if (!this.applyAuth(data)) {
      throw new Error('注册成功，但未获取到登录令牌')
    }

    wx.showToast({ title: '注册成功', icon: 'success' })
    wx.switchTab({ url: '/pages/docai/dashboard/index' })
  },

  async submit() {
    if (this.data.loading) {
      return
    }

    const username = this.data.username
    const password = this.data.password

    if (!username || !password) {
      wx.showToast({ title: '请填写用户名和密码', icon: 'none' })
      return
    }

    if (!this.data.agreedToPolicies) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' })
      return
    }

    const app = getApp()
    this.setData({ loading: true })

    try {
      if (app && app.ensurePrivacyAuthorized) {
        await app.ensurePrivacyAuthorized('account-login', () => this.executeSubmit(username, password))
      } else {
        await this.executeSubmit(username, password)
      }
    } catch (err) {
      wx.showToast({
        title: (err && err.message) || '请求失败，请稍后重试',
        icon: 'none',
      })
    } finally {
      this.setData({ loading: false })
    }
  },
})
