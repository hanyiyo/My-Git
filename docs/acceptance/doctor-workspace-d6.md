# D6 医生排班与停诊替诊申请接入验收

对应 GitHub 任务：[#29 医生排班与停诊替诊申请接入](https://github.com/hanyiyo/My-Git/issues/29)。

## 验收案例与公开边界

| 案例 | 公开边界 | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 医生查看今日、历史和未来排班，并看到总号源、已确认占用、临时占号、可用数和接诊进度 | `GET /doctor/catalog/schedules`、医生“我的认证与排班” | 每个场次由真实 `schedule`、`appointment`、`catalog_slot`、`reception_queue` 聚合返回；前端按中国标准时间展示真实月历和只读历史 | 接诊进度只统计候诊、叫号、接诊中、已完成四类队列状态 |
| 已付款预约与待付款临时占号同时存在时，统计保持区分 | `GET /doctor/catalog/schedules` | `CatalogHttpTest.scheduleMetricsExposeConfirmedAndTemporaryOccupancyForDoctorWorkspace` 验证 confirmed=1、pending=1、可用号源=1 | 待付款占号按 `hold_expires_at` 到期自动失效 |
| 从具体排班进入停诊申请，场次上下文和影响数只读带入，理由可编辑 | `/doctor/catalog?schedule=...`、`/doctor/governance?schedule=...`、`POST /doctor/catalog/suspensions` | 申请页显示日期、时段、服务方式、时间、总号源、确认占用、临时占号、影响数和可用数；服务端仍以排班归属与截止规则为准 | 影响数是当前有效预约与临时占号快照，不替代管理员逐患者影响明细 |
| 同场重复申请、超过截止时间、跨科室或越权处理被拒绝，冲突时保留输入 | 停诊申请、科主任审核、管理员复核 REST 接口 | 既有 `WorkflowHttpTest` 停诊/替诊边界用例覆盖 `SUSPENSION_CONFLICT`、`EMERGENCY_REQUIRED`、回避和候选医生冲突；前端表单不会因失败清空理由 | 紧急停诊由管理员办理 |
| 替诊先经科主任、替诊医生回应和管理员复核，最终迁移预约并通知患者；直接停诊取消并退款 | `/doctor/catalog/head/suspensions/{id}/review`、`/doctor/catalog/substitutions/{id}/respond`、`/admin/catalog/suspensions/{id}/review`、患者通知与订单 | 既有 HTTP 与浏览器流程覆盖申请→回应→科主任/管理员复核→患者结果、自动迁移和取消退款 | 支付与通知仍为本地可验证实现 |

## 验证记录

- 后端：`CatalogHttpTest` 包含真实 MySQL/Redis HTTP 统计回归；定向 `BrowserAcceptanceTest` 通过（1 个 Java 测试驱动 194 个 Playwright 用例，全部通过）。
- 前端：`npm run build` 已通过 Vue 类型检查、生产构建和生产资产检查；D6 目录浏览器用例通过。
- 文档、架构和规格检查以 `scripts/verify.ps1` 为准；交付前必须确认退出码为 0。

## 当前限制

排班月历使用已发布排班和服务端规则快照，未引入外部排班系统；通知和支付沿用仓库内可审计的本地实现。GitHub Issue #29 保持开放，等待统一验收关闭。
