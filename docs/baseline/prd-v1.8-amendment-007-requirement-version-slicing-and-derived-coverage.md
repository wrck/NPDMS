# PRD V1.8批准修订007：Requirement版本切片与自动派生覆盖

> 修订编号：`CHG-PRD-2026-08-29-007`<br>
> 批准日期：2026-08-29<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-08-29-006`<br>
> 当前快照SHA-256：`95D0A8573C0989721EBA56420CDAD5743A0911E80613F73318439316F26E5697`

## 1. 权威来源

- 前置正式底稿：PRD V1.8修订006。
- 全面审核：`docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`。
- 需求方裁决：`docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`中的VS-001～VS-011。
- 需求方追加原则：已经明确动态模板、动态表单、规则匹配等业务时，对应配置能力是模块基础能力并应优先实现；正文已经明确V2、V3或延后的内容保持原版本。

## 2. 批准结论

1. 100项正式Requirement编号、Owner、优先级及主交付版本统计不变：V1 53项、V2 47项。
2. 按“Requirement ID + 可独立验收的目标版本业务结果”派生111个正式覆盖键：V1 53个、V2 58个。
3. 在100个主切片之外增加11个V2切片：PM-08、PM-11、ACC-01、ACC-02、CUT-01、CUT-03、CUT-05、INT-02、INT-05、INT-12、NFR-02。
4. EXE-05自动识别/升级/分析、CUT-06多角色协同填写、INT-03同步治理工作台不形成未定义V2承诺，转为V3跨需求演进方向。连同既有CLO-05→ACC-02和SUB-03，跨需求演进方向共5项。
5. CUT-07、CUT-09、CUT-10是CUT动态模板、表单和匹配的V1首批配置基础；删除未定义的V2“规则配置与使用效率增强”，不把删除内容虚构为V3承诺。
6. 配置基础前置规则不改变已明确延期：PRD或需求方已明确为V2、V3或后置的配置、模板、表单、匹配能力继续保持既定版本。

## 3. 自动派生规则

1. PRD附录A.1和A.1.1是100项Requirement及111个正式版本切片的唯一业务输入。
2. 参与覆盖计算的Feature Spec以`Requirement切片覆盖`登记`Requirement@V1|V2=FULL|PARTIAL`；关联、支撑、依赖和历史说明不自动形成覆盖。
3. Feature Implementation Done只从`tasks/features/F-*.md`的权威实施状态读取；缺少任务记录时不得仅凭Feature Spec中的历史实施说明派生完成。
4. `FULL`覆盖且权威Task已完成时，切片派生为`IMPLEMENTATION_COMPLETE`；只有已完成的`PARTIAL`覆盖时派生为`IMPLEMENTATION_PARTIAL`；没有已完成权威Task覆盖时派生为`NOT_STARTED`。
5. `scripts/generate_requirement_traceability.py`同时生成`docs/traceability/requirement-version-coverage.json`和`docs/traceability/requirement-matrix.md`。脚本中的人工版本覆盖和人工完成状态覆盖已删除，两个输出均不得直接编辑晋级状态。

## 4. 影响边界

- 直接影响：PM-08、PM-11、EXE-05、ACC-01、ACC-02、CUT-05、CUT-06、CUT-07、CUT-09、CUT-10、INT-02、INT-03、INT-04、NFR-02及版本规划/端到端验收投影。
- 追溯影响：PRD、13领域派生规格、Requirement矩阵、结构化覆盖、工程链、基线元数据、变更记录及相关审核/裁决材料。
- Feature自身已经形成的合法Implementation Done不因Requirement仍为部分覆盖而撤销；修订007只重新计算Feature对目标版本切片的覆盖范围。
- 本修订不直接批准新的API、数据库、权限、审批节点或状态机实现；受影响的SDS、Feature Spec和Technical Plan须按差异重新评审后再实施未覆盖义务。

## 5. 基线关系

本修订合并至`需求/PRD-项目实施交付管理平台.md`，并冻结为`docs/baseline/prd-v1.8.md`。两份文件必须保持字节一致，当前SHA-256均为`95D0A8573C0989721EBA56420CDAD5743A0911E80613F73318439316F26E5697`。
