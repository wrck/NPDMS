# F-SOL-002 工勘分工信息采集与实施就绪 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`
> Requirement：`PRE-02（V1/P0）`
> Requirement切片覆盖：`PRE-02@V1=FULL`
> Owner Context：`SOL（交付准备与方案）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-003`、`F-PROJ-005`、`F-PROJ-007`、`F-PLT-001`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 边界裁决：`GO NPDMS-FSOL002-BOUNDARY-20260827-01`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

在SOL建立PRE-02实施准备业务真值：从项目模板冻结适用工勘项与V1固定表单版本，完成逐项指派、填写、证据固定版本、确认/退回/不适用确认、外包及OA来源引用、逐项豁免和就绪判定；每次判定生成不可变来源快照。任一适用项、来源状态、证据或豁免失效后，当前就绪事实必须恢复为`NOT_READY`。

## 2. Scope

### 2.1 包含

- PRE-02专用的版本化准备实例、工勘项和V1固定表单实例；发布/提交后冻结，不建设通用Schema设计器；
- 六类V1基准项：电源、网络端口、光纤、机柜、网线、光模块；扩展项只来自批准字典及项目模板配置；
- 项目模板冻结每项适用性、表单代码/版本、证据要求、来源要求、完成规则和豁免审批角色；
- 逐项负责人指派、表单填写、PLT FileArtifact精确引用、确认、退回及“不适用”项目经理确认；
- 外包事实与OA领料/外采稳定来源引用、当前归一结果、来源版本/水位和同步状态；不复制OA原始单据；
- 项目经理逐项申请豁免、模板冻结角色审批、有效期/风险/补偿措施及失效；
- 阻断清单、`READY/NOT_READY`当前事实和不可变PRE-02就绪快照；
- 受信、版本化、可锁定重验的SOL公共就绪契约，供后续IMP S4聚合门禁消费；
- 功能权限、ProjectStageScope、当前项目角色/负责人、租户、CAS、幂等和平台审计；
- 项目工作区内的响应式工勘准备页面；旧V1.7实现只作字段与交互复用证据。

### 2.2 不包含

- 通用动态表单Schema设计器、任意脚本、V2发布管理或完整SOL-01；
- OA领料/外采单据、审批和状态机；INT-05仍由OA Owner实施；
- 第二套任务树、`TASK_NATIVE`正文、PROJ表写入或项目任务生命周期命令；
- IMP的S4聚合门禁、`imp_implementation_readiness_snapshot`、项目阶段推进或S4命令；
- 文件正文、可变URL或INFRA存储事实；文件能力只消费F-PLT-001公开契约；
- 旧`pms_eng_site_survey`双写或自动迁移；AI-MIG-000、Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FSOL002-001 模板冻结与准备版本

- PROJ Owner在现有`pms-module-project-api`前向增加窄`ProjectWorkBindingFactApi`，公开读取的物理真值仍是既有`proj_project_task_execution_contract`，不新增项目级绑定表。项目模板任务定义在发布时必须唯一生产：`workBindingType=BUSINESS_OBJECT`、`targetContext=SOL`、`targetObjectType=SITE_SURVEY_PREPARATION`、`targetObjectKey=PRE_02_SITE_SURVEY`；其`bindingConfig`使用封闭V1 JSON Schema，包含`preparationTemplateCode/preparationTemplateRevision/fixedFormCatalogVersion/itemConfiguration[]`，项目创建时原样冻结到执行契约`binding_parameter_snapshot`。
- `inspect`按受信租户、projectId、上述目标四元组并限定执行契约`current_marker=1`精确联结ProjectTask与ExecutionContract；0条或多条均失败关闭。响应冻结`projectTaskId/projectTaskVersion/executionContractId/contractVersion/templateTaskDefinitionId/sourceDefinitionVersion`、目标四元组及解析后的PRE-02配置。
- `lockAndRevalidate`携带`projectId/projectTaskId/executionContractId/expectedProjectTaskVersion/expectedContractVersion/expectedProjectVersion`，按既有物理键`tenant_id+project_task_id+current_marker`锁定当前执行契约并核验ID、项目归属、目标四元组、合同版本和冻结配置，在调用方事务内持有Project/Task/Contract锁至提交。空、多记录、越租户、版本变化或配置不完整均失败关闭；SOL不读取PROJ表，也不前置`TASK_NATIVE`工作台。
- 项目创建已冻结的公开WorkBinding事实必须满足`targetContext=SOL`、`objectType=SITE_SURVEY_PREPARATION`并包含PRE-02固定表单版本和工勘项配置。空、多记录、越租户、版本变化或配置不完整均失败关闭；SOL按受信项目事实幂等初始化，不读取PROJ表，也不前置`TASK_NATIVE`工作台。
- V1表单结构只允许平台发布的PRE-02固定`formCode+formVersion`；SOL冻结该版本及字段定义快照。不存在、未发布、未知字段类型或任意脚本配置均失败关闭。
- 同一项目、`PRE_02`准备类型和业务版本唯一；同一时点只有一个current版本。退回后原提交版本保持冻结，显式创建下一`DRAFT`版本并复制可编辑事实，不覆盖历史。
- 每个版本至少包含模板要求的六类基准项中适用的项目；扩展项编码必须命中启用字典与冻结模板配置。模板后续变更不反向覆盖已冻结版本。

