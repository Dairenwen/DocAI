# DocAI 智能文档处理系统 — 作品介绍

---

## 一、作品简介

DocAI是基于大语言模型的智能文档信息提取与自动填表系统，支持Word/Excel/TXT/Markdown多格式文档解析，通过AI自动提取关键信息并智能填充至模板表格，将数小时人工整理缩短至90秒内完成，准确率≥90%，具备全链路审计追溯能力。

---

## 二、开源代码与组件使用情况说明

### 2.1 开源框架与组件

| 组件名称 | 版本 | 开源协议 | 用途说明 |
|---------|------|---------|---------|
| **Vue.js 3** | 3.5.25 | MIT | 前端单页应用框架 |
| **Element Plus** | 2.13.3 | MIT | 前端UI组件库 |
| **Pinia** | 3.0.4 | MIT | 前端状态管理 |
| **Vite** | - | MIT | 前端构建工具 |
| **Axios** | 1.13.6 | MIT | HTTP客户端 |
| **ECharts** | 6.0.0 | Apache-2.0 | 数据可视化图表 |
| **Marked** | 17.0.4 | MIT | Markdown渲染引擎 |
| **DOMPurify** | 3.3.3 | Apache-2.0/MPL-2.0 | XSS安全过滤 |
| **VueOffice (Docx/Excel)** | 1.6.3/1.7.14 | MIT | 文档在线预览 |
| **Mammoth** | 1.12.0 | BSD-2 | Word文档解析 |
| **SheetJS (XLSX)** | 0.18.5 | Apache-2.0 | Excel客户端处理 |
| **wangEditor** | 5.1.23 | MIT | 富文本编辑器 |
| **Spring Boot** | 3.2 | Apache-2.0 | 后端微服务框架 |
| **Spring Cloud Gateway** | 2023.0.0 | Apache-2.0 | API网关路由与限流 |
| **Spring AI Alibaba** | 1.0.0.2 | Apache-2.0 | 大模型集成框架（DashScope） |
| **MyBatis-Plus** | 3.5.7 | Apache-2.0 | ORM持久化框架 |
| **Apache POI** | 5.2.4 | Apache-2.0 | Excel/Word模板读写 |
| **FastJSON 2** | 2.0.43 | Apache-2.0 | JSON解析 |
| **JJWT** | 0.11.5 | Apache-2.0 | JWT认证令牌生成与校验 |
| **阿里云OSS SDK** | 3.17.4 | MIT | 对象存储（可选） |
| **HttpClient 5** | 5.2.1 | Apache-2.0 | HTTP客户端 |
| **pinyin4j** | 2.5.1 | GPL-2.0 (with CE) | 中文拼音处理 |
| **Nacos** | 2.2 | Apache-2.0 | 服务注册与配置中心 |
| **MySQL** | 8.4 | GPL-2.0 | 关系型数据库 |
| **Redis** | 7.0 | BSD-3 | 分布式缓存 |
| **Nginx** | 1.24 | BSD-2 | 反向代理与静态资源服务 |
| **Qt** | 5.14.2 | LGPL-3.0 | 跨平台C++桌面GUI框架 |
| **Docker Compose** | v2+ | Apache-2.0 | 容器化中间件编排 |

### 2.2 AI模型

| 模型 | 提供商 | 用途 |
|------|--------|------|
| Qwen2.5-VL-7B-Instruct | 阿里云/开源 | 多模态文档信息提取（文字+图片） |
| qwen-plus / qwen-turbo | 阿里云DashScope | 难例LLM判定与AI对话 |
| DeepSeek（可选） | DeepSeek | 备选推理模型 |

### 2.3 自主开发部分

本项目自主实现了以下核心模块，**非直接引用已有开源项目**：
- 多格式文档统一提取管线（docx/xlsx/txt/md四格式解析+LLM抽取）
- 八阶段模板智能填充流水线（槽位解析→候选召回→难例判定→置信度融合→模板写回）
- 五维加权候选召回评分算法（别名×0.40 + 类型×0.20 + 投票×0.20 + 上下文×0.10 + 置信度×0.10）
- 全链路审计日志追溯系统
- SSE流式AI对话与文档在线编辑功能
- Qt C++桌面客户端完整实现
- 微信小程序适配层

---

## 三、作品安装说明

### 3.1 环境要求

| 软件 | 最低版本 | 用途 |
|------|---------|------|
| JDK | 17+ | 后端运行 |
| Maven | 3.8+ | Java项目构建 |
| Node.js | 18+ | 前端构建 |
| Docker & Docker Compose | 24+ / v2+ | 中间件容器化 |

**硬件建议**：4核CPU / 8GB内存 / 20GB磁盘（开发环境最低配置）

### 3.2 必需环境变量

```
DOC_DASHSCOPE_API_KEY=sk-xxxxxxxx  （阿里云DashScope API密钥，从 dashscope.console.aliyun.com 获取）
```

### 3.3 后端一键启动（Windows）

```powershell
cd docai-pro-后端
$env:DOC_DASHSCOPE_API_KEY = "sk-xxxxxxxx"
.\start-lite-windows.ps1
```

