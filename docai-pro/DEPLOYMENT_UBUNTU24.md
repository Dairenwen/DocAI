# DocAI Ubuntu 24.04 部署说明

## 1. 环境要求

- Ubuntu 24.04 LTS
- OpenJDK 17
- Maven 3.9+
- Node.js 18+
- Docker + Docker Compose Plugin
- MySQL 8.0+

## 2. 关键配置

### 2.1 前端 API 地址

当前前端目录为 `docai-frontend`，默认通过 `/api` 反向代理访问后端。

开发调试如需指向指定服务器，请设置环境变量后启动前端：

```bash
cd docai-frontend
export VITE_DEV_API_TARGET=http://124.222.53.34:8080
```

### 2.2 OSS 凭据（禁止写入仓库）

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
mvn -DskipTests package
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

- 前端入口: `http://124.222.53.34:8080`
- 网关地址: `http://124.222.53.34:18080`

## 6. 停止

```bash
cd docai-pro
bash stop-lite.sh
```

## 7. 验证清单

- 登录（验证码/密码）
- 文件上传、预览、下载、删除
- AI 助手流式对话
- 信息提取：上传源文档 -> 查看抽取字段
- 自动填表：上传模板 -> 解析槽位 -> 自动填表 -> 下载结果 -> 查看审计/决策

如果部署在同一台主机并暴露 8080 端口，前端无需使用 localhost，可直接用 `124.222.53.34` 对外访问。
