# F-COM-001 旧实现复用审计

> Requirement：`COM-01@V1`
> 审计结论：`COMPLETE`
> 规则：旧实现逐项分类为`DIRECT_REUSE / COPY_THEN_ENHANCE / DO_NOT_REUSE`；未列项不得静默进入Technical Plan

## 1. 审计范围

- `pms-module-commerce`全部API、DTO、Service、DO、Mapper和测试；
- `sql/migrations/V70__commerce_delivery_scope_slice.sql`；
- `sql/migrations/V72__fproj002_v18_seed_and_menu.sql`及其V74 SYSTEM公司/办事处补充事实；
- F-PROJ-002对`DeliveryScopeApi`的消费与回归契约；
- `pms-module-project-api`的`ProjectOrganizationFactApi`、DTO及其现有调用契约；
- `pms-module-project`的`ProjectOrganizationFactApiImpl`、`ProjectMasterDO/Mapper`、`ProjectStageSnapshotDO/Mapper/Repository`、`ProjectGovernanceApplicationService`及对应测试；
- `AcceptanceController`、`AcceptanceService/Impl`、`AcceptanceDO/Mapper`、`sql/migrations/V17__pms_acceptance_tables.sql`，以及`ProjectClosureServiceImpl`、`ProjectClosureStateAdapter`和对应测试；
- `ProjectParticipantFactApi/Impl/Test`当前项目经理事实、`AssetDeviceScopeApi/Impl/Test`序列号可分配校验，以及通用`NotificationRequested`事件契约；
- `yudao-module-system`现有`OrganizationScopeApi.getActiveScopes`、`OrganizationScopeApiImpl`、`UserCompanyDepartmentScopeRespDTO/DO/Mapper`及`OrganizationScopeApiImplTest`；
- Yudao CRM合同API、列表、表单、详情、审批和权限组件；
- 旧系统`pm_order_data_from_erp`订单头、`pm_order_line_from_erp`订单行及`pm_project_product_line`项目订单/原始子单参照证据；
- 已批准COM物理DDL、ADR-0023当前范围粒度及ADR-0036/0037的Feature-forward差量。

仓库中不存在其他COM合同、销售订单、订单行管理后端或PMS Commerce页面；不存在ERP网络适配器。旧源库及迁移证据仅用于批准物理模型，不授权本Feature执行历史数据迁移。

## 2. 判定矩阵