### BR-FSOL002-002 逐项责任、填写与确认

- 工勘项将适用性与确认生命周期分轴：`REQUIRED/NOT_APPLICABLE_PENDING/NOT_APPLICABLE_CONFIRMED`和`PENDING/CONFIRMED/RETURNED`不得混载。
- 项目经理负责适用性、负责人、外包标识及“不适用”确认。工程师只能填写本人当前有效指派的适用项、固定表单值、现场结论和证据引用；不得改模板规则或他人项。
- 表单保存只允许`DRAFT`准备版本和当前负责人，使用item/form版本CAS。提交确认前服务端验证固定Schema字段、必填项和PLT精确证据引用。
- 确认或退回由当前项目经理执行。确认冻结表单值、负责人、外包、证据及来源输入；`NOT_APPLICABLE_CONFIRMED`同时把item确认状态记为`CONFIRMED`并冻结项目经理、原因和时间。
- 最后一个未决item确认后，同一事务把Preparation从`PENDING_CONFIRMATION`推进为`CONFIRMED`；聚合条件是每个`REQUIRED`项均为`CONFIRMED`，每个非适用项均为`NOT_APPLICABLE_CONFIRMED+CONFIRMED`，且不存在`RETURNED`项。
- 任一`PENDING_CONFIRMATION`或`CONFIRMED`版本的item被退回时，旧item记`RETURNED`、旧Preparation记`RETURNED`并清除`current_marker`，随后原子创建`businessVersion+1`的current `DRAFT`。新版本保留同一模板/表单版本：未退回且已确认项连同冻结事实与确认元数据复制为`CONFIRMED`；退回项复制表单值、负责人、外包和证据作为可编辑基线，但重置为`PENDING`、表单`DRAFT`并清除确认/退回元数据；其他未决项保持`PENDING`。来源稳定引用及last-success可复制，但当前权威值清空且同步状态置`UNKNOWN`；豁免按`projectId+itemCode`继续引用原追加历史，不复制或重建。

### BR-FSOL002-003 文件与来源事实

- 每份证据冻结`artifactId+versionNo+referenceKey+fileFactVersion+scopeVersion`，不保存文件正文、URL或INFRA定位。证据数量和用途由冻结项策略决定。
- OA仍是领料/外采流程Owner。SOL只保存`sourceType/sourceObjectType/sourceObjectId/sourceReferenceKey`、归一结果、来源版本/水位和同步状态，不复制原单据。
- 涉及OA的项必须通过INT-05公开Provider同步inspect/lockAndRevalidate；Provider不可用、未命中、越租户、未知结果、同步异常或版本变化均形成阻断，不能解释为“无来源要求”。来源表将当前权威值与`last_success_*`分开：只有`SYNCED`时当前`normalizedResult/sourceFactVersion/sourceWatermark`非空；首次失败时三者为空；已有成功后的失败同样清空当前权威值并保留last-success只作显示，不得据其判READY。
- 显式来源刷新命令捕获Provider失败，在自身事务内写`ERROR/UNKNOWN`、稳定失败码和拒绝审计后提交，再由HTTP层返回失败结果；不以抛异常回滚该异常事实，也不产生成功幂等/审计。就绪评估遇到Provider失败时可在同一评估事务记录来源异常并追加`NOT_READY`快照；公共只读inspect/revalidate不得写来源行。
- 无OA要求的项不伪造来源记录；已批准且仍有效的逐项豁免可按冻结规则替代指定来源条件。

