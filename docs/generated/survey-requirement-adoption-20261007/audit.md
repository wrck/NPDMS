# 工勘与需求分析接入前审计

基准：`12137caffa1738ad24700b2d2873a2636222abe1`。2026-10-07 用户明确要求两项存量业务接入；由主会话审计和实施，分业务提交及验收。

## 数据源与不可丢失语义

- 工勘使用 `SiteSurveyEntityDO` / `sol_site_survey`，保持现有 ID。`SiteSurveyDO` / `sol_eng_site_survey` 是另一条旧实现，不能误当接入源。
- 工勘条件与材料属于 `sol_site_survey_condition`、`sol_site_survey_material` 子表，由 `SiteSurveyDetails` 维护；不能因默认 Mapper 忽略非持久化字段而漏掉或清空。
- 工勘草稿允许编辑/删除，确认/驳回/归档走现有操作语义；外包关联限制删除，结构化地点、项目截止日、动态表单绑定及历史引用必须保留。新默认 CRUD 不能直接开放 status、外包关联键或地点快照等控制字段。
- 需求分析的 `sol_requirement_analysis` 为当前有效内容，`sol_requirement_analysis_revision` 为草稿/冻结/生效修订；逻辑实体 ID 与修订 ID 不相同。不能直接把生效主表当成可任意编辑的普通记录。
- 需求分析每项目草稿/生效唯一性、修订编号、复制来源/基线、冻结历史不可改、草稿读取的项目经理限制继续保留；已有 `complete/copy/discard` 是业务差异，不变成所有业务必须采用的流程。
- 两项统一交付件仍使用 `plt_delivery_material`，需要核对业务实体键究竟是当前对象还是修订，避免历史归属变化。

## 已追踪实现

工勘：`SiteSurveyEntityController` → `SiteSurveyEntityServiceImpl` → `SiteSurveyEntityCommands` / `SiteSurveyBusinessApplicationService` → `SiteSurveyEntityDomainCommands` → `SiteSurveyEntityMapper`，以及 WriteAccess、FormService、Details、地点与截止日公共 API。

需求分析：`RequirementAnalysisEntityController` → EntityCommands / BusinessApplicationService → DomainCommands → EntityProvider / Access → `RequirementAnalysisMapper.xml`；当前与修订字段、扩展和文件历史由各公共能力读取。

## 本轮发现的默认框架缺口

- 内部实体复制通过 HTTP JSON 序列化，会丢失 `@JsonIgnore` 的身份、项目、内部关联字段。生产两业务广泛使用该注解，不能直接接入。已补失败测试：内部隐藏字段复制变为 null；修复为保留内部字段并隔离嵌套可变对象的复制，9 项继承单元测试通过。
- 子表字段的读取/保存、特殊验证时机及既有权限命名需要可重载扩展点；应补公共默认实现的扩展，不以每业务新增一套 CRUD/权限策略/交付件适配器代替。

状态：审计与接入实施中，本文不表示两项业务已验收完成。
