# DocAI Ubuntu 24.04 部署说明

## 1. 环境要求

- Ubuntu 24.04 LTS
- OpenJDK 17
- Maven 3.9+
- Node.js 18+
- Docker + Docker Compose Plugin（v2+）

> **注意**：MySQL 8.4、Redis 7.0、Nacos 2.2、Nginx 1.24 均通过 Docker Compose 自动启动，无需单独安装。

## 2. 关键配置

### 2.1 环境准备（Ubuntu 24.04）

```bash
# 安装OpenJDK 17
sudo apt update
sudo apt install -y openjdk-17-jdk

# 安装Maven
sudo apt install -y maven

# 安装Node.js 18+ (via NodeSource)
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# 安装Docker + Docker Compose
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
newgrp docker
```

### 2.2 前端 API 地址

当前前端目录为 `docai-frontend`，默认通过 `/api` 反向代理访问后端。

开发调试如需指向指定服务器，请设置环境变量后启动前端：

```bash
cd docai-frontend
export VITE_DEV_API_TARGET=http://124.222.53.34:8080
```

### 2.3 AI 模型密钥

请通过环境变量注入 DashScope/DeepSeek 密钥：

```bash
export DOC_DASHSCOPE_API_KEY="<your-dashscope-api-key>"
export DOC_DEEPSEEK_API_KEY="<your-deepseek-api-key>"
```

### 2.4 邮件服务（可选）

```bash
export DOC_SMTP_HOST="smtp.qq.com"
export DOC_SMTP_PORT="587"
export DOC_SMTP_USER="<your-email>"
export DOC_SMTP_AUTH_CODE="<your-auth-code>"
```

### 2.5 OSS 凭据（可选，禁止写入仓库）

请通过环境变量注入，不要把 AccessKey 写入 Git：

```bash
export ALIYUN_OSS_ACCESS_KEY_ID="<your-access-key-id>"
export ALIYUN_OSS_ACCESS_KEY_SECRET="<your-access-key-secret>"
```

Nacos 或配置中心中的 OSS 配置请使用占位符：

```yaml
aliyun:
  oss:
    access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID:}
    access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET:}
```

## 3. 数据库初始化

先创建业务库（如 `docai`）与 Nacos 库（如 `docai_nacos`），再执行：

```bash
mysql -uroot -p docai < docai-pro/init.sql
mysql -uroot -p docai_nacos < docai-pro/deploy/mysql/sql/nacos.sql
mysql -uroot -p docai_nacos < docai-pro/deploy/mysql/sql/inituser.sql
```

## 4. 构建

### 4.1 后端

```bash
cd docai-pro
mvn -DskipTests clean package
```

### 4.2 前端

```bash
cd docai-frontend
npm install
npm run build
```

将 `docai-frontend/dist` 复制到 `docai-pro/deploy/nginx/web/dist`。

## 5. 启动

### 5.1 一键轻量启动

```bash
cd docai-pro
bash start-lite.sh
```

默认会启动：

- Redis
- Nacos
- Nginx
- user-service / file-service / ai-service / gateway-service

### 5.2 访问地址

- 前端入口: `http://<服务器IP>:8080`
- 网关地址: `http://<服务器IP>:18080`

> 通过 `PUBLIC_HOST` 环境变量可指定公网IP，如 `export PUBLIC_HOST=124.222.53.34`

## 6. 停止

```bash
cd docai-pro
bash stop-lite.sh
```

## 7. 验证清单

- 登录（验证码/密码）
- 文件上传、预览、下载、批量下载/删除
- AI 助手流式对话（支持停止生成）
- 对话历史持久化（跨浏览器/设备）
- 信息提取：上传源文档 → 轻量轮询状态 → 查看抽取字段
- 自动填表：上传模板 → 解析槽位 → 自动填表 → 下载结果 → 查看决策详情

如果部署在同一台主机并暴露 8080 端口，前端无需使用 localhost，可直接用服务器公网IP对外访问。
