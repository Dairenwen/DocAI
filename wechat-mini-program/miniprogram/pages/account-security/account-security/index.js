const api = require('../../../api/docai')

Page({
  data: {
    updatedAt: '2026-04-05',
    responseItems: [
      '注销成功后会删除当前账号及其关联的文档、会话、填表结果和本地缓存。',
      '操作完成后会自动退出登录并返回登录页，已删除的数据无法恢复。',
      '若网络异常或服务端未完成处理，本次注销不会生效，你可以稍后重新发起。',
    ],
    submitting: false,
  },

  confirmCancelAccount() {
    wx.showModal({
      title: '注销账号',
      content: '确认后将直接删除当前账号及其关联数据，并自动退出登录。此操作不可恢复，是否继续？',
      confirmText: '确认注销',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm || this.data.submitting) {
          return
        }

        this.setData({ submitting: true })
        wx.showLoading({
          title: '正在注销',
          mask: true,
        })

        try {
          await api.deleteCurrentUserAccount()

          const app = getApp()
          const currentUserId = app && app.globalData ? app.globalData.currentUserId : ''

          if (app && app.clearCurrentUserBusinessCache) {
            app.clearCurrentUserBusinessCache(currentUserId, {
              mode: 'all',
            })
          }

          if (app && app.clearAuthState) {
            app.clearAuthState({
              lastLoginUserId: '',
            })
          } else if (app && app.clearAuth) {
            app.clearAuth()
          }

          wx.hideLoading()
          wx.showToast({
            title: '账号已注销',
            icon: 'success',
          })

          setTimeout(() => {
            wx.reLaunch({
              url: '/pages/docai/login/index',
            })
          }, 800)
        } catch (err) {
          wx.hideLoading()
          wx.showToast({
            title: (err && err.message) || '账号注销失败，请稍后重试',
            icon: 'none',
          })
        } finally {
          this.setData({ submitting: false })
        }
      },
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
})
