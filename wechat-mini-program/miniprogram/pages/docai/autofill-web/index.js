const {
  buildDocaiWebUrl,
  resolveWebEntryConfig,
} = require('../../../utils/docai-web')

Page({
  data: {
    pageTitle: '网页入口',
    webviewUrl: '',
    errorText: '',
  },

  onLoad(options) {
    this.pageMode = (options && options.mode) || 'autofill'
    const entryConfig = resolveWebEntryConfig(this.pageMode)
    const pageTitle = entryConfig.title || '网页入口'

    if (typeof wx.setNavigationBarTitle === 'function') {
      wx.setNavigationBarTitle({
        title: pageTitle,
      })
    }

    this.setData({ pageTitle })
    this.reloadWebView()
  },

  reloadWebView() {
    const webviewUrl = buildDocaiWebUrl(this.pageMode)

    if (!webviewUrl) {
      this.setData({
        webviewUrl: '',
        errorText: '当前版本暂未开放可用的网页入口。',
      })
      return
    }

    this.setData({
      webviewUrl,
      errorText: '',
    })
  },

  handleWebError() {
    const entryConfig = resolveWebEntryConfig(this.pageMode)
    const pageTitle = entryConfig.errorTitle || entryConfig.title || '网页入口'

    this.setData({
      errorText: [
        pageTitle + '打开失败。',
        '请稍后重试，或检查网页端服务是否已正确部署。',
        '如果网页端尚未登录，进入后可能仍需要先完成网页登录。',
      ].join('\n'),
    })
  },

  handleRetry() {
    this.reloadWebView()
  },
})
