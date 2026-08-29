# F-COM-001 合同订单关联与交付范围分配实施计划

> **面向实施代理：** 必须使用`executing-plans`执行本计划。F-COM-001按一个完整正向闭环实施；所有后端、前端、迁移、种子和Owner Provider接通后，再集中执行整体测试、真实浏览器验收与Implementation Done独立评审。

**计划ID：** `NPDMS-FCOM001-TECHPLAN-20260829-01`

**当前状态：** `APPROVED / PASS / GO`

**目标：** 实现ERP合同/销售订单/订单行受控只读副本、合同管理员SYSTEM当前公司范围、项目—合同关系、项目交付范围预览/分配/调整/释放、办事处发生时快照、ERP冲突冻结通知，以及项目进入验收阶段和验收阶段内新范围版本的ACC精确绑定完整闭环。

**架构：** COM拥有合同、订单、订单行、项目—合同关系、DeliveryScope及事务Outbox；PROJ拥有项目、项目阶段快照、办事处和当前项目经理事实；ACC拥有独立AcceptanceScopeBinding；AST验证序列号；SYSTEM提供当前公司授权。当前模块化单体使用同一MySQL事务资源，PROJ/COM发起方调用的跨Context写Provider均以`MANDATORY`加入，锁序固定为`PROJ项目当前行 → COM订单行 → COM当前DeliveryScope → ACC绑定`。

**技术栈：** JDK 25、Spring Boot、MyBatis/MySQL 8、Flyway、PlatformCommandExecutionApi/OperationAuditApi、Vue 3、Element Plus、pnpm 9.15.5、Vitest、Chromium。

**规格与基线：**

- `docs/baseline/prd-v1.8.md`：`COM-01@V1`，协作`PM-03/PM-10/ACC-03`；
- `docs/design/05-state-machine.md`、`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`11-event-design.md`、`12-integration-design.md`、`16-exception-and-idempotency.md`；
- `specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md`；
- `specs/features/F-COM-001-physical-contract.json`；
- `specs/features/F-COM-001-legacy-reuse-audit.md`（REUSE-01～21）；
- Feature Ready裁决提交`c57ee7b5f5226f5dc902d817c034ff1a8f6618c3`，状态回写`ead6c8bf3eca721a221564ac13c6f656aeb44f9e`；V72受管种子补充GO提交`3412e38397776d471c6ea3867def2001609d5b46`，状态回写`8e6db2d3`。

## 一、全局实施约束

- 本计划通过前不修改产品代码、Flyway、菜单或Task；通过后也不宣称Deployment、SIT、UAT或Release完成。
- 只实现`COM-01@V1`完整Feature，不夹带COM-02、第三方ERP/CRM连接器、历史生产迁移或Q-FCOM-002退出/回退关闭规则。
- Yudao CRM合同、BPM、SYSTEM Provider和基础平台源码保持不变；只消费现有`OrganizationScopeApi.getActiveScopes`并通过正式菜单/权限配置接入新PMS页面。
- 最小权限键和服务端控制点必须准确；不固化角色—权限映射。实施与验收身份通过正式授权配置取得全部相关权限键，不得删除鉴权或租户隔离。
- 复杂查询、动态公司集合、锁和窗口函数全部进入Mapper XML并使用场景Query对象；禁止SQL注解、`${}`、`.last(...)`、`Map`和长位置参数。
- 现有`DeliveryScopeApi`公开签名与F-PROJ-002行为保持兼容；增强逻辑进入新类，既有旧类保留直到目标Provider切换完成，不原位改造Yudao或CRM资产。
- 所有普通V70业务行执行批准的逐字段Owner解析、冻结水位、历史保留和任一缺失/冲突整批失败。V72夹具仅在全部身份谓词与关系闭包精确命中后隔离重建；不得修改V70/V72、使用`item_code`推断或把种子常量用于普通业务行。
- Q-FCOM-002关闭前，任何代码、迁移或测试都不得为退出/回退验收阶段写`effective_to`、解锁或改写既有绑定。

## 二、现有实现吸收与真实缺口

| 现有资产 | 决策 | 本计划处理 |
|---|---|---|
| `DeliveryScopeApi`及DTO | `DIRECT_REUSE` | 保持三方法及错误/原子语义，Provider切换到新COM应用服务 |
| `DeliveryScopeService`数量、版本、锁、幂等和Outbox算法 | `COPY_THEN_ENHANCE` | 新建完整COM命令/查询服务，补Owner、历史、ACC、AST和通知守卫 |
| `OrderLineDO`、`DeliveryScopeDO/DetailDO`及Mapper | `COPY_THEN_ENHANCE` | 新建目标模型DO/Mapper/XML；旧类不再作为新写路径 |
| V70三表 | `COPY_THEN_ENHANCE` | V124前置预检后一次性转换，成功后不保留第二业务真值 |
| V72/V74 F-PROJ-002验收夹具 | `COPY_THEN_ENHANCE` | 按机器契约精确认定并以目标种子重建；部分命中整批失败 |
| `ProjectOrganizationFactApi`、ProjectMaster锁、ProjectStageSnapshot | `COPY_THEN_ENHANCE / DIRECT_REUSE` | 新建窄办事处/验收阶段Provider，复用可信租户、项目锁和只追加快照 |
| 现有`AcceptanceController/Service/DO/Mapper`与`pms_acc_acceptance` | `DO_NOT_REUSE` | 报告栈保持不变；独立新建ACC范围绑定事实、表、Mapper和Provider |
| `ProjectParticipantFactApi.inspect` | `DIRECT_REUSE` | 解析当前项目经理通知收件人，不读取PROJ表 |
| `AssetDeviceScopeApi.validateAssignableSerials` | `DIRECT_REUSE` | 预览显示校验结果；每个含SN写命令写前重新调用，失败零COM写入 |
| `OrganizationScopeApi.getActiveScopes` | `DIRECT_REUSE` | 每次查询/写前读取；非空companyCode精确去重；不缓存正向授权 |
| Yudao CRM合同与页面 | `DO_NOT_REUSE` | 零修改；新增PMS Commerce API、菜单和页面 |

## 三、公开接口与事务边界

### 3.1 新增或增强的跨Context API

在`pms-module-project-api`新增：

```java
public interface ProjectOfficeFactApi {
    ProjectOfficeFact resolve(ProjectOfficeFactQuery query);
    ProjectOfficeFact lockAndRevalidate(ProjectOfficeFactQuery query);
}

