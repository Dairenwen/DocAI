# DocAI — 智能文档信息提取与自动填表系统

基于大语言模型的智能文档处理平台，支持从 docx/xlsx/txt/md 等非结构化文档中自动提取关键信息，并智能填充到用户提供的模板表格中。

## 核心功能

1. **文档智能操作交互** — 自然语言对话操作文档，支持摘要、提取、分析
2. **非结构化文档信息提取** — AI 自动抽取文档中的关键字段（人名、机构、日期、电话、金额等）
3. **表格自定义数据填写** — 上传 Word/Excel 模板，系统自动匹配数据并填写

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + Pinia |
| 网关 | Spring Cloud Gateway |
| 后端 | Spring Boot 3.2 + MyBatis-Plus + Java 17 |
| AI | 阿里云 DashScope（Qwen 系列大模型） |
| 文件解析 | Apache POI 5.2（docx/xlsx） |
| 数据库 | MySQL 8.4 |
| 缓存 | Redis 7.0 |
| 配置中心 | Nacos 2.2 |
| 容器化 | Docker Compose |

## 快速启动

### 环境要求
- JDK 17+、Maven 3.8+、Node.js 18+、Docker 24+

### 配置 API Key
```bash
# Windows PowerShell
$env:DOC_DASHSCOPE_API_KEY = "your-api-key"

# Linux/Mac
export DOC_DASHSCOPE_API_KEY="your-api-key"
```

### 一键启动
```bash
# Windows
cd docai-pro
.\start-lite-windows.ps1

# Linux
cd docai-pro
chmod +x start-lite.sh
./start-lite.sh
```

### 访问
- 前端界面: http://localhost:8080
- API 网关: http://localhost:18080

## 项目结构

```
DocAI/
├── docai-frontend/          # Vue 3 前端
├── docai-pro/               # Java 后端（微服务）
│   ├── common-service/      # 公共服务（JWT、Redis、OSS、邮件）
│   ├── gateway-service/     # API 网关（路由、鉴权）
│   ├── user-service/        # 用户服务（认证、注册）
│   ├── file-service/        # 文件服务（Excel 处理）
│   ├── ai-service/          # AI 服务（抽取、填表、对话）
│   ├── mcp-service/         # MCP 协议服务
│   └── deploy/              # Docker Compose 部署配置
├── docs/                    # 项目文档
│   ├── 商业项目策划书.md
│   ├── PPT制作纲要.md
│   ├── 项目启动说明书.md
│   └── 项目详细流程介绍.md
└── test-data/               # 测试数据
```

## 文档

详细文档请查看 [docs/](docs/) 目录：
- [商业项目策划书](docs/商业项目策划书.md)
- [PPT 制作纲要](docs/PPT制作纲要.md)
- [项目启动说明书](docs/项目启动说明书.md)
- [项目详细流程介绍](docs/项目详细流程介绍.md)