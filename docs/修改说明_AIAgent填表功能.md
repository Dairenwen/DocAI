# DocAI 修改说明 — AI Agent 智能填表功能

> **修改日期**：2025年  
> **修改范围**：AI对话模块新增 AI Agent 智能引导填表工作流  
> **影响文件**：4个文件（后端2个 + 前端2个）

---

## 一、修改背景

### 1.1 需求来源

原有填表功能（文档列表页 → 自动填表页）需要用户在多个页面间跳转操作，流程割裂，用户体验不连贯。

新需求：在 AI 对话页面内，当用户表达填表意图时，AI 应自动识别并在当前页面内引导用户完成**源文档上传 → 字段提取 → 模板上传 → 自动填写 → 结果下载**的完整工作流，无需切换页面。

### 1.2 设计原则

- **零新接口**：复用所有已有 REST API，不新增后端端点
- **最小改动**：仅修改4个文件，不影响其他功能
- **渐进增强**：Agent 功能与原有对话功能完全兼容，不影响非填表对话

---

## 二、修改内容

### 2.1 后端：`AiUnifiedResponse.java`

**文件路径**：`docai-pro/ai-service/src/main/java/com/docai/ai/dto/response/AiUnifiedResponse.java`

**变更类型**：新增字段

**具体修改**：添加 `agentAction` 字段，用于在 SSE 完成事件中携带 Agent 动作指令。

```java
/** Agent动作字段 - AI Agent工作流触发 */
private java.util.Map<String, Object> agentAction;
```

**字段说明**：
- `type`：动作类型，当前值为 `"agent_fill_start"`
- `step`：当前步骤，值为 `"upload_source_docs"`
- `label`：按钮显示文本，值为 `"开始填表"`

---

### 2.2 后端：`AiServiceImpl.java`

**文件路径**：`docai-pro/ai-service/src/main/java/com/docai/ai/service/impl/AiServiceImpl.java`

**变更类型**：在 `handleGeneralChatFlow` 方法中添加填表意图识别逻辑

**识别关键词（20个）**：

| 中文关键词 | 英文关键词 |
|---------|---------|
| 填表、填写表格、填充模板、自动填表 | autofill |
| 帮我填、智能填表、填写表单 | fill template |
| 表格填写、模板填充、填写报表 | auto fill |
| 填好的表、帮填、自动填写 | — |
| 一键填表、批量填表、填写数据 | — |

**触发行为**：识别到关键词后，不调用 LLM，直接返回引导回复和 `agentAction` 字段：

```json
{
  "agentAction": {
    "type": "agent_fill_start",
    "step": "upload_source_docs",
    "label": "开始填表"
  }
}
```

---

### 2.3 前端：`api/index.js`

**文件路径**：`docai-frontend/src/api/index.js`

**变更类型**：新增代码（不修改现有逻辑）

**具体修改**：

1. **SSE 解析补充**：从 `complete` 事件中提取 `agentAction` 字段并随 payload 返回
2. **新增 5 个 Agent API 函数**（均封装自现有 API）：

| 函数名 | 封装自 | 用途 |
|------|------|------|
| `agentUploadSourceDoc` | `uploadSourceDocument` | 上传源文档 |
| `agentCheckSourceStatus` | `getSourceDocuments` | 轮询提取状态 |
| `agentUploadAndFill` | `uploadTemplateFile` + `parseTemplateSlots` + `fillTemplate` | 上传模板并执行填写 |
| `agentGetDecisions` | `getTemplateDecisions` | 获取填充决策明细 |
| `agentDownloadResult` | `downloadTemplateResult` | 下载填充结果 |

---

### 2.4 前端：`AIChat.vue`

**文件路径**：`docai-frontend/src/views/AIChat.vue`

**变更类型**：功能增强（添加 Agent 工作流 UI 及逻辑）

**具体修改**：

#### a. 模板（Template）部分

1. **"开始填表"按钮**：当 AI 消息的 `_meta.agentAction.type === 'agent_fill_start'` 时，在气泡操作区展示此按钮
2. **填充结果内嵌展示区**（`agent-result-inline`）：当 AI 消息携带 `_meta.agentResult` 时展示，包含：
   - 三个统计卡片（总字段数、成功填充数、平均置信度）
   - 置信度分布条形图（高/中/低）
   - 前10条填充明细表格
   - 下载 / 预览分析 按钮
3. **源文档上传对话框**（`el-dialog`）：拖拽/点击上传，实时显示上传进度和提取状态
4. **模板上传+填写对话框**（`el-dialog`）：选择模板文件、填写需求说明、一键启动填表
5. **填充结果详情预览对话框**（`el-dialog`）：完整决策表格 + 决策方式分布图

#### b. Script 部分

