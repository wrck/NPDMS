# F-COM-001 合同订单关联与交付范围分配

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / REQUIREMENT_CONVERGENCE_APPROVED`
> Technical Plan Gate：`PASS / USER_APPROVED`
> Implementation Done Gate：`PENDING_RUNTIME_REVALIDATION_AND_INDEPENDENT_REVIEW`
> 当前阻断：`合同关联与范围分配局部实库/浏览器验证已通过；其余AC的完整运行复核与Implementation Done独立裁决未完成`
> Requirement ID：`COM-01@V1`；协作`PM-03`、`PM-10`、`ACC-03`
> Feature Spec：`specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md`
> Technical Plan：`docs/superpowers/plans/2026-09-02-f-com-001-requirement-convergence.md`
> Delivery Unit：`tasks/delivery-units/DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE.md`

## 当前检查点

### 2026-09-17 合同来源表补齐（局部实施）

- 需求方明确要求先补齐 `proj_project_party`、`com_contract_receivable`、`com_shipment_contract_reference`、`com_shipment_package`。本次为现有核心迁移物理模型的建表承接，不宣称完成历史迁移、财务业务或整个Feature。
- 新增前向迁移 `V258__project_party_and_contract_source_tables.sql`，完整继承 `project-order-physical-schema.mysql.sql` 中四表的127列、主键、租户内来源唯一键、同领域外键及检查约束；不修改已执行迁移，不读写旧源库，不加载真实来源数据。
- 合同主档生成方式正在由需求方重新选择。此前多来源及字段优先级的回答不作为当前实施依据；主档汇总相关规格和实现暂不修改，四表建表独立推进。
- 已完成静态核对：四张建表语句与既定目标DDL一致；被引用的 `proj_project(tenant_id,id)` 唯一键由V88提供，`com_contract(tenant_id,id)`由V160提供。
- 固定测试环境 `npdms-50eb-test` 已存在，但容器配置凭据及本地配置凭据均无法登录MySQL（1045）。未重置密码、未清库、未切换到开发数据库；实库建表、约束失败用例及Flyway升级/重复运行验证尚未执行。

### 2026-09-18 合同主档字段归并改判（不建视图）

- 需求方指令：主档`com_contract`直接包含合并后的全部合同级信息，数据同步时更新对应业务最新值，不创建`com_contract_summary`视图。2026-09-17"主档汇总暂缓"解除，视图方案废止（登记为Q-MIG-DIM-006）。
- 落实：前向迁移`V300__contract_master_field_consolidation.sql`为主档新增17列（币种名称、合同创建时间、项目名称/编码、市场部/办事处/系统部/拓展部、行业、市场代表/辅助代表），已应用开发库并validate通过；规格DDL同步修订（SHA-256 `F9B29ED5D9EE651453AA11DBFFC771E9346D2F5B3B45374F9CEF21F075E996D6`）。
- 语义契约：来源表保持不变作为来源证据，消费以主档列为唯一权威；回款/发货归属同步以来源行非空值更新主档列（最新同步覆盖，两来源共用同义列）；ERP Owner字段（客户、合同类型、合同名称、合同金额、币种编码、生效/失效日期、状态）仍由ERP主档同步负责，来源同步不覆盖。同步实现代码随后续接入任务交付，本检查点不宣称同步已实现。

需求方已确认COM-A与COM-B承载不同需求，按Requirement整体合并。master已形成可构建增量：统一规格以项目办事处发生时快照作为COM唯一地点事实，COM-B的AST站点/位置迁入IMP/AST；PLT迁移证据Owner已随CUT旧数据核对依赖由`master代码回执c9066332`独立落位，COM仍只消费公开API。历史分支Gate与Done只作来源证据，不能转记master完成状态。

## 实施边界

- 以COM-A合同/订单副本、公司范围、DeliveryScope、ACC绑定、REST/UI闭环为代码基础。
- 吸收COM-B的批次event/前驱CAS、人工候选/对账、订单合同来源身份、项目范围水位和`getAssignedScope`。
- `CommerceAuthorityIngestApi`成为新批次主入口；`CommerceAuthorityWriteApi`仅在替代路径可用后标记废弃并保留兼容适配，新能力不得继续依赖旧接口。
- COM范围和DTO不得持有`siteId/siteLocationId/locationText/locationResolutionStatus`；设备序列号仍只通过AST公开校验契约验证。
- 不接收ACC-001/002、CUT、IMP业务实现，不实现ERP网络连接器或历史生产迁移。
- 来源分支V124～V127重新编号为master V160～V163；不得修改已执行V70/V72或与master/其他分支冲突的版本。

## Task 1：权威规格与工程链

- [x] PRD修订010、COM领域规格和唯一Feature Spec完成。
- [x] COM-A/COM-B旧实现复用、迁移和废弃边界完成。
- [x] master排他DU和实施计划完成。
- [x] PRD/SDS/Feature/追溯机器校验全部通过并提交上游基线。

## Task 2：COM-A纵向闭环适配

- [x] 选择性迁入COM/PROJ/ACC窄契约、Provider、业务实现、REST、UI和测试。
- [x] 将来源V124～V127重编号为V160～V163并更新全部引用。
- [x] 在当前master依赖上完成聚焦测试和模块构建，形成可构建增量提交。

## Task 3：COM-B非重复需求能力

- [x] 实现`CommerceAuthorityIngestApi`批次CAS并迁移受控导入调用。
- [x] 实现人工候选、不可变依据与ERP Owner对账。
- [x] 为订单—合同关系补齐稳定来源身份。
- [x] 实现项目范围版本水位和`DeliveryScopeApi.getAssignedScope`，统一全部写路径的递增与锁序。
- [x] 对已替代旧接口/旧模型加废弃标记，确认没有新调用继续建立在旧功能上。

## Task 4：拆分与整体验证

- [x] 确认PLT迁移证据、IMP/AST实施地点不进入COM代码、表、DTO或完成状态。
- [x] 完成迁移静态校验、权限负向、并发/幂等、前端测试及生产构建。
- [ ] 在具备隔离凭据的环境完成MySQL迁移和应用集成测试。
- [ ] 完成真实浏览器正向闭环及关键负向验收。
- [x] 更新Requirement矩阵与DU阶段回执。
- [ ] 完成Implementation Done独立裁决。

> 检查点：基线=PRD修订010/014；当前Gate=Implementation Done复核；已通过=master既有增量及下述局部实库/浏览器链；剩余=其余AC运行复核与独立裁决。

## 2026-09-07 合同关联与范围分配运行修复

- 沿用已在master认领的`DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR`，Requirement为COM-01；保留replay可复用来源，不恢复旧表、旧状态或重复迁移。
- 实际断点：已有4台范围调整为6台时，预览按新增分配判断自身冲突，按钮始终禁用。调整预览现携带当前范围ID/版本，排除自身占用，并保持项目/订单归属、版本和ACC减量守卫。
- 合同权限拒绝原本以未处理异常返回业务码500，现使用403；真实无权关联请求仍被拒绝，未放宽权限。
- 隔离环境`npdms-pr7-6644`，租户1；宿主后端58286、前端18086，原58086处于Windows保留端口段。测试数据前缀`FCOM-UI-1788777648045`，项目`992203060003`。
- 真实浏览器完成ERP合同/订单展示、项目关联、分配4台、调整6台、释放和刷新历史；分配版本1/2、项目范围水位1/2/3，两条RELEASED历史均保留，页面异常为0。
- 后端25项、前端7项聚焦测试及TypeScript检查通过；后端制品构建通过。未修改SQL或数据隔离机制，本轮不宣称全部Feature Done。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。

## 2026-09-18 行业划分维度列名统一落地（Q-MIG-DIM-001）

- 需求方2026-09-18裁决：行业划分是市场/系统/拓展/行业四维统称（非部门），列名按主档`cus_market_relation`；办事处为部门维度取`department_`；创建时行业划分原值合并`original_division_values` JSON。裁决与落实记录见`docs/decisions/open-questions.md` Q-MIG-DIM-001（已关闭）。
- 规格权威DDL `project-order-physical-schema.mysql.sql` 已修订`com_contract_receivable`、`com_shipment_contract_reference`、`com_crm_execution_order`三表列名并删除行业划分维度三个`*_department_id`列；修订后SHA-256 `AF8C3BA5D44BDE9809E9F10AC70A60E0CA4DA43E9BFE7E6B1714759294BBB3D0`（修订前基线`6B203BF3...`，与AI-MIG证据基线一致），隔离MySQL 8.4容器完整执行66张表建成。
- 列名更正迁移（现并入合并迁移`sql/migrations/V294__migration_field_naming_and_comment_alignment.sql`第1段）对V258所建`com_contract_receivable`、`com_shipment_contract_reference`（核查时均0行）完成同口径更正，2026-09-18经Flyway实际应用成功（库版本v294），实库列状态及注释已复核；列注释另经原`V295`迁移精简为界面显示字段口径（已并入合并迁移V294第3段）。`com_crm_execution_order`未建表，建表时直接采用修订后规格DDL。
- 本回执仅覆盖列名口径落地，不改变Task 4其余验收项状态；隔离凭据环境集成测试、浏览器闭环与Implementation Done独立裁决仍按原剩余项执行。

## 2026-09-18 办事处维度列名统一落地（Q-MIG-DIM-002）

- 承Q-MIG-DIM-001裁决，履约范围侧`com_delivery_scope`四列`office_department_*→department_*`（原V296迁移，现并入合并迁移V294第2段，Flyway应用成功）；`DeliveryScopeDO`/`DeliveryScopeRespVO`/`DeliveryScopePreviewResult`/scope三服务/`DeliveryScopeController`及`SplitScope*Command`契约字段同步改名为`department*`，项目侧`ProjectOfficeFact` API契约四组件同步改名。
- 聚焦验证：commerce范围命令服务16项+公共API契约2项通过；project `ProjectOfficeFactApiImplTest` 6项+契约2项通过。`CommerceAuthorityIngestMySqlTest`因隔离库凭据不可用未执行（同Task 4既有未决项）。
- 本回执仅覆盖命名统一，不改变Task 4其余验收项状态。
