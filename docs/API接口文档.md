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
| isRegister | Boolean | 否 | 是否为注册请求；密码模式下注册时传 `true` |

支持两种认证方式：
- **密码模式-登录**：提供 `username` + `password`，若用户不存在则返回“用户不存在，请先注册”
- **密码模式-注册**：提供 `username` + `password` + `isRegister=true`，创建新用户并直接登录
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

### 3.5 文档在线编辑（保存修改） 🔒

```
POST /api/v1/ai/documents/{docId}/apply-edit
Authorization: Bearer <token>
Content-Type: application/json
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| docId | Long | 源文档 ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 修改后的文档内容 |

**响应示例**

```json
{
  "code": 200,
  "message": "文档修改已保存",
  "data": {
    "downloadUrl": "/api/v1/ai/documents/edited/报告_edited.md",
    "fileName": "报告_edited.md"
  }
}
```

> **注意**：根据原文档扩展名自动选择输出格式：`.docx` 生成 Word 文档，`.md`/`.txt` 直接写入文本文件。

### 3.6 下载修改后文档

```
GET /api/v1/ai/documents/edited/{fileName}
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| fileName | String | 修改后的文件名（由 3.5 接口返回） |

**响应**：直接返回文件流（`Content-Type` 根据扩展名自动设置）。

> **安全措施**：文件名校验禁止路径遍历（`..`、`/`、`\` 等非法字符），非法请求返回 400。

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
>
> **提取能力增强**：
> - LLM抽取Prompt覆盖12大类信息实体（项目/人员/机构/联系方式/证件/时间/经费/研究/地理信息/数量统计/非结构化实体/表格数据），最大文本长度30000字符。
> - **多值完整提取**：LLM抽取Prompt明确要求同类字段出现多次时逐一独立提取（如3个国家→3条country字段，5个省份→5条state_province字段），不合并不遗漏。
> - **docx智能表格识别**：对于docx文件中的表格，自动检测key-value行结构（如 `项目名称 | 值`），将其转换为 `键：值` 格式输出；同时支持**多行数据表格**（表头行+多行数据行），按列号逐行提取所有数据。
> - **Markdown表格增强**：支持标准Markdown表格（含 `---` 分隔行），自动跳过分隔行、向上查找真正的表头行，逐行提取多条数据记录。
> - **Markdown标题提取**：不再跳过 `#` 前缀行，支持从Markdown标题中提取 `键：值` 信息（如 `## 项目名称：xxx`）。
> - **扩展别名词典**：覆盖40+种中文别名到30+个标准key，新增人口、国家、州/省、面积、首都、GDP、语言、货币、地区、城市等地理/数量类字段映射。
> - **值类型交叉验证**：数字类槽位（如人口、面积、经费）自动拒绝非数值类候选值，日期槽位拒绝无年份信息的候选，避免跨类型错填。
> - LLM失败时自动降级到增强版规则引擎：支持"键:值"模式、Markdown表格行关联提取（含分隔行跳过）、行内实体识别（手机号/邮箱/身份证号）。
> - 字段标准化覆盖40+种中文别名到30+个标准key。

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
{
  "docIds": [1, 2, 3],
  "userRequirement": "仅填写2024年北京地区、类型为公开招标的数据"
}
```

**请求字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docIds | Array<Long> | 否 | 指定数据源文档ID列表（支持多个不同类型文档混合）；为空时默认使用当前用户全部已解析文档 |
| userRequirement | String | 否 | 用户需求描述（时间、地点、日期、类型等），后端将进行需求强匹配并优先填入符合条件的数据 |

> **增强说明**：填表流程现包含多阶段"表头引导提取"机制，确保非结构化文档也能精准填表：
>
> 1. **docx表格直接提取**：对于源docx文件，直接打开并解析其表格结构。支持两种模式：(a) key-value行扫描匹配，(b) **多行数据表格**识别——自动检测表头行与数据行结构，按列号逐行提取所有匹配值（如表头"国家"列下有多行值则全部提取），特别适用于列表型源文档。
> 2. **正则全量提取**：在源文档文本中使用别名扩展搜索（每个标签对应20+种中文别名），通过正则表达式匹配 `标签：值` 模式提取数据。**对复合标签进行全量匹配**——提取所有出现的匹配项（而非仅第一个），每个子标签的匹配结果使用独立的标准化key存储。
> 3. **LLM上下文精准提取**：对仍缺失的字段，搜索源文档中标签出现的上下文（前后各500字符），将上下文发送给LLM进行精准提取。**复合标签提示**告知LLM将"/"两侧作为不同类别分别提取所有值并用换行分隔。
> 4. **值类型交叉验证**：候选匹配阶段自动检测值与槽位类型是否兼容（数字槽位拒绝纯文本值、日期槽位拒绝无年份值、人员槽位拒绝纯数字值），防止跨字段错填。
> 5. **收紧模糊匹配**：短标签（≤2字符）禁用字符重叠匹配，仅允许精确匹配和包含匹配，避免无关字段误匹配。
> 6. **复合标签拆分匹配**：模板槽位标签含"/"时（如"国家/省份"），自动拆分为子标签分别匹配。匹配采用6层策略：精确key匹配→名称匹配→别名集合匹配→包含匹配→别名双向交叉匹配→字段自带aliases匹配。同时合并allFields和allFieldsRaw两个字段列表搜索，对逗号/顿号/换行分隔的多值字段自动拆分。**只要找到任意值即返回**（不再要求至少2个值）。各子标签的值**分行写入不同行**——先填完第一属性的所有信息，再换行填写第二属性的所有信息。
> 7. **多源文档合并**：支持多个不同类型的源文档（docx/xlsx/txt/md混合）共同填充同一份表格，自动跨文档聚合字段。

**响应**：填表结果，包含每个槽位的填充值、置信度、决策模式。

**成功响应示例**

```json
{
  "code": 200,
  "message": "自动填表完成",
  "data": {
    "templateId": 10000035,
    "outputFile": "F:/DocAI/docai-pro/data/local-oss/template_files/demo_filled.xlsx",
    "filledCount": 7,
    "blankCount": 0,
    "totalSlots": 7,
    "auditId": "audit_10000035_1773924800000",
    "decisions": [
      {
        "slotLabel": "地区",
        "finalValue": "北京市",
        "finalConfidence": 0.96,
        "decisionMode": "rule_only",
        "reason": "规则Top1候选"
      }
    ]
  }
}
```

**常见错误响应（code=400）**

| message | 说明 |
|------|------|
| 未找到有效的已提取数据源文档 | 传入 docIds 不属于当前用户或文档未解析完成 |
| 所选文档仍在提取中，请等待提取完成后再执行填表操作 | 文档 uploadStatus 为 parsing |
| 模板未解析槽位，请先解析 | 未先调用 /parse 或模板无有效槽位 |
| 数据源文档中未提取到任何字段信息，无法进行自动填表 | 提取字段为空，需重新上传/提取源文档 |

**小程序调用建议**

1. 按顺序调用：`upload -> parse -> fill -> decisions/download`
2. 轮询文档状态接口确认 `uploadStatus=parsed` 后再调用 `fill`
3. `fill` 请求建议设置 120~300 秒超时（复杂模板耗时较长）
4. 建议在前端保存 `auditId`，用于定位填表审计日志

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

**响应字段**：

| 字段 | 说明 |
|------|------|
| slotLabel | 槽位标签 |
| finalValue | 最终填入值 |
| finalConfidence | 置信度(0~1) |
| decisionMode | 决策方式 |
| reason | 决策原因 |

**decisionMode 取值**：

| 值 | 含义 |
|------|------|
| rule_only | 纯规则决策 |
| rule_plus_llm | 规则+AI判定 |
| fallback_blank | 拒填（置信度过低） |
| statistical_aggregation | 统计聚合(SUM/AVG等) |
| direct_table_copy | 表格整体复制 |
| direct_copy_fallback_single | 整表复制未命中时回退到单值填充 |
| greedy_fallback | 贪心兜底匹配 |
| llm_fallback | AI终极兜底 |
| requirement_force_fill | 需求感知强兜底回填（尽量避免空值） |
| mandatory_fallback | 通用强兜底回填（有可用源数据时避免空值） |
| composite_match | 复合标签拆分匹配（"国家/地区"等含"/"的标签分别匹配，分行填入） |

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

### 5.8 发送填充结果至邮箱 🔒

```
POST /api/v1/template/{templateId}/send-email
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | ✅ | 收件人邮箱地址 |

