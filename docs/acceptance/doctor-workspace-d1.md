# D1 医生首页与正式工作区验收

对应 GitHub 任务：[#24 医生首页与正式工作区接入](https://github.com/hanyiyo/My-Git/issues/24)。本记录只覆盖 D1；D2–D7 的候诊细节、持久化草稿、结构化病历、摘要授权、通知和资料页仍由后续任务交付。

## 交付内容

- 新增受保护 `GET /api/v1/doctor/home` 聚合接口，服务端按 `Asia/Shanghai` 当前时间返回本人账户、医生认证/科室、当前或下一场出诊、候诊/接诊中/完成/申请待办计数和未结束接诊入口。
- 统计从 MySQL 的 `schedule`、`reception_queue`、`appointment`、停诊申请和替诊邀请读取；候诊只计已确认且队列状态为 `WAITING` 的登记，不使用号源数或演示数组替代。
- 正式 `/doctor` 使用真实 Vue 工作区，提供六模块导航、URL 临时标签、加载/空/失败重试、375px 侧栏和继续接诊入口；原型继续留在 `/prototype/doctor-home`。
- `/doctor/notifications` 与 `/doctor/profile` 暂保留正式入口，明确由 D7 补齐独立真实页面；正式工作区仍挂载现有共享站内通知面板，不写入演示业务状态。

## 验收案例

| 案例 | 公开边界 | 结果 |
| --- | --- | --- |
| 医生首页聚合本人资料、认证、科室和当前排班 | `GET /doctor/home` | 通过；HTTP 测试核对账户、科室、医院日期和当前排班 |
| 候诊口径只计真实已签到等待患者 | `POST /patient/appointments/{id}/check-in` 后 `GET /doctor/home` | 通过；`waitingCount=1`，未以号源数代替 |
| 接诊数据按医生隔离 | 两名医生分别 `GET /doctor/home` | 通过；另一医生看不到预约、排班和候诊计数 |
| 患者不能读取医生聚合 | `GET /doctor/home` 使用患者令牌 | 通过；返回 403 |
| 当前页标签关闭只改变 URL 导航 | 浏览器 `/doctor`、`/doctor/reception` | 通过；真实接诊页面保留，未触发业务写操作 |
| 加载失败可重试且无原型兜底 | 浏览器拦截 `GET /doctor/home` 一次 503 后恢复 | 通过；展示错误和重试，不出现原型医生或演示数据 |
| 六模块导航、375px 侧栏和临时标签 | 浏览器正式医生工作区 | 通过；导航、标签和窄屏侧栏可操作 |
| 正式工作区保留共享站内通知入口 | 浏览器医生首页与三端通知流程 | 通过；通知区域显示实时/轮询状态，既有认证与排班入口继续可用 |

## 验证证据

- `pwsh -NoProfile -File scripts/test-backend.ps1 -Test 'IdentityHttpTest#csrfAndRoleEscalationAndCrossAccountAccessAreRejected'`：1 项通过。
- `pwsh -NoProfile -File scripts/test-backend.ps1 -Test 'WorkflowHttpTest#doctorHomeAggregatesOwnScheduleQueueAndActiveEncounterWithoutCrossDoctorData'`：1 项通过，使用真实 MySQL/Redis HTTP 边界。
- `$env:MEDICAL_PLAYWRIGHT_GREP='正式医生首页'; pwsh -NoProfile -File scripts/test-backend.ps1 -Test BrowserAcceptanceTest`：新增正式医生首页浏览器案例通过。
- `$env:MEDICAL_PLAYWRIGHT_GREP='管理员维护目录并发布排班|三端站内提醒'; pwsh -NoProfile -File scripts/test-backend.ps1 -Test BrowserAcceptanceTest`：目录/认证入口与三端通知回归通过。
- `npm --prefix frontend run build`：Vue 类型检查、Vite 构建和生产原型扫描通过。
- `pwsh -NoProfile -File scripts/verify.ps1`：完整门禁通过；后端/浏览器测试 98 项通过，浏览器回归 203 项通过，未跳过、无失败。

## 当前限制

D1 不交付候诊叫号并发规则、诊室草稿、结构化病历/用法用量、AI 摘要授权、独立通知列表和个人资料详情；这些能力分别由 D2–D7 实现。
