# 由管理员发布号源并控制预约状态

状态：accepted

适用范围更新：本 ADR 描述早期 MVP 的号源和状态模型；[ADR-0011](0011-paid-booking-holds-mvp.md) 已将首版细化为临时占号、模拟支付成功后确认，并加入签到候诊。有效临时占号同样必须保护，不能被其他预约占用。停诊审批职责由 [ADR-0009](0009-suspension-review-chain.md) 细化。

平台将医生的 Schedule 与患者实际可占用的 Slot 分开建模。管理员发布可预约的 Slot；医生可以提交 Schedule change request，但不能直接改变已发布的患者承诺。Appointment 只引用一个 Slot，并通过 `requested`、`confirmed`、`completed`、`cancelled` 和 `no-show` 表达生命周期；MVP 不做候补队列。这样可以把医生排班意图、平台可售号源和患者预约记录分开，避免取消、调班和审批互相覆盖。

## 后果

后续确认：早期 ADR-0006 的提交即确认规则已被 ADR-0011 部分替代。MVP 需要区分支付前临时占号和支付后确认；`requested` 不是已批准的医生二次确认阶段。完整枚举、完成与爽约触发规则仍待确定。

- 占用 Slot 必须具备并发保护，同一时段只能产生一个有效 Appointment。
- 取消是否释放 Slot 必须由明确的状态转换规则决定，不能由界面层随意修改。
- 排班申请和管理员审批需要独立记录，患者看到的是审批后的已发布安排。