脚本自动完成：环境检测 → Maven构建 → 前端npm构建 → Docker启动MySQL/Redis/Nacos/Nginx → 微服务启动（user-service:9001 → file-service:9003 → ai-service:9002 → gateway:18080）

### 3.4 前端单独启动（开发模式）

```bash
cd docai-frontend-前端
npm install
npm run dev
```

### 3.5 Qt桌面客户端

使用Qt Creator 5.14.2打开`DocAI-Qt客户端/DocAI.pro`，配置MinGW 64-bit编译器后构建运行。

### 3.6 微信小程序

使用微信开发者工具导入`wechat-mini-program-小程序`目录，配置AppID后编译预览。

### 3.7 启动验证

| 验证项 | 访问地址 | 预期结果 |
|--------|---------|---------|
| Web前端 | http://localhost:8080 | 显示登录页 |
| API网关 | http://localhost:18080 | 网关响应 |
| Nacos控制台 | http://localhost:8848/nacos | nacos/nacos登录 |

---

## 四、设计思路

### 4.1 整体架构

系统采用**前后端分离 + 微服务架构**设计，分为四个独立端：

```
用户端（Web/Qt/小程序） → Nginx反向代理(8080) → Spring Cloud Gateway(18080)
  → 微服务层：user-service(9001) + file-service(9003) + ai-service(9002)
  → 基础设施层：MySQL 8.4 + Redis 7.0 + Nacos 2.2（Docker Compose编排）
```

### 4.2 核心设计理念

1. **双阶段分离架构**：将文档信息提取与模板填充解耦为两个独立阶段——源文档上传时异步预提取结构化字段，模板填充时从已有字段库中快速检索匹配，避免实时调用LLM造成的高延迟。

2. **规则优先、模型辅助**：90%以上字段由五维加权规则引擎快速决策，仅对Top1/Top2分差<0.05的难例才调用LLM仲裁，兼顾速度与准确率，同时大幅降低API调用成本。

3. **多格式统一框架**：docx/xlsx/txt/md四种格式统一进入同一提取管线，新增格式只需实现解析适配器即可扩展，无需改动下游填充逻辑。

4. **全链路可审计**：每个填入字段都生成包含来源文档、原文、候选评分、决策方式、决策理由的完整审计日志，支持结果验证与持续优化。

5. **多端覆盖**：Web端满足日常办公，Qt桌面端提供原生体验，微信小程序实现移动端触达，三端共享同一套后端API。

### 4.3 填充流水线设计

八阶段处理流程：基础设施定义 → 源文档异步提取 → 字段标准化归一 → 模板槽位解析 → 五维候选召回(Top5) → LLM难例判定 → 置信度阈值融合 → 模板写回+审计追溯

---

## 五、设计重点难点

### 5.1 多格式文档统一提取

**难点**：Word文档中包含段落、表格、嵌入图片等多种内容形式，Excel有多Sheet多列结构，不同格式的信息组织方式差异巨大。

**解决方案**：设计统一的Document→Text→LLM→Fields管线。Word通过Apache POI提取段落文本+表格文本+嵌入图片（最多2张），Excel逐Sheet逐行拼接，TXT/MD直接读取。统一截断至15000字符后送入Qwen2.5-VL多模态模型进行结构化抽取。图片通过VL模型实现视觉理解，提取图片中的表格和文字信息。

### 5.2 候选召回与决策精度

**难点**：同一字段在不同文档中可能有不同名称（如"项目名称"与"课题名称"），多文档可能包含重复或冲突数据。

**解决方案**：构建字段别名词典(field_alias_dict)支持40+中文别名到30+标准键的映射。五维加权评分综合别名匹配度、类型匹配度、多文档投票、上下文关联、提取置信度。多级去重机制（字段级、候选级、行级）确保数据不重复不冲突。

### 5.3 表格直写与统计聚合

**难点**：模板中的表头行模板需要从源Excel复制整列数据并自动去重，汇总型槽位需要从数据列中自动计算统计值。

**解决方案**：`directTableCopy`策略直接匹配源Excel与模板的表头结构，跳过逐槽LLM调用，将匹配列的数据行批量复制。识别"总"→SUM、"平均"→AVG等关键词自动执行统计聚合。实测38000+原始行去重为4500+有效行，处理时间约5秒。

### 5.4 SSE流式AI对话与文档在线编辑

**难点**：大模型生成响应耗时长，需实时展示生成过程；用户通过自然语言修改文档需识别编辑意图并保持原文档格式。

**解决方案**：采用SSE(Server-Sent Events)实现流式传输，前端逐token渲染打字效果。通过关键词检测（修改/编辑/删除/润色等12个动词）自动识别编辑意图，AI输出修改后的完整文档内容，后端自动保存为docx/txt/md格式。

---

## 六、创新描述

**创新点一：源文档预提取+模板实时匹配的双阶段解耦架构。** 将信息提取与模板填充分离为异步预提取和实时匹配两个独立阶段，模板填充时无需再次调用LLM，响应时间从分钟级降至秒级，为业界首创的文档填表加速方案。

