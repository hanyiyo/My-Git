# 由管理员发布号源并控制预约状态

状态：accepted

平台将医生的 Schedule 与患者实际可占用的 Slot 分开建模。管理员发布可预约的 Slot；医生可以提交 Schedule change request，但不能直接改变已发布的患者承诺。Appointment 只引用一个 Slot，并通过 `requested`、`confirmed`、`completed`、`cancelled` 和 `no-show` 表达生命周期；MVP 不做候补队列。这样可以把医生排班意图、平台可售号源和患者预约记录分开，避免取消、调班和审批互相覆盖。

## 后果

- 占用 Slot 必须具备并发保护，同一时段只能产生一个有效 Appointment。
- 取消是否释放 Slot 必须由明确的状态转换规则决定，不能由界面层随意修改。
- 排班申请和管理员审批需要独立记录，患者看到的是审批后的已发布安排。
