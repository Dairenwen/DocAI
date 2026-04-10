function buildFocusInfo(type) {
  if (type === 'feedback') {
    return {
      title: '客服反馈',
      desc: '如遇到登录、上传、智能问答或智能填表问题，可通过下列方式反馈。',
    }
  }

  return {
    title: '联系我们',
    desc: '如需问题反馈、投诉建议或账号协助，可通过下列方式联系支持团队。',
  }
}

Page({
  data: {
    updatedAt: '2026-04-05',
    focusTitle: '',
    focusDesc: '',
    subjectName: '以微信公众平台公示主体为准',
    contactChannels: [
      {
        label: '服务邮箱',
        value: '请在发布前替换为真实客服邮箱',
        desc: '用于问题反馈、服务咨询和账号使用协助。',
      },
      {
        label: '服务电话',
        value: '请在发布前替换为真实客服电话',
        desc: '建议在工作时间内联系人工支持。',
      },
      {
        label: '服务时间',
        value: '工作日 09:00-18:00',
        desc: '法定节假日顺延处理，紧急问题请在邮件中备注。',
      },
    ],
    reviewSupportItems: [
      {
        label: '正式域名',
        value: '请在发布前替换为已备案并配置到微信后台的正式 HTTPS 域名',
      },
      {
        label: '审核测试账号',
        value: '请在提审前替换为审核专用账号和密码',
      },
      {
        label: '隐私入口',
        value: '登录页、个人中心、用户协议、隐私政策、账号与安全、联系我们',
      },
    ],
    feedbackItems: [
      '提交问题反馈时，建议一并提供账号、操作时间、问题步骤和错误截图。',
      '上传或下载异常请补充文件名称、格式和失败提示，便于快速定位。',
      '如遇账号异常、登录失效或功能使用问题，请说明账号和时间，便于快速排查。',
    ],
    serviceItems: [
      '问题反馈原则上在 1 至 3 个工作日内首次响应。',
      '复杂问题原则上在 15 个工作日内反馈处理结果。',
      '如涉及安全核验、法律留存或异常排查，处理周期可能根据实际情况延长。',
    ],
  },

  onLoad(options) {
    const focusInfo = buildFocusInfo(String((options && options.type) || ''))
    this.setData({
      focusTitle: focusInfo.title,
      focusDesc: focusInfo.desc,
    })
  },

  openAccountSecurity() {
    wx.navigateTo({
      url: '/pages/account-security/account-security/index',
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
})