**创新点二：规则优先、模型辅助的多级混合决策引擎。** 独创五维加权候选召回+LLM难例仲裁的分层决策架构，90%字段纯规则毫秒级决策，仅难例调用LLM，相比纯LLM方案API调用量降低80%+，兼顾速度、准确率与成本可控。

**创新点三：多模态视觉语言模型驱动的文档全要素提取。** 业界竞品仅支持纯文本OCR识别，DocAI集成Qwen2.5-VL多模态视觉模型，可同时理解文档中的文字、表格和嵌入图片，实现图文混排文档的全要素结构化提取，填补了智能填表领域的多模态识别空白。

---

## 七、AI在作品中的应用说明

### 7.1 AI技术应用概览

DocAI系统深度融合了大语言模型（LLM）和多模态视觉语言模型（VL Model），AI贯穿系统的文档处理、智能决策、用户交互三大核心环节。

### 7.2 具体应用场景

#### （1）多模态文档信息提取

- **使用模型**：Qwen2.5-VL-7B-Instruct（多模态视觉语言模型）
- **应用方式**：用户上传Word/Excel/TXT/Markdown文档后，系统自动异步调用VL模型，将文档文本（截断至15000字符）及Word中嵌入的图片（最多2张）一并送入模型进行结构化信息抽取
- **AI输出**：模型以JSON格式返回提取的字段键值对，包含字段名、字段值、字段类型、来源原文、置信度评分等
- **失败兜底**：若AI模型调用失败，系统自动降级为基于正则表达式的规则提取（模式：`key:value`行匹配），确保可用性

#### （2）智能填表难例判定

- **使用模型**：qwen-plus（通义千问大语言模型）
- **触发条件**：仅在规则引擎无法确定最佳候选时触发——即Top1与Top2候选分差<0.05且值不同，或Top1得分<0.30且别名匹配度<0.2
- **应用方式**：将槽位标签、上下文、Top5候选字段及评分组成Prompt发送给LLM，LLM从候选中选择最佳值并给出置信度
- **融合策略**：最终置信度 = 0.70 × 规则候选分 + 0.30 × LLM置信度，决策模式标记为`rule_plus_llm`

#### （3）LLM兜底填充

- **使用模型**：qwen-plus
- **触发条件**：经过规则决策和难例判定后仍有空槽位时
- **应用方式**：汇聚所有已提取字段的来源原文（<8000字符则补充完整源文档），构造提取提示词，请求LLM重新从原始文本中提取目标字段
- **决策标记**：`llm_fallback`，置信度设为0.65

#### （4）AI智能对话与文档编辑

- **使用模型**：qwen-plus / qwen-turbo / DeepSeek（用户可热切换）
- **交互方式**：基于SSE（Server-Sent Events）实现流式实时对话，支持多轮上下文
- **功能集**：
  - 文档内容摘要、信息提取、润色优化、格式调整
  - 数据分析（关联Excel文件自动生成SQL查询）
  - **AI文档在线编辑**：通过自然语言指令（如"删除第三段"、"将标题改为XXX"），AI自动读取原文档，输出修改后的完整内容并保存为可下载文件
  - 编辑意图识别：基于12个关键动词（修改/编辑/删除/增加/添加/替换/改为/改成/更新/移除/插入/润色）自动检测

#### （5）Excel智能问答

- **使用模型**：qwen-plus
- **应用方式**：用户上传Excel后可通过自然语言提问，系统将Excel转为MySQL动态表，AI根据用户问题自动生成SQL查询语句，执行后返回结构化结果和可视化图表

### 7.3 AI调用优化策略

| 策略 | 说明 | 效果 |
|------|------|------|
| 规则优先 | 90%+字段由五维加权规则引擎决策 | LLM调用量降低80%+ |
| 信号量并发控制 | Semaphore限制同时LLM请求数 | 避免API限流 |
| 1.5秒调用间隔 | 相邻LLM请求间强制等待 | 平滑API负载 |
| 指数退避重试 | 失败后最多3次重试 | 提升稳定性 |
| 30秒超时保护 | 超时后跳过LLM，使用规则Top1 | 保障响应速度 |
| 模型热切换 | 运行时切换qwen-plus/DeepSeek | 灵活适配 |

---

## 八、开发制作工具

### 8.1 开发工具

| 工具名称 | 版本/说明 | 用途 |
|---------|----------|------|
| **IntelliJ IDEA** | Ultimate | Java后端微服务开发（Spring Boot） |
| **Visual Studio Code** | Latest | 前端Vue 3开发 & 脚本编写 |
| **Qt Creator** | 5.14.2 | Qt C++桌面客户端开发 |
| **微信开发者工具** | Latest | 微信小程序开发与调试 |
| **Navicat / DataGrip** | - | MySQL数据库管理与SQL调试 |
| **Postman** | - | API接口调试与测试 |
| **Docker Desktop** | 24+ | 中间件容器化运行环境 |
| **Git** | 2.30+ | 版本控制 |

### 8.2 构建与部署工具

