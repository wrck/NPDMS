# F-IMP-002 到货签收与里程碑事实 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY`
> Requirement：`EXE-01（V1/P0）`
> Requirement切片覆盖：`EXE-01@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 消费Feature：`F-IMP-003`、`F-IMP-001`

## 1. 业务目标与范围

按项目设备范围和到货批次保存部分签收、补签、差异、拒收、具体设备/数量豁免和不可覆盖证据；全部应到范围签收或取得有效豁免后，形成“已签收”权威里程碑事实。本Feature不实现安装、CUT、ACC归档Owner或外部适配器。

## 2. 核心规则

- 每批必须有物流单号、签收人/时间、设备或订单型号数量明细和签收证据；数量不得超过未签范围。
- 明细至少表达未到货、已签收、差异待处理和已拒收；部分签收只开放已签设备给EXE-02。
- 差异和豁免保存原因、风险、证据、批准人、有效范围和版本；未知差异不得用豁免掩盖。
- 已提交批次不覆盖；补签、差异关闭和纠正追加新记录并使里程碑事实版本递增。
- 项目经理确认本人负责项目；授权现场成员只编辑本人未提交批次。证据归档失败不回滚签收真值，但必须标记待重试。

## 3. 公开事实契约

`ArrivalAcceptanceFactApi.inspect/lockAndRevalidate`按`tenantId/projectId/deviceIds/expectedFactVersion/expectedScopeWatermark`查询或锁定重验，返回`arrivalAcceptanceId`、`ACCEPTED/NOT_ACCEPTED`、`factVersion`、`scopeWatermark`、已签/豁免/未满足设备范围和`reopened`。不返回DO、客户签收人隐私或附件正文。

## 4. 数据、UI与验收

- Owner表：`imp_arrival_acceptance`、`imp_arrival_line`、`imp_arrival_difference`；历史追加，不直接重解释旧`pms_eng_arrival` tinyint。
- UI支持批次草稿、部分签收/拒收、差异闭环、补签、豁免和里程碑摘要，按项目/设备范围裁剪。
- 验收覆盖多批、部分到货、数量超限、差异/豁免、补签不覆盖历史、重开使事实版本变化、越权、真实MySQL和真实浏览器。

## 5. Feature Ready Gate

`NOT_READY`：须审批旧到货记录字段/状态/完整性复用映射，锁定项目应到设备范围、ACC-04引用和公开事实Schema后独立复审。