| ID | 旧资产 | 判定 | 新目标/使用方式 | 依据与约束 |
|---|---|---|---|---|
| REUSE-01 | `DeliveryScopeApi`及DTO：可用切片、预览、应用 | `DIRECT_REUSE` | 保持公开方法与F-PROJ-002错误/原子语义；目标实现适配完整COM模型 | 已有稳定跨Context调用方；PROJ不得依赖COM实现或表 |
| REUSE-02 | `DeliveryScopeService`数量、版本、锁、幂等和Outbox算法 | `COPY_THEN_ENHANCE` | 复制到新的完整COM应用服务后补Owner、办事处发生时快照、验收绑定及冲突守卫 | 现服务只处理父项目拆分，直接修改会把完整COM倒置为单一消费者切片 |
| REUSE-03 | `OrderLineDO/Mapper`与`com_order_line` | `COPY_THEN_ENHANCE` | 新建`SalesOrderLine`模型并迁入批准的`com_sales_order_line`；旧表仅作一次性转换输入 | V70缺合同/订单完整业务身份及部分必填目标字段；来源版本、单位精度、状态和必填快照必须按ADR-0036确定性转换 |
| REUSE-04 | `DeliveryScopeDO/DetailDO`及Mapper | `COPY_THEN_ENHANCE` | 新类绑定批准字段、主明细粒度、办事处发生时快照和有效区间；复杂锁查询进入Mapper XML | V70明细办事处编码只作来源证据，目标在范围主记录保存PROJ部门快照并要求产品/设备类型/序列号主体；现Mapper含SQL注解和位置参数，不能扩散 |
| REUSE-05 | `com_delivery_scope*` V70结构 | `COPY_THEN_ENHANCE` | 前向迁移执行受控结构转换，保留历史与当前范围，最终单Owner | 已执行迁移不得修改；目标DDL同名表语义不同，禁止长期双写或并行真值 |
| REUSE-06 | `com_outbox_event`与Assigned/Released事件 | `DIRECT_REUSE` | 复用事务Outbox和事件名；载荷按SDS补齐但不携带商务正文 | 事件设计已冻结Producer、Consumer和幂等语义 |
| REUSE-07 | `DeliveryScopeServiceTest`八类拆分测试 | `DIRECT_REUSE` | 作为兼容回归继续执行，并新增完整COM测试 | 现测试只证明F-PROJ-002切片，不证明COM-01完整完成 |
| REUSE-08 | Yudao CRM合同API、CRUD表单、列表、详情、BPM审批和CRM权限 | `DO_NOT_REUSE` | 保持零修改；新建PMS Commerce API、页面、路由和权限 | CRM拥有销售上下文而非ERP商务事实；可编辑CRM合同及审批状态与COM-01只读Owner冲突；用户禁止未授权修改Yudao基础平台 |
| REUSE-09 | Yudao/Element Plus通用前端组件和主题变量 | `DIRECT_REUSE` | 通过现有公共import用于新PMS页面 | 只复用通用表现组件，不复制CRM业务状态、权限或API |
| REUSE-10 | 当前仓库ERP网络连接实现 | `DO_NOT_REUSE` | 无资产；定义`CommerceAuthorityIngestApi`批次入口和受控本地Provider边界，旧`CommerceAuthorityWriteApi`只保留废弃兼容适配 | 第三方平台功能只预留接口，不实现连接器；新能力不得建立在旧Write接口上 |
| REUSE-11 | `ProjectOrganizationFactApi`、DTO、`ProjectOrganizationFactApiImpl`及`ProjectOrganizationFactApiImplTest` | `COPY_THEN_ENHANCE` | 保持现有组织事实API不变；复制其可信租户、项目版本和项目行锁校验模式，新建窄`ProjectOfficeFactApi`真实Provider | 现接口只有项目组织部门ID/编码，缺SYSTEM办事处名称、部门版本和显式结果枚举；不得把项目缓存名称或实施地点当权威事实 |
| REUSE-12 | `ProjectMasterDO/Mapper`及`selectByIdForUpdate`项目当前行锁 | `DIRECT_REUSE` | `ProjectOfficeFactApi`与`ProjectAcceptanceStageFactApi`Provider复用项目当前行、租户、版本、当前阶段和统一首锁；组织引用仍须向SYSTEM部门事实精确校验 | 已有锁原语符合ADR-0037锁序；`implementationLocation/locationResolutionStatus`及项目缓存部门名称不得用于生成办事处快照 |
| REUSE-13 | `ProjectStageSnapshotDO/Mapper/Repository`、`ProjectStageSnapshotRulesTest` | `DIRECT_REUSE` | 复用只追加阶段快照、稳定`snapshotId`和统一追加入口，作为`ProjectAcceptanceStageFactApi`返回的不可变阶段身份 | Mapper不暴露更新/删除，Repository执行可信租户校验；只允许引用真实阶段进入快照，不得从报告状态反推 |
| REUSE-14 | `ProjectGovernanceApplicationService`及`ProjectGovernanceApplicationServiceTest` | `COPY_THEN_ENHANCE` | 保持现有回退/异常关闭/重开路径不变；在批准的阶段进入命令边界复制其项目锁、幂等、快照追加和提交后事件模式，并接入窄PROJ Provider | 当前服务只实现治理动作且Q-FCOM-002禁止本Feature决定回退关闭；不得直接修改现有治理动作来夹带范围解锁或补建绑定 |
| REUSE-15 | `AcceptanceController`、`AcceptanceService/Impl`、`AcceptanceDO/Mapper`与`pms_acc_acceptance`（`V17__pms_acceptance_tables.sql`） | `DO_NOT_REUSE` | 初验/终验报告CRUD、审批状态机、交付件门禁及现有表保持不变；不得承接`AcceptanceScopeBinding`身份、触发、表或Provider | 该栈的身份是报告`acceptance_id`，而ADR-0037已冻结范围绑定身份为项目阶段快照与精确范围版本；报告上传、提交、审批或状态不得触发或反推绑定 |
| REUSE-16 | `ProjectClosureServiceImpl`对终验报告的消费、`ProjectClosureStateAdapter`及`ProjectClosureStateAdapterTest` | `DO_NOT_REUSE` | 保持关项对终验完成结果和项目生命周期的既有消费；不得作为范围绑定创建、关闭、解锁或存在性来源 | 关项是报告完成后的下游门禁，不是项目进入验收阶段或新范围版本生效的Owner事实 |
| REUSE-17 | `ProjectParticipantFactApi.inspect`、DTO、真实Provider及`ProjectParticipantFactApiImplTest` | `DIRECT_REUSE` | COM以空subject、`PROJECT_MANAGER`和请求时间读取唯一当前项目经理及`projectVersion/factVersion`，填充冲突通知收件人 | 现接口已有PROJ Owner、可信租户和唯一参与人失败关闭；不得读取PROJ Mapper或把合同管理员当通知收件人 |
| REUSE-18 | `AssetDeviceScopeApi.validateAssignableSerials`、`SerialScopeValidationResult`、真实Provider及`AssetDeviceScopeApiImplTest` | `DIRECT_REUSE` | F-COM直达预览和每个含SN的写命令均传目标承接项目与完整SN集合；仅valid且三类失败列表全空时通过 | 现接口已校验存在性、租户、可分配状态、其他项目归属和重复；无版本令牌，故预览结果不得复用为写授权，写前必须实时重验，异常/不可用失败关闭 |
| REUSE-19 | COM `com_outbox_event`与SDS通用`NotificationRequested`契约 | `DIRECT_REUSE` | 冲突冻结事务内追加`DELIVERY_SCOPE_CONFLICT_FROZEN`请求；按范围、分配版本和ERP来源版本幂等，投递失败独立重试 | 通知请求/送达均不表示冲突处置完成；无唯一经理时保留逻辑角色收件人并经PROJ事实重试，不伪造用户、不回滚冻结 |
| REUSE-20 | SYSTEM `OrganizationScopeApi.getActiveScopes`、`OrganizationScopeApiImpl`、`UserCompanyDepartmentScopeRespDTO/DO/Mapper`及`OrganizationScopeApiImplTest` | `DIRECT_REUSE` | COM只调用现有公开API取得当前有效scope，以非空`companyCode`精确去重形成合同管理员公司范围，并在关系写入前重新读取；成功审计记录命中`id/version` | 现Provider已由SYSTEM按可信租户、当前时点、启用状态和有效期查询；不得修改Yudao实现、访问SYSTEM表、复制有效期算法、从部门推导公司或缓存正向授权 |
| REUSE-21 | V72高段ID、`creator=seed`、`source_system=SEED`及`FPROJ002-V18-`证据闭包 | `COPY_THEN_ENHANCE` | 不修改V72；仅在全部身份谓词和4订单行/2范围/4明细关系闭包精确命中后，从普通V70转换输入中隔离，并在同一Feature前向迁移按机器契约重建稳定目标种子 | V72是F-PROJ-002受管验收夹具而非ERP业务数据；部分命中或被改写时整批失败。种子专用订单、产品主体和场景常量不得用于普通业务行，不得以`item_code`推断或无边界跳过/删除 |
| REUSE-22 | 旧系统`pm_order_data_from_erp`、`pm_order_line_from_erp`、`pm_project_product_line`及结构化迁移证据 | `DO_NOT_REUSE`（当前运行时）；`COPY_THEN_ENHANCE`（未来正式迁移） | 订单头/订单行作为ERP历史来源，项目订单按项目、订单号和行号作为原始子单参照；本Feature仅保留映射与原始载荷证据，不建立运行时表访问、适配器或第二Owner | 现证据仍存在增加产品编码后的多义关系及跨项目分配量缺失；需在`AI-MIG-000`正式迁移Gate按稳定业务键、来源版本和问题单解析。旧字段名或`pm_project_product_line.itemCode`不得静默覆盖当前`CommerceAuthorityIngestApi`产品编码，也不得改变master V160/V161前向基线 |
| REUSE-23 | COM-B `CommerceAuthorityIngestApi`、批次DTO及版本前驱CAS | `COPY_THEN_ENHANCE` | 合入统一COM Owner应用服务；保留不透明版本、批次原子性和对象重放语义 | 不复制COM-B第二套表模型，不依赖旧`CommerceAuthorityWriteApi` |
| REUSE-24 | COM-B人工候选与对账实现 | `COPY_THEN_ENHANCE` | 纳入统一`com_authority_candidate`及REST；只能关联已存在ERP Owner事实 | 人工候选不得晋级、覆盖或伪造ERP事实 |
| REUSE-25 | COM-B项目范围水位与`getAssignedScope`契约 | `COPY_THEN_ENHANCE` | 纳入统一DeliveryScope，复用COM-A事务和办事处快照；所有范围写路径共同推进水位 | 禁止保留COM-B AST地点字段或第二套范围表 |
| REUSE-26 | COM-B `siteId/siteLocationId/locationText/locationResolutionStatus` | `DO_NOT_REUSE_IN_COM / MOVE_TO_IMP_AST` | 从COM物理契约、DTO和命令中移除；后续由独立IMP/AST Requirement承接 | 需求方已确认COM地点唯一为项目办事处发生时快照 |
| REUSE-27 | COM-B `PlatformMigrationEvidenceApi`及四张PLT表 | `DO_NOT_REUSE_IN_COM / MOVE_TO_PLT` | 保留为PLT候选，不随F-COM-001实现或完成状态进入 | 缺独立Requirement、Feature Task和COM真实消费方时不得宣称完成 |
| REUSE-28 | 已进入master但无Provider的`ProjectDeliveryScopeQualificationFactApi` | `DEPRECATE_UNLESS_USED` | 若统一COM接入真实PROJ Provider和调用方则保留；否则在替代事实接口可用后标记废弃 | 禁止继续扩展只有机器契约、没有生产Provider的空接口 |