public interface ProjectAcceptanceStageFactApi {
    ProjectAcceptanceStageFact lockAndRead(ProjectAcceptanceStageFactQuery query);
}

public interface AcceptanceScopeGuardApi {
    AcceptanceScopeGuardResult checkReduction(AcceptanceScopeGuardQuery query);
}

public interface AcceptanceScopeBindingApi {
    AcceptanceScopeBindingResult bindForStageEntry(AcceptanceStageEntryBindingCommand command);
    AcceptanceScopeBindingResult bindEffectiveScope(EffectiveScopeBindingCommand command);
}
```

`ProjectOfficeFactApi`的`FOUND`结果从同一次已通过期望版本校验的ProjectMaster读取非空`projectCode`，并与办事处稳定ID/编码/名称/版本共同返回；COM只使用该Owner事实写`DeliveryScope.projectCode`，项目编码空白、身份或版本不一致时零写入，不接受客户端值且不访问PROJ表。

- `ProjectOfficeFactQuery`固定`tenantId/projectId/expectedProjectVersion`，结果枚举仅为`FOUND/NOT_FOUND/INACTIVE/VERSION_CONFLICT`；FOUND返回同一次读取或锁定的项目版本、非空项目编码和SYSTEM部门`id/code/name/version`。预览使用`resolve`，写命令使用`MANDATORY`的`lockAndRevalidate`，两者不得切换Owner来源或结果语义。
- `ProjectAcceptanceStageFactQuery`固定`tenantId/projectId/expectedProjectVersion/operationId`；PROJ锁项目当前行，从项目冻结阶段实例识别其验收阶段，只有当前阶段与该阶段一致时返回当前只追加`projectStageSnapshotId`。
- `AcceptanceScopeGuardResult`固定`UNLOCKED/LOCKED/UNKNOWN`、`acceptanceFactVersion`和最小锁定引用；UNKNOWN与Provider不可用对减量失败关闭。
- `AcceptanceScopeBindingApi`写方法标注`@Transactional(propagation = MANDATORY)`；同身份同请求返回原结果，同身份异请求拒绝。

在`pms-module-commerce-api`新增：

```java
public interface CommerceAuthorityWriteApi {
    AuthorityWriteResult apply(CommerceAuthorityWriteCommand command);
}