### BR-FSOL002-004 逐项豁免

- 豁免按`project+preparation item+waiverNo`追加，申请须冻结原因、风险、补偿措施、适用阻断类型、有效期和模板配置的审批角色；申请人不得自报审批角色。
- 生命周期为`DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED/WITHDRAWN`，时间到达后派生为失效。每项同一时点最多一个待审豁免和一个有效豁免；同一豁免只允许合法终态一次。
- 项目经理使用管理权限申请/撤回；审批人必须同时具备豁免审批功能权限、PROJECT_VIEW范围和模板冻结的当前项目角色。角色或范围变化在决定前锁定重验。
- 批准豁免只替代快照中明确列出的阻断，不改变工勘项、OA来源或文件原事实；过期、撤回或被替代后立即参与重新判定。

### BR-FSOL002-005 就绪计算与不可变快照

- 当前版本只有在所有适用项均已确认、固定表单有效、必需证据可用、负责人/外包规则满足、必需来源达到模板要求终态且同步正常、所有剩余阻断均被有效豁免时才为`READY`。
- 只有显式`evaluate-readiness`命令在幂等事务中追加`sol_preparation_readiness_snapshot`，冻结项目范围版本、准备输入版本、项/表单版本、PLT文件事实、来源版本/水位、豁免版本、阻断清单和计算规则版本；历史快照不可更新或删除。
- `sol_preparation`保存单调`input_version/readiness_version`、最新快照指针和持久`READY/NOT_READY`。任一SOL输入变更在原命令事务内递增`input_version`、置`NOT_READY`并把快照标记为非当前，但不隐式追加快照；外部文件/来源/范围变化由只读inspect实时比对结构化事实向量发现。
- evaluate锁定根行并计算排序稳定的结构化事实向量；若与最新快照的`inputVersion/projectScopeVersion/itemVersions/fileFacts/sourceFacts/waiverFacts`完全相同，直接重放该快照，不递增`readiness_version`；仅向量变化时追加`snapshotNo+1`并CAS更新指针与状态。无需哈希或查询写入即可保证同一事实不重复快照。
- inspect/lockAndRevalidate只读重算：当前向量与最新快照不一致时返回`snapshotCurrent=false/NOT_READY`或版本冲突，不修改来源、快照、指针和版本。调用方必须先显式evaluate获得当前快照，不能继续信任历史READY。
- F-SOL-002不写IMP/PROJ表、不改变项目阶段。后续S4命令必须调用SOL `lockAndRevalidate`，以当前锁定事实为准。

### BR-FSOL002-006 状态轴

| 事实 | 物理承载 | 值域/规则 |
|---|---|---|
| 准备版本生命周期 | `sol_preparation.status_code` | `DRAFT/PENDING_CONFIRMATION/CONFIRMED/RETURNED`；退回版本保留RETURNED并由下一DRAFT接任current |
| 项适用性 | `sol_preparation_item.applicability_code` | `REQUIRED/NOT_APPLICABLE_PENDING/NOT_APPLICABLE_CONFIRMED` |
| 项确认状态 | `sol_preparation_item.confirmation_status_code` | `PENDING/CONFIRMED/RETURNED` |
| 来源同步 | `sol_preparation_source_reference.sync_status_code` | `SYNCED/ERROR/UNKNOWN` |
| 豁免审批 | `sol_preparation_item_waiver.status_code` | `DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/WITHDRAWN`；有效性另由有效期派生 |
| 当前就绪 | `sol_preparation.readiness_status_code` | `READY`只能由当前不可变快照决定；SOL输入变更可直接失效为`NOT_READY/snapshot_current=false`，外部变化由纯只读重验判非当前，只有显式evaluate追加新快照 |

