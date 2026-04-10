function ensureLogin() {
  const token = wx.getStorageSync('token') || ''
  if (token) {
    return true
  }

  wx.showToast({
    title: '请先登录',
    icon: 'none',
  })

  wx.reLaunch({
    url: '/pages/docai/login/index',
  })
  return false
}

module.exports = {
  ensureLogin,
}
