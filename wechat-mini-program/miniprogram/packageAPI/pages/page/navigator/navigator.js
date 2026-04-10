Page({
  onShareAppMessage() {
    return {
      title: '页面跳转',
      path: 'packageAPI/pages/page/navigator/navigator'
    }
  },

  navigateTo() {
    wx.navigateTo({url: './navigator'})
  },

  navigateBack() {
    wx.navigateBack()
  },

  redirectTo() {
    wx.redirectTo({url: './navigator'})
  },

  switchTab() {
    wx.switchTab({url: '/pages/docai/dashboard/index'})
  },

  reLaunch() {
    wx.reLaunch({url: '/pages/docai/dashboard/index'})
  },
  onLoad() {
    this.setData({
      theme: wx.getSystemInfoSync().theme || 'light'
    })

    if (wx.onThemeChange) {
      wx.onThemeChange(({theme}) => {
        this.setData({theme})
      })
    }
  }
})
