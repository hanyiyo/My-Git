# D4 结构化病历与药品提交闭环验收

对应 GitHub 任务：[#27 医生结构化病历与药品提交闭环](https://github.com/hanyiyo/My-Git/issues/27)。

## 验收案例与公开边界

| 案例 | 公开边界 | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 医生提交主诉、现病史、既往史/过敏史、诊断、处理建议 | `POST /doctor/clinical/{id}/result` | `WorkflowHttpTest.structuredClinicalResultPersistsRequiredFieldsAndPharmacySnapshot` 返回 200，患者读取五项字段 | 现病史、既往史/过敏史允许为空 |
| 主诉、诊断、处理建议必填，旧 `body` 请求继续兼容 | 结构化结果 REST 接口、旧接诊完成接口 | 空必填字段返回 400；既有结果提交回归通过 | 旧请求只保存兼容 `body`，结构化字段为空 |
| 处方来自启用目录，数量、用法用量服务端校验并保存快照 | `POST /doctor/clinical/{id}/result`、`GET /doctor/pharmacy/appointments/{id}/prescriptions` | 订单为 `PENDING_PAYMENT`，规格、价格、数量、用法用量和合计可读 | 仅支持启用药品目录 |
| 病历、结束接诊和库存锁定同一事务，库存不足不产生结果 | 结构化提交 REST 接口 | 库存锁定与结果写入同一事务；完整 HTTP 门禁 103 个测试全部通过 | 未接入真实支付或外部库存系统 |
| 患者模拟支付成功，药费超时释放库存且不撤销病历 | `POST /patient/pharmacy/orders/{id}/pay` | `WorkflowHttpTest.expiredPharmacyPaymentReleasesStockWithoutRemovingClinicalResult` 验证库存恢复、订单失效、病历仍可读 | 支付为模拟支付 |
| 结果提交后只读，不自动叫下一位；更正保留原作者、版本、原因 | 医生、患者正式诊室页面 | 既有 `clinicalMessagesResultsAndCorrectionsRespectOwnershipAndReadOnlyBoundary` 回归通过；提交后诊室显示只读 | 更正以追加记录保存，不重写原结果 |
| 医生和患者正式页面显示结构化病历、订单状态和用法用量 | `/doctor/clinical/{id}`、`/patient/orders/{id}/clinical` | 浏览器用例 `医生提交结构化病历并让患者独立支付药费` 通过；205 个浏览器用例全通过 | 页面支付入口仍是模拟支付 |

## 验证记录

- 后端：真实 MySQL/Redis HTTP 测试 103 个通过，0 失败。
- 前端：Vue 类型检查、生产构建通过；浏览器验收 205 个通过，0 失败。
- 文档与架构检查全部通过；完整门禁 `pwsh -NoProfile -File scripts/verify.ps1` 返回退出码 0。

## 当前限制

药费支付仍是患者端模拟支付，未接入真实支付渠道；库存锁定、释放和结果保存均在服务端事务中完成。GitHub Issue #27 保持开放，等待后续统一验收关闭。
