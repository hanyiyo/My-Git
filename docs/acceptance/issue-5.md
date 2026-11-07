# #5 MVP-04 签到、候诊与现场挂号

当前状态：2026-11-07 收尾修复已通过本地完整门禁与规格/规范复查；本次提交推送后的远程 Verify 成功后关闭 issue。

## 收尾验收（2026-11-07）

- 签到左右边界：`BookingHttpTest.checkInWindowIncludesOpeningAndExcludesCutoffForBothSessions` 验证上午/下午均在开始前30分钟包含、结束前15分钟不包含。
- 来源与顺序：既有现场号源用例及 `laterAdvanceCheckInPrecedesWalkInAndRestoredPatientsAndEndPreventsRestoration` 验证提前预约→现场→恢复三组及组内先后；较晚签到的正常患者仍优先恢复者。
- 延迟服务：`confirmedAppointmentChecksInAndDoctorCompletesEncounter` 验证已签到患者在门诊结束后仍可被叫号、开始和结束接诊。
- 一次恢复与幂等：`missedPatientCanRestoreQueueOnlyOnceAsThirdGroup` 验证重试返回原恢复结果，再次过号拒绝第二次恢复；时段结束拒绝恢复且不产生退款。
- 爽约不可恢复：`unattendedPatientCannotUseRestorationToBypassCheckInCutoff` 验证未签到者扫描为爽约后不能绕过签到截止；只有医生叫号后过号才提供恢复入口。
- 页面窗口：`frontend/tests/orders.spec.ts` 两个新增浏览器用例验证结束后按钮禁用及不退款说明、患者设备时间偏差不影响按服务器时间开放签到。浏览器使用固定订单响应验证展示，真实后端规则由上述 HTTP 用例验证。
- 最近门禁：`pwsh -NoProfile -File scripts/verify.ps1` 于医院时区 2026-11-07 19:56 退出0，48条 Java 测试与11条浏览器流程通过，0失败/错误/跳过；Vue 类型检查/构建、真实 MySQL/Redis 和文档/架构检查均通过。
- 审查：规范轴无发现；规格轴提出的设备时钟、延迟服务、恢复幂等、签到按钮窗口及爽约绕过已修复并复查通过。
- 测试管理员在 `BookingHttpTest` 内复用会话，避免重复登录污染限流计数；生产限流阈值未更改。

实现来源：`codex/reception-queue`，已合入当前预约分支。依据 GitHub #5、mvp-spec、GLOSSARY.md 与预约/候诊架构决策。

| 验收规则 | 公共 seam / 用例 | 当前实现 | 备注 |
| --- | --- | --- | --- |
| 预约在出诊前30分钟开放签到，结束前15分钟停止 | `POST /patient/appointments/{id}/check-in`，以可控 Clock 覆盖窗口左右边界 | 已实现 | 仅 CONFIRMED 预约可签到 |
| 现场患者只使用当天线下剩余号源 | 既有 `POST /patient/appointments` 的 `source=WALK_IN` 与排班号源 | 已实现 | #4 已提供当天线下登记截止与真实号源占用 |
| 队列遵循预约优先、签到先后，恢复排队为第三组 | `GET /doctor/reception` 按 `queue_group, queued_at, id` 返回 | 已实现 | 预约组、现场组、恢复组分别为1/2/3 |
| 医生叫号、开始接诊、过号、结束接诊 | `/doctor/reception/{id}/{call,start,miss,complete}` | 已实现 | 服务端校验医生归属和状态转换 |
| 签到截止后仍未入队的预约标记为爽约 | `ReceptionScheduler` / `dueNoShows` | 已实现 | 扫描幂等写入 `MISSED` 队列记录 |
| 患者最多恢复排队一次，时段结束后不可恢复且不退款 | `POST /patient/appointments/{id}/restore-queue` | 已实现 | 持久化 `restoration_count`；恢复不产生新预约或退款 |
| 患者与医生查询签到、候诊和叫号状态 | 订单 API 的 `reception` 事实与医生候诊页面 | 已实现 | 患者订单页和医生工作台已接入 |

## 本轮门禁

按当前批次策略，仅执行编译、静态检查和本模块单元测试：

- 后端 `mvn -f backend/pom.xml -DskipTests test-compile`：通过。
- `ReceptionServiceTest`：2 条通过，覆盖“门诊开始前禁止恢复排队”和“接诊已开始后允许跨时段结束”。
- 前端 `npm --prefix frontend run build`：通过（包含 `vue-tsc --noEmit`）。
- `git diff --check`：通过。

本轮未启动 MySQL/Redis HTTP 集成、浏览器 E2E、全量 `npm test` 或 `scripts/verify.ps1`；这些检查在一批 issue 合并前统一执行。完整验收仍需双轴审查和上述延后测试通过后再关闭 issue。

## 批次完整门禁补充（2026-11-07）

批次修复完成后重新执行 `pwsh -NoProfile -File scripts/verify.ps1` 并退出 0：文档/架构检查、Vue 类型检查与生产构建、MySQL/Redis HTTP 集成、浏览器验收及后端 45 条测试全部通过（0 失败、0 错误、0 跳过）。`BookingHttpTest` 定向复跑为 14/14 通过；本记录此前的“延后”描述仅反映实现当时的阶段性门禁。
