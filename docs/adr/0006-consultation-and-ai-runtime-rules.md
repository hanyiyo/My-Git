# 预约与问诊运行规则

状态：部分替代，参见 [ADR-0011](0011-paid-booking-holds-mvp.md) 与 [ADR-0012](0012-separate-booking-payment-and-encounter.md)。

适用范围更新：本 ADR 为早期异步 MVP 决策。Q31–Q42 已确认首版包含模拟支付、签到、候诊与线上按序接诊，见 [当前 MVP](../design/current-mvp.md)。支付有效后才确认预约；接诊后医生提交结果并结束服务，预约才标记完成。下文 `open/replied/closed` 不能直接充当整个就诊生命周期。

MVP 中可用 Slot 被患者成功提交后，Appointment 立即进入 `confirmed`；患者可在预约开始前取消，取消会释放 Slot，开始后不提供患者自助取消。一个 Appointment 最多关联一个 Consultation，问诊状态为 `open`、`replied` 或 `closed`；患者可在关闭前补充材料，医生关闭后只读。AI 在患者首次提交后自动运行，失败或延迟不阻塞问诊提交和医生回复，医生可以重新生成结果。

## 后果

- 预约写入必须同时保护 Slot 占用和请求重试，避免重复预约。
- AI 结果需要独立于 Consultation 主状态保存，不能用 AI 任务状态代替问诊状态。
- 取消和关闭都必须记录操作者与时间，方便医疗记录审计。
