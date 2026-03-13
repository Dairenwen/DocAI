源文档上传时先抽取结构化数据，模板上传时只做槽位识别、候选检索、难例判定和代码写回。

这样最符合比赛场景：速度快、错误可控、结果可解释。

阿里云这套双模型里，Qwen-Doc-Turbo 更适合做文档到结构化 JSON 的抽取，官方明确把它定位为信息抽取、分类打标、摘要生成等数据挖掘场景，强调比通用对话模型更适合快速、规范地输出结构化数据；它支持 文件URL（推荐）、文件ID、纯文本 三种输入，且中国内地场景要求使用北京地域 API Key。文件URL(doc_url) 方式当前走 DashScope 协议，文件ID(file-id) 上传与管理必须走 OpenAI 兼容 SDK。qwen3.5-plus 则更适合放在候选字段判定、冲突消解、复杂问答这一层；阿里云当前也明确把它列为推荐的推理模型之一，且上下文窗口达到 1,000,000 token。北京地域 OpenAI 兼容接入点为 https://dashscope.aliyuncs.com/compatible-mode/v1。 (阿里云帮助中心)

## 一、最终架构

默认技术栈这样定，Claude Code 最容易生成可运行代码：

- 前端：Vue 3
- 主后端：Node.js + Express
- Word 模板处理子服务：Python + python-docx
- Excel 模板处理：Node.js + exceljs
- 数据库：MySQL
- 缓存：Redis
- 文件存储：本地 uploads/ 开发，OSS 上线
- 向量/检索：先用 MySQL + 倒排检索实现，后续可升级 ES / OpenSearch

之所以这样搭，是因为 exceljs 官方支持读取、修改并写回 .xlsx 文件；python-docx 官方提供了表格、单元格、段落访问能力，适合处理未知 Word 表单模板。需要注意的是，python-docx 直接给 cell.text 赋值会替换单元格原有内容，所以对 Word 模板要优先走“定点替换文本/运行块”而不是粗暴整格覆盖。(GitHub)

## 二、分阶段方案：每一阶段的方法与模型

# 阶段 0：基础设施与统一数据契约

### 方法

先不要急着写“自动填表”按钮，先定义统一数据结构。后面所有模块都围绕这些结构通信：

1. SourceDocument
2. ExtractedField
3. TemplateSlot
4. FillCandidate
5. FillDecision
6. FillAuditLog

### 模型

不调用模型

### 必须实现的数据结构

{
  "SourceDocument": {
    "docId": "string",
    "fileName": "string",
    "fileType": "docx|md|txt|xlsx|pdf",
    "storagePath": "string",
    "uploadStatus": "uploaded|parsed|failed"
  },
  "ExtractedField": {
    "fieldId": "string",
    "docId": "string",
    "fieldKey": "string",
    "fieldName": "string",
    "fieldValue": "string",
    "fieldType": "text|date|number|phone|org|person|enum",
    "aliases": ["string"],
    "sourceText": "string",
    "sourceLocation": "string",
    "confidence": 0.0
  },
  "TemplateSlot": {
    "slotId": "string",
    "templateId": "string",
    "templateType": "docx|xlsx",
    "label": "string",
    "context": "string",
    "position": {},
    "expectedType": "text|date|number|phone|org|person|enum",
    "required": true
  },
  "FillCandidate": {
    "slotId": "string",
    "fieldId": "string",
    "score": 0.0,
    "matchReasons": ["string"]
  },
  "FillDecision": {
    "slotId": "string",
    "finalValue": "string",
    "finalFieldId": "string",
    "finalConfidence": 0.0,
    "decisionMode": "rule_only|rule_plus_llm|fallback_blank",
    "reason": "string"
  }
}

# 阶段 1：源文档接入与结构化抽取

### 方法

对每个源文档逐文件独立抽取，不要把所有文档一次性混在一个 prompt 里。原因很简单：

- 更容易定位错误来源
- 每个字段都能保留文档出处
- 后续模板填写时更快
- 更利于并发处理

本阶段支持两种接入方式：

### 开发阶段

本地文件直接上传成 file-id

### 上线阶段

文件先上传 OSS，传 doc_url

### 模型

Qwen-Doc-Turbo

Qwen-Doc-Turbo 官方推荐用于结构化抽取，支持 文件URL、文件ID、纯文本 三种方式；doc_url 当前仅支持 DashScope 协议，file-id 上传必须用 OpenAI 兼容 SDK。北京地域是中国内地使用前提。(阿里云帮助中心)