1. **新增 import**：`agentUploadSourceDoc`, `agentCheckSourceStatus`, `agentUploadAndFill`, `agentGetDecisions`, `agentDownloadResult`, `downloadTemplateResult`, `Loading` 图标
2. **新增 Agent 状态变量**（14个）：对话框开关、文件列表、进度状态等
3. **修改 `beginTypewriter`**：打字动画完成后自动触发 `handleAgentFillStart`；在 `_meta` 中保存 `agentAction`
4. **新增 Agent 工作流函数**（15个）：完整的 `handleAgentFillStart` → `doAgentSourceUpload` → `pollAgentSourceExtraction` → `doAgentFill` → `downloadAgentFillResult` 完整工作流
5. **新增辅助函数**（8个）：`getFilledCount`, `getAvgConfidence`, `getConfPercent`, `getConfCount`, `getConfClass`, `formatDecisionMode`, `getDecisionModeType`, `getDecisionModeSummary`

#### c. Style 部分

新增约80行 scoped CSS，覆盖所有新增 UI 元素：
- `.agent-fill-btn` — 开始填表按钮
- `.agent-result-inline` — 结果内嵌卡片及子元素（stats、conf-chart、decisions-table）
- `.agent-dialog-body` — 对话框通用样式（上传区域、文件列表、进度条等）
- `.agent-result-full` — 完整预览弹窗样式

---

## 三、完整工作流说明

```
用户输入"帮我填表/自动填写..." 
    → 后端识别填表意图 
    → SSE返回引导文本 + agentAction字段
    → 打字动画完成后自动弹出「源文档上传」对话框

用户拖入/选择源文档（docx/xlsx/txt/md）
    → 前端调用 uploadSourceDocument API（逐个上传）
    → 轮询 getSourceDocuments 状态直至所有文档提取完成
    → AI发送消息告知提取结果
    → 自动打开「模板上传」对话框

用户上传表格模板（xlsx/docx）+ 可选填写需求说明
    → 前端调用 uploadTemplateFile → parseTemplateSlots → fillTemplate API
    → 进度条模拟动画（0-100%）
    → 填表完成后调用 getTemplateDecisions 获取所有决策
    → AI发送消息展示摘要统计（总字段数、填充率、平均置信度）

用户查看内嵌结果卡片
    → 点击「下载填充文件」：调用 downloadTemplateResult API → 浏览器下载
    → 点击「预览分析」：弹出完整决策表格 + 决策方式分布图
```

---

## 四、涉及 API 汇总

所有 API 均为现有接口，本次修改仅新增了前端封装层：

| API | 方法 | 路径 | 服务 |
|-----|-----|------|------|
| 上传源文档 | POST | `/api/v1/file/source-document` | file-service |
| 查询源文档状态 | GET | `/api/v1/file/source-documents` | file-service |
| 上传模板 | POST | `/api/v1/template/upload` | file-service |
| 解析模板槽位 | POST | `/api/v1/template/{id}/parse-slots` | file-service |
| 填写模板 | POST | `/api/v1/ai/fill` | ai-service |
| 获取填充决策 | GET | `/api/v1/template/{id}/decisions` | ai-service |
| 下载结果 | GET | `/api/v1/template/{id}/download` | file-service |

---

## 五、与答辩材料的对应关系

本次修改对应答辩材料中以下章节的更新：

### 5.1 系统架构升级

在原有三层架构（用户层 → 服务层 → 数据层）基础上，补充说明 **AI Agent 编排层**概念：

> "系统在 v1.1 版本中引入了轻量级 Agent 架构。在 AI 对话模块中，后端负责意图识别（Classifier），前端负责状态编排（Orchestrator）和工具调用（Tool Calling），通过现有 REST API 作为 Tools 完成多步骤的自动化表单填写任务，无需额外的 Agent 框架。"

### 5.2 技术创新点补充

**新增技术亮点**：
1. **对话驱动的工作流**：用户无需主动切换页面，AI 对话自然引导完成多步骤任务
2. **前端 Agent 编排**：利用现有 API 在前端实现状态机式的多步工作流，轻量且无耦合
3. **意图识别关键词引擎**：20个关键词覆盖中英文"填表"表达，O(1)匹配无需调用 LLM，响应速度更快

### 5.3 PPT 补充建议（新增1页）

**幻灯片标题**：AI Agent 智能填表工作流

**内容要点**：
- 左侧：工作流程图（用户对话 → 弹窗引导 → 上传 → AI填写 → 结果展示）
- 右侧：技术架构说明（意图识别 → agentAction信号 → 前端状态机 → 工具调用）
- 底部：亮点数据（0个新API、4个文件改动、全程无页面跳转）

---

## 六、构建验证

| 验证项 | 结果 |
|------|------|
| 前端 `npm run build` | ✅ 成功，无错误 |
| 后端 `mvn compile` (ai-service) | ✅ 成功，无错误 |
| 现有功能兼容性 | ✅ 非填表对话不受影响 |