| 工具名称 | 版本 | 用途 |
|---------|------|------|
| **Apache Maven** | 3.8+ | Java项目构建与依赖管理 |
| **npm** | 9+ | 前端Node.js包管理 |
| **Vite** | Latest | 前端开发热重载与生产构建 |
| **qmake (MinGW 64-bit)** | Qt 5.14.2 | Qt项目编译构建 |
| **Inno Setup** | 6.x | Windows桌面端安装包打包 |
| **Docker Compose** | v2+ | 中间件统一编排（MySQL/Redis/Nacos/Nginx） |
| **Nginx** | 1.24 | 前端静态资源部署与API反向代理 |

### 8.3 运行环境

| 环境 | 版本 | 说明 |
|------|------|------|
| **JDK** | 17+ | Java后端运行时 |
| **Node.js** | 18+ | 前端构建运行时 |
| **MySQL** | 8.4 | 业务数据存储 |
| **Redis** | 7.0 | 分布式缓存（Token/会话/锁） |
| **Nacos** | 2.2 | 微服务注册中心与配置中心 |

### 8.4 AI平台与工具

| 平台/工具 | 说明 |
|----------|------|
| **阿里云DashScope** | 通义千问系列模型API（qwen-plus/qwen-turbo） |
| **Spring AI Alibaba** | Spring生态AI集成框架，对接DashScope |
| **Qwen2.5-VL-7B** | 开源多模态视觉语言模型（可本地部署或API调用） |
| **Ollama / vLLM** | 本地模型推理引擎（可选，用于私有化部署） |

### 8.5 设计与文档工具

| 工具 | 用途 |
|------|------|
| **Markdown** | 项目文档撰写 |
| **Draw.io / Mermaid** | 架构图与流程图绘制 |
| **GitHub Copilot** | AI辅助编程 |

---

## 九、技术路线图

### 9.1 总体技术路线

```mermaid
graph LR
    subgraph 技术选型阶段
        A1[需求分析] --> A2[多格式文档解析需求]
        A1 --> A3[AI大模型集成需求]
        A1 --> A4[多端覆盖需求]
    end

    subgraph 核心技术方案
        A2 --> B1[Apache POI 5.2.4<br>Word/Excel读写]
        A2 --> B2[多格式统一提取管线<br>docx/xlsx/txt/md]
        A3 --> B3[Spring AI Alibaba<br>DashScope SDK集成]
        A3 --> B4[Qwen2.5-VL-7B<br>多模态视觉模型]
        A3 --> B5[qwen-plus/turbo<br>文本大语言模型]
        A4 --> B6[Vue 3 + Element Plus<br>Web前端]
        A4 --> B7[Qt 5.14.2 C++<br>桌面客户端]
        A4 --> B8[微信小程序<br>移动端]
    end

    subgraph 架构设计
        B1 --> C1[Spring Cloud<br>微服务架构]
        B3 --> C1
        C1 --> C2[API Gateway<br>统一入口]
        C1 --> C3[Docker Compose<br>中间件编排]
        C1 --> C4[Nacos<br>服务治理]
    end

    subgraph 创新算法
        B4 --> D1[多模态文档提取]
        B5 --> D2[规则+LLM混合决策]
        D2 --> D3[五维加权候选召回]
        D2 --> D4[LLM难例仲裁]
        D1 --> D5[全链路审计追溯]
    end
```

### 9.2 技术栈分层架构

```mermaid
graph TB
    subgraph 用户接入层
        U1[🌐 Web浏览器<br>Vue 3 + Element Plus + Vite]
        U2[🖥️ Qt桌面端<br>Qt 5.14.2 C++ MinGW64]
        U3[📱 微信小程序<br>原生WXML/WXSS]
    end

    subgraph 网络代理层
        N1[Nginx 1.24<br>端口:8080<br>静态资源 + API反向代理<br>SSE流式支持 proxy_buffering=off]
    end

    subgraph API网关层
        G1[Spring Cloud Gateway<br>端口:18080<br>路由转发 / JWT认证 / 超时300s<br>白名单: /users/auth, /verification-code]
    end

    subgraph 微服务层
        S1[user-service<br>端口:9001<br>JWT认证 / BCrypt密码<br>邮箱验证码 / 用户CRUD]
        S2[ai-service<br>端口:9002<br>文档提取 / 模板填充<br>AI对话 / 文档编辑]
        S3[file-service<br>端口:9003<br>Excel上传 / 动态建表<br>预览 / 下载]
        S4[gateway-service<br>端口:18080<br>路由 / 认证过滤器<br>限流 / SSE代理]
        S5[mcp-service<br>端口:9004<br>MCP协议适配]
        S6[common-service<br>共享工具库<br>JwtUtil / OssService<br>EmailService / RedisUtil]
    end

    subgraph 数据与缓存层
        D1[(MySQL 8.4<br>16张业务表<br>utf8mb4编码)]
        D2[(Redis 7.0<br>Token缓存<br>会话存储 / 分布式锁)]
        D3[Nacos 2.2<br>服务注册中心<br>配置中心<br>standalone模式]
    end

    subgraph AI模型层
        AI1[Qwen2.5-VL-7B-Instruct<br>多模态视觉语言模型<br>文字+图片联合理解]
        AI2[qwen-plus<br>通义千问大语言模型<br>难例判定 / AI对话]
        AI3[qwen-turbo<br>通义千问快速模型<br>字段提取 / 轻量对话]
        AI4[DeepSeek-Chat<br>备选推理模型<br>OpenAI兼容接口]
    end

    subgraph 文件存储层
        F1[本地文件系统<br>data/local-oss/<br>source_documents/ + template_files/]
        F2[阿里云OSS<br>可选 对象存储]
    end

    U1 --> N1
    U2 --> N1
    U3 --> N1
    N1 --> G1
    G1 --> S1
    G1 --> S2
    G1 --> S3
    S1 --> D1
    S1 --> D2
    S2 --> D1
    S2 --> D2
    S3 --> D1
    S1 --> D3
    S2 --> D3
    S3 --> D3
    S2 --> AI1
    S2 --> AI2
    S2 --> AI3
    S2 --> AI4
    S2 --> F1
    S3 --> F1
    S2 --> F2
    S3 --> F2
    S4 --> D2
    S1 -.-> S6
    S2 -.-> S6
    S3 -.-> S6
```