## 3. 实施约束

1. Technical Plan必须把REUSE-01～28逐项绑定到Task、目标文件和验证，不得以“整体重写”绕过旧行为回归。
2. 增强服务、DO、Mapper和页面先复制到新类/新页面后再改造；旧公开API在切换前后保持兼容，旧Yudao CRM资产零修改。
3. V70转换必须使用合入时的下一个Flyway编号，验证空库、当前基线升级、重复迁移和转换前后数量/范围/事件对账；不修改V70/V72。精确V72夹具只按REUSE-21隔离重建，普通V70行继续执行严格Owner解析与整批失败规则。
4. 新增查询遵守场景Query对象、`LambdaQueryWrapperX`和Mapper XML规则；不得新增SQL注解、`${}`、`.last(...)`、`Map`或长位置参数。
5. Implementation Done候选必须证明：F-PROJ-002回归通过、Yudao CRM路径零修改、新PMS Commerce真实浏览器闭环、ERP适配器不存在、无COM双Owner或长期双写。
6. `AcceptanceScopeBinding`在现有仓库中没有可直接复用的物理事实；后续仅可按ADR-0037独立新建ACC事实、`acc_acceptance_scope_binding`表和`AcceptanceScopeGuardApi/AcceptanceScopeBindingApi`真实Provider。它不得复用`pms_acc_acceptance`主键、表、状态、Controller、Service或Mapper，也不得由`ProjectClosure`消费反向补建。

结论：COM-A作为统一闭环基础，COM-B只按REUSE-23～28吸收非重复能力；两个历史Feature模型均不再作为继续开发基础。旧接口和旧表只有在统一替代路径可用后才标记废弃，且任何新需求不得继续建立在旧路径上。本审计锁定实现输入，不转记历史Implementation Done。
