# ADR-0044：修订017设计与物理载体对齐

> 状态：`IN_REVIEW`
> PRD：`PRD_V1.8_REVISION_017`；Blob `fd701f153f3148001625fcfe45180b36aae719c2`
> Requirement：PM-01、PM-03、PM-06、PM-10、PM-11、COM-01、ACC-03、CLO-01、CLO-02、RPT-02
> 证据：`docs/engineering/gates/phase-1/revision-017-review-remediation.md`

## 背景与本轮结论

PR #1来源修订012/014/016（统一基线017）已确认模板图、三类退出和同项目范围追加。旧SDS仍保留数字相邻推进、强制S6闭环及PM-06多期群组；旧校验器又固定匹配修订007批准和没有版本切片的Owner列，不能证明现行设计正确。

本轮不变更PRD业务语义。SDS正文替换冲突契约；对象目录采用规范标识与唯一Owner，禁止把说明段落当对象或表名。COM拥有唯一项目范围水位及不可变版本，PROJ拥有追加申请/实例编排，ACC拥有报告及其版本，PLT只托管业务视图的技术注册。业务对象事实仍由对应领域管理。

模板配置使用类型化、版本化定义及引用，图转移单独保存；发布校验全部引用、唯一开始与正常收口、无环、可达、唯一目标、主绑定及Owner权限。Stage与Task分别保存主执行绑定，不能通过通用完成绕过领域事实。BPM既有六类Fact谓词、实际定义身份、候选人约束与窄接口保留；ENTRY与EXIT分别绑定被评估的Stage，不复用源准出为目标准入。

## 物理设计与迁移边界

沿用ADR-0030/0031的前向Feature载体模式，新增/复用的12张参考表由`sds-revision-016-physical-contract.json`唯一描述，参考SQL由生成器派生。冻结字段、类型、空值、租户唯一、当前版本、来源幂等、同Owner引用和检查约束；不建立跨Context物理外键。已有同名表只能进行受控前向兼容演进，不得重建或改写已执行Flyway。

原CORE_MIGRATION_SUBSET DDL未改，旧P3-E09只证明其同哈希范围；原修订016参考Schema的MySQL8.4.11/42项历史证据继续保留；本轮标准字段Schema另行验证。参考Schema验证不等于存量升级、历史迁移、业务Provider、UI或生产验收。受影响对象的历史来源明确为已有证据或NONE_NEW，不按旧表名猜测恢复多期业务。

## 批准边界

当前为技术修复提案与自审，不记为独立复审或需求方已批准。本轮用户执行授权不冒充产物签署；Phase 1/2/3当前技术结果和正式批准分别写入原有唯一gate-status，未新增并行Gate。

正式晋级仍须真实独立Reviewer对当前PRD、SDS、对象目录及参考DDL审阅，并按现有治理完成需求方批准。历史Task Done、DU认领、Feature Ready差量、P3-E01～08、AI-MIG-000和Release不由此ADR自动放行。
