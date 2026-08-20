# P3-E09 数据模型基线独立复审记录

> status: `APPROVED`<br>
> conclusion: `GO`<br>
> ddlSha256: `6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`<br>
> itemsSha256: `DD49BDEF20995CF3B17D85E946F678A0CD8CFB404A4A585A36E00401A273D60D`<br>
> itemCount: `2079`<br>
> deferCount: `0`<br>
> testResult: `PASS`<br>

## 复审结论

结论：`GO`。ADR-0030六表与正式领域模型、对象到表映射、当前DDL、字段目录、逐项寄存器和隔离MySQL 8.4执行证据一致，2,079项中`DEFER=0`。复审发现并修正了一处纯追溯偏差：V1.8六表差量不再把PM-10项目异常关闭或CUT-01任务容器虚挂为物理承载需求；该修正未改变DDL、item集合或两项哈希。当前模型可恢复为`MODEL_BASELINE_READY`，仅作为SDS和Feature数据模型输入。

该结论不授权历史数据迁移、数据切换或生产发布。Release不包含历史迁移和数据切换时，`AI-MIG-000`为`NOT_APPLICABLE`；包含任一项时，仍须在Release前完成真实批次验证并只在批准窗口内执行。

## 已复审模型事实

|项目|复审事实|
|---|---|
|Git 基线|Git原生保存commit ID、作者、时间和差异；复审记录不重复维护候选提交、日期或范围字段|
|当前 DDL SHA-256|`6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`|
|逐项寄存器|`ddl-item-decision-register.json`，SHA-256 `DD49BDEF…3D60D`，共 2,079 项；994 项 `ACCEPT_CURRENT`、1,085 项 `AMEND_CURRENT`、0 项 `DEFER`|
|逐项决策证据|ADR-0019～ADR-0023、ADR-0025、ADR-0027、ADR-0028历史清单及ADR-0030六表差量；逐项裁决和整体一致性复审均已完成|
|隔离执行事实|MySQL 8.4.10 执行证据绑定同一当前 DDL 哈希，状态 `PASS`|
|Q08|130 项候选索引；仍须由 Feature 查询计划及 P3-E06 性能验收验证|
|需求追溯|ADR-0030六表只回指PM-03、PM-11、CUT-03、INT-12；PM-10与CUT-01不作为这六表的物理承载依据|
|迁移批准哈希|P3-E09不定义该字段；未来历史迁移门禁按真实批次另行定义|

## 独立复审范围

本复审只核对当前模型制品的整体一致性：正式制品哈希、当前 DDL、MySQL 8.4 执行事实、`DEFER=0`以及决策与复审责任人不同。提交、作者、时间和文件差异由Git原生记录；本文件不重复构造候选提交、日期或范围授权。它不要求四角色外部附件、OA/电子签名、逐项 Reviewer 签署、独立批准 JSON 或迁移批准状态机。

固定字段只包括复审结论和模型事实，且均只出现一次；仅在`APPROVED / GO`时才要求`testResult`和隔离MySQL状态均为`PASS`。后续任何DDL或逐项寄存器变化均会使模型哈希失配并要求重新复审。`AI-MIG-000`不属于本模型复审：Release不含历史迁移或数据切换时为`NOT_APPLICABLE`；包含任一项时须另行达到`VERIFIED`并绑定批准窗口。
