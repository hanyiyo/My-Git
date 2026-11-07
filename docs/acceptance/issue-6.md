# #6 MVP-05 临床接诊与病历记录

实现分支：`codex/clinical-records`。依据 GitHub #6、ADR-0004、ADR-0007、ADR-0012、接诊规则和 GLOSSARY.md。

| 验收规则 | 公共 seam / 用例 | 当前实现 | 备注 |
| --- | --- | --- | --- |
| 医生叫号后线上图文接诊或线下面诊登记 | `/doctor/clinical/{id}`、结果提交接口；提交结果联动接诊结束和预约完成 | 已实现 | 线上保存 `ONLINE_CHAT`，线下保存 `OFFLINE_RESULT` |
| 患者诊前症状文本与授权附件 | `POST /patient/appointments/{id}/clinical/materials` multipart | 已实现 | 必须显式 `shared=true` |
| 附件格式、大小、数量、权限 | `ClinicalAttachmentValidatorTest`；私有存储 key 不出现在 API；患者/当前医生分别鉴权下载 | 已实现 | JPEG/PNG/PDF，单份≤10MB，每次≤5份 |
| 结果保存作者、时间和版本，结束后只读 | `clinical_result` 版本表与关闭问诊状态 | 已实现 | 预约状态联动为 `COMPLETED` |
| 更正追加不覆盖 | `/doctor/clinical/{id}/corrections` 写入 `clinical_correction` | 已实现 | 原结果保持不变 |
| 患者与医生按资源归属查看 | 患者本人和预约医生独立查询/下载接口 | 已实现 | 其他患者/医生返回 NOT_FOUND |

本 issue 采用当前批次门禁：已执行后端编译、前端类型检查/构建和本模块附件校验单元测试；跳过 MySQL/Redis 集成、浏览器 E2E 与全量 `npm test`，待一批 issue 合并前统一执行。完整测试前不关闭 issue。
