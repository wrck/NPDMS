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

## 10:13 接入检查点

- 工勘：验证分支 `103e925d` 已通过 16 项 H2/旧入口测试；独占 MySQL 工勘文件/材料 1 项及公共回归 15 项通过，浏览器尚待执行。
- 需求分析：新办理实体直接对应已有修订表，沿用真实修订 ID 作为业务实体键，字段 `entityId` 保留逻辑实体 ID；使用 `SOL_REQUIREMENT_ANALYSIS_REVISION`，不改变原 `SOL_REQUIREMENT_ANALYSIS` 对当前有效主表的含义。
- 普通创建、保存、查询与逻辑删除继承默认链；完成/复制调用原有冻结、生效、来源基线等多行领域操作，仍共享默认权限、锁、幂等与回执。草稿内容允许不完整，完成时沿用原完整性校验；普通项目读者不能看经理私有草稿。
- 13 项 H2 持久化/旧链路测试通过。V399 仅调整已逻辑删除草稿的活动唯一槽，保留真实修订号、内容和冻结历史；当前有效行仍保持相同唯一性。修正原放弃草稿 SQL 写负修订号与正数 CHECK 冲突的问题。真实 MySQL 和完整 Flyway 不据此视为通过。
- 需求分析修订复制的公共交付件继承、两业务浏览器、绑定表单/特殊地点及截止日操作还在补齐和验收，未宣称两业务完整接入结束。

## 13:03 接入检查点（未宣布最终验收）

- 工勘、需求分析继承公共 `GET /{id}/form` 与 `POST /{id}/save-form`，原生扩展数据继续使用原 Owner 身份；需求分析继续使用逻辑实体 ID + 修订 ID，未改成新修订实体 ID 作为扩展主键。原布局与扩展编辑复用现有渲染器，未提交字段不清空。
- 默认 Service 本身提供 EntityFieldProvider，无须新增专用字段适配器。空业务继承失败测试先红后绿；不允许用传入 actor 冒充会话用户，不暴露内部字段。
- 工勘结构化地点通过既有 EngineeringLocationFactService 维护并保存引用/快照；项目期限通过既有 ProjectEndDateApi 按项目版本更新，均共享默认业务锁、版本、幂等及草稿限制。普通更新不能覆盖结构化地点快照。
- Java 定向回归 43 通过（公共 11、工勘 18、需求分析 14），包含原链测试及新增扩展保存失败回滚、子表保留、原生扩展身份、地点/期限域端口与状态拒绝。前端 17 项唯一测试通过。SQL 为 H2；外部地点、期限及布局策略为测试替身，不代表生产环境服务已验收。
- TypeScript 真实传递依赖检查仅保留四项已知旧错误：CollectionDialog unused row、processDefinition unused computed、ProjectSchedulePanel tasks nullable、schedulePresentation acceptanceTime 类型；未称全量类型检查通过。
- 验证分支提交 cd617a7ab72fbf61412797e8540138b176d5618f 正在执行新增表单/扩展真实浏览器与 MySQL 回归；此前 72587a65 的浏览器 3 组场景及 63 请求通过不能替代这次变更验收。目标 fix 分支尚未接收本轮接入。

## 13:37 两项存量接入的限定验收

验证提交：`7482c287e01dcfef174d6214e19995519722aa2b`，树 `d7e33148e286c5e5c02f65d7b410577cf8f64ec0`。生产代码与此前 `cd617a7` 相同，之后三次仅修正测试装配、可见开关定位及当前生效扩展投影的数量/归属断言。

- 最新联合浏览器 1 项通过、3 组场景完成、73 条 HTTP 全部 200 / 业务码 0；两项实际默认页面编辑扩展字段、保存、文件上传、工勘确认/归档、需求冻结/复制、删除后再复制、统一归集全部执行。未调用旧 `/business-models/`。
- 需求分析扩展验证源冻结、当前生效投影（revision_id=0）、新草稿三份均为同一逻辑实体的合法内容；三条修订保留删除历史。统一材料四条历史、三条有效；两文件版本/存储对象被复用。
- `cd617a7` 的真实 MySQL 需求分析 14 项、公共交付件/CRUD 16 项通过。前述测试修正未改变生产代码，无须重复这些结果。工勘地点/期限的具体跨模块端口已由 H2 测试验证调用身份、版本与状态拒绝，未宣称外部地址服务或真实项目库验收。
- 根执行 `mvn -pl yudao-server -am package -DskipTests` 成功；这仅证明打包，不代替业务测试。43 项 Java、17 项前端结果与四项旧 TS 错误边界仍适用。
- 原历史布局/schema 策略、登录/权限事实、项目事实及技术存储是隔离测试边界；无生产数据库迁移、生产登录或部署完成声明。V399 真实隔离 MySQL 已执行，完整既有 Flyway 链未因此认证。

### 实际新入口

- 工勘：`/pms/business/site-surveys`，专用 API `/api/v1/pms/site-survey-business`。
- 需求分析：`/pms/business/requirement-analyses`，专用 API `/api/v1/pms/requirement-analysis-business`。
- 旧入口保持；本轮不是全项目模板/任务入口统一切换。权限来自原业务权限及项目范围，未自动授权或新增权限策略类。

### 仍需完成的默认框架目标

用户 13:26 明确要求字段配置、扩展读写、版本化都由默认实现继承提供。字段/扩展已由此批补入。需求分析的完成/复制目前仍复用原专用版本策略，不能证明一个新空业务可直接获得完整版本化默认链。旧 `InheritedRevisionAdapterFactory` 依赖声明目录，尚不能作为新直接继承主干的完成证据；默认版本化与相应新增实体证明继续实施。上述两业务限定验收不等于全部公共框架、全部业务或全部旧交付件迁移完成。