### 输入

单个源文件

### 输出

统一 ExtractedField[]

### 抽取方法

每个文档调用时，提示词固定成“只抽字段，不做业务推理”。
要求输出：

- 文档标题
- 文档类型
- 关键实体
- 关键字段
- 原文证据
- 表格内容摘要
- 字段置信度

### 必须使用的抽取 Schema

Claude Code 要按这个 schema 写：

{
  "doc_title": "string",
  "doc_type": "string",
  "fields": [
    {
      "field_key": "string",
      "field_name": "string",
      "field_value": "string",
      "field_type": "text|date|number|phone|org|person|enum",
      "aliases": ["string"],
      "source_text": "string",
      "source_location": "string",
      "confidence": 0.0
    }
  ],
  "summary": "string"
}

### 本阶段准确率保证方法

1. 一文档一调用，避免跨文档污染
2. 字段必须带 source_text 和 source_location
3. 字段类型必须显式标注，比如 date / phone / org
4. 字段值不做业务归并，只忠实抽取
5. 不允许模型直接输出最终填表答案

### 这一阶段不要做的事

- 不要让 Qwen-Doc-Turbo 直接决定模板字段填什么
- 不要让它跨多个文档综合判断
- 不要在这一阶段写回模板

# 阶段 2：字段标准化与字段库构建

### 方法

把 Qwen-Doc-Turbo 抽出的字段，统一归一化成标准字段库。

### 模型

不调用模型

### 归一化规则

对每条 ExtractedField 做下面处理：

1. 文本清洗
- 去首尾空格
- 中文全角半角统一
- 去掉字段名中的冒号、括号、换行
2. 字段名标准化
例如：
- 项目名称、课题名称、申报项目名称 → project_name
- 负责人、项目负责人 → owner
- 联系电话、手机号码、电话 → phone
3. 值格式化
- 日期统一成 YYYY-MM-DD
- 电话统一成纯数字字符串
- 金额去逗号、保留原值与标准值两份
4. 构建别名词典
例如：

{
  "project_name": ["项目名称", "课题名称", "申报项目名称"],
  "owner": ["负责人", "项目负责人", "课题负责人"],
  "org_name": ["单位名称", "申报单位", "承担单位"]
}

### 数据库存储

必须有两张核心表：

#### extracted_fields

存真实字段

#### field_alias_dict

存标准字段与别名

### 本阶段准确率保证方法

1. 字段归一化优先于模型判定
2. 把同义字段提前收敛，减少后面模型误判
3. 保留原值 + 标准值，避免格式化导致信息丢失
4. 相同字段多文档出现时不立刻合并，只打 group

# 阶段 3：模板解析与槽位识别

### 方法

模板上传后，不要立刻调用模型。先用代码解析模板，找到所有待填槽位。

### 模型

不调用模型

### Excel 模板解析方法

使用 exceljs

- 读取 workbook
- 遍历每个 sheet
- 遍历每个单元格
- 找出“标签-值”结构

exceljs 官方支持读取 .xlsx、访问工作簿和单元格、修改后再写回文件，适合直接做 Excel 模板解析与回填。(GitHub)

### Word 模板解析方法

使用 Python 子服务的 python-docx

- 遍历所有表格
- 遍历每行每列
- 识别“左标签右空白”“上标签下空白”“同单元格标签:____”三类槽位
- 同时记录表格索引、行列号、段落索引

python-docx 官方提供表格单元格、段落、文本访问接口，适合表单类 Word 识别。(python-docx.readthedocs.io)

### 槽位识别规则

1. 左边非空、右边空 → 右边是槽位
2. 上边非空、下边空 → 下边是槽位
3. 文本中出现 字段名：____ → 冒号后是槽位
4. 字段长度一般 2 到 20 个字
5. 出现“说明”“备注”“填写要求”等大段文字不算标签

### 统一输出

模板无论是 Word 还是 Excel，都统一转成：

{
  "templateId": "tpl_001",
  "templateType": "xlsx",
  "slots": [
    {
      "slotId": "Sheet1!B2",
      "label": "项目名称",
      "context": "基本信息",
      "position": {
        "sheet": "Sheet1",
        "cell": "B2"
      },
      "expectedType": "text",
      "required": true
    }
  ]
}

