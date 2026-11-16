# D2 医生候诊队列与接诊状态验收

对应 GitHub 任务：[#25 医生候诊队列与接诊状态接入](https://github.com/hanyiyo/My-Git/issues/25)。本记录只覆盖 D2；D3 的诊室草稿、D4 的结构化病历和 D5 的摘要授权仍由后续任务交付。

## 验收案例与公开边界

| 案例 | 公开边界 | 结果 |
| --- | --- | --- |
| 按日期、排班和服务方式查看真实候诊队列 | `GET /doctor/reception?date=&scheduleId=` | 已通过：服务端按日期/排班过滤，页面按服务方式过滤；队列排序仍由服务端事实决定 |
| 搜索患者只筛选页面显示 | 正式医生 `/doctor/reception` | 已通过：搜索只计算页面可见项，新增前端用例确认没有 POST 写请求 |
| 预览患者不改变状态 | 正式医生 `/doctor/reception` 预览对话框 | 已通过：只读对话框展示患者、场次、状态和候诊组；前端用例确认预览期间写请求数为 0 |
| 只能叫当前队首 | `POST /doctor/reception/{id}/call` | 已通过：现有队首 HTTP 回归覆盖 `QUEUE_ORDER` 冲突，服务端在事务内重新校验队首 |
| 有未结束接诊时禁止并行叫号 | `POST /doctor/reception/{id}/call` | 已通过：`BookingHttpTest.doctorCannotCallNextPatientWhileAnotherEncounterIsActive` 返回 409/`ACTIVE_ENCOUNTER` |
| 重复叫号或跨医生访问被拒绝 | 同一接口并发/跨账户请求 | 已通过：完整后端 HTTP 回归覆盖重复状态和医生归属边界，未改变其他医生队列 |
| 线上过号等待和患者应答校验 | `POST /doctor/reception/{id}/miss` | 已通过：完整后端 HTTP 回归使用 fake clock 覆盖线上等待快照与叫号后回复边界 |
| 一次恢复原类别队尾、二次过号作废 | 患者 `POST /patient/appointments/{id}/restore-queue` | 已通过：完整后端 HTTP 回归覆盖原类别队尾恢复和二次过号作废 |
| 关闭标签、刷新或排班结束不自动完成接诊 | `/doctor/reception` 与接诊状态接口 | 已通过：完整浏览器验收与 HTTP 回归确认状态只由明确接诊操作改变 |
| 375px 队列仍可筛选和操作 | 正式医生浏览器验收 | 已通过：新增前端用例验证 375px 无横向溢出；完整浏览器验收通过 |

## 计划验证

- `BookingHttpTest`：已补充活动接诊并发边界；与既有队首、跨医生、过号恢复用例一起通过真实 MySQL/Redis 与可调时钟验证。
- `frontend/tests/doctor-home.spec.ts`：已扩展正式队列筛选、预览和 375px 移动端行为；由完整浏览器门禁执行通过。
- `BrowserAcceptanceTest`：正式医生入口和接诊流程浏览器验收通过。
- `pwsh -NoProfile -File scripts/verify.ps1`：通过；文档检查、Vue 类型检查/构建、99 个后端测试和浏览器验收均无失败或跳过。

## 当前限制

D2 不交付诊室草稿、结构化病历、药品提交、AI 摘要授权、独立通知页或个人资料编辑；这些能力分别由 D3–D7 实现。前端单独运行 Playwright 时需要先启动后端测试服务；本次完整 `BrowserAcceptanceTest` 已在真实 HTTP 服务上执行并通过。
