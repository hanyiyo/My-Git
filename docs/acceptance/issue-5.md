# #5 MVP-04 签到、候诊与现场挂号

实现分支：`codex/reception-queue`。依据 GitHub #5、mvp-spec、GLOSSARY.md 与预约/候诊架构决策。

| 验收规则 | 公共 seam / 用例 | 当前实现 | 备注 |
| --- | --- | --- | --- |
| 预约在出诊前30分钟开放签到，结束前15分钟停止 | `POST /patient/appointments/{id}/check-in`，以可控 Clock 覆盖窗口左右边界 | 已实现 | 仅 CONFIRMED 预约可签到 |
| 现场患者只使用当天线下剩余号源 | 既有 `POST /patient/appointments` 的 `source=WALK_IN` 与排班号源 | 已实现 | #4 已提供当天线下登记截止与真实号源占用 |
| 队列遵循预约优先、签到先后，恢复排队为第三组 | `GET /doctor/reception` 按 `queue_group, queued_at, id` 返回 | 已实现 | 预约组、现场组、恢复组分别为1/2/3 |
| 医生叫号、开始接诊、过号、结束接诊 | `/doctor/reception/{id}/{call,start,miss,complete}` | 已实现 | 服务端校验医生归属和状态转换 |
| 患者最多恢复排队一次，时段结束后不可恢复且不退款 | `POST /patient/appointments/{id}/restore-queue` | 已实现 | 持久化 `restoration_count`；恢复不产生新预约或退款 |
| 患者与医生查询签到、候诊和叫号状态 | 订单 API 的 `reception` 事实与医生候诊页面 | 已实现 | 患者订单页和医生工作台已接入 |

本轮按用户要求暂缓执行完整测试；测试 seam 和用例需在全部 MVP issue 完成后统一运行。已完成前端类型检查/构建和后端 `test-compile`，未启动 MySQL/Redis 集成或浏览器测试。最终交付仍必须执行完整 `npm test` / `scripts/verify.ps1`、真实 MySQL/Redis HTTP 集成、浏览器验收和双轴审查后再关闭 issue。
