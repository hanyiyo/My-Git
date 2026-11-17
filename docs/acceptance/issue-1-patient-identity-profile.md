# 患者注册资料与管理员建档验收记录

范围：患者注册必须采集姓名、手机号、账号、密码和图形验证码；管理员可手动创建患者账户。姓名和手机号保存到 `patient_profile`，登录后的账户资料与患者首页使用数据库中的姓名。

| 验收项 | 公开测试边界 | 结果 |
| --- | --- | --- |
| 患者注册写入姓名与手机号 | `IdentityHttpTest.patientRegistersLogsInRefreshesAndRevokesSession` | 已覆盖：`/profile` 返回 `displayName` 与 `phoneNumber` |
| 缺少姓名、手机号或验证码时拒绝注册 | `IdentityHttpTest.patientRegistrationRequiresProfileFieldsAndCaptcha` | 已覆盖：缺少资料返回 400，错误验证码返回 401 |
| 管理员手动添加患者并可登录读取资料 | `IdentityHttpTest.administratorCanProvisionPatientProfile`、前端管理员账户页 | 已覆盖 |
| 患者注册表单展示资料与图形验证码字段 | `frontend/tests/identity.spec.ts`、`frontend/tests/catalog.spec.ts` | 已覆盖，完整浏览器验收通过 |

限制：暂不扩展头像、证件、地址、紧急联系人等其他患者资料；手机号当前只做中国大陆手机号格式校验，不做短信所有权验证。
