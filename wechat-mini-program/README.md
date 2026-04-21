# A23-WXMiniprogram

本项目是 `DocAI` 微信小程序端，默认对接现有 `DocAI` 后端，不改 `DocAI` 服务端代码。当前已接通的核心接口都在 `/api/v1` 下，包括：

- 用户登录 / 注册：`POST /api/v1/users/auth`
- 当前用户信息：`GET /api/v1/users/info`
- 来源文档上传 / 列表 / 删除：`/api/v1/source/*`
- 模板上传 / 解析 / 填表 / 下载：`/api/v1/template/*`
- AI 对话：`POST /api/v1/ai/chat/stream`
- 会话与消息：`/api/v1/ai/conversations/*`

## 当前配置方式

旧版 README 里提到的 `useRemoteApiBaseUrl`、`REMOTE_API_BASE_URL`、`REMOTE_WEB_BASE_URL`、`REAL_DEVICE_API_BASE_URL` 已经不再存在。现在整个小程序只看一个入口配置文件：

- [config.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/config.js)

当前配置项：

```js
const APP_ORIGIN = 'https://docai.sa1.tunnelfrp.com'
const API_PREFIX = '/api/v1'
const ENABLE_WEBVIEW_ASSIST = /^https:\/\//i.test(APP_ORIGIN)
```

规则如下：

- `APP_ORIGIN` 只填写协议、域名和端口，不要带 `/api/v1`
- 请求地址会自动拼成 `${APP_ORIGIN}/api/v1`
- 上传、下载默认复用同一主机
- 网页辅助入口默认复用 `${APP_ORIGIN}`
- 只有 `APP_ORIGIN` 为 HTTPS 时，网页 `web-view` 入口才会启用

## 启动方式

### 方法一：本地联调

1. 启动 `DocAI` 后端。
2. 把 [config.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/config.js) 中的 `APP_ORIGIN` 改成你的本地服务入口，例如：

```js
const APP_ORIGIN = 'http://127.0.0.1:8080'
```

3. 重新编译微信开发者工具项目。
4. 在开发者工具里验证登录、文档上传、智能填表。

说明：

- 本地联调通常只适合微信开发者工具
- 真机调试不要写 `127.0.0.1`，要改成同局域网可访问的机器地址
- 如果本地是 HTTP，网页辅助入口会自动关闭，这是预期行为

### 方法二：使用 `https://docai.sa1.tunnelfrp.com`

这个仓库现在默认已经指向该远端地址。如需确认，请检查 [config.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/config.js)：

```js
const APP_ORIGIN = 'https://docai.sa1.tunnelfrp.com'
```

然后执行：

1. 重新编译微信开发者工具项目
2. 登录小程序账号
3. 进入“文档”页或“智能填表”页验证接口

我本地实测探活结果：

- `GET https://docai.sa1.tunnelfrp.com` 返回 `200`，说明站点入口可达
- `GET https://docai.sa1.tunnelfrp.com/api/v1/users/info` 未登录时返回 `401` JSON，说明后端 API 也可达

这意味着“方法二无法正常进行”主要不是隧道地址失效，而是旧文档和工程配置没有对齐。

## 微信小程序后台域名配置

若使用 `https://docai.sa1.tunnelfrp.com`，请把同一个主机名同时加入微信小程序后台：

- `request` 合法域名：`https://docai.sa1.tunnelfrp.com`
- `uploadFile` 合法域名：`https://docai.sa1.tunnelfrp.com`
- `downloadFile` 合法域名：`https://docai.sa1.tunnelfrp.com`
- `web-view` 业务域名：`https://docai.sa1.tunnelfrp.com`

注意：

- 配置项里不要带路径，例如不要写成 `https://docai.sa1.tunnelfrp.com/api/v1`
- 不能只配父域名，必须配完整主机名
- 如果后续启用 `WSS`，再额外配置对应的 `socket` 域名

## 智能填表说明

### 原生模式

主页面是 [autofill/index.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/pages/docai/autofill/index.js)。当前推荐优先走原生流程：

1. 选择或上传数据源文档
2. 选择模板文件
3. 输入填表需求
4. 执行智能填表
5. 在同页下载、打开或转发结果

原生填表固定调用：

1. 校验已选来源文档状态
2. `POST /api/v1/template/upload`
3. `POST /api/v1/template/{templateId}/parse`
4. `POST /api/v1/template/{templateId}/fill`
5. `GET /api/v1/template/{templateId}/download`

数据源支持：

- `docx`
- `xlsx`
- `txt`
- `md`

模板支持：

- `docx`
- `xlsx`

### 网页辅助模式

网页模式不是主流程，而是附加入口：

- 入口页：[autofill/index.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/pages/docai/autofill/index.js)
- `web-view` 容器页：[autofill-web/index.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/pages/docai/autofill-web/index.js)

网页入口会打开：

```text
https://docai.sa1.tunnelfrp.com/autofill?from=miniapp&scene=form&entry=wx-autofill
```

当前能力边界：

- 能从小程序跳到同域名网页端
- 不能自动把小程序登录态换成网页登录态
- 首次进入网页端时，仍可能需要单独登录

这是 `DocAI` 现有 Web 登录机制决定的，不是小程序单侧能补齐的。

## 建议验证顺序

1. 在微信开发者工具中导入项目目录 `E:\A23服创赛\A23-WXMiniprogram`
2. 小程序根目录选择 `miniprogram`
3. 确认 [config.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/config.js) 中的 `APP_ORIGIN`
4. 重新编译项目
5. 登录账号
6. 在“文档”页测试上传
7. 在“智能填表”页测试数据源上传、模板上传、开始填表
8. 如需网页入口，再点击“打开网页入口”

## 关键文件

- 配置文件：[config.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/config.js)
- 小程序全局初始化：[app.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/app.js)
- 智能填表入口页：[autofill/index.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/pages/docai/autofill/index.js)
- 网页容器页：[autofill-web/index.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/pages/docai/autofill-web/index.js)
- 接口封装：[docai.js](/E:/A23服创赛/A23-WXMiniprogram/miniprogram/api/docai.js)
