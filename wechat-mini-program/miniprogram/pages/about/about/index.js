Page({
  data: {
    updatedAt: '2026-04-05',
    productName: 'DocAI',
    version: '1.0.0',
    subjectName: '以微信公众平台公示主体为准',
    summary: 'DocAI 是面向文档处理、智能问答和智能填表场景的小程序工作台，帮助用户在一个入口完成资料上传、问答交互、模板填表和结果下载。',
    baseInfo: [
      { label: '产品名称', value: 'DocAI' },
      { label: '主体名称', value: '以微信公众平台公示主体为准' },
      { label: '当前版本', value: '1.0.0' },
      { label: '服务时间', value: '工作日 09:00-18:00' },
    ],
    featureItems: [
      {
        title: '资料上传与解析',
        desc: '支持 DOCX / XLSX / MD / TXT 资料上传，并在文档库中统一管理与查看。',
      },
      {
        title: '智能问答',
        desc: '围绕已上传资料发起问答，辅助梳理重点内容与处理结论。',
      },
      {
        title: '智能填表',
        desc: '选择模板和资料后生成结构化结果，并支持下载与转发。',
      },
    ],
    reviewInfo: [
      {
        label: '正式域名',
        value: '请在发布前替换为已备案并配置到微信后台的正式 HTTPS 域名',
      },
      {
        label: '测试账号',
        value: '请在提审前替换为审核专用账号和密码',
      },
      {
        label: '提审资料',
        value: '已在仓库内补充内容安全方案、真机回归清单和提审说明模板',
      },
    ],
    privacyItems: [
      '已提供《用户协议》《隐私政策》本地页面，并在登录与上传链路接入隐私授权。',
      '当前处理的信息包括账号信息、上传文件、AI 问答内容、智能填表需求与结果。',
      '用户可通过“账号与安全”直接注销当前账号；注销成功后会自动退出登录并清理关联数据。',
    ],
    readinessDocs: [
      'docs/content-safety-plan.md',
      'docs/mobile-regression-checklist.md',
      'docs/wechat-review-template.md',
    ],
  },

  openContact() {
    wx.navigateTo({
      url: '/pages/contact/contact/index?type=feedback',
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