### 本阶段准确率保证方法

1. 模板解析不用模型，完全走代码规则
2. 每个槽位都保留上下文 context
3. 预判 expectedType，为后面匹配过滤掉明显错误候选

# 阶段 4：候选字段召回

### 方法

对于每个 TemplateSlot，先不问模型，先用规则从字段库里召回 Top-K 候选。

### 模型

不调用模型

### 候选召回策略

每个槽位执行这 5 步：

1. 精确别名匹配
slot.label == field_name 或 slot.label in aliases
2. 标准字段键匹配
模板标签先标准化后，再查 field_key
3. 类型过滤
- 槽位是日期，只留 fieldType=date
- 槽位是手机号，只留 fieldType=phone
4. 上下文关键字加权
例如槽位 context 是“申报单位信息”，则 org_name 权重加分
5. 多文档投票
同一值如果在多个文档同时出现，得分上升

### 候选得分公式

Claude Code 直接按这个实现：

candidate_score =
0.40 * alias_match_score +
0.20 * type_match_score +
0.20 * source_vote_score +
0.10 * context_match_score +
0.10 * field_confidence

各项取值范围统一 0 到 1。

### 召回输出

每个槽位只保留 Top-5 候选：

{
  "slotId": "Sheet1!B2",
  "candidates": [
    {
      "fieldId": "f_001",
      "fieldKey": "project_name",
      "fieldValue": "智能文档处理系统",
      "candidateScore": 0.92,
      "sourceDoc": "doc_01.docx",
      "sourceText": "项目名称：智能文档处理系统"
    }
  ]
}

### 本阶段准确率保证方法

1. 先规则再模型
2. 只把高相关候选送给模型
3. 模型永远不直接面对全量原始文档
4. 候选结果必须带 sourceText

# 阶段 5：难例判定与最终选择

### 方法

只对“规则不能稳定判断”的槽位调用大模型。

### 模型

qwen3.5-plus

阿里云当前把 qwen3.5-plus 列为推荐推理模型之一，适合复杂判断。它默认开启思考模式；而阿里云的结构化输出和 JSON 模式在开启思考时会有限制，官方错误说明里明确写到：使用 response_format=json_object 时若开启 thinking 会报错，因此在线填表阶段应关闭思考模式，优先换取更低延迟和更稳定的结构化回复。(阿里云帮助中心)

### 什么时候调用 qwen3.5-plus

满足任一条件才调用：

1. Top-1 与 Top-2 分差 < 0.15
2. Top-1 分数 < 0.75
3. 候选值类型冲突
4. 多文档给出不同值
5. 模板标签是长字段名，规则词典没命中

### 给 qwen3.5-plus 的输入

只传：

- 当前槽位 label
- 当前槽位 context
- Top-5 候选
- 每个候选的 sourceText
- 输出要求

绝不传全量原始文档

### 模型输入示例

{
  "slot": {
    "label": "项目承担单位",
    "context": "项目基本信息"
  },
  "candidates": [
    {
      "fieldKey": "org_name",
      "fieldValue": "金陵科技学院",
      "sourceDoc": "doc_01.docx",
      "sourceText": "申报单位：金陵科技学院",
      "score": 0.81
    },
    {
      "fieldKey": "dept_name",
      "fieldValue": "软件工程学院",
      "sourceDoc": "doc_02.docx",
      "sourceText": "所属单位：软件工程学院",
      "score": 0.73
    }
  ]
}

### 模型输出要求

输出固定 JSON：

{
  "slotId": "string",
  "selectedFieldId": "string",
  "selectedValue": "string",
  "modelConfidence": 0.0,
  "reason": "string"
}

### 准确率保证方法

1. 模型只做选择题，不做开放式生成
2. 只从候选集中选，不允许自己编新值
3. 必须返回 reason
4. 若 modelConfidence < 0.65，则拒填

### 注意

阿里云结构化输出文档说明：

- json_object 模式要求提示词里出现 “JSON”
- json_schema 更严格
- thinking 模式下不要直接配 json_object，否则会报错。(阿里云帮助中心)

所以实现时建议：

- 在线填表：enable_thinking=false
- 优先尝试 json_object
- 若模型版本结构化输出不稳定，则退回“纯文本 JSON + 本地校验修复”
- 不把结构化输出能力作为唯一兜底

# 阶段 6：最终置信度融合与拒填策略

### 方法

