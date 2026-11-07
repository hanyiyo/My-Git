# #8 MVP-07 停诊、替诊与排班治理

依据 GitHub #8、ADR-0009、`mvp-spec`、`reception-rules.md` 和 `GLOSSARY.md`。

| 验收规则 | 实现 seam | 当前结果 | 限制 |
| --- | --- | --- | --- |
| 医生提前至少24小时申请，紧急由管理员办理 | `POST /doctor/catalog/suspensions`；服务端按排班快照校验24小时 | 已实现；不足24小时返回 `EMERGENCY_REQUIRED` | HTTP 集成延后 |
| 科主任选择直接停诊或替诊，替诊医生确认/拒绝 | 科主任 inbox/review、替诊医生 inbox/respond | 已实现；拒绝回科主任，科主任自审转管理员 | 通知投递归 #10 |
| 管理员复核重新检查资质、科室和时间冲突 | `adminReview` 调用替诊重验 | 已实现 | HTTP 集成延后 |
| 停诊生效释放号源并处理患者影响 | `BookingService.cancelForGovernance`、号源停用、已付款生成停诊退款 | 已实现；未付款占号解除，已付款全额退款意向 | 退款/通知集成延后 |
| 替诊等待患者选择，接受转交医生，拒绝/超时取消退款 | `substitution_choice`、患者 choices/choose、管理员 expiry scan | 已实现；等待期保留预约名额 | 持久后台扫描归 #10 批次 |
| 审批和排班变化可审计 | `audit_event` 记录提交、审核、选择和生效动作 | 已实现 | 审计查询归 #9 |

## 本轮门禁

- 后端 `mvn -f backend/pom.xml -DskipTests test-compile`：通过。
- `GovernanceServiceTest`：通过，覆盖24小时申请边界。
- 前端 `npm --prefix frontend run build`（含 `vue-tsc --noEmit`）：通过。
- `git diff --check`：通过。

按当前批次策略未执行 MySQL/Redis HTTP 集成、浏览器 E2E、全量 `npm test` 或 `scripts/verify.ps1`；待一批 issue 合并前统一执行。完整双轴审查和延后门禁通过前不关闭 issue。