---

## 十、系统架构图

### 10.1 系统整体架构图

```mermaid
graph TB
    subgraph 客户端
        Client1["🌐 Web端 (Vue 3)<br>Element Plus + Pinia + ECharts<br>VueOffice预览 + SSE流式"]
        Client2["🖥️ Qt桌面端 (C++)<br>Qt 5.14.2 MinGW64<br>QNetworkAccessManager + SseClient"]
        Client3["📱 微信小程序<br>原生框架 + 云函数"]
    end

    subgraph "反向代理 Nginx:8080"
        Nginx["Nginx 1.24<br>┌─ / → 静态资源(Vue dist)─┐<br>└─ /api/v1/** → Gateway:18080 ┘<br>SSE: proxy_buffering=off<br>超时: 300s"]
    end

    subgraph "API网关 Gateway:18080"
        GW["Spring Cloud Gateway<br>┌── JWT认证过滤器 ──┐<br>│ Redis校验token存在性 │<br>│ 白名单路由跳过认证  │<br>└── StripPrefix=2 ──┘"]
    end

    subgraph "微服务集群"
        direction LR
        US["user-service:9001<br>──────────────<br>• 用户注册/登录<br>• BCrypt密码加密<br>• JWT Token生成(HS256, 20h)<br>• Redis Token存储<br>• 邮箱验证码(SMTP)"]
        
        FS["file-service:9003<br>──────────────<br>• Excel文件上传/下载<br>• Excel→MySQL动态表转换<br>• 分页预览(50行/页)<br>• 文件元信息管理"]
        
        AS["ai-service:9002<br>──────────────<br>• 多格式文档提取(4格式)<br>• 八阶段模板填充流水线<br>• SSE流式AI对话<br>• 文档在线编辑<br>• Excel智能问答(SQL生成)<br>• 模型热切换"]
        
        MCP["mcp-service:9004<br>──────────────<br>• MCP协议适配<br>• 扩展接口"]
    end

    subgraph "基础设施 (Docker Compose)"
        MySQL["MySQL 8.4<br>:3306<br>16张业务表"]
        Redis["Redis 7.0<br>:6379<br>Token + Session缓存"]
        Nacos["Nacos 2.2<br>:8848<br>服务注册 + 配置中心"]
    end

    subgraph "AI模型服务"
        DashScope["阿里云DashScope API<br>dashscope.aliyuncs.com<br>──────────────<br>qwen-plus (对话/难例)<br>qwen-turbo (提取/轻量)<br>qwen2.5-vl-7b (多模态)"]
        DeepSeek["DeepSeek API<br>api.deepseek.com<br>──────────────<br>deepseek-chat<br>OpenAI兼容接口"]
        LocalModel["本地部署 (可选)<br>Ollama / vLLM<br>──────────────<br>Qwen2.5-VL-7B GGUF<br>Metal/CUDA加速"]
    end

    Client1 -->|"HTTP/SSE"| Nginx
    Client2 -->|"HTTP/SSE"| Nginx
    Client3 -->|"HTTP"| Nginx
    Nginx -->|"/api/v1/**"| GW
    GW -->|"/users/**"| US
    GW -->|"/files/**"| FS
    GW -->|"/ai/** /source/** /template/** /llm/**"| AS
    US --> MySQL
    US --> Redis
    FS --> MySQL
    AS --> MySQL
    AS --> Redis
    US --> Nacos
    FS --> Nacos
    AS --> Nacos
    AS -->|"Spring AI Alibaba"| DashScope
    AS -->|"OpenAI兼容API"| DeepSeek
    AS -->|"OpenAI兼容API"| LocalModel
```

### 10.2 AI模型架构与调用关系