规则分和模型分融合，得出最终置信度。

### 模型

不额外调用模型

### 最终置信度公式

final_confidence =
0.70 * candidate_score +
0.30 * model_confidence

若未调用模型，则：

final_confidence = candidate_score

### 决策阈值

Claude Code 直接实现这套规则：

- >= 0.85：直接填写
- 0.70 ~ 0.85：填写，但写入 audit_log 标记 review_recommended
- < 0.70：不填写，保留空白，并记录 fallback_blank

### 为什么这样设计

比赛里“错填”通常比“少填”更伤准确率。
所以低置信度场景，系统应拒绝瞎填，并留下可解释日志。

# 阶段 7：模板写回

### Excel 写回方法

使用 exceljs

- 定位 sheet + cell
- 只改目标值
- 不改整张 sheet 结构
- 遇到合并单元格，只写主单元格
- 遇到公式格，不覆盖公式

### Excel 写回模型

不调用模型

### Word 写回方法

使用 Python 子服务 + python-docx

分两类：

1. 空单元格填值
- 目标位置为空时，可直接写入
2. 同单元格“字段名：___”替换
- 定位冒号后的文本区域替换
- 尽量不整段重写，减少格式破坏

python-docx 文档说明，给 cell.text 直接赋值会替换单元格内容，所以 Word 里优先做“局部文本替换”；只有单元格本身就是空白输入位时，才允许整格写值。(python-docx.readthedocs.io)

### Word 写回模型

不调用模型

### 写回后自动校验

写回完成后立刻重新读取结果文件，校验：

- 目标槽位是否已写入
- 非空字段是否为空
- 日期/手机号/数字格式是否正确
- 模板其它区域是否被误改

如果校验不通过：

- 回滚
- 记录错误日志
- 返回“填表失败”

# 阶段 8：结果审计与可追溯输出

### 方法

每个填入值都必须能追溯。

### 模型

不调用模型

### 每个填入字段都要生成 audit log

{
  "slotId": "Sheet1!B2",
  "slotLabel": "项目名称",
  "finalValue": "智能文档处理系统",
  "finalConfidence": 0.91,
  "decisionMode": "rule_plus_llm",
  "sourceDoc": "doc_01.docx",
  "sourceText": "项目名称：智能文档处理系统",
  "reason": "规则别名命中且模型在前2候选中选择该值"
}

### 这样做的意义

答辩时你可以直接展示：

- 这个值从哪份文档来的
- 为什么填这个
- 为什么不是另一个候选
- 哪些字段是系统主动拒填的

这比“黑盒自动填表”更有说服力。

## 三、最终接口设计

Claude Code 直接按这组接口生成。

### 1. 上传源文档并抽取

POST /api/source/upload

返回：

{
  "docId": "doc_001",
  "status": "parsed"
}

### 2. 查询已抽取字段

GET /api/source/:docId/fields

### 3. 上传模板并解析槽位

POST /api/template/upload

返回：

{
  "templateId": "tpl_001",
  "templateType": "xlsx|docx"
}

### 4. 解析模板槽位

POST /api/template/:templateId/parse

返回：

{
  "templateId": "tpl_001",
  "slots": []
}

### 5. 自动匹配并填表

POST /api/template/:templateId/fill

请求体：

{
  "docIds": ["doc_001", "doc_002"]
}

返回：

{
  "templateId": "tpl_001",
  "outputFile": "/outputs/result_001.xlsx",
  "filledCount": 18,
  "blankCount": 2,
  "auditId": "audit_001"
}

### 6. 获取填表审计日志

GET /api/fill/audit/:auditId

## 四、数据库表

Claude Code 必须生成这些表：

1. source_documents
2. extracted_fields
3. field_alias_dict
4. template_files
5. template_slots
6. fill_candidates
7. fill_decisions
8. fill_audit_logs

## 五、真正能稳住准确率的 10 条硬规则

1. 源文档抽取和模板填写彻底分离
2. Qwen-Doc-Turbo 只做抽取，不做最终填写
3. qwen3.5-plus 只判难例，不读全量原始文档
4. 模板槽位识别优先用代码规则
5. 字段召回优先走别名词典和类型过滤
6. 模型只在 Top-K 候选里选，不允许自由发挥
7. 所有字段都必须带 sourceText
8. 低置信度允许拒填，不允许乱填
9. 写回后必须二次校验
10. 所有结果必须能追溯到来源文档与来源片段

