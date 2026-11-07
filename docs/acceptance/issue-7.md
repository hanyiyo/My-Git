# #7 MVP-06 AI 业务助手与文本预问诊

依据 GitHub #7、mvp-spec、Q58/Q60/Q66、ADR-0002/0004/0008/0010/0013。此前批次绿灯未证明关键 AC，旧“已全部验收”推断撤回，以本轮证据为准。

| AC | 公共接口/用例 | 观察结果 |
| --- | --- | --- |
| 普通业务助手只使用医院事实，症状先本地引导 | GET /assistant/business；WorkflowHttpTest.aiFailureRemainsVisibleWithBoundedMetadataAndBusinessSymptomsStayLocal | 医院地址匹配目录；胸痛混合地址查询转授权并显示急救指引，不先发外部分类；未知主题拒答 |
| 预约前授权，拒绝不阻塞挂号 | /patient/ai/consents、/preconsultations、/results；prebookingPreconsultationNeedsNoAppointmentAndSharingIsExplicit；aiAuthorizationBindsOneTextAndRefusalDoesNotBlockIndependentBooking | 零预约可分析；未接受、文本改变、撤回403，外人404；拒绝后独立登记支付仍成功；每次授权仅限精确文本且单次使用 |
| 提取症状、持续时间、科室、追问、风险，不作诊断处方 | LocalAiTextProviderTest 3条 + HTTP + 两条业务浏览器流程 | 本地模拟始终标明；3天、胸痛安全提示和非诊断文案均验证；无法匹配时明确不确定，不任意推荐首个科室 |
| 只推荐真实医生和有效排班 | aiResultsUseRealRecommendationsKeepVersionsAndRespectRevokedSharing | 推荐ID逐一与公开有效排班交叉校验；目录服务只返回有效认证医生；推荐到目录仍经独立登记校验 |
| 医生可读摘要、材料变化标旧摘要过时、撤回共享 | HTTP版本和归属矩阵；workflows.spec.ts | 新结果关联材料/授权/provider/模型与提示版本，旧结果保留；预约前摘要须主动共享给具体预约，撤回后医生不可见 |
| provider失败不可用、元数据可追踪 | EXTERNAL_TEXT HTTP+浏览器失败刷新保留；/admin/ai/calls及管理页 | 三次失败、不产生模拟答案；失败任务刷新可见；患者/医生访问管理日志403，管理日志不含医疗正文 |
| 已有数据升级版本准确 | populatedVersion15DatabaseUpgradesWithCorrectAiMaterialProvenance | 单独构造带数据V15库升级V16，原第2版材料正确回填为2及对应文本 |

完整门禁：pwsh -NoProfile -File scripts/verify.ps1 退出0。文档/架构检查、Vue类型/生产构建、真实 MySQL/Redis HTTP、60条后端测试、13条浏览器流程均成功，零失败/错误/跳过。规范与规格双轴审查已知发现均已修复。

限制：默认只用标识明确的 LOCAL_MOCK，未接入或启用真实付费外部模型。MySQL 持久任务的重启抢占/有限重试恢复属于仍开放的 #10，不能从本 issue 的同步三次尝试推断已具备。