```mermaid
graph TB
    subgraph "DocAI AI引擎 (ai-service)"
        LPF["LlmProviderFactory<br>模型提供商工厂<br>运行时动态切换"]
        AMS["AiModelService<br>模型管理<br>当前Provider追踪"]
        
        subgraph "模型调用层"
            DC["DashScopeConfig<br>Spring AI Alibaba ChatModel<br>API Key: DOC_DASHSCOPE_API_KEY"]
            DSC["DeepSeekConfig<br>RestTemplate + OpenAI兼容<br>API Key: DOC_DEEPSEEK_API_KEY"]
        end
        
        subgraph "业务调用场景"
            EX["文档信息提取<br>ExtractionService<br>Semaphore并发=1<br>1.5s调用间隔"]
            TF["模板填充决策<br>TemplateFillService<br>难例LLM仲裁<br>LLM兜底填充"]
            CH["AI智能对话<br>AiService<br>SSE流式响应<br>多轮上下文"]
            ED["文档在线编辑<br>AiService<br>编辑意图检测<br>docx/txt/md输出"]
            SQL["Excel问答<br>SQLGenerationService<br>自然语言→SQL<br>结果可视化"]
        end
    end

    subgraph "Qwen2.5-VL-7B-Instruct 架构"
        direction TB
        VL_IN["输入层<br>──────────<br>文本Token序列<br>+ 图像Patch序列"]
        VL_VE["视觉编码器 (ViT)<br>──────────<br>Vision Transformer<br>图像→视觉Token"]
        VL_PA["Patch Embedding<br>──────────<br>动态分辨率<br>自适应图像尺寸"]
        VL_TR["Transformer Decoder<br>──────────<br>7B参数<br>28层 / 28头<br>hidden_size=3584"]
        VL_OUT["输出层<br>──────────<br>结构化JSON<br>字段键值对提取"]
        
        VL_IN --> VL_VE
        VL_IN --> VL_PA
        VL_VE --> VL_TR
        VL_PA --> VL_TR
        VL_TR --> VL_OUT
    end

    subgraph "Qwen-Plus / Qwen-Turbo 架构"
        direction TB
        QW_IN["输入层<br>──────────<br>System Prompt<br>+ 用户消息<br>+ 历史上下文"]
        QW_TR["Transformer Decoder<br>──────────<br>Qwen2.5系列<br>GQA注意力<br>SwiGLU激活<br>RoPE位置编码<br>128K上下文窗口"]
        QW_OUT["输出层<br>──────────<br>流式Token生成<br>JSON/自然语言"]
        
        QW_IN --> QW_TR
        QW_TR --> QW_OUT
    end

    subgraph "DeepSeek-Chat 架构"
        direction TB
        DS_IN["输入层<br>──────────<br>OpenAI兼容格式<br>messages数组"]
        DS_TR["Transformer Decoder<br>──────────<br>DeepSeek-V2/V3<br>MoE混合专家<br>Multi-Head Latent Attention<br>DeepSeekMoE路由"]
        DS_OUT["输出层<br>──────────<br>流式/非流式响应<br>兼容OpenAI格式"]
        
        DS_IN --> DS_TR
        DS_TR --> DS_OUT
    end

    LPF --> DC
    LPF --> DSC
    AMS --> LPF
    EX -->|"多模态提取"| DC
    TF -->|"难例判定"| DC
    CH -->|"对话生成"| DC
    CH -->|"备选模型"| DSC
    ED -->|"文档编辑"| DC
    SQL -->|"SQL生成"| DC
    DC -->|"DashScope API"| VL_IN
    DC -->|"DashScope API"| QW_IN
    DSC -->|"OpenAI兼容API"| DS_IN
```

### 10.3 程序运行总流程图