**响应示例**

```json
{
  "code": 200,
  "message": "邮件发送成功",
  "data": "ok"
}
```

> **注意**：此接口需要配置 SMTP 邮件服务。如未配置环境变量 `DOC_SMTP_USER` 和 `DOC_SMTP_AUTH_CODE`，接口将返回错误提示。

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
8. 发送邮件 → POST /api/v1/template/{templateId}/send-email → 将结果发送至指定邮箱（可选）
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

---

## 十一、错误码与排错指南

### 11.1 通用错误码

| HTTP状态码 | 业务Code | 说明 | 排查建议 |
|-----------|---------|------|---------|
| 200 | 200 | 成功 | — |
| 400 | 400 | 请求参数错误 | 检查请求体字段是否完整、格式是否正确 |
| 401 | 401 | 未认证 | Token过期或无效，重新调用 `/users/auth` 获取新Token |
| 403 | 403 | 无权限 | 检查是否访问了其他用户的资源 |
| 413 | — | 请求体过大 | 文件超过200MB限制，检查Nginx `client_max_body_size` |
| 500 | 500 | 服务器内部错误 | 查看后端日志 `docai-pro/logs/ai-service.log` |
| 502 | — | 网关错误 | 目标微服务未启动，检查端口9001/9002/9003 |
| 504 | — | 网关超时 | AI处理超时，检查DashScope API连通性 |

### 11.2 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| SSE连接中断 | Nginx超时配置不足 | 确保 `proxy_read_timeout ≥ 300s` |
| 填表返回空结果 | 源文档未提取完成 | 等待 `uploadStatus=parsed` 后再调用fill |
| 模板解析无槽位 | 模板格式不符预期 | 确保模板含有"标签+空白单元格"的标准结构 |
| 文件下载乱码 | 文件名编码问题 | 使用 `filename*=UTF-8''<编码文件名>` 格式 |

---

## 相关文档

- [项目启动说明书](项目启动说明书.md) — 环境配置、启动部署
- [项目详细流程介绍](项目详细流程介绍.md) — 八阶段填表流程详解
- [商业项目策划书](商业项目策划书.md) — 商业模式与市场分析
- [答辩材料](答辩材料.md) — 技术架构与常见问答

---

> **文档更新日期**：2026年3月20日