禁止用单一状态字段同时表达提交、适用性、来源、豁免和就绪。

### BR-FSOL002-007 权限与主体矩阵

| 能力 | 功能权限码 | ProjectStageScope | 主体约束 |
|---|---|---|---|
| 查看准备、工勘项、阻断和历史 | `pms:preparation-survey:query` | `PROJECT_VIEW` | 当前项目可见成员；文件访问另行回源PLT |
| 初始化恢复、适用性、指派、确认/退回、不适用、来源刷新、就绪评估 | `pms:preparation-survey:manage` | `PROJECT_MANAGE` | 当前项目经理 |
| 填写本人项与提交证据 | `pms:preparation-survey:fill` | `PROJECT_VIEW` | 当前有效item负责人；仅本人项 |
| 提交/撤回豁免 | `pms:preparation-survey:manage` | `PROJECT_MANAGE` | 当前项目经理 |
| 审批豁免 | `pms:preparation-survey:waiver-approve` | `PROJECT_VIEW` | 模板冻结审批角色的当前项目参与人，且不是申请人 |

服务端统一使用受信tenant/actor、功能权限、ProjectStageScope、当前角色或item责任区间、状态和版本重验；请求不得自报tenant、角色或审批人。

### BR-FSOL002-008 幂等、并发与审计

- 初始化、提交准备版本、确认/退回、来源刷新、豁免申请/决定和显式就绪评估使用`Idempotency-Key`；同键同载荷重放原结果，异载荷冲突。inspect和lockAndRevalidate为纯只读，不要求幂等键。
- 所有修改使用`If-Match`和场景化CAS。稳定锁序为PROJ范围事实→SOL准备/项/表单/来源/豁免→PLT精确文件事实；INT-05来源重验在SOL锁后按冻结引用批量执行。版本变化返回稳定冲突且无成功副作用。
- 成功审计冻结项目、准备/项/表单/来源/豁免/快照标识、动作、前后状态与版本、阻断摘要、operationId、操作者和时间；失败事务回滚后通过平台公共审计记录稳定拒绝码和必要安全事实。
- SDS锁定为同步命令或查询；本Feature不发布跨Context业务事件，不建立Outbox事件。

## 4. API与模块契约

