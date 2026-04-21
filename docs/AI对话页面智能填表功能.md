# AI 对话页面——智能填表功能详细文档

## 1. 功能概述

智能填表（Agent Fill）是 DocAI AI 对话页面（`AIChat.vue`）中的核心增值功能。用户无需离开 AI 对话界面，即可通过三步向导完成：

1. **上传源文档**（PDF、Word、TXT 等），AI 自动提取关键字段信息
2. **上传表格模板**（Excel `.xlsx`），指定可选的填写要求
3. **AI 智能填写**，一键生成填充好的 Excel 文件并提供下载

---

## 2. 用户操作流程

```
AI 对话页面
    │
    ▼
[输入框上方工具栏] 点击紫色 "开始填表" 按钮
    │
    ▼
[步骤 1 / 3 — 智能填表向导] 弹出对话框
    上传源文档（支持多文件，拖拽或点击选择）
    点击 "开始提取字段"
    │  后台调用 agentUploadSourceDoc 上传
    │  轮询 agentCheckSourceStatus 等待解析（最多 120s）
    ▼
[步骤 2 / 3 — 智能填表向导] 弹出第二个对话框
    展示已提取文档数量摘要
    上传表格模板文件（Excel，拖拽或点击选择）
    可选：输入特殊填写要求（最大 200 字）
    点击 "开始 AI 智能填写"
    │  后台调用 agentUploadAndFill（上传模板 + 执行 AI 填写）
    │  调用 agentGetDecisions 获取填充决策列表
    ▼
[填表完成] AI 消息气泡
    显示结果摘要（总字段数、成功填充数、平均置信度）
    内嵌结果卡片，包含：
    - 统计面板（字段数 / 成功数 / 平均置信度）
    - 置信度分布条形图（高 ≥85% / 中 70-85% / 低 <70%）
    - 填充明细表（前 10 条，含字段名、填充值、置信度、决策方式）
    - "下载填充文件" 按钮 → 下载填充好的 Excel
    - "预览分析" 按钮 → 弹出完整分析弹窗
```

---

## 3. 入口设计

### 3.1 输入框工具栏（主入口）

位于消息输入框上方，始终可见：

```html
<div class="input-toolbar">
  <button class="toolbar-fill-btn" @click="handleAgentFillStart(null)">
    <!-- 表格图标 -->
    开始填表
  </button>
  <span class="toolbar-fill-tip">上传源文档 + 模板，AI 智能自动填写</span>
</div>
```

样式：紫色渐变按钮（`#4F46E5 → #7C3AED`），醒目且始终显示。

### 3.2 AI 消息气泡下方按钮（辅助入口）

当 AI 回复中包含 Agent 动作标记（`agentAction.type === 'agent_fill_start'`）且消息已完成流式输出时，气泡下方额外渲染一个 "开始填表" 按钮：

```html
<div class="agent-fill-cta"
     v-if="msg._meta?.agentAction?.type === 'agent_fill_start' && !msg._streaming">
  <el-button type="primary" @click="handleAgentFillStart(msg._meta.agentAction)">
    开始填表
  </el-button>
</div>
```

---

## 4. 核心逻辑实现

### 4.1 状态管理

| 响应式变量 | 类型 | 说明 |
|------------|------|------|
| `showAgentSourceDialog` | Boolean | 步骤1对话框显示 |
| `showAgentTemplateDialog` | Boolean | 步骤2对话框显示 |
| `showAgentResultDialog` | Boolean | 完整分析弹窗显示 |
| `agentSourceFiles` | Array | 上传的源文档列表（含状态） |
| `agentSourceDocIds` | Array | 上传成功的文档 ID 列表 |
| `agentTemplateFile` | File | 选中的模板文件 |
| `agentRequirement` | String | 用户输入的填写要求 |
| `agentFilling` | Boolean | 是否正在填写 |
| `agentFillProgress` | Number | 进度条 0-100 |
| `agentExtracting` | Boolean | 是否正在提取字段 |
| `agentCurrentResult` | Object | 当前填表结果 `{ templateId, decisions }` |

### 4.2 步骤 1：源文档上传与提取

**函数：`doAgentUploadSource()`**

