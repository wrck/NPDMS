# PRD V1.8批准修订012：巡检规则名称稳定身份与唯一性

> 修订编号：`CHG-PRD-2026-09-01-012`<br>
> 批准日期：2026-09-01<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-09-01-011`<br>
> 关联裁决：`NPDMS-Q-FINS001-004-GO-20260901-01`

## 1. 权威来源

- 前置正式底稿：PRD V1.8修订011。
- 既有业务语义：INS-09规则名称用于规则检索、展示和一线工程师稳定识别，规则发布后版本不可覆盖。
- 待关闭歧义：原“规则名称在租户和版本内唯一”没有定义“版本”的业务对象，无法形成有效的物理唯一约束。
- 批准裁决：采用Q-FINS001-004方案A，以规则稳定身份承载名称唯一性。

## 2. 批准结论

1. 巡检规则名称归属规则稳定身份，在同一租户内永久唯一；唯一性不按单条revision号、revision主键或未定义的共享规则库版本计算。
2. 停用、软删除或形成新revision均不释放规则名称；不得将既有名称复用于另一条规则稳定身份。
3. 同一规则稳定身份的后续revision沿用原规则名称；规则名称不通过创建新revision改名，历史revision名称不可覆盖。
4. 为保持八字段版本解释，revision可保存发布时规则名称快照，但唯一性由规则稳定身份负责，快照不得成为另一套可变名称真值。
5. 巡检规则草稿revision允许除稳定身份检测ID和规则名称外的八字段及从属内容暂为空或不完整；发布时必须全部完整。结果阈值数据类型固定为`NUMBER`，命令与正则安全审核结论只允许`PASSED/REJECTED`，且只有绑定当前revision及当前内容摘要的`PASSED`允许发布。
6. 本修订不改变巡检执行结果仍按“通过/异常”解释；`PASSED/REJECTED`仅用于规则发布前安全审核结论。
7. 本修订不新增共享规则库版本、Requirement、业务角色、审批节点、生命周期状态、API动作、外部集成或数据Owner。
8. 已发布规则revision和历史任务继续按冻结版本解释，不因本修订覆盖不可变历史。

## 3. Requirement与影响边界

- 直接细化：`INS-09@V2`规则名称配置、版本解释和唯一性验收。
- 关联边界：`INS-03@V2`规则稳定身份、发布与历史读取。
- 数据设计影响：`InspectionRule`稳定身份保存规则名称并承担租户内永久唯一约束；revision只保存不可变名称快照。
- 细化但不扩展：草稿与发布完整性边界、阈值受控数据类型和安全审核结论机器码。
- 不影响：规则状态机、权限码、安全审核主体与流程、正则预算、产品类型契约、命令执行和INS-02运行时通过/异常行为。

## 4. 验收边界

- 同一租户创建两条不同稳定身份但名称相同的规则时，后创建请求必须失败且无半成品。
- 不同租户可使用相同规则名称。
- 停用或软删除规则后，原租户不得创建另一条同名稳定身份。
- 复制历史revision只能在原稳定身份下形成新草稿并沿用原名称；不得借复制或编辑revision改名。
- 并发创建同租户同名规则时最多一个成功；失败请求不产生孤立稳定身份或revision。
- 字段不完整的草稿可保存并继续编辑；相同内容在发布时必须失败并返回字段级错误。
- `NUMBER`以外的阈值数据类型不得发布；审核结论为`REJECTED`、缺失、失效或摘要不一致时不得发布。
- 本修订完成不代表数据库迁移、Feature实现、Deployment、SIT、UAT或Release完成。

## 5. 基线关系与下游落位

本修订合并至`需求/PRD-项目实施交付管理平台.md`，并冻结为`docs/baseline/prd-v1.8.md`。两份文件必须保持一致。

下游正式落位：

- 数据与唯一性：`docs/design/08-data-model.md`、`docs/design/09-database-design.md`、`docs/design/15-cache-and-concurrency.md`。
- 验收边界：`docs/design/20-test-design.md`。
- Feature Ready与实施计划：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`、`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`、`tasks/features/F-INS-001.md`。
- 问题关闭：`docs/decisions/open-questions.md`中的`Q-FINS001-004`。