所有HTTP路径继承`/api/v1/pms`，返回平台统一`CommonResult`和稳定业务错误码。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/preparations?projectId={id}&type=PRE_02` | `GET` | 返回当前准备版本、就绪状态、最新快照摘要和允许动作；无记录为空业务结果 |
| `/preparations/{id}` | `GET` | 返回冻结模板摘要、版本、状态、阻断摘要；不返回文件URL和OA原单据 |
| `/preparations/{id}/items` | `GET` | 稳定`sortOrder,itemCode,id`游标分页；按权限收窄可编辑事实 |
| `/preparations/{id}/items/{itemId}` | `PATCH` | 项目经理修改适用性/负责人/外包，或负责人按字段存在性修改表单与证据；空PATCH拒绝，必填`If-Match` |
| `/preparations/{id}/actions/submit` | `POST` | 冻结当前版本并进入待确认；必填`Idempotency-Key/If-Match` |
| `/preparations/{id}/items/{itemId}/actions/confirm|return|confirm-not-applicable` | `POST` | 当前项目经理逐项确认/退回/不适用确认；退回/不适用原因必填 |
| `/preparations/{id}/items/{itemId}/sources/actions/refresh` | `POST` | 按冻结来源引用调用公开Provider；未知/不可用写同步异常并失败关闭 |
| `/preparations/{id}/items/{itemId}/waivers` | `POST,GET` | 申请及稳定历史；申请必填幂等键、If-Match、原因/风险/补偿/有效期 |
| `/preparations/{id}/items/{itemId}/waivers/{waiverId}/actions/submit|approve|reject|withdraw` | `POST` | 按稳定状态、主体、ProjectScope和角色推进；决定不可重复 |
| `/preparations/{id}/actions/evaluate-readiness` | `POST` | 锁定重验全部输入并追加不可变快照；返回READY或阻断清单 |
| `/preparations/{id}/readiness-snapshots` | `GET` | 稳定`snapshotNo,id`游标历史 |

初始化由项目创建后冻结的公开WorkBinding事实触发，并提供同一内部幂等恢复命令；不新增第二个用户可写模板入口。

### 4.1 SOL公共就绪契约

- `SiteSurveyReadinessApi.inspect(SiteSurveyReadinessQuery)`输入受信`projectId`及可选`preparationId`，纯只读返回当前preparation、businessVersion、status、readinessStatus、latestSnapshotId/snapshotNo、inputVersion/preparationVersion/readinessVersion、projectScopeVersion、`snapshotCurrent`、阻断代码和结构化`ReadinessFactVector`。
- `SiteSurveyReadinessApi.lockAndRevalidate(SiteSurveyReadinessRevalidationQuery)`必须携带`projectId/preparationId/expectedBusinessVersion/expectedInputVersion/expectedPreparationVersion/expectedReadinessVersion/expectedSnapshotId/expectedProjectScopeVersion/expectedFactVector`；任一为空、越租户、非current、版本变化、向量变化、快照非当前或当前不READY均失败关闭。
- inspect与重验均不写业务表。重验在调用方事务中持有PROJ范围和SOL事实锁至提交，并同步重验精确PLT文件引用、INT-05来源事实及豁免有效性；返回的READY只对该事务冻结事实有效。
- 后续IMP S4只消费该公开只读契约，不读SOL表；本Feature不创建IMP快照或S4命令。

### 4.2 WorkBinding与领域边界

- PRE-02工勘项是SOL业务事实，不是`TASK_NATIVE`任务正文。初始化只消费上述PROJ Owner的窄`ProjectWorkBindingFactApi`；F-SOL-002 V1使用项目工作区的SOL页面，不以任务工作台作为完成前置。
- 后续接入F-PROJ-007工作台时，只能注册`BUSINESS_OBJECT`或`DYNAMIC_FORM`非原生绑定Provider，目标键指向SOL preparation/item；allowedActions和complete均回源SOL重验，缺失、无权、版本变化或Provider不可用失败关闭。
- SOL位于`pms-module-engineering`并持有六表DO/Mapper/Service。SOL只消费PROJ、PLT和INT-05公开API，不访问其Service/Mapper/DO/表。
- INT-05尚未实施时，来源Provider固定返回不可用；这只让有OA要求且无有效豁免的项保持阻断，不伪造“无OA要求”，也不阻断无OA项的正向主线。

## 5. 数据与物理边界

机器契约：`specs/features/F-SOL-002-physical-contract.json`。

- 前向新建`sol_preparation`、`sol_preparation_item`、`sol_dynamic_form_instance`、`sol_preparation_source_reference`、`sol_preparation_item_waiver`、`sol_preparation_readiness_snapshot`。
- SOL内部引用使用`tenant_id`复合外键；`project_id`、用户、PLT文件及OA来源均为跨Context稳定引用，不建物理外键。
- Mapper仅暴露场景化insert、稳定查询、锁定读和专用CAS；不继承通用CRUD，不提供历史快照更新/删除。
- 使用实施时下一未占用Flyway版本前向迁移，不修改已执行迁移；旧`pms_eng_site_survey`保留历史差距证据但不再作为PRE-02当前入口或双写目标。

## 6. UI

- 项目工作区新增“工勘准备”入口，按准备概览、工勘项、来源/豁免、阻断与快照历史组织，不创建第二套任务树。
- 项目经理可批量指派并逐项确认；负责人只看到本人可填写项；来源异常、文件失效和豁免过期必须显示明确阻断且不能用旧成功值伪装READY。
- 优先复用Yudao现有Card、Descriptions、Form、Table、Tabs、Steps、Upload、Drawer/Dialog、Timeline和权限组件；无可复用时遵循Element Plus结构、主题变量和响应式断点，避免过多内联样式。
- 320/768/1024/1440无页面级横向溢出；窄屏用卡片/抽屉呈现项详情和阻断，刷新后状态、草稿及最新快照保持一致。

## 7. 验收标准

- `AC-FSOL002-001`：项目冻结模板可幂等初始化PRE-02 current版本、六类基准项和固定表单版本；前向父Feature数据不存在、模板非法或通用脚本配置均失败关闭。
- `AC-FSOL002-002`：项目经理指派/适用性、负责人填写/证据、项目经理确认/退回/不适用确认按主体和CAS生效；最后一项确认聚合CONFIRMED；退回按锁定复制/重置矩阵原子切换下一current DRAFT且历史冻结不被覆盖。
- `AC-FSOL002-003`：证据只冻结精确FileArtifact引用；文件失效或版本/范围变化后，纯只读inspect/revalidate判旧READY快照非当前并失败关闭且不写表；随后显式evaluate才追加NOT_READY快照。
- `AC-FSOL002-004`：OA引用只保存稳定引用、当前权威结果、last-success、版本/水位和同步状态；首次及后续失败可提交异常事实并保持阻断，最后成功值不伪装完成；无OA要求项可正常闭环。
- `AC-FSOL002-005`：豁免仅由项目经理申请、冻结角色的当前参与人审批；有效豁免只替代指定阻断，过期/撤回/角色变化后重新阻断。
- `AC-FSOL002-006`：READY要求所有当前输入满足；仅显式evaluate在事实向量变化时追加不可变快照，同一向量/幂等重放不重复；inspect/revalidate纯只读且旧快照向量不匹配时失败关闭。
- `AC-FSOL002-007`：SOL公共inspect/lockAndRevalidate精确冻结current准备、快照、范围、文件、来源和豁免；任一版本变化返回冲突且调用方无成功副作用。
- `AC-FSOL002-008`：仅使用`PROJECT_VIEW/PROJECT_MANAGE`范围动作；项目经理、负责人、豁免审批人及跨租户/越权负向均由服务端拒绝。
- `AC-FSOL002-009`：并发指派、填写、确认、豁免决定和就绪评估均单胜；同键同载荷重放，异载荷冲突，审计可按project/preparation/item/operationId追溯。
- `AC-FSOL002-010`：全新MySQL从V1迁移至当前版本，验证六表、租户复合外键、唯一键、CAS、冻结历史、失败回滚、字典/权限和组合示例种子。
- `AC-FSOL002-011`：真实浏览器完成初始化→指派→填写/证据→确认→来源/豁免→READY，以及退回、文件/来源/豁免失效恢复NOT_READY、权限负向、刷新和四档响应式。
- `AC-FSOL002-012`：不写PROJ/IMP/OA/PLT业务表，不创建第二任务树或跨Context事件，不宣称SOL-01、INT-05、PM-11、S4、Deployment、SIT/UAT或Release完成。

## 8. 测试与证据

按已确认的非TDD方式，先闭合正向主线，再后置不影响主线的分支和异常验证。Feature完成证据至少包含服务/API自动化、状态/权限负向、CAS/幂等并发、PLT文件、来源Provider、平台审计、真实MySQL迁移与事务、真实浏览器全链和独立代码评审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PRE-02独立Feature与上下游边界 | PASS（`NPDMS-FSOL002-BOUNDARY-20260827-01`） |
| 最小完整正向闭环与失效恢复 | PASS |
| 六表物理模型、状态轴和稳定键 | PASS |
| 权限、公共就绪API、同步契约和无事件边界 | PASS |
| 独立Feature Ready裁决 | PASS（`NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`） |

结论：`BASELINE / READY`。原Feature Ready阻断已闭环，独立裁决已GO；本规格修订合入目标分支后创建全新Technical Plan。不重开已通过的PRD/SDS门禁，本结论不代表Technical Plan、Implementation、Deployment、SIT、UAT或Release已通过。
