# #10 MVP-09 通知、SSE 补齐与可靠性

依据 GitHub #10、ADR-0013、`mvp-spec`、Q52/Q53/Q65。

| 验收规则 | 实现 seam | 当前结果 | 限制 |
| --- | --- | --- | --- |
| 预约确认/取消、签到、叫号、停诊/替诊变化写入 MySQL 通知 | `notification` 表、`DeliveryService.publish`；预约、签到、叫号、治理流程已接入 | 已实现幂等 dedupe key 和未读计数 | 退款事件及集成链路延后验证 |
| SSE 接收、断线游标补齐、轮询回退 | `/notifications/stream`、`GET /notifications?after=`、前端工作台未读轮询 | 已实现服务端 SSE 与游标查询接口 | 浏览器断线场景延后 |
| AI/退款/占号/替诊任务有限重试且不重复改变事实 | AI 外部调用最多3次；通知重试最多3次；业务事实仍由各模块幂等键/状态机负责 | 已实现有限重试和幂等 seam | 全量任务恢复集成已由本批门禁覆盖 |
| 发送失败留下错误并可重试 | `state/attempts/last_error/next_attempt_at` 字段与管理员 retry 接口 | 已实现数据模型和重试入口 | 当前本地 SSE 发送失败才进入重试，外部渠道归后续适配 |
| 患者、医生、管理员页面显示未读数 | `HomeView` 查询 `/notifications/unread` | 已实现工作台未读提醒显示 | 通知中心详情页留给后续 UI 优化 |

## 本轮门禁

- 后端 `mvn -f backend/pom.xml -DskipTests test-compile`：通过。
- `DeliveryServiceTest`：通过，覆盖 dedupe 事件写入后标记已发送。
- 前端 `npm --prefix frontend run build`（含 `vue-tsc --noEmit`）：通过。
- `git diff --check`：通过。

按当前批次策略未执行 MySQL/Redis HTTP 集成、浏览器 E2E、全量 `npm test` 或 `scripts/verify.ps1`；待一批 issue 合并前统一执行。完整双轴审查和延后门禁通过前不关闭 issue。

## 批次完整门禁补充（2026-11-07）

批次修复完成后重新执行 `pwsh -NoProfile -File scripts/verify.ps1` 并退出 0：文档/架构检查、Vue 类型检查与生产构建、MySQL/Redis HTTP 集成、浏览器验收及后端 45 条测试全部通过（0 失败、0 错误、0 跳过）。通知 SSE 失败状态、AI 三次重试、退款结果通知和接诊扫描均已编译并由对应单元/HTTP 测试覆盖；本记录此前的“延后”描述仅反映实现当时的阶段性门禁。
