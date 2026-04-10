const api = require('../../../api/docai')
const { ensureLogin } = require('../../../utils/auth')

Page({
  data: {
    userName: '团队成员',
    userEmail: '',
    avatarText: '团',
  },

  onShow() {
    if (!ensureLogin()) {
      return
    }

    this.loadUser()
  },

  async loadUser() {
    const app = getApp()
    const appUser = (app && app.globalData && app.globalData.user) || null
    const cachedUser = wx.getStorageSync('user') || null
    const user = appUser || cachedUser || {}

    this.applyUser(user)

    try {
      const res = await api.getCurrentUser()
      const latestUser = res.data || {}
      const mergedUser = Object.assign({}, user, {
        id: latestUser.userId || latestUser.id || user.id || '',
        username: latestUser.username || latestUser.userName || user.username || '',
        userName: latestUser.userName || latestUser.username || user.userName || '',
        nickname: latestUser.nickname || latestUser.userName || latestUser.username || user.nickname || '',
        email: latestUser.email || user.email || '',
      })

      if (app && app.setAuth) {
        app.setAuth(wx.getStorageSync('token') || '', mergedUser)
      } else {
        wx.setStorageSync('user', mergedUser)
      }

      this.applyUser(mergedUser)
    } catch (err) {
      // keep cached profile when backend user info request fails
    }
  },

  applyUser(user) {
    const userName = user.nickname || user.username || user.userName || '团队成员'
    const userEmail = user.email || ''
    const avatarText = String(userName || '团').trim().slice(0, 1).toUpperCase() || '团'

    this.setData({
      userName,
      userEmail,
      avatarText,
    })
  },

  openAccountSecurity() {
    wx.navigateTo({
      url: '/pages/account-security/account-security/index',
    })
  },

  openCancelAccount() {
    this.openAccountSecurity()
  },

  openContact() {
    wx.navigateTo({
      url: '/pages/contact/contact/index',
    })
  },

  openAbout() {
    wx.navigateTo({
      url: '/pages/about/about/index',
    })
  },

  openUserAgreement() {
    wx.navigateTo({
      url: '/pages/legal/user-agreement/index',
    })
  },

  openPrivacyPolicy() {
    wx.navigateTo({
      url: '/pages/legal/privacy-policy/index',
    })
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确认退出当前账号并返回登录页吗？',
      success: (res) => {
        if (!res.confirm) {
          return
        }

        Promise.resolve(api.userLogout()).catch(() => null).finally(() => {
          const app = getApp()
          const currentUserId = app && app.globalData ? app.globalData.currentUserId : ''

          if (app && app.clearCurrentUserBusinessCache) {
            app.clearCurrentUserBusinessCache(currentUserId, {
              mode: 'sessionOnly',
            })
          }

          if (app && app.clearAuthState) {
            app.clearAuthState({
              lastLoginUserId: currentUserId,
            })
          } else if (app && app.clearAuth) {
            app.clearAuth()
          }
          wx.reLaunch({ url: '/pages/docai/login/index' })
        })
      },
    })
  },
})
