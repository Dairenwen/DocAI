# DocAI API 接口文档

> **基础地址**
> - 本地开发：`http://localhost:18080`（API 网关）
> - 服务器部署：`http://<server-ip>:8080`（Nginx 反向代理 → 网关 18080）
>
> **前端客户端对接说明**
> - Web 前端：前缀 `/api/v1`（由 Nginx 反向代理转发到网关 18080）
> - 微信小程序：设置 `baseURL` 为 `http(s)://<server-ip>:8080/api/v1`
> - 所有请求头需设置 `Content-Type: application/json`（除文件上传外）
>
> **认证方式**
> - 所有标记 🔒 的接口需要在请求头中携带 JWT Token：`Authorization: Bearer <token>`
> - Token 有效期 20 小时，通过 `/api/v1/users/auth` 获取
> - 标记 🔓 的接口为公开接口，无需 Token

---

## 一、用户服务（user-service）

网关路由前缀：`/api/v1/users/**` → 转发到 user-service（端口 9001），去掉前两层前缀。

### 1.1 统一认证（注册/登录）🔓

```
POST /api/v1/users/auth
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 条件 | 用户名（密码模式必填） |
| password | String | 条件 | 密码（密码模式必填） |
| email | String | 条件 | 邮箱（验证码模式必填） |
| verificationCode | String | 条件 | 验证码（验证码模式必填） |

支持两种认证方式：
- **密码模式**：提供 `username` + `password`，新用户自动注册
- **验证码模式**：提供 `email` + `verificationCode`

**响应示例**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 10000001,
    "userName": "admin",
    "email": null,
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenExpireTime": "03-15 06:48:24",
    "isNewUser": false
  }
}
```

**错误响应**

| code | 说明 |
|------|------|
| 400 | 用户名与密码不匹配 |
| 400 | 请求参数错误（缺少必填字段） |

### 1.2 发送邮箱验证码 🔓

```
POST /api/v1/users/verification-code
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | ✅ | 目标邮箱（@Email 格式） |

**响应示例**

```json
{
  "code": 200,
  "message": "验证码发送成功",
  "data": {
    "sendTo": "te***@example.com",
    "expireTime": 300,
    "sendSuccess": true,
    "deliveryMode": "smtp"
  }
}
```

### 1.3 获取用户信息 🔒

```
GET /api/v1/users/info
Authorization: Bearer <token>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 10000001,
    "username": "admin",
    "email": "ad***@example.com"
  }
}
```

### 1.4 修改密码 🔒

```
POST /api/v1/users/change-password
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| currentPassword | String | ✅ | 当前密码 |
| newPassword | String | ✅ | 新密码（6-64位） |
| confirmPassword | String | ✅ | 确认密码（须与新密码一致） |

> 注意：通过邮箱验证码注册的用户未设置密码，调用此接口会返回 400 错误提示"当前账户通过邮箱验证码注册，未设置密码，无法通过此方式修改密码"。此类用户若需设置密码，请先使用邮箱重置密码功能。

**可能的错误响应（HTTP 400）**

| 错误信息 | 说明 |
|---------|------|
| 当前密码不正确 | currentPassword 与数据库中的密码不匹配 |
| 新密码与确认密码不一致 | newPassword 与 confirmPassword 不同 |
| 新旧密码不能相同 | newPassword 与 currentPassword 相同 |
| 当前账户通过邮箱验证码注册，未设置密码，无法通过此方式修改密码 | 邮箱注册用户未设置密码 |

### 1.5 邮箱重置密码 🔓

