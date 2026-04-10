function normalizeText(value) {
  return String(value || '').trim()
}

function getDocaiEntryHint(options) {
  const settings = Object.assign({
    role: 'source',
  }, options || {})
  const role = normalizeText(settings.role).toLowerCase()

  if (role === 'template') {
    return '先把模板发到微信会话或文件传输助手，再在小程序里选择；也可以直接从模板库复用已上传模板。'
  }

  return '先把文件发到微信会话或文件传输助手，再在小程序里选择；也可以直接从资料库复用已上传文档。'
}

function showDocaiEntryActionSheet(options) {
  const settings = Object.assign({
    includeLibrary: false,
    includeNative: true,
    libraryLabel: '从模板库选择',
    nativeLabel: '从微信会话选择',
  }, options || {})
  const items = []

  if (settings.includeLibrary) {
    items.push({
      key: 'library',
      label: settings.libraryLabel,
    })
  }

  if (settings.includeNative) {
    items.push({
      key: 'native',
      label: settings.nativeLabel,
    })
  }

  if (!items.length) {
    return Promise.resolve('')
  }

  if (items.length === 1) {
    return Promise.resolve(items[0].key)
  }

  return new Promise((resolve) => {
    wx.showActionSheet({
      itemList: items.map((item) => item.label),
      success: (res) => {
        const target = items[res.tapIndex]
        resolve(target ? target.key : '')
      },
      fail: () => resolve(''),
    })
  })
}

module.exports = {
  getDocaiEntryHint,
  showDocaiEntryActionSheet,
}