```
1. 校验：agentSourceFiles 不为空
2. 设置 agentExtracting = true
3. 并发调用 agentUploadSourceDoc(file) 上传所有文件
   → 返回 docId，存入 agentSourceDocIds
   → 更新 agentSourceFiles[i].status = 'uploaded' | 'failed'
4. 调用 pollAgentSourceExtraction() 轮询解析状态
   → 每 3 秒调用 agentCheckSourceStatus(agentSourceDocIds)
   → 当所有文档 uploadStatus === 'parsed' 或超时 (40次 × 3s = 120s)
   → 成功后关闭步骤1对话框，打开步骤2对话框
```

**错误处理：**
- 上传失败：标记文件状态为 `failed`，显示错误消息
- 轮询失败：清除定时器，显示错误消息

### 4.3 步骤 2：模板上传与 AI 填写

**函数：`doAgentFill()`**

```
1. 校验：agentTemplateFile 不为空，agentSourceDocIds 不为空
2. 设置 agentFilling = true，启动进度条模拟动画
3. 调用 agentUploadAndFill(template, docIds, requirement)
   → 返回 templateId
4. 调用 agentGetDecisions(templateId)
   → 返回 decisions[]（每条含 slotLabel, finalValue, finalConfidence, decisionMode）
5. 进度条推到 100%，清理定时器
6. 关闭步骤2对话框
7. 构建结果摘要文本，序列化 agentResult 为持久化标记
8. 向 messages[] 推入结果消息，包含 _meta.agentResult
9. 同步保存到服务器（content 包含 <!-- agentData:{...} --> 标记）
```

### 4.4 结果持久化机制

填表完成后，结果数据通过特殊 HTML 注释标记嵌入到消息内容末尾，实现跨会话持久化：

**写入（`doAgentFill` 中）：**
```javascript
const agentDataMark = `\n<!-- agentData:${JSON.stringify({ templateId, decisions })} -->`
const persistContent = summaryMsg + agentDataMark
// 显示时用 summaryMsg（无标记），存储时用 persistContent（含标记）
addConversationMessage(conversationId, { role: 'ai', content: persistContent })
```

**读取（`mapServerMsg` / `parseAgentDataFromContent`）：**
```javascript
const parseAgentDataFromContent = (raw) => {
  const match = raw.match(/\n<!-- agentData:([\s\S]*?) -->$/)
  if (!match) return { content: raw, agentResult: null }
  try {
    const agentResult = JSON.parse(match[1])
    const content = raw.slice(0, raw.length - match[0].length)
    return { content, agentResult }
  } catch (e) {
    return { content: raw, agentResult: null }
  }
}
```

从服务器加载历史消息时，`mapServerMsg` 会自动解析标记并还原 `_meta.agentResult`，使填表结果卡片在页面刷新后仍能正常渲染。

---

## 5. 前端组件结构

```
AIChat.vue
├── <template>
│   ├── .input-toolbar          ← 工具栏（主入口按钮）
│   ├── .agent-fill-cta         ← 气泡下按钮（辅助入口）
│   ├── .agent-result-inline    ← 内嵌结果卡片
│   │   ├── .agent-result-header     ← 标题 + 操作按钮
│   │   ├── .agent-result-stats      ← 3格统计面板
│   │   ├── .agent-conf-chart        ← 置信度条形图
│   │   └── .agent-decisions-table   ← 填充明细表（前10条）
│   ├── el-dialog（步骤1）      ← 源文档上传
│   │   ├── .agent-wizard-steps      ← 进度条（步骤1高亮）
│   │   ├── .agent-upload-zone       ← 拖拽上传区
│   │   └── .agent-file-list         ← 已上传文件列表
│   ├── el-dialog（步骤2）      ← 模板上传
│   │   ├── .agent-wizard-steps      ← 进度条（步骤2高亮）
│   │   ├── .agent-source-summary    ← 已提取文档摘要
│   │   ├── .agent-upload-zone       ← 模板拖拽上传区
│   │   ├── .agent-requirement-section ← 填写要求输入框
│   │   └── .agent-fill-progress     ← 填写进度条
│   └── el-dialog（完整分析）   ← 完整决策表格弹窗
└── <script setup>
    ├── parseAgentDataFromContent()   ← 解析持久化标记
    ├── mapServerMsg()                ← 服务端消息转换
    ├── handleAgentFillStart()        ← 打开步骤1对话框
    ├── doAgentUploadSource()         ← 上传源文档
    ├── pollAgentSourceExtraction()   ← 轮询提取状态
    ├── doAgentFill()                 ← 执行 AI 填写
    ├── downloadAgentFillResult()     ← 下载结果文件
    └── previewAgentFillResult()      ← 预览完整分析
```

