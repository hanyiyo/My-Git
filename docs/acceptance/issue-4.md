# #4 MVP-03 预约登记与模拟支付

状态：实现中，尚未验收。依据 GitHub #4、mvp-spec、ADR-0011/0012/0013。

测试 seam 已在规格 Testing Decisions 授权：真实 Spring HTTP + MySQL/Redis；可控 Clock/支付适配器；真实浏览器（含手机视口）。不通过查询业务表验证结果。

| 验收规则 | 公共 seam / 用例 | 结果与限制 |
| --- | --- | --- |
| 患者选择线上/线下、医生、日期、上午/下午排班后登记 | `BookingHttpTest.patientCreatesPendingPaymentAppointmentAndClaimsPublishedSlot`：HTTP 发布排班、创建登记、重新查询订单与可用号源；现场登记边界用例 | HTTP 登记和快照已通过；支付和前端未实现 |
| 同一患者同日期半天跨医生/模式仅一条有效登记；号源不超卖 | `sixPatientsCompetingForOneSlotCreateExactlyOneHold`、`onePatientCompetingAcrossDoctorsAndModesHoldsOnlyOneHalfDayRegistration`；真实 HTTP 并发、患者半天 guard | 已通过六人竞争一个号源、同一患者跨六位医生/两种模式仅一条登记成功；其余409 |
| 截止=min(提交+10分钟,结束−15分钟)，提前预约开诊截止，现场挂号签到截止 | `offlineWalkInUsesCurrentSessionAndEarlierOfTenMinutesOrCheckInCutoff`、精确到期复用用例；可控 Clock + HTTP | 已验证10分钟、17:35/17:40均在17:45截止、开诊起现场可登记、线上/未来/已过时段拒绝、开诊停止提前预约、签到截止右端排除；锁等待专项仍需最终审查确认 |
| 模拟支付幂等、金额快照、预约凭证 | `simulatedPaymentConfirmsSnapshotAndCredentialOnceAndRetainsSlotAfterHoldDeadline`；HTTP 重复付款及归属限制 | 本地模拟付款、2000分订单快照、唯一凭证和持续占号已通过；迟到付款退款及支付并发仍待完成 |
| 取消/停诊/迟到付款退款可追踪、幂等，失败可恢复 | HTTP 取消、停诊应用接口、支付适配器失败/重启；旧订单付款不释放新订单占用 | 待实现/验证 |
| 患者订单 API/UI、权限及手机兼容 | 订单归属/RBAC HTTP；真实浏览器完整路径和手机视口 | 待实现/验证 |

完成后运行完整 `scripts/verify.ps1`，记录结果；双轴代码审查与远端 CI 通过前不声称完成验收。

## 首个切片的 TDD 证据

- Red：`pwsh -NoProfile -File scripts/test-backend.ps1 -Test BookingHttpTest`，1 个测试失败；创建接口不存在，期望201，实际500（现有通用异常响应）。身份、管理员发布排班成功。
- Green：同一命令，1 个测试通过、0失败/错误/跳过，BUILD SUCCESS。测试通过 HTTP 观察待支付状态、2000分金额快照、10分钟截止、本人订单查询、目录剩余名额从2变1。
- 完整回归：父工作单元执行 `npm test`（即 `scripts/verify.ps1`）退出0；六项文档/架构检查、Vue 类型检查与构建、21条 Java 测试全部通过（0失败/错误/跳过），其中包括原有8条真实浏览器流程。此结果仅验证首个切片没有破坏已有行为，不代表 #4 完成。
- 报告：`backend/target/surefire-reports/cn.medical.BookingHttpTest.txt`（最近一次运行）。后续需补齐幂等、超时释放、并发验证、支付退款和 UI，之后进行完整双轴审查和远程验收。

## 幂等、到期复用与并发切片

- 重复提交先以201期望、409实际复现失败，修复后相同请求返回原订单，键与登记内容不匹配返回409且不重复占号。
- 精确截止测试在到期时申请仍返回409，修复后到期前不可占、到期即可复用；不依赖后台扫描。号源租期与订单共用同一截止快照，历史订单读取为 `EXPIRED`，按订单标识释放患者半天 guard，不影响替代订单占用。
- 六条预约 HTTP 用例通过，包含上述并发竞争及本人订单、外人404、角色403和匿名401。
- 父工作单元 `npm test` 完整回归退出0：六项文档/架构检查、Vue 类型检查/构建、26条 Java 用例（0失败/错误/跳过），包含原有8条真实浏览器流程。此切片尚不包括现场截止、支付、退款或订单 UI，#4 保持未验收。

## 现场登记截止切片

2026-11-07（医院时区 Asia/Shanghai），新用例先以开诊时现场登记期望201、实际400复现未支持的来源；实现后仅当天线下本门诊时段可现场登记，锁后再次检查时间并共用截止快照。测试准备复用管理员会话以免反复登录触发限流，生产阈值不变。完整 `npm test` 退出0：27条 Java 测试（0失败/错误/跳过）、8条浏览器流程、文档检查和 Vue 类型检查/构建均通过。支付、退款、订单 UI 与远程验收仍待完成。

## 模拟支付确认切片

新增付款用例先因缺少接口失败（外人请求期望404、实际500）。实现后，账务模块独立持久化标为 `SIMULATED` 的唯一付款事实；预约和号源模块通过应用接口在同一 MySQL 事务中保存凭证、确认状态与持续占用。重复付款返回相同结果，外人不能付款，旧占号期限到达后已确认预约不失效。此处不发生真实资金转移或外部调用。

完整 `npm test` 退出0：28条 Java 测试（0失败/错误/跳过）、8条浏览器流程、文档检查及 Vue 类型检查/构建全部通过。此阶段迟到付款暂时拒绝；取消、迟到退款、停诊及失败重试、订单 UI 和远程验收尚未完成，#4 仍未验收。
