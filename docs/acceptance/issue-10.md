# #10 MVP-09 通知、SSE 补齐与可靠性验收

依据 GitHub #10、MVP规格与 ADR-0013。旧批次绿灯不能证明客户端接入或重启恢复；本记录以本轮实际用例为准。

| AC | 真实验收入口 | 观察结果 |
| --- | --- | --- |
| 业务先提交 MySQL 再投递 | notificationsPersistBeforeDeliveryAndRollbackDoesNotEmit；confirmedCheckInAndCallNotificationsFollowBusinessFactsWithoutDuplicateCheckIn；停诊/替诊 HTTP 和退款租约用例 | 未提交通知不被 worker 读取，回滚无消息；确认/取消/停诊/替诊/退款/签到/叫号持久通知；重复签到及扫描不重复生成或投递 |
| SSE、断线游标、轮询 | notificationCursorPagesAndAuthenticatedSseReplayCatchUpMoreThanOneHundred；laterDeliveryWorkerDrainsEarlierCursorBeforeItsOwnEvent；三端通知浏览器流程 | 105条分100+5查询，认证HTTP流从 Last-Event-ID 重放最后5条；屏障阻塞较早 worker，较晚 worker 仍按持久游标顺序发送；浏览器阻断流后自动轮询，解除后恢复实时，无重复 |
| AI 有限幂等恢复 | expiredAiLeaseIsRecoveredAndDelayedOldWorkerCannotCreateAnotherResult；abandonedAiLeaseExhaustionAndRevocationPublishTerminalNotificationsOnce；RuntimeRestartHttpTest | AI调用不持有业务事务；31秒后抢占租约，旧 worker 返回不能覆盖或重复写结果；真实两次应用启动恢复 RUNNING 到第2次成功；同一提交仍返回同一任务；构造第3次租约已废弃状态，由真实扫描器处理耗尽/撤权并生成一次终态通知 |
| 退款有限幂等 | BookingHttpTest退款失败、废弃租约恢复及 thirdAbandonedRefundLeaseStopsAndPublishesOneFinalReminder | 三次上限；到期恢复同一退款；第三次租约废弃后最终失败、不再调用 provider，错误和最终提醒保留，重复处理幂等 |
| 占号/替诊持久任务 | durableHoldAndSubstitutionTasksExpireOnceAndReminderHonorsWindow；durableTaskErrorsRespectBackoffAndStopAfterThreeAttempts；RuntimeRestartHttpTest | 不依赖患者GET触发；持久扫描到期释放占号、处理替诊取消退款；重启后占号任务继续；业务效果和任务完成同事务；失败60秒退避、3次封顶、重复扫描不增加尝试 |
| 发送失败不回滚事实 | deliveryFailureRetriesAreBoundedAndDoNotRollbackConfirmedAppointment | 可控传输连续失败3次，留下错误码，确认预约事实不变；通知仍可查；已读204幂等、外人404 |
| 三端未读和关键提醒 | NotificationPanel、临床消息自动刷新、三端通知浏览器流程 | 患者/医生/管理员全局未读及关键提醒；全部分页通知可标已读，不截断早期未读；预约签到窗口提醒去重；诊室双方消息无需手动刷新 |
| 双轴复核 | code-review规格与规范复审 | 已修复通知列表截断、AI/退款终态漏通知、SSE重放与实时乱序、旧异步请求清理新会话问题；无剩余阻断发现 |

最终批次完整验证：`pwsh -NoProfile -File scripts/verify.ps1` 退出0。文档/架构检查、Vue类型与生产构建、真实MySQL/Redis HTTP、84条后端测试及16条浏览器流程全部通过，零失败/错误/跳过。此前为修正测试上下文清理而停止的一轮不计为通过证据。定向验证另有29条后端/16条浏览器及新增5条边界用例通过；交付结论以冻结源码后的最终整轮为准。

运行边界：单后端节点、私有本地附件与持久审计目录；通知和任务依赖 MySQL，Redis只用于登录。站内消息不承诺客户端只收到一次，服务端dedupe和浏览器ID去重确保重放安全。无活跃流时持久站内消息即视为投递成功，后续登录按游标查询；SSE失败不删除站内消息。任务3次终止失败需运维排查后制定恢复方案。没有接入真实支付或付费外部AI，不以模拟成功冒充外部验收。README记录扫描频率、租约、退避、代理和单机边界。
