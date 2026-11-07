# #5 MVP-04 签到、候诊与现场挂号

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
