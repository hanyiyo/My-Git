# #6 MVP-05 临床接诊与病历记录

依据 GitHub #6、mvp-spec、GLOSSARY、ADR-0004/0007/0012 与接诊规则 Q39/Q46/Q59。此前批次测试未覆盖关键临床 HTTP 路径，因此此前“完整验收”结论撤回，以本记录为准。

| AC | 公共接口与验收用例 | 观察结果 |
| --- | --- | --- |
| 线上图文、线下面诊登记 | WorkflowHttpTest 对 ONLINE/OFFLINE 分别走登记、支付、签到、叫号、开始、双方消息、结果与更正 | 通过；结果类型分别为 ONLINE_CHAT/OFFLINE_RESULT，完成后预约与诊室联动只读 |
| 授权诊前材料 | multipart /patient/appointments/{id}/clinical/materials | 未授权返回400；完成后提交材料、消息或 AI 返回409 |
| 格式、大小、数量与附件权限 | HTTP 上传/鉴权下载 + ClinicalAttachmentValidatorTest | 格式不符、6份文件返回400；10MB成功，10MB+1返回413；非本人/非当前医生404，撤回后医生下载403 |
| 作者、时间、版本和结束只读 | HTTP 断言作者ID、服务器时间、版本1，重复提交409；并发结束测试 | 并发结束一方200、一方409；旧接诊完成入口也必须提供结果 |
| 更正追加而不覆盖 | HTTP 更正后原结果逐字段保持相同；患者可读更正 | 通过；作者保留，更正单独追加 |
| 患者本人和预约医生归属 | HTTP 查询/下载权限矩阵及医生所有写响应投影 | 通过；撤回后医生消息、结果、更正响应不携带材料 |
| 私有存储故障回滚 | partialUploadFailureRemovesPrivateFilesAndRollsBackMetadata；ClinicalFileStoreTest | 第二份写失败503；第一份清理，元数据回滚；覆盖已有文件、路径逃逸被拒绝 |
| 三端可用界面 | frontend/tests/workflows.spec.ts | 浏览器真实完成患者上传、双方消息、结果作者/版本、追加更正、患者结束只读 |

验证：pwsh -NoProfile -File scripts/verify.ps1 退出0。文档/架构检查、Vue 类型检查与生产构建、真实 MySQL/Redis HTTP 测试、54条后端测试及12条 Playwright 流程均通过，零失败、零错误、零跳过。规范与规格两个审查轴的已知发现均已修复。

限制：只使用虚构医疗数据；附件为本机私有目录，数据库与文件需联合备份。#7–#10 的独立缺口不在本 issue 完成范围内。