---

## 6. 后端 API 端点

| 前端函数 | HTTP 方法 | 路径 | 说明 |
|----------|-----------|------|------|
| `agentUploadSourceDoc` | POST | `/api/v1/agent/source/upload` | 上传源文档，返回 `docId` |
| `agentCheckSourceStatus` | GET | `/api/v1/agent/source/status` | 查询文档解析状态 |
| `agentUploadAndFill` | POST | `/api/v1/agent/fill` | 上传模板并执行 AI 填写，返回 `templateId` |
| `agentGetDecisions` | GET | `/api/v1/agent/decisions/{templateId}` | 获取填充决策列表 |
| `downloadTemplateResult` | GET | `/api/v1/agent/result/{templateId}` | 下载填充好的 Excel 文件 |

所有路径经过 nginx → gateway（18080）→ file-service（9003）或 ai-service（9002）路由。

---

## 7. 数据结构

### Decision（填充决策条目）

```typescript
interface Decision {
  slotLabel: string      // 字段标签（如"姓名"、"日期"）
  label?: string         // 备用字段名
  finalValue: string     // AI 填充的最终值
  finalConfidence: number // 置信度 [0.0, 1.0]
  decisionMode: string   // 决策方式：'direct' | 'ai_infer' | 'default' | 'empty'
}
```

### AgentResult（填表结果）

```typescript
interface AgentResult {
  templateId: number     // 模板文件 ID（用于下载结果）
  decisions: Decision[]  // 所有字段的填充决策
}
```

---

## 8. 置信度分级与颜色

| 等级 | 范围 | 颜色 | CSS 类 |
|------|------|------|--------|
| 高 | ≥ 85% | 绿色 `#67c23a` | `.green` |
| 中 | 70% ~ 85% | 黄色 `#e6a23c` | `.yellow` |
| 低 | < 70% | 红色 `#f56c6c` | `.red` |

---

## 9. 决策方式说明

| `decisionMode` | 显示文本 | el-tag 类型 | 含义 |
|----------------|----------|-------------|------|
| `direct` | 直接提取 | `success` | 从源文档中直接匹配到明确值 |
| `ai_infer` | AI 推断 | `warning` | AI 通过上下文推断填写 |
| `default` | 默认值 | `info` | 使用预设默认值 |
| `empty` | 未填充 | `danger` | 无法确定，留空 |

---

## 10. 关键设计决策

### 为什么不跳转到其他页面？
用户全程在 AI 对话页面完成操作，保持上下文连续性。填表过程中 AI 消息同步推入对话历史，用户随时可向 AI 追问。

### 为什么使用 HTML 注释持久化？
- API 的 `addConversationMessage` 只接受 `role` 和 `content` 字段
- 无需修改后端数据库模型
- HTML 注释在 Markdown 渲染时不可见，不影响用户阅读
- 正则解析简单可靠

### 为什么进度条使用模拟动画？
AI 填写服务没有实时进度回调，使用模拟动画提升用户等待体验：
- 上传时：根据实际上传进度推进至 30%
- AI 处理时：以随机步长缓慢爬升至 85%
- 完成后：直接跳至 100%

---

## 11. 已知限制

1. **轮询超时**：源文档提取最长等待 120 秒（40 次 × 3 秒），超时后静默完成
2. **前 10 条明细**：内嵌结果卡片只显示前 10 条决策，完整内容需点击"预览分析"
3. **文件格式**：模板文件目前仅支持 `.xlsx`；源文档支持 PDF、Word、TXT 等主流格式
4. **单对话绑定**：填表结果与当前会话绑定，跨会话无法复用已上传的源文档 ID