## 六、直接交给 Claude Code 的开发任务书

把下面这段原样交给 Claude Code 就可以：

请为一个“智能文档自动填表系统”生成完整可运行代码，技术栈为：

- 前端：Vue 3
- 主后端：Node.js + Express
- Word模板处理子服务：Python + python-docx
- Excel模板处理：Node.js + exceljs
- 数据库：MySQL
- 缓存：Redis

系统使用两个阿里云模型：

1. Qwen-Doc-Turbo：仅用于源文档结构化抽取
2. qwen3.5-plus：仅用于难例字段判定与冲突消解

严格按以下架构实现：

一、源文档阶段

1. 提供源文档上传接口，支持 docx、md、txt、xlsx、pdf
2. 每个源文档单独调用 Qwen-Doc-Turbo 进行结构化抽取
3. 开发环境用 file-id 模式；生产环境预留 doc_url 模式
4. 抽取结果必须包含：
   - fieldKey
   - fieldName
   - fieldValue
   - fieldType
   - aliases
   - sourceText
   - sourceLocation
   - confidence
5. 将抽取结果入库到 extracted_fields 表
6. 建立 field_alias_dict 表进行字段标准化映射

二、模板阶段

1. 支持上传 docx / xlsx 模板
2. xlsx 用 exceljs 解析槽位
3. docx 用 Python 子服务 + python-docx 解析槽位
4. 识别三类槽位：
   - 左标签右空白
   - 上标签下空白
   - 同单元格 标签:____
5. 统一输出 template_slots

三、匹配阶段

1. 对每个 slot 先执行规则召回，不调用模型
2. 规则召回包括：
   - label 与 fieldName/aliases 精确匹配
   - 标准字段键匹配
   - expectedType 与 fieldType 过滤
   - context 关键词加权
   - 多文档一致性投票
3. 为每个 slot 生成 Top-5 候选
4. 候选得分公式：
   candidate_score =
   0.40 * alias_match_score +
   0.20 * type_match_score +
   0.20 * source_vote_score +
   0.10 * context_match_score +
   0.10 * field_confidence

四、LLM 判定阶段

1. 仅当以下情况调用 qwen3.5-plus：
   - Top1 与 Top2 分差 < 0.15
   - Top1 < 0.75
   - 候选冲突
2. qwen3.5-plus 只接收：
   - slot.label
   - slot.context
   - Top-5 candidates
   - 每个候选的 sourceText
3. qwen3.5-plus 不允许读取全量原始文档
4. qwen3.5-plus 只允许从候选中选择，不允许生成新值
5. 若模型返回 modelConfidence < 0.65，则拒填
6. 在线阶段设置 enable_thinking=false

五、最终决策

1. 若未调用模型：
   final_confidence = candidate_score
2. 若调用模型：
   final_confidence = 0.70 * candidate_score + 0.30 * model_confidence
3. 阈值：
   - >=0.85 直接填写
   - 0.70~0.85 填写并标记 review_recommended
   - <0.70 不填写，记录 fallback_blank

六、写回阶段

1. xlsx 用 exceljs 写回
2. docx 用 python-docx 写回
3. 必须保留模板原格式，尽量只改目标槽位
4. 写回后重新读取结果文件做校验：
   - 是否成功写入
   - 日期/手机号/数字格式是否正确
   - 模板其他区域是否被误改

七、审计与展示

1. 每个填写结果必须生成 audit log
2. audit log 包含：
   - slotId
   - slotLabel
   - finalValue
   - finalConfidence
   - decisionMode
   - sourceDoc
   - sourceText
   - reason
3. 提供接口查看 audit log
4. 前端展示：
   - 模板预览
   - 自动填写结果
   - 字段来源说明
   - 低置信度标记

请直接生成：

- 项目目录结构
- 后端完整代码
- Python 子服务代码
- 数据库建表 SQL
- 前端页面与接口联调代码
- .env.example
- README
- 本地运行步骤

## 七、最终一句话方案

最符合大赛要求的做法，不是让一个大模型从头到尾“端到端自动填表”，而是：

Qwen-Doc-Turbo 先把源文档抽成带证据的字段库，模板阶段再用规则召回 + qwen3.5-plus 判难例 + 代码写回 + 写后校验。

这套方案最能稳住准确率，也最容易在 VSCode 里被 Claude Code 一次性生成成体系代码。