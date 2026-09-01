# PRD V1.8批准修订011：巡检预期结果正则受限语法子集

> 修订编号：`CHG-PRD-2026-09-01-011`<br>
> 批准日期：2026-09-01<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-08-30-010`<br>
> 关联裁决：`NPDMS-Q-FINS001-003-GO-20260901-01`

## 1. 权威来源

- 前置正式底稿：PRD V1.8修订010。
- 既有业务语义：INS-09使用预期结果正则判定巡检命令输出通过或异常，发布前必须通过语法和复杂度校验。
- 安全约束：NFR-02要求防止不受控正则造成资源耗尽，规则、日志和结果不得包含Secret。
- 工程裁决：以JDK 25 `java.util.regex.Pattern`受限子集形成唯一可测试的服务端语法基线。

## 2. 批准结论

1. INS-09预期结果正则的服务端语法基线明确为JDK 25 `java.util.regex.Pattern`受限子集，不再表述为兼容主流正则语法或PCRE。
2. 平台不承诺PCRE兼容性，不支持PCRE专有语法；表达式必须通过JDK语法编译、允许结构和复杂度限制校验后方可发布。
3. 允许结构、禁止结构和复杂度预算由正式安全SDS冻结；当前采用`NPDMS-Q-FINS001-003-GO-20260901-01`批准口径。
4. 本修订不改变正则用于判定巡检结果通过或异常的业务语义，不新增Requirement、业务角色、审批节点、生命周期状态、接口、数据对象或外部集成。
5. 已发布规则revision和历史任务继续按冻结版本解释，不因本修订覆盖不可变历史。

## 3. Requirement与影响边界

- 直接细化：`INS-09@V2`预期结果正则配置与发布校验。
- 关联安全边界：`INS-03@V2`规则发布和`NFR-02@V2`资源与Secret保护。
- 不影响：INS-02运行时匹配输入长度、匹配超时和任务执行预算；这些由后续执行Feature冻结。
- 不影响：API、数据库、权限、状态机、第三方平台或Yudao基础平台实现。

## 4. 验收边界

- JDK 25受限子集内且满足SDS预算的表达式可通过发布校验。
- PCRE专有语法、JDK语法错误、禁止结构或超预算表达式必须拒绝发布，旧发布revision继续有效。
- 发布校验只解析和编译表达式，不对不可信设备输出执行试匹配。
- 本修订完成不代表INS-02执行引擎、Deployment、SIT、UAT或Release完成。

## 5. 基线关系与下游落位

本修订合并至`需求/PRD-项目实施交付管理平台.md`，并冻结为`docs/baseline/prd-v1.8.md`。两份文件必须保持一致。

下游正式落位：

- 受限语法、复杂度与秘密扫描：`docs/design/14-security-design.md`。
- 字段级错误码：`docs/design/16-exception-and-idempotency.md`。
- 验收边界：`docs/design/20-test-design.md`。
- Feature Ready与实施计划：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`、`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`、`tasks/features/F-INS-001.md`。
