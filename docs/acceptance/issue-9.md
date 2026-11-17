# #9 MVP-08 医疗内容访问控制与审计验收

依据 GitHub #9、MVP规格、ADR-0004/0007/0010/0013。旧记录把编译或现有绿灯当作完整验收的推断已撤回，以本轮证据为准。

| AC | 实测入口和用例 | 观察结果 |
| --- | --- | --- |
| 独立授权和理由 | medicalReviewRequiresIndependentPermissionAndSeparatesRecordAndAttachmentScopes | 普通管理员无医疗权限403，禁止自授；另一管理员按理由授予资格；每次预约访问另填理由和范围，30分钟有效 |
| 最小范围 | RECORD/ATTACHMENTS/ALL、其他审核人员、外人目标、科主任矩阵 | 正文授权无附件名称和下载权；附件授权不含正文、消息、更正、正文审核理由；外人授权/目标404，患者和医生进入管理入口403；主任无默认临床访问权 |
| 限制展示且保留原文 | medicalRestrictionsApplyToAllClinicalReadersAndAiDerivativesAndCanBeCleared | 材料、附件、消息、结果、更正分别可限制/解除；患者、医生、AI派生和下载入口同步执行；审核取证原文保留；同一时钟下最新决定按序号生效 |
| 下载、授权、审核和敏感审计 | HTTP授权/共享/下载/更正；AuditSignerTest；medicalReviewExpiresAtThirtyMinutesAndAuditDetectsChangedContentAndMissingTail | Ed25519签名链、事务原子追加、数据库外单调校验点；篡改、缺尾及数据库链尾回滚检测；损坏后两次普通写入均503，不能重新签署覆盖旧历史；公钥可独立留存，私钥和校验点重启保留 |
| 撤回和过期立即执行 | 上述授权矩阵、sharingRevocationSerializesWithAnAuthorizedAttachmentDownload | 资格撤回同时撤销已有访问；单次撤回/恰好30分钟拒绝后续读取；临床读取和撤回用相同care锁串行，进行中的授权下载先完成，撤回完成后的读取403 |
| 审计不泄露医疗正文 | 普通管理员audit查询回归 | 更正医疗理由留在临床记录；普通审计只返回脱敏元数据，不经审计入口绕过审核资格或内容限制 |
| 并发审计正确 | concurrentReviewGrantsAppendAnIntactAuditChainWithoutSnapshotFalseAlarms | 屏障强制4个事务先建立快照，四次授权全部200；当前锁定读取避免REPEATABLE_READ误报，最终完整性通过 |
| 真实审核页面 | workflows.spec.ts新增浏览器链 | 新建独立审核管理员→登录→选择范围/理由→读正文/下载→限制→患者隐藏→解除→原文恢复→撤回→可解释拒绝→完整性通过 |

最终完整门禁：`pwsh -NoProfile -File scripts/verify.ps1` 退出0。文档/架构、Vue类型/生产构建、真实MySQL/Redis HTTP、73条后端测试、15条浏览器流程全部通过，零失败/错误/跳过。双轴复审已知阻塞项全部修复。较早被主动停止的门禁不计为通过证据。

应用层不可抵赖边界：Ed25519证明记录由持有应用私钥的服务签署，认证主体和版本用于归责；不冒充患者/医生个人电子签名。数据库之外的审计目录须持久、限权并联合备份，拥有数据库和私钥目录全部写权的运维仍受信任。旧未签名审计明确计数，不追认历史签名。部署与恢复说明见README。