public interface DeliveryScopeAcceptanceLockApi {
    List<DeliveryScopeVersionFact> lockCurrentByProject(
            DeliveryScopeAcceptanceLockCommand command);
}
```

- `CommerceAuthorityWriteCommand`承载受控本地来源批次、来源记录键/版本、合同/订单/行字段（含ERP订单行`productCode`）和operationId；`productCode`按原值保存并参与同版本异载荷冲突判断，不由`itemCode/productId`补齐；命令不包含ERP认证、HTTP、调度或游标。
- `DeliveryScopeAcceptanceLockApi`按`deliveryScopeId ASC`锁定项目全部当前范围并返回精确`deliveryScopeId/allocationVersion`；空集合成功返回空列表；写/锁方法必须加入已有外层事务。
- 既有`DeliveryScopeApi.applySplit`方法与`Allocation` DTO/语义不变；`SplitScopeApplyCommand`仅加性增加`expectedParentProjectVersion`和`projectVersionsByClientItemKey`。PROJ从同一事务已锁父项目及刚创建子项目的`ProjectMasterDO.version`原值形成不可变版本映射；COM在任何写入前校验三个clientItemKey集合完全一致，并按projectId升序以精确版本调用`ProjectOfficeFactApi.lockAndRevalidate`。部分拆分REMAINDER使用父项目同版本事实。`DeliveryScopeApiImpl`改委托新的兼容适配服务；PROJ不依赖COM实现或表。
- 旧系统`pm_order_data_from_erp`、`pm_order_line_from_erp`和`pm_project_product_line`只作为订单头、订单行及原始子单的历史参照；当前实现不读取旧库、不实现连接器，也不以旧字段名补运行时Owner事实。其正式迁移另受`AI-MIG-000`约束。

### 3.2 事务与锁序

1. COM范围写：读取并重验权限/项目范围；锁PROJ项目当前行并取得办事处/验收阶段事实；按订单行ID升序锁COM订单行；按范围ID升序锁当前范围；必要时调用ACC绑定；新版本、旧区间关闭、明细、审计、Outbox和ACC绑定共同提交或回滚。
2. PROJ进入验收阶段：锁ProjectMaster；由PROJ读取项目冻结`proj_project_stage`，要求当前阶段实例已经由上游Owner标记`DONE`、目标为下一顺序且仍`PENDING`的V1验收阶段`S5`，不得在本Feature重算CUT或其他S4门禁；追加ProjectStageSnapshot；调用ACC `bindForStageEntry`，ACC再调用COM锁全部当前范围并按稳定顺序追加绑定；最后CAS更新`current_stage`和两个阶段实例状态，并写`ProjectStageChanged`提交后事件。报告不存在不阻断进入，COM不得自行从S5字符串推断验收事实。
3. 合同关系写：先按现有项目范围与功能权限校验，再调用SYSTEM读取当前scope；锁合同和关系唯一键；写入前最后一次按合同当前ERP companyCode重验；关系、幂等成功和安全审计共同提交。
4. ERP冲突：新ERP版本导致当前范围超量时，同事务把范围置`CONFLICT_FROZEN`并追加`NotificationRequested(DELIVERY_SCOPE_CONFLICT_FROZEN)`；收件人解析/投递失败不回滚冻结，Outbox按批准幂等键重试。

## 四、文件与实现责任

### 4.1 COM后端

- `pms-module-commerce/pom.xml`：只增加现有`pms-module-project-api`、`pms-module-asset-api`、`pms-module-platform-api`、`yudao-module-system`及Web/Security依赖；不依赖其他模块`-biz`。
- `pms-module-commerce-api/.../api/authority/`：`CommerceAuthorityWriteApi`及合同/订单/行来源DTO。
- `pms-module-commerce-api/.../api/scope/`：新增`DeliveryScopeAcceptanceLockApi`和DTO；保持现有`DeliveryScopeApi`。
- `pms-module-commerce/.../dal/dataobject/{contract,order,scope,outbox}/`：目标Contract、SalesOrder、SalesOrderLine、ProjectContractRelation、DeliveryScope、DeliveryScopeDetail和Outbox DO。
- `pms-module-commerce/.../dal/mysql/...`及`src/main/resources/mapper/commerce/*.xml`：场景Query、列表/详情、公司集合、稳定锁、当前唯一和历史查询。
- `pms-module-commerce/.../service/authority/CommerceAuthorityWriteService.java`：来源版本、同版本冲突、单位精度、ERP减量/取消和冲突冻结。
- `.../service/contract/ContractAccessService.java`、`ContractRelationCommandService.java`：SYSTEM公司范围、敏感字段和写前重验。
- `.../service/scope/CommerceDeliveryScopeCommandService.java`、`CommerceDeliveryScopeQueryService.java`、`DeliveryScopeCompatibilityService.java`：完整范围闭环与F-PROJ-002兼容适配。
- `.../controller/admin/{contract,order,scope}/`：Feature Spec锁定的GET/POST资源、场景VO、`If-Match`和`Idempotency-Key`。

### 4.2 PROJ/ACC真实Provider

- `pms-module-project-api/.../api/commerce/`：`ProjectOfficeFactApi`、`ProjectAcceptanceStageFactApi`及DTO。
- `pms-module-project-api/.../api/acceptancescope/`：`AcceptanceScopeGuardApi`、`AcceptanceScopeBindingApi`及DTO。
- `pms-module-project/.../api/commerce/`：复用ProjectMaster锁、ProjectOrganization事实和SYSTEM部门公开API，交付两个PROJ Provider。
- `pms-module-project/.../dal/dataobject/acceptancescope/AcceptanceScopeBindingDO.java`、Mapper/XML、Repository：只拥有ACC绑定表；不引用`AcceptanceDO`或`acceptance_id`。
- `pms-module-project/.../service/acceptancescope/AcceptanceScopeBindingService.java`：两个`MANDATORY`绑定入口、幂等身份、守卫查询和Q-FCOM-002禁止写路径。
- `pms-module-project/.../service/projectstage/ProjectAcceptanceStageEntryService.java`及`POST /api/v1/pms/projects/{id}/actions/enter-acceptance-stage`窄Controller动作：使用既有`pms:project:update`、项目范围、`If-Match`和`Idempotency-Key`，复用ProjectMaster锁、冻结阶段实例、阶段快照和当前阶段实例`DONE`事实；不重算或伪造CUT/S4门禁，只实现进入项目冻结V1验收阶段S5，不修改回退/关闭/重开服务。

### 4.3 数据库与种子

- `V124__fcom001_contract_order_scope_forward_migration.sql`使用“停写冻结→影子装载→切换前对账→单条多表原子换名”算法。整个V124窗口必须停止宿主机前后端写流量并保持Flyway独占；开始时记录V70三表的`count/min(id)/max(id)/max(version)/max(update_time)`输入水位，换名前再次比较，任一变化`SIGNAL`。不得仅依赖Flyway锁代替应用停写。
- V124重试首先检查正式/归档/影子名称组合：若V123三张正式表仍存在且归档表不存在，则按“子表→父表”顺序删除上次失败遗留的影子表后重新开始；若全部目标正式表和三张归档表已存在、所有影子表均不存在，则执行只读结构/对账复核并把本次视为原子换名已完成的幂等重放；任何混合名称组合立即失败并要求从迁移前数据库快照恢复，不猜测补表或继续换名。
- 所有目标表先使用以下固定影子名称创建，字段、约束、索引和外键一次建全：`fcom001_shadow_com_contract`、`fcom001_shadow_com_sales_order`、`fcom001_shadow_com_sales_order_line`、`fcom001_shadow_com_order_contract_relation`、`fcom001_shadow_com_project_contract_relation`、`fcom001_shadow_com_delivery_scope`、`fcom001_shadow_com_delivery_scope_detail`、`fcom001_shadow_acc_acceptance_scope_binding`。影子外键只引用对应影子表或既有Owner稳定表，不引用待退出的V70正式表。
- V124在影子表内完成普通V70转换和精确V72夹具重建。预检/装载逐项验证冻结水位、普通V70父订单/单位/产品或设备类型/项目办事处Owner映射、十项必填目标映射、detail_sequence稳定生成、唯一键/数值/区间冲突，以及V72全部身份和关系闭包；普通明细不得用item_code补产品。精确V72夹具按机器契约创建稳定SEED父订单、V74公司/办事处和四条种子专用明细。
- 原子换名前必须在影子表完成并保存一次只读对账结果：普通输入与精确夹具分类行数、全部保留ID集合、订单→订单行→范围→明细父子闭包、每订单行和每范围数量合计、有效区间、来源/分配版本、当前唯一和全部目标唯一键。对账只比较业务列和确定性集合，不新增哈希；任一不一致`SIGNAL`，V123正式表保持原样。
- 唯一切换语句必须是一条多表`RENAME TABLE`：把`com_order_line/com_delivery_scope/com_delivery_scope_detail`分别改名为`fcom001_v70_com_order_line/fcom001_v70_com_delivery_scope/fcom001_v70_com_delivery_scope_detail`，同时把八张`fcom001_shadow_*`改为各自目标正式名。MySQL多表RENAME要么全部生效要么全部不生效；该语句是V124最后一个可改变业务表的步骤，换名后不得再执行建索引、补数据、删旧表或其他可能使业务真值部分完成的DDL/DML。
- 换名前任一步失败时，旧V123正式表名称和内容保持可用；保持应用停写，执行Flyway repair后由V124开头按固定顺序清理影子并重试。换名成功后，三张`fcom001_v70_*`只作只读迁移证据，产品Mapper、Provider、菜单和后续种子均不得引用或写入，不构成第二业务真值；不得启动旧V123应用写该归档名。归档表删除另需后续明确批准，不放入V124/V125。
- V124转换保持ID、审计、来源版本、数量、区间和事件；V125只有在Flyway确认V124成功后才可执行。若V124原子换名已生效而Flyway元数据写入失败，应用仍保持停止；清除失败元数据后重跑V124，必须命中“全部正式+全部归档+零影子”的幂等复核分支，不重复装载或换名。
- `V125__fcom001_permissions_menu_and_acceptance_seed.sql`：第一步以加性`ALTER TABLE`增加`com_sales_order_line.product_code varchar(64) NULL`，随后写入八个最小权限键、PMS Commerce菜单和受控验收数据。精确V72夹具的四个固定SEED订单行只按机器契约列举值写测试专用`productCode`，不得宣称ERP事实、由V72 `item_code`推断或覆盖普通业务行。验收身份通过正式用户—角色—权限配置获得全部八键；不固化业务角色映射，不修改SYSTEM Provider源码。
- V125数据覆盖：合同公司精确命中/空范围、敏感字段有无权限、订单行CONFIRMED/PENDING、精确/部分/无匹配、RELEASED不参与、超量、AST SN有效/无效、验收阶段内外和ACC锁定/未锁定；使用高段ID与`creator=seed`。

### 4.4 前端

- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/index.ts`：精确REST类型、数值版本、错误和允许动作。
- `src/views/pms/commerce/contracts/index.vue`与`detail.vue`：公司范围后的合同/订单/订单行只读查询、关系维护和敏感字段投影。
- `src/views/pms/commerce/delivery-scope/index.vue`、`DeliveryScopeEditor.vue`、`DeliveryScopeHistoryDrawer.vue`：预览、分配、调整、释放、占用明细、办事处发生时快照、AST SN校验、版本冲突刷新和历史。
- UI只使用服务端返回事实和动作；不得推断公司范围、项目办事处、AST设备或ACC锁定。320/768/1024/1440无页面级横向溢出。

## 五、唯一实施任务

### Task 1：一次完成F-COM-001正向业务闭环

**Files：** 第四节列出的COM、PROJ/ACC、V124/V125、前端和测试文件；不修改PRD/SDS、V70/V72、Yudao CRM或SYSTEM Provider。

- [ ] **Step 1：以最终接口签名补齐聚焦失败测试并确认RED**

  后端测试固定覆盖Authority乱序、公司范围、关系写前重验、范围数量/单位/历史、AST写前重验、办事处快照、ACC绑定、阶段进入、冲突通知、兼容API和迁移契约；前端测试覆盖查询、关系、范围编辑、动作投影、响应未知和四档布局。RED必须因目标类型或行为缺失；环境/装配错误先修复，不作RED证据。

- [ ] **Step 2：实现公开Owner契约和PROJ/ACC真实Provider**

  先交付API DTO及Provider单测，再实现ProjectOffice、ProjectAcceptanceStage、DeliveryScopeAcceptanceLock、AcceptanceScopeGuard和AcceptanceScopeBinding。验证所有调用只经公开API、`MANDATORY`无外层事务拒绝、统一锁序、Q-FCOM-002无写路径、报告栈零调用。

- [ ] **Step 3：实现V124目标模型和确定性前向转换**

  先执行SQL静态契约与真实MySQL失败预检，再按第4.3节固定名称创建八张影子表、装载普通V70与精确V72夹具、完成切换前全量对账，并以唯一一条多表RENAME切换。测试须在影子已装载但换名前注入父子关系或唯一键不一致，使对账`SIGNAL`；断言三张V123正式表名称、行数、查询和既有DeliveryScopeApi仍可用。随后执行Flyway repair，验证影子按固定顺序清理并可重试成功；另覆盖原子换名已成功但Flyway元数据失败后的幂等复核分支。最后验证空库V1→V125、当前V123→V125、部分身份整批失败、普通行缺Owner整批失败，以及转换前后数量/范围/事件对账。

- [ ] **Step 4：实现COM权威副本与合同管理员公司范围**

  CommerceAuthorityWrite按来源键/版本幂等写只读副本；查询每次调用SYSTEM当前scope并把非空companyCode集合作为Mapper XML必选条件；详情不泄露存在性；关系写前重验并记录scope id/version；敏感字段另校验`pms:commerce:contract:sensitive-read`。

- [ ] **Step 5：实现DeliveryScope命令、历史、AST和冲突通知**

  复制增强旧算法到新服务，保持既有方法和`Allocation`语义、主明细合计、单位精度、当前唯一、关闭旧区间追加新版本、幂等/CAS和占用明细；Apply Command仅加性传入父/子项目版本。写前校验父版本、子版本和三个clientItemKey集合，并按projectId升序重验父子ProjectOfficeFact，REMAINDER使用父项目同版本事实。无SN及REMAINDER从同一租户、已锁定、来源版本有效且已确认的订单行读取非空ERP `productCode`并生成一条等量产品主体明细；缺失、空白、待确认或版本冲突时在范围、历史和Outbox零写入，禁止`itemCode/productId`、客户端、历史明细或种子常量回退。含SN的预览可显示结果，但每个写命令必须重新调用AST。ERP冲突冻结与NotificationRequested同事务，投递失败不改变冻结。

- [ ] **Step 6：接通项目阶段进入和验收阶段内新版本绑定**

  PROJ阶段进入先锁项目并追加快照，再由ACC锁定全部当前范围并绑定，最后更新current_stage；COM新范围生效在相同事务内调用PROJ阶段事实和ACC绑定。任一失败时阶段/快照/范围/历史/Outbox/绑定均不留部分成功；进入时不要求验收报告。

- [ ] **Step 7：完成REST、前端和V125正式配置**

  接通Feature Spec全部资源、最小权限键和服务端控制点；合同、订单、范围页面完成正向闭环。实施/验收身份通过正式配置取得八键，角色映射保持可配。低收益异常分支不得取代正向闭环或制造额外Gate。

- [ ] **Step 8：复跑聚焦集合至GREEN并完成必要重构**

  只清理本Feature产生的重复和无用代码；确认旧DeliveryScopeApi回归、CRM/Yudao零修改、报告栈零耦合、普通V70规则未放宽。全部聚焦通过后才进入Task 2。

### Task 2：集中执行整体验证、真实验收和Implementation Done送审

- [ ] **Step 1：真实MySQL与后端事务矩阵**

```powershell
.\scripts\test-infrastructure.ps1 reset
.\scripts\test-infrastructure.ps1 status
$fcomTestEnv = Get-Content -Raw .env | ConvertFrom-StringData
$env:NPDMS_DB_NAME = 'npdms_test'
$env:NPDMS_MYSQL_PORT = '23316'
$env:NPDMS_REDIS_PORT = '26379'
$env:NPDMS_DB_USER = $fcomTestEnv.NPDMS_DB_USER
$env:NPDMS_DB_PASSWORD = $fcomTestEnv.NPDMS_DB_PASSWORD
mvn.cmd -pl pms-module-commerce,pms-module-project -am `
  "-Dtest=CommerceAuthorityWriteServiceTest,ContractAccessServiceTest,ContractRelationCommandServiceTest,CommerceDeliveryScopeCommandServiceTest,CommerceDeliveryScopeQueryServiceTest,DeliveryScopeCompatibilityServiceTest,ProjectOfficeFactApiImplTest,ProjectAcceptanceStageFactApiImplTest,AcceptanceScopeBindingServiceTest,ProjectAcceptanceStageEntryServiceTest,Fcom001MigrationContractTest,Fcom001ApplicationMySqlIntegrationTest" `
  "-DskipITs=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

  必须确认目标MySQL IT实际执行且非SKIPPED；直接断言锁序、并发单胜、幂等同键重放/异载荷冲突、跨租户/无权零写、绑定全有或全无、报告不存在可进入、减量锁定失败关闭和冲突通知不回滚冻结。

- [ ] **Step 2：前端、类型与构建**

```powershell
$fcomUiRoot = 'yudao-ui/yudao-ui-admin-vue3'
Push-Location $fcomUiRoot
corepack pnpm vitest run --config vitest.pms-file.config.ts src/views/pms/commerce
corepack pnpm ts:check
corepack pnpm build:local
Pop-Location
```

- [ ] **Step 3：全仓、Flyway与规格检查**

```powershell
mvn.cmd -pl pms-module-commerce,pms-module-project -am "-DskipITs=true" test
mvn.cmd -DskipTests package
py -3.13 -B scripts/validate_sds_phase2.py --root .
py -3.13 -B scripts/validate_sds_phase3.py --root .
py -3.13 -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check
git diff --check
```

  使用仓库权威Compose对空库和V123基线分别执行Flyway migrate/info/validate；确认V124/V125约束、索引、种子和转换对账，并证明V70/V72字节未改。至少保留一轮“影子装载后、原子换名前失败”的真实MySQL证据：旧正式表可查询、无目标正式表部分出现、影子清理后同一基线重试成功；V125在失败轮不得出现任何记录。

- [ ] **Step 4：真实Chromium公开UI/REST闭环**

  新建`scripts/tests/run_fcom001_browser_acceptance.cjs`，使用正式授权配置的全权限验收身份完成合同查询→关系维护→订单行→范围预览→分配→调整/释放→历史→ERP减量冲突冻结→项目进入验收阶段→阶段内新版本绑定；另用空公司范围、无敏感字段权限、无项目范围、AST无效SN和ACC锁定身份验证服务端拒绝。四档视口意外console/page/request错误为零。

- [ ] **Step 5：证据、自审、提交与独立评审**

  在`docs/engineering/evidence/f-com-001-browser-evidence.json`和对应截图目录记录运行身份、HTTP状态、最终刷新事实、MySQL绑定/历史/Outbox引用和预期负向。更新`tasks/features/F-COM-001.md`唯一检查点（不超过300字），只暂存本Feature实施与证据，形成一个Implementation Done候选并按正式模板送独立评审。GO前不得回写Implementation Done。

## 六、计划自检

- **正向闭环：** 权威副本、公司范围、合同关系、范围写、ERP冲突、项目阶段进入和ACC绑定都有真实Provider与生产入口。
- **Owner：** COM不读PROJ/ACC/AST/SYSTEM表；PROJ/ACC不读COM表；验收报告不承接范围绑定；AST不提供办事处事实。
- **历史：** 范围调整关闭旧区间并追加版本；授权撤销不删关系历史；V70/V72不修改；Q-FCOM-002不写关闭/解锁。
- **事务：** 两条绑定路径均使用同一MySQL资源和`MANDATORY`；ProjectStageChanged只在成功提交后通知，不补建绑定。
- **权限：** 八个最小权限键与服务端控制点一致；角色映射可配；全权限身份仍经过鉴权、数据范围和租户隔离。
- **复用：** REUSE-01～21逐项落实；Yudao CRM/SYSTEM Provider和旧Acceptance报告栈零修改。
- **执行粒度：** 只有一个完整实施Task和一个集中验收Task；任何局部能力不单独宣称Feature完成。

## 七、Technical Plan Gate

当前状态：`APPROVED / PASS / GO`。独立裁决已批准候选`c33b0836f71e0875008a084ff360e7027d276ec9`，允许创建`tasks/features/F-COM-001.md`并按本计划进入Implementation。该GO不批准产品代码、V124/V125或迁移执行结果，不等于Implementation Done、SIT、UAT、Deployment、Migration或Release通过。