```
POST /api/v1/users/password/reset-by-email
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | ✅ | 注册邮箱 |
| verificationCode | String | ✅ | 邮箱验证码 |
| newPassword | String | ✅ | 新密码（6-64位） |
| confirmPassword | String | ✅ | 确认密码 |

### 1.6 退出登录 🔒

```
POST /api/v1/users/logout
Authorization: Bearer <token>
```

---

## 二、文件服务（file-service）

网关路由前缀：`/api/v1/files/**` → 转发到 file-service（端口 9003）

### 2.1 上传文件 🔒

```
POST /api/v1/files/upload/single
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数**

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| file | File | form-data | ✅ | Excel 文件（.xlsx, ≤200MB） |
| category | String | query | 否 | 文件分类目录 |

**响应示例**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "fileId": 1001,
    "fileName": "data.xlsx",
    "fileSize": 102400,
    "fileUrl": "https://oss.example.com/files/data.xlsx",
    "ossKey": "excel/data.xlsx",
    "uploadStatus": 1
  }
}
```

### 2.2 分页查询文件列表 🔒

```
GET /api/v1/files/list?pageNum=1&pageSize=10&fileName=xxx
Authorization: Bearer <token>
```

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNum | Integer | 1 | 页码 |
| pageSize | Integer | 10 | 每页数量 |
| fileName | String | - | 文件名模糊搜索 |
| uploadStatus | Integer | - | 上传状态过滤 |

**响应示例**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "fileId": 1001,
        "fileName": "data.xlsx",
        "fileSize": 102400,
        "fileType": "excel",
        "fileExtension": "xlsx",
        "uploadStatus": 1
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 2.3 下载文件 🔒

```
GET /api/v1/files/download?fileId=1001
Authorization: Bearer <token>
```

返回二进制文件流。

### 2.4 Excel 预览 🔒

```
GET /api/v1/files/excel/preview/{fileId}?page=1&pageSize=20&sheetIndex=0
Authorization: Bearer <token>
```

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 1 | 数据页码 |
| pageSize | Integer | 20 | 每页行数 |
| sheetIndex | Integer | 0 | Sheet 索引 |

**响应示例**

```json
{
  "code": 200,
  "data": {
    "excelInfo": { "fileId": 1001, "fileName": "data.xlsx", "totalRows": 100, "totalColumns": 5 },
    "sheets": [{ "sheetIndex": 0, "sheetName": "Sheet1", "totalRows": 100 }],
    "headers": [{ "dbFieldName": "col_name", "originalHeader": "姓名" }],
    "dataRows": [{ "col_name": "张三" }],
    "paginationInfo": { "currentPage": 1, "totalPages": 5, "totalRecords": 100 }
  }
}
```

### 2.5 获取 Excel 信息 🔒

```
GET /api/v1/files/excel/info/{fileId}
Authorization: Bearer <token>
```

### 2.6 复原文件数据 🔒

```
POST /api/v1/files/restore/{fileId}
Authorization: Bearer <token>
```

### 2.7 批量删除文件 🔒

```
DELETE /api/v1/files/delete
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

```json
{ "fileIds": [1001, 1002] }
```

---

## 三、AI 服务（ai-service）

### 3.1 AI 流式对话 🔒

```
POST /api/v1/ai/chat/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fileId | Long | 否 | 关联文件 ID（可选） |
| userInput | String | ✅ | 用户输入（最大 2000 字符） |

**响应**：SSE 事件流，实时返回 AI 生成内容。超时时间 5 分钟。

**SSE 事件格式**：
- `event: init` / `event: progress`：进度通知，包含 `stage`、`progress`、`message` 字段
- `event: complete`：最终结果，`data.result.aiResponse` 为 AI 回复文本
- `event: error`：错误事件，`data.error` 为错误信息

> **注意**：
> - 无 `fileId` 时走通用在线大模型对话
> - `fileId` 对应 **Excel 文件**时走数据分析流程（file-service 的 files 表）
> - `fileId` 对应 **源文档**（txt/md/docx/xlsx 等）时走源文档智能对话流程（ai-service 的 source_documents 表），自动读取原始文件内容作为上下文（优先读取原文，原文不可读时回退到提取字段）
> - 源文档对话支持编辑、修改、删除、增添等操作，AI 会输出修改后的完整文档内容

**前端文档编辑功能**：
- 侧边栏提供"内容摘要、信息提取、润色优化、格式调整、数据分析、删除冗余、内容补充、翻译文档、导出结果"9 个快捷命令
- 每条 AI 回复提供：复制、预览、导出文档（.txt/.md）、发送至邮箱、保存到文档 操作按钮
- 导出文件格式根据关联文档类型自动选择（.md 文档导出为 .md，其余导出为 .txt）

### 3.2 AI 历史记录 🔒

```
GET /api/v1/ai/requests?pageNum=1&pageSize=10&fileId=xxx
Authorization: Bearer <token>
```

### 3.3 发送 Excel 到邮箱 🔒

```
POST /api/v1/ai/send-email
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | ✅ | 目标邮箱 |
| excelUrl | String | ✅ | Excel 文件下载链接 |

### 3.4 发送AI内容到邮箱 🔒

```
POST /api/v1/ai/send-content-email
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | ✅ | 目标邮箱 |
| content | String | ✅ | AI 生成的文本内容 |
| subject | String | 否 | 邮件主题（默认 "DocAI - AI生成内容"） |

> **注意**：此接口需要配置 SMTP 邮件服务。如未配置环境变量 `DOC_SMTP_USER` 和 `DOC_SMTP_AUTH_CODE`，接口将返回错误提示"邮件服务未配置"。详见[项目启动说明书](项目启动说明书.md)中的 SMTP 配置章节。

---

## 四、文档提取服务（ai-service / source）

### 4.1 上传并提取文档 🔒

```
POST /api/v1/source/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

> **重要变更**：上传接口现在**立即返回**文档记录（`uploadStatus: "parsing"`），字段提取在后台异步进行。前端通过轮询文档列表获取最新提取状态。同时最多 2 个文档并行提取（Semaphore 限流），避免 LLM API 过载。
>
> **性能优化**：字段提取固定使用 `qwen-turbo` 模型（速度最快），不受用户当前选择的对话模型影响。

**请求参数**

| 参数 | 类型 | 位置 | 说明 |
|------|------|------|------|
| file | File | form-data | 源文档（支持 .docx, .xlsx, .txt, .md，≤200MB） |

**响应示例**

```json
{
  "code": 200,
  "message": "上传并抽取成功",
  "data": {
    "id": 1,
    "fileName": "proposal.docx",
    "fileType": "docx",
    "docSummary": "文档摘要...",
    "createdAt": "2024-01-01T10:00:00"
  }
}
```

### 4.2 获取文档抽取字段 🔒

```
GET /api/v1/source/{docId}/fields
Authorization: Bearer <token>
```

**响应示例**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "docId": 1,
      "fieldKey": "project_name",
      "fieldName": "项目名称",
      "fieldValue": "智能文档系统",
      "fieldType": "text",
      "confidence": 0.95,
      "sourceText": "项目名称：智能文档系统"
    }
  ]
}
```

### 4.3 获取用户文档列表 🔒

```
GET /api/v1/source/documents
Authorization: Bearer <token>
```

### 4.3.1 轻量级文档状态轮询 🔒

```
GET /api/v1/source/documents/status
Authorization: Bearer <token>
```

只返回文档的 `id`、`uploadStatus`、`docSummary`，用于前端高效轮询文档解析状态，避免加载完整文档列表造成不必要的带宽和数据库开销。

**响应示例**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    { "id": 10000001, "uploadStatus": "parsed", "docSummary": "5个字段已抽取" },
    { "id": 10000002, "uploadStatus": "parsing", "docSummary": null }
  ]
}
```

### 4.4 获取文档详情 🔒

```
GET /api/v1/source/{docId}
Authorization: Bearer <token>
```

### 4.5 下载源文档 🔒

```
GET /api/v1/source/{docId}/download
Authorization: Bearer <token>
```

返回原始文件二进制流，响应头包含 `Content-Disposition: attachment; filename*=UTF-8''<编码后文件名>`。

### 4.6 删除源文档 🔒

```
DELETE /api/v1/source/{docId}
Authorization: Bearer <token>
```

**响应示例**

```json
{
  "code": 200,
  "message": "删除成功",
  "data": true
}
```

### 4.7 批量删除源文档 🔒

```
DELETE /api/v1/source/batch
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

```json
{ "docIds": [1, 2, 3] }
```

---

## 五、模板填充服务（ai-service / template）

### 5.1 上传模板文件 🔒

```
POST /api/v1/template/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数**

| 参数 | 类型 | 位置 | 说明 |
|------|------|------|------|
| file | File | form-data | 模板文件（支持 .xlsx, .docx） |

### 5.2 解析模板槽位 🔒

```
POST /api/v1/template/{templateId}/parse
Authorization: Bearer <token>
```

**响应示例**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "templateId": 1,
      "label": "项目名称",
      "context": "Sheet1",
      "position": "{\"sheet\":\"Sheet1\",\"row\":2,\"col\":1}",
      "expectedType": "text",
      "slotType": "adjacent_blank"
    }
  ]
}
```

### 5.3 自动填表 🔒

```
POST /api/v1/template/{templateId}/fill
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

```json
{ "docIds": [1, 2, 3] }
```

**响应**：填表结果，包含每个槽位的填充值、置信度、决策模式。

### 5.4 获取审计日志 🔒

```
GET /api/v1/template/{templateId}/audit
Authorization: Bearer <token>
```

### 5.5 获取决策结果 🔒

```
GET /api/v1/template/{templateId}/decisions
Authorization: Bearer <token>
```

### 5.6 获取模板列表 🔒

```
GET /api/v1/template/list
Authorization: Bearer <token>
```

### 5.7 下载填充结果 🔒

```
GET /api/v1/template/{templateId}/download
Authorization: Bearer <token>
```

返回填充完成的文件二进制流。

---

## 六、LLM 模型管理（ai-service / llm）

### 6.1 获取模型提供商列表 🔒

```
GET /api/v1/llm/providers/list
Authorization: Bearer <token>
```

**响应示例**

```json
{
  "code": 200,
  "data": [
    {
      "name": "dashscope",
      "available": true,
      "defaultModel": "qwen-plus",
      "models": ["qwen-turbo", "qwen-plus", "qwen-max", "qwen-long", "qwen2.5-72b-instruct", "qwen2.5-32b-instruct", "qwen2.5-14b-instruct", "qwen2.5-7b-instruct"]
    },
    {
      "name": "deepseek",
      "available": true,
      "defaultModel": "deepseek-chat",
      "models": ["deepseek-chat", "deepseek-reasoner"]
    }
  ]
}
```

### 6.2 切换模型提供商与模型 🔒

```
POST /api/v1/llm/providers/switch
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

```json
{ "providerName": "dashscope:qwen-max" }
```

> 格式为 `provider:model`（如 `dashscope:qwen-turbo`），仅指定 `dashscope` 时使用该提供商默认模型。

**响应**

```json
{ "code": 200, "data": { "currentProvider": "dashscope", "currentModel": "qwen-max" } }
```

### 6.3 获取当前提供商与模型 🔒

```
GET /api/v1/llm/providers/current
Authorization: Bearer <token>
```

**响应**

```json
{ "code": 200, "data": { "currentProvider": "dashscope", "currentModel": "qwen-plus" } }
```

**DashScope 可用模型说明**：

| 模型 | 特点 | 推荐场景 |
|------|------|---------|
| qwen-turbo | 速度最快，成本低 | 简单问答、信息提取 |
| qwen-plus | 平衡速度与质量（默认） | 通用对话、文档编辑 |
| qwen-max | 最强能力 | 复杂推理、长文档处理 |
| qwen-long | 支持超长上下文 | 超长文档分析 |
| qwen2.5-*b-instruct | 开源模型系列 | 特定场景优化 |

**DeepSeek 可用模型**：

| 模型 | 特点 |
|------|------|
| deepseek-chat | 通用对话（默认） |
| deepseek-reasoner | 强推理能力 |

---

## 七、通用响应格式

所有 API 统一返回格式：

```json
{
  "code": 200,
  "message": "操作描述",
  "data": {}
}
```

**状态码说明**

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限 |
| 500 | 服务器内部错误 |

---

## 八、认证说明

1. 调用 `POST /api/v1/users/auth` 获取 JWT Token
2. 后续请求在 Header 中携带：`Authorization: Bearer <token>`
3. Token 有效期 20 小时
4. 公开接口（🔓）无需 Token：认证、验证码、重置密码

---

## 九、微信小程序对接指南

### 9.1 环境配置

在小程序 `app.js` 或配置文件中设置 API 基础地址：

```javascript
const BASE_URL = 'https://your-domain.com/api/v1' // 生产环境
// const BASE_URL = 'http://192.168.x.x:8080/api/v1' // 开发环境（需配置不校验合法域名）
```

### 9.2 请求封装示例

```javascript
function request(options) {
  const token = wx.getStorageSync('token')
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': options.contentType || 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      success(res) {
        if (res.statusCode === 401 || (res.data && res.data.code === 401)) {
          wx.removeStorageSync('token')
          wx.navigateTo({ url: '/pages/login/login' })
          reject(new Error('登录已过期'))
          return
        }
        resolve(res.data)
      },
      fail: reject
    })
  })
}
```

### 9.3 文件上传示例

```javascript
function uploadFile(filePath) {
  const token = wx.getStorageSync('token')
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + '/source/upload',
      filePath: filePath,
      name: 'file',
      header: { 'Authorization': `Bearer ${token}` },
      success(res) { resolve(JSON.parse(res.data)) },
      fail: reject
    })
  })
}
```

### 9.4 核心接口调用流程

```
1. 登录认证 → POST /api/v1/users/auth → 获取 token 存入 wx.setStorageSync('token')
2. 上传文档 → POST /api/v1/source/upload → 获取 docId
3. 查看列表 → GET /api/v1/source/documents → 文档列表
4. 上传模板 → POST /api/v1/template/upload → 获取 templateId
5. 解析模板 → POST /api/v1/template/{templateId}/parse → 获取槽位列表
6. 智能填表 → POST /api/v1/template/{templateId}/fill → 自动匹配并填充
7. 下载结果 → GET /api/v1/template/{templateId}/download → 获取填充后的文件
```

### 9.5 注意事项

- 微信小程序不支持 SSE，AI 对话接口（`/ai/chat/stream`）需改用轮询或 WebSocket 适配
- 文件下载需使用 `wx.downloadFile` API
- 生产环境需配置 HTTPS 域名并在小程序管理后台添加合法域名
- 接口返回的时间格式为 ISO 8601（`yyyy-MM-ddTHH:mm:ss`）

---

## 十、前端功能说明

### 10.1 文件预览

前端支持 4 种文件格式的在线预览：

| 格式 | 渲染方式 | 依赖库 |
|------|----------|--------|
| `.txt` | `<pre>` 原文展示 | 无 |
| `.md` | Markdown 渲染（GFM） | marked + DOMPurify |
| `.xlsx` | HTML 表格渲染 | SheetJS (xlsx) + DOMPurify |
| `.docx` | HTML 富文本渲染 | mammoth + DOMPurify |

预览窗口宽度为 85vw，高度自适应（最大 80vh 滚动）。所有 HTML 渲染内容均经过 DOMPurify 消毒以防 XSS。

### 10.3 文档批量操作

工具栏提供批量操作按钮（勾选文档后激活）：

| 操作 | 说明 |
|------|------|
| 下载已选 | 逐个下载所有已勾选文档的原始文件 |
| 删除已选 | 调用批量删除 API 一次性删除多个文档及其关联提取字段 |

选中数量实时显示在统计信息栏和按钮上。

### 10.2 AI 智能对话 - 文档编辑

关联源文档后，侧边栏提供以下快捷编辑命令：

| 命令 | 说明 |
|------|------|
| 内容摘要 | 总结文档核心内容 |
| 信息提取 | 提取关键实体信息 |
| 润色优化 | 优化语言表达，输出完整修改后文档 |
| 格式调整 | 优化标题层级、段落划分 |
| 数据分析 | 分析数据趋势和关键发现 |
| 删除冗余 | 精简冗余内容和重复段落 |
| 内容补充 | 补充缺失章节内容 |
| 翻译文档 | 翻译为英文 |
| 导出结果 | 下载最近一次 AI 回复内容 |

每条 AI 回复消息提供以下操作：
- **重新生成**：重新提交上一次提问
- **继续对话**：补充未完成的回答
- **复制**：复制纯文本内容
- **预览**：大窗口 Markdown 渲染预览
- **导出文档**：下载为 .txt 或 .md 文件
- **发送至邮箱**：将 AI 结果发送到指定邮箱
- **保存到文档**：保存回文档（需后端支持）

### 10.4 对话会话管理 API

所有接口需携带 `Authorization: Bearer <token>` 请求头。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/conversations` | 获取当前用户的所有对话（按置顶、更新时间排序） |
| POST | `/api/v1/ai/conversations` | 创建新对话 |
| PUT | `/api/v1/ai/conversations/{id}` | 更新对话元信息（标题、置顶、关联文档等） |
| DELETE | `/api/v1/ai/conversations/{id}` | 删除对话（级联删除所有消息） |
| GET | `/api/v1/ai/conversations/{id}/messages` | 获取对话的所有消息 |
| POST | `/api/v1/ai/conversations/{id}/messages` | 添加消息到对话（同时更新对话 updatedAt） |

**创建对话请求体（可选，支持空 body）：**
```json
{ "title": "新对话", "linkedDocId": null, "linkedDocName": "" }
```
> 注：请求体为空时将创建默认标题为 "新对话" 的对话。

**添加消息请求体：**
```json
{ "role": "user", "content": "用户消息内容" }
```

### 10.5 停止生成功能

AI 对话过程中，用户可随时点击停止按钮中止生成：
- **请求阶段**：通过 AbortController 中止 SSE 流式请求
- **打字动画阶段**：立即停止动画，展示已接收内容并持久化保存