```mermaid
flowchart TB
    START([系统启动]) --> DOCKER["Docker Compose启动<br>MySQL + Redis + Nacos + Nginx"]
    DOCKER --> SERVICES["启动Java微服务<br>user→file→ai→gateway"]
    SERVICES --> READY([系统就绪 :8080])

    READY --> LOGIN["用户登录<br>POST /api/v1/users/auth"]
    LOGIN --> JWT["JWT Token生成<br>HS256签名 / 20h有效期<br>Redis存储 token:<jwt>"]
    JWT --> MAIN["进入主界面<br>Dashboard工作台"]

    MAIN --> FLOW1
    MAIN --> FLOW2
    MAIN --> FLOW3
    MAIN --> FLOW4

    subgraph FLOW1["流程一：文档上传与信息提取"]
        direction TB
        F1_UP["上传源文档<br>docx/xlsx/txt/md"] --> F1_SAVE["保存至本地存储<br>data/local-oss/source_documents/"]
        F1_SAVE --> F1_ASYNC["异步提取<br>(线程池 + Semaphore)"]
        F1_ASYNC --> F1_PARSE["文档解析<br>Apache POI / 文本读取"]
        F1_PARSE --> F1_IMG{"Word含图片?"}
        F1_IMG -->|是| F1_VL["Qwen2.5-VL多模态提取<br>文字+图片→JSON"]
        F1_IMG -->|否| F1_LLM["qwen-turbo文本提取<br>文字→JSON"]
        F1_VL --> F1_STD["字段标准化<br>40+别名→30+标准键"]
        F1_LLM --> F1_STD
        F1_STD --> F1_DB["存入extracted_fields表<br>状态: parsed"]
    end

    subgraph FLOW2["流程二：模板智能填充"]
        direction TB
        F2_UP["上传模板文件<br>xlsx/docx"] --> F2_PARSE["槽位解析<br>right/below/inline/header_below"]
        F2_PARSE --> F2_SELECT["选择源文档<br>+ 可选用户需求"]
        F2_SELECT --> F2_PIPE["八阶段填充流水线"]
        
        F2_PIPE --> F2_S1["①数据准备<br>字段预去重+索引构建"]
        F2_S1 --> F2_S2["②表头引导<br>directTableCopy快速路径"]
        F2_S2 --> F2_S3["③候选召回<br>五维加权Top5"]
        F2_S3 --> F2_S4["④难例判定<br>分差<0.05→LLM仲裁"]
        F2_S4 --> F2_S5["⑤置信度融合<br>≥0.70直接填/<0.05拒填"]
        F2_S5 --> F2_S6["⑥兜底填充<br>贪心+LLM+强制"]
        F2_S6 --> F2_S7["⑦文件写回<br>POI写Excel/Word"]
        F2_S7 --> F2_S8["⑧审计记录<br>全链路追溯日志"]
        F2_S8 --> F2_DL["结果下载/在线预览"]
    end

    subgraph FLOW3["流程三：AI智能对话"]
        direction TB
        F3_IN["用户输入消息<br>可关联文档/Excel"] --> F3_SSE["SSE流式请求<br>POST /ai/chat/stream"]
        F3_SSE --> F3_DETECT{"意图检测"}
        F3_DETECT -->|普通对话| F3_CHAT["LLM多轮对话<br>逐Token流式返回"]
        F3_DETECT -->|编辑意图| F3_EDIT["AI读取原文档<br>输出修改后完整内容<br>保存docx/txt/md"]
        F3_DETECT -->|数据查询| F3_SQL["生成SQL<br>执行查询<br>返回图表数据"]
        F3_CHAT --> F3_OUT["前端实时渲染<br>Markdown安全渲染"]
        F3_EDIT --> F3_OUT
        F3_SQL --> F3_OUT
    end

    subgraph FLOW4["流程四：Excel文件管理"]
        direction TB
        F4_UP["上传Excel"] --> F4_DB["Excel→MySQL动态表"]
        F4_DB --> F4_PREVIEW["分页预览<br>50行/页"]
        F4_DB --> F4_AI["AI问答<br>自然语言→SQL"]
        F4_DB --> F4_DL["文件下载"]
    end

    style FLOW1 fill:#e3f2fd,stroke:#1565c0
    style FLOW2 fill:#f3e5f5,stroke:#7b1fa2
    style FLOW3 fill:#e8f5e9,stroke:#2e7d32
    style FLOW4 fill:#fff3e0,stroke:#e65100
```

### 10.4 候选召回与决策引擎详细流程

```mermaid
flowchart TB
    INPUT["输入: 模板槽位(label+context)<br>+ 全部已提取字段(N个)"] --> RECALL["五维加权候选召回"]
    
    subgraph RECALL_DETAIL["候选召回评分 (每个字段)"]
        direction LR
        R1["别名分 ×0.40<br>编辑距离归一化<br>别名词典加速"]
        R2["类型分 ×0.20<br>text/number/date<br>类型匹配度"]
        R3["投票分 ×0.20<br>多文档相同值<br>投票数/最大投票"]
        R4["上下文分 ×0.10<br>槽位context<br>包含fieldKey"]
        R5["置信度 ×0.10<br>提取置信度<br>原始confidence"]
    end
    
    RECALL --> RECALL_DETAIL
    RECALL_DETAIL --> DEDUP["候选去重<br>同值保留最高分"]
    DEDUP --> TOP5["取Top-5候选"]
    
    TOP5 --> DECISION{"决策判定"}
    DECISION -->|"Top1分≥0.30<br>且分差≥0.05"| RULE["纯规则决策<br>mode=rule_only<br>confidence=Top1分"]
    DECISION -->|"分差<0.05<br>或Top1<0.30"| LLM_JUDGE["LLM难例仲裁<br>发送Top5候选给qwen-plus"]
    DECISION -->|"无候选"| BLANK["拒绝填写<br>mode=fallback_blank"]
    
    LLM_JUDGE --> FUSE["置信度融合<br>0.70×规则分 + 0.30×LLM分<br>mode=rule_plus_llm"]
    
    RULE --> THRESHOLD
    FUSE --> THRESHOLD
    
    subgraph THRESHOLD["置信度阈值策略"]
        T1["≥0.70 → 直接填写<br>高置信度"]
        T2["0.05~0.70 → 填写<br>标记'建议人工复核'"]
        T3["<0.05 → 拒绝填写<br>保留空白"]
    end
    
    THRESHOLD --> FALLBACK{"还有空槽?"}
    FALLBACK -->|否| WRITEBACK["模板写回"]
    FALLBACK -->|是| FB_GREEDY["贪心匹配<br>归一化key直接查找<br>conf=0.50"]
    FB_GREEDY --> FB_LLM["LLM兜底<br>汇聚原文重新提取<br>conf=0.65"]
    FB_LLM --> FB_FORCE["强制回填<br>最佳需求匹配候选"]
    FB_FORCE --> WRITEBACK
    
    WRITEBACK --> AUDIT["生成审计日志<br>来源+候选+评分+理由"]
```

