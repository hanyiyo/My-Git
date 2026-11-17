# #18 患者首页通知与实时刷新

## 范围

首页显示患者本人站内通知未读数并进入正式通知列表；未读数通过持久化通知查询获得。全局通知面板继续负责 SSE、游标补齐、断线重连和轮询兜底，首页在新通知或标记已读事件后重新读取未读数，不把 SSE 连接状态当作预约或候诊事实。

## 验收案例与公共测试 seam

| 验收标准 | 公共测试 seam | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 首页显示当前患者未读数并进入通知列表 | Playwright 首页注入 `/notifications/unread` 并点击站内通知入口 | 已通过 | 通知列表继续由正式通知路由加载 |
| 新通知通过 SSE 事件唤醒页面刷新，内容来自持久化查询 | `NotificationPanel` 的持久化查询和 `medical-notification` 事件；首页重新读取未读数 | 已通过定向浏览器验收 | 浏览器边界用自定义事件模拟 SSE 唤醒 |
| SSE 断开、重连或不可用时游标补齐并轮询兜底 | 既有全链路 `WorkflowHttpTest` 与 `workflows.spec.ts` 的 SSE abort/轮询场景 | 已有覆盖，随完整门禁回归 | SSE 连接状态仅作为同步提示 |
| 标记已读后未读数和列表同步 | 通知面板标记已读后发出事件；首页响应 `medical-notification-read` 重新读取未读数 | 已通过类型检查与定向验收 | 列表内容仍以服务端查询为准 |
| 通知失败显示可理解状态且不影响预约事实 | 首页通知查询独立失败处理，预约加载仍走独立请求；既有预约 HTTP/浏览器回归 | 已通过 | 通知不可用时只影响通知提示 |
| HTTP 与 Playwright 覆盖未读数、游标补齐、断线和已读 | 既有 `WorkflowHttpTest` 游标/归属/幂等测试与首页通知 Playwright 测试 | 已有 HTTP 覆盖，浏览器新增通过 | 本切片不改变通知后端持久化模型 |

## 验收记录

- `MEDICAL_PLAYWRIGHT_GREP='患者首页显示通知' pwsh -NoProfile -File scripts/test-backend.ps1 -Test BrowserAcceptanceTest`：通过。
- `npm --prefix frontend run build`：通过，包含 `vue-tsc --noEmit` 与 Vite 生产构建。
- `git diff --check`：通过。
- 完整 `pwsh -NoProfile -File scripts/verify.ps1`：通过，96 个后端测试、真实 MySQL/Redis、全量浏览器验收、前端构建与文档检查均通过。

限制：首页只展示未读数和导航；通知正文、游标读取、SSE 连接和已读操作仍由正式通知面板及后端接口负责。