### 10.5 SSE流式通信架构

```mermaid
sequenceDiagram
    participant User as 用户
    participant Web as Vue 3 前端
    participant Qt as Qt C++ 客户端
    participant Nginx as Nginx:8080
    participant GW as Gateway:18080
    participant AI as ai-service:9002
    participant LLM as DashScope API

    User->>Web: 输入消息 + 点击发送
    Web->>Nginx: POST /api/v1/ai/chat/stream<br>Accept: text/event-stream
    Nginx->>GW: 转发 (proxy_buffering=off)
    GW->>GW: JWT认证 (Redis校验)
    GW->>AI: 转发至ai-service

    AI->>AI: 意图识别 + 历史拼装
    AI->>LLM: 调用qwen-plus (Stream=true)
    
    loop 逐Token流式返回
        LLM-->>AI: Token片段
        AI-->>GW: event:progress<br>data:{"content":"...","done":false}
        GW-->>Nginx: SSE事件透传
        Nginx-->>Web: SSE事件
        Web->>Web: 追加渲染Markdown
    end

    LLM-->>AI: 生成完毕
    AI-->>GW: event:complete<br>data:{"content":"完整内容","done":true}
    GW-->>Nginx: SSE完成事件
    Nginx-->>Web: SSE完成
    Web->>Web: 保存消息到对话历史

    Note over Qt,Nginx: Qt客户端使用相同SSE协议
    User->>Qt: 输入消息
    Qt->>Nginx: QNetworkRequest POST<br>SseClient::start()
    Nginx->>GW: 转发
    GW->>AI: 转发

    loop readyRead信号
        AI-->>Qt: SSE数据块
        Qt->>Qt: 解析event/data<br>emit textReceived()
    end
    AI-->>Qt: 完成事件
    Qt->>Qt: emit completed()
```

### 10.6 数据库ER关系图

```mermaid
erDiagram
    users ||--o{ source_documents : "上传"
    users ||--o{ template_files : "上传"
    users ||--o{ files : "上传"
    users ||--o{ chat_conversations : "创建"
    users ||--o{ ai_requests : "发起"

    source_documents ||--o{ extracted_fields : "包含"
    template_files ||--o{ template_slots : "包含"
    template_files ||--o{ fill_decisions : "产生"
    template_files ||--o{ fill_audit_logs : "记录"

    files ||--o{ file_table_mappings : "映射"
    file_table_mappings ||--o{ field_mappings : "字段"

    chat_conversations ||--o{ chat_messages : "包含"

    users {
        bigint id PK
        varchar username
        varchar password_hash "BCrypt加密"
        varchar email
        timestamp created_at
    }

    source_documents {
        bigint id PK
        bigint user_id FK
        varchar file_name
        varchar file_type "docx/xlsx/txt/md"
        varchar storage_path
        varchar upload_status "parsing/parsed/failed"
        text doc_summary
    }

    extracted_fields {
        bigint id PK
        bigint doc_id FK
        varchar field_key "标准化键"
        varchar field_name "原始名称"
        text field_value
        varchar field_type "text/number/date"
        varchar aliases "别名列表"
        text source_text "来源原文"
        decimal confidence "置信度0-1"
    }

    field_alias_dict {
        bigint id PK
        varchar standard_key "标准键"
        varchar alias_name "中文别名"
    }

    template_files {
        bigint id PK
        bigint user_id FK
        varchar file_name
        varchar template_type "xlsx/docx"
        varchar parse_status
        int slot_count
        varchar output_path "填充后文件路径"
    }

    template_slots {
        bigint id PK
        bigint template_id FK
        varchar slot_id
        varchar label "槽位标签"
        text context "上下文"
        varchar position "right/below/inline"
        varchar slot_type "null/inline/header_below"
    }

    fill_decisions {
        bigint id PK
        varchar audit_id
        bigint template_id FK
        varchar slot_id
        varchar slot_label
        text final_value
        decimal final_confidence
        varchar decision_mode "rule_only/rule_plus_llm/fallback"
        text reason
    }

    fill_audit_logs {
        bigint id PK
        varchar audit_id
        bigint template_id FK
        bigint user_id FK
        varchar slot_label
        text final_value
        varchar decision_mode
        text source_doc_name
        text source_text
        text candidates_summary "Top5候选JSON"
    }

    chat_conversations {
        bigint id PK
        bigint user_id FK
        varchar title
        boolean pinned
    }

    chat_messages {
        bigint id PK
        bigint conversation_id FK
        varchar role "user/assistant"
        text content
        text metadata
    }

    files {
        bigint id PK
        bigint user_id FK
        varchar file_name
        varchar file_path
        varchar oss_key
    }

    ai_requests {
        bigint id PK
        bigint user_id FK
        bigint file_id FK
        text user_input
        text ai_response
        varchar status
    }
```
