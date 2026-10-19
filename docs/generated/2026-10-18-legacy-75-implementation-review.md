# 75项存量实现逐项审查与修复

本次审查对应上一轮94项诊断中的75个生产文件：16个Controller、30个数据对象、17个前端API、12个页面。核对对象是当前工作树的实现、调用方、迁移来源及适用业务契约，不把文件覆盖、注释或一次构建当作Feature完成。

## 处理结论

| 审查分类 | 文件数 | 处置 |
|---|---:|---|
| 可复用 | 61 | 保留当前模型和调用边界；仅在各自职责与既定业务契约内复用 |
| 需修复 | 9 | 问题落在6个入口依赖的服务及3个前端文件，修复与验证见下文 |
| 应退役 | 5 | 停止作为新实现基础；保留旧功能、原始数据和历史证据。旧验收兼容入口同时接受必要安全修复 |

分类按文件计数。同一问题会覆盖多个入口；旧验收已归入“应退役”，其本次安全修复不重复计入9个“需修复”文件。75项之外的直接依赖Service、Mapper、查询对象、测试，以及场景执行页面/API也随确认缺陷作最小修改。

退役依据：`tasks/features/F-ACC-001.md`明确禁止V17单行验收栈承接新实施，`docs/design/16-exception-and-idempotency.md`要求保留旧表和旧功能，不得从旧状态推断新有效报告；`V374__p06r_unified_delivery_data_migration.sql`将旧交付提交迁入PLT统一存储，当前ACC服务已不再调用旧提交Mapper。因此本次没有删除历史表、停用用户服务、重写旧数据或重新开放退役入口。

退役不要求新功能逐项承接旧功能。按本轮用户明确的范围，未被当前业务使用的能力不实现；本次仅修复现有调用链的确定缺陷，没有新增替代模块、业务流程或菜单。隔离验收使用的模板、流程定义和权限配置都是临时测试夹具，不进入产品初始化数据。

复用判断仅覆盖这75个文件及本次必要调用链，不把宽目录兜底项整体认证为Done。`tasks/implementation-baseline-inventory.json`的模块级待逐Feature认证边界继续保留；新增功能仍须按其Feature契约审查。

## 已确认问题与修复

1. **原生验收数据范围缺口。** 四类CRUD原本只有功能权限，没有项目范围裁剪。新增`NativeAcceptanceProjectAccess`复用PROJ范围API，读取使用当前用户的VIEW范围，写入使用MANAGE范围并锁定/重验树版本；页查询显式包含租户和可见项目，空集合返回空页。完工证明设备明细先鉴权父证明，更新同时检查原项目与目标项目。
2. **旧验收状态与并发保护。** 创建仅接受草稿，写操作在事务内锁行；编辑和状态转换使用观察版本并检查实际影响行数，冲突不得报告成功。既有状态机和必交件规则保持。交付清单关联验收必须存在且同租户、同项目；已有清单不能因父验收改项目而成为跨项目引用。
3. **同步场景执行。** 运行前使用真实登录用户验证对象访问，实例与结果列表按对象范围过滤；识别契约访问拒绝和Owner返回的标准403，其余错误仍抛出。系统事实观察者不能替代调用者授权。幂等键重放须匹配租户、定义及对象身份，冲突使用既有409错误契约，结果形成和实例记录进入同一事务。直接消费页面保留64位ID字符串，不再经过`Number`截断。
4. **正常闭环总览。** 原实现捕获图异常后继续调用两次图查询；改为一次观察生成检查。依赖不可用返回失败检查，不开放提交。
5. **满意度模板发布。** 根已使用MyBatis乐观锁，原实现先手动递增导致CAS条件错误；移除提前递增，检查根与修订更新条数。旧发布修订保留，发布失败回滚，不产生假成功。
6. **旧页面契约。** 验收页面对齐`acceptanceDate/approveOpinion`，交付清单对齐`deliverableUrl/checkResult/version`；移除没有后端存储的虚假字段。归档编辑按钮改用`update`权限。未新增旧验收关联选择能力。

## 验证记录

后端按测试类取最近一次有效结果，累计69类、434例通过，无失败、错误或跳过；不是将重复运行相加。覆盖既有验收/绑定回归及本次状态绕过、版本冲突、项目和租户范围、非法关联、幂等意图冲突、事务回滚、依赖失败与发布失败。后端状态、并发、引用、幂等、总览与发布缺陷保留先失败再修复的记录；异常类型和错误码也作了定向失败/通过验证。明细见`.run/build/legacy75-review-20261018/backend-verification-index.json`及同目录测试日志。

前端34个测试文件、215例通过。巡检项目选择测试原先在Node环境导入浏览器字典依赖失败，仅对该依赖作测试隔离，保留真实组件及选择行为断言。改动页面ESLint无错误；没有将组件测试视为真实浏览器验收。

后端通过工作树构建入口完成构建与冻结，22项既有修复保护检查通过；该轮75项浏览器验收使用的最终产物为`.run/build/releases/f4c88a8d-447e-48a6-847e-ea049a2d0016/manifest.json`，运行配置为`local,dac`。该轮验收结束前未再修改生产代码；后续单独授权的模板修复及新产物见下文。

正式空库包B498+V499+V500在独立schema中完成迁移、校验及重复迁移：应用启动前461张表、238个触发器，7组差异查询均无行。该库不含项目或组织业务样本；测试模板以仓库`TemplateVersionSnapshotTest.designer()`为起点，按当前`TemplateCompilerTest`已有契约补齐权限、交付件、优先级和默认分支字段，属于隔离测试夹具。任务状态机通过正式API建立并发布V89已定义的8条转换。系统公司、部门、用户和角色通过正式API建立；公司部门联合范围无写入API，隔离库仅使用现有SYSTEM集成测试同构的3条权限夹具。两个项目、经理指派、验收、工勘和场景业务数据均通过正式API创建，未直接写业务状态。

真实Chromium浏览器完成管理员及两位非管理员的登录。两位项目经理具有相同功能权限，分别管理项目2、3；验证先证明各自有权，再证明跨项目拒绝。证据目录为`.run/build/legacy75-review-20261018/browser-acceptance/`：

| 验证对象 | 实际结果 | 证据 |
|---|---|---|
| 4类原生CRUD的项目范围 | 48项检查通过：双方各自创建、读取、修改成功；不加项目筛选的分页仅返回各自项目；跨项目读取、修改和创建均被拒绝 | `acc-scope-evidence.json` |
| 状态、版本及引用失败边界 | 8项检查通过：4类对象不能改到他人项目；旧版本编辑拒绝且原值保留；直接创建非草稿拒绝且未增行；跨项目验收引用拒绝，同项目引用成功 | `acc-failure-boundaries.json` |
| 旧页面保存后重开 | 验收日期/意见、清单真实上传文件URL/检查结果/数值版本、归档名称均通过页面保存后重新打开核实；补齐双方相同的现有读取权限后，相关页面API无失败 | `acc-ui-evidence.json`、`clean-ui-read-permissions.json`及3张`acc-*-saved-reopened.png` |
| 归档编辑权限 | 移除B的归档更新权限并重新登录后，仍能读自身归档，编辑按钮为0，直接更新被拒绝；没有移除其全部写权限 | `archive-query-only-evidence.json`、`archive-B-query-only-no-edit.png` |
| 满意度模板发布 | 页面创建、保存、发布首修订，再创建并发布第二修订；根版本为2，当前指针指向第二修订，首修订转为SUPERSEDED且冻结问卷、阈值和维度内容不变 | `satisfaction-evidence.json`、`satisfaction-second-published.png` |
| 场景运行、幂等及结果范围 | 草稿工勘运行返回UNSATISFIED；同键重放原结果，换对象返回HTTP 409；跨项目运行拒绝。工勘经正式Owner确认并自动归档后，使用另一份冻结定义通过页面运行得到SATISFIED；请求完整保留64位ID字符串。A/B非空结果分别仅含自身对象，管理员能看到双方；确认后重放原草稿请求仍为UNSATISFIED | `scenario-evidence.json`、`scenario-confirm-evidence.json`、`scenario-satisfied-evidence.json`及2张`scenario-real-ui-*.png` |

上述权限拒绝遵循上游响应约定，原生CRUD多数为HTTP 200、业务码403；幂等冲突为HTTP 409、业务码1010006002，不以HTTP状态单独判断通过。实例清单还包含既有事件驱动后端生成的实例，断言按实际对象及后端记录核对，没有将实例数固定为1。

页面验收通过已登录浏览器中的临时内存路由加载仓库现有组件，操作真实后端；没有恢复已停用的旧验收菜单。该结果证明受影响组件的操作与服务契约，不代表正式菜单导航或75项全部业务流程均已重新验收。正常闭环的依赖失败路径由定向服务测试验证，本轮未人为中断共享服务。其余可复用项依据源码、调用关系和适用回归判断，未改写为整模块Feature完成。

隔离环境已清理，见同目录`cleanup-final.json`：独占schema和数据库账户均不存在；59299、18199、24389端口均空闲；本轮应用、浏览器、runner、Redis容器和两次准备过程的独占网络均已退出或删除。59191和48182仍为原PID及原启动时刻。验收证据与截图保留；日志中的敏感SQL参数/凭据行已清除，73个文本文件的敏感内容检查为0命中。本轮未提交、推送或部署到既有服务。

## 范围外运行观察

初次准备项目夹具时还发现项目模板模块的3个既有问题，均不属于75项审查范围，当时未扩展生产代码修改：

1. 旧测试模板中含未引用决策规则时，草稿保存返回500，堆栈经过`TemplateRuleCollection.forEditing/index`；尚未确认完整根因。
2. 无效草稿发布时，错误路径触发`ck_project_template_revision_designer_v2`数据库约束并返回500，没有形成预期的业务校验拒绝。
3. 缺少转换优先级的模板可以通过校验和发布，但创建项目时因数据库`priority`无默认值而失败。

这些原始失败保留在同一证据目录的`backend.log`；当时夹具按当前测试契约补齐字段后成功发布和创建项目，夹具修正不代表产品缺陷已解决。用户随后单独授权修复这3项问题，结果如下。

## 三项模板问题的后续修复与验收

本次仅修改现有模板编辑、发布和实例化链路中的3个生产文件，并调整5个相关测试文件。未新增业务能力、菜单或替代模块，未修改PRD业务语义、数据库结构或既有不可变快照。

| 问题 | 根因与修复 |
|---|---|
| 决策策略草稿保存500 | `TemplateRuleCollection`将JSON往返产生的显式`NullNode`误认为第二份条件来源。现在将其识别为空，仍拒绝缺少条件来源和同时包含两份真实来源；不改写原始规则JSON或历史快照。 |
| 无效草稿发布500 | `ProjectTemplateV2ServiceImpl.rememberValidation`对草稿做部分更新时，由字段更新策略将必需Designer写为空，先触发数据库约束；该事务最终也会回滚。移除这次无效写入，直接返回原有发布校验业务错误，保留状态、权限、依赖及规则校验。 |
| 发布后创建项目因转换字段缺失失败 | 编译器原先未检查转换的`priority/defaultBranch`，而运行表`priority/is_default`均为无默认值的必填列。现在在编译/发布前返回具体字段的`REQUIRED`错误，不凭空填充业务优先级或分支语义。 |

先失败验证中，新增5个回归场景全部复现原问题（4个断言失败、1个错误，无额外失败）。修复后按各测试类最近有效结果统计，11类、179例全部通过，无失败、错误或跳过；两个旧测试夹具仅补齐当前已规定的阶段、交付件和转换字段，保留原断言。结果及日志位于`.run/build/template3-review-20261018/`，汇总为`green-final-summary.json`。使用工作树构建入口完成新后端构建，22项既有修复保护检查通过；新冻结产物为`.run/build/releases/62c2fef2-44c3-4970-bd3f-d9ca64e11148/manifest.json`，隔离运行配置为`local,dac`。

按用户指定，从测试库`npdms_domain_test`只读同步项目`16200723052503N-0`实际生效的“工程类－直签 · 计划 v4”：计划ID`2104870198458802262`、revision 4，完整保留7阶段、16任务、9交付件、5门禁、6条转换和39条条件规则。该对象是现行项目计划，未用历史来源模板v2替代。原Designer、执行快照、元数据及8个办理视图的当前发布原件均保留在上述证据目录。

独立目标库使用正式B498+V499+V500初始化并通过迁移、重复迁移和校验。8个办理视图中7个复用目标已有的等价发布配置，1个割接审批工作台视图按源精确组件通过正式API创建、校验和发布。逐项比较Owner、对象类型、组件版本、上下文Schema、动作及Provider等11个字段；仅在派生验收稿映射环境相关修订引用，其他Designer内容与源原件完全一致。遇到业务操作缺少模板或表单时，应按本轮用户授权从测试环境现行有效版本补齐；本轮指定验收链路未遇到其他此类缺项，没有额外创建未使用配置。

真实浏览器及API证据位于`.run/build/template3-review-20261018/browser-acceptance/`：

| 验证 | 实际结果与证据 |
|---|---|
| 完整源配置保存、重开、发布 | 在正式模板管理路由`/pms/project-templates`完成操作；UI保存后的Designer与源映射稿完全相等，重开一致，发布修订1成功。见`source-v4-positive-evidence.json`及`source-v4-ui-*.png`。 |
| 无效发布和历史保护 | 发布前、已有发布版本后，各执行缺优先级、缺默认标记、缺交付件配置3种负例，共6次；均返回HTTP 200、业务码`1014023005`及准确字段错误。每次失败前后草稿、根版本和修订数量不变，已发布原件不变。页面预检也显示真实配置错误。见`template-negative-before-evidence.json`、`template-negative-after-evidence.json`。 |
| 项目实例化与实际页面 | 通过正式匹配与创建API成功创建1个项目；实际项目详情页展示7阶段、16任务，相关请求无业务错误；生成9交付件、5门禁，6条运行转换的优先级和默认标记均正确落库。见`source-project-create-evidence.json`、`source-project-ui-evidence.json`、`source-project-runtime-edges.json`及`source-v4-real-project-7stage-16task.png`。 |
| 决策策略实际编辑 | 源v4本身没有DECISION，因此从已发布验收模板正式复制独立草稿，用已有UI添加合法决策表，绑定开放字段，再将同一策略改绑“签约方式”；两次保存成功，重开内容一致，预检通过。派生稿保持未发布，主模板39条条件及已发布原件不变。见`decision-existing-ui-edit-evidence.json`及`decision-existing-ui-edited-saved-reopened.png`。 |

本轮证明3项修复、模板发布与项目实例化链路，不代表完整S0～S6业务任务已逐项执行完成。源测试环境始终只读，结束时再次核实Designer、执行快照和生效元数据均未变化；现有59191/48182仍为原PID及原启动时刻。独占schema、数据库账户、应用、浏览器、runner、Redis容器和网络已清理，59299/18199/24389均空闲。证据与截图保留，688条敏感日志行已清理，92个文本文件敏感检查0命中；见同目录`cleanup-final.json`。未提交、推送或部署到既有服务。

## 逐项结果

| 序号 | 当前文件 | 分类 | 依据与复用/退役边界 |
|---:|---|---|---|
| 1 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptance/AcceptanceController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptance/AcceptanceController.java) | 应退役 | V17 单行验收入口，F-ACC-001 已禁止承接新实施；保留旧功能并补项目范围、初始状态及并发保护。 |
| 2 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptancereport/AcceptanceReportController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptancereport/AcceptanceReportController.java) | 可复用 | 活动/报告版本入口；查询经项目范围、写命令经 Owner 并发和状态守卫，附件及当前/历史版本分别读取。 |
| 3 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentController.java) | 需修复 | 原生 CRUD 缺项目范围；本次补列表裁剪、详情和写入授权，保留归档后不可改及文件归属校验。 |
| 4 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentFileController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentFileController.java) | 可复用 | 下载先经 NativeAcceptanceDeliveryAccess 校验原生根、权限及项目范围，再请求精确材料文件票据。 |
| 5 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionCertificateController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionCertificateController.java) | 需修复 | 原生 CRUD 与设备明细读取缺项目范围；补父对象授权、列表裁剪和写事务范围重验。 |
| 6 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionConfirmationWorkflowController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionConfirmationWorkflowController.java) | 可复用 | 现有成套确认工作流入口；完工证明、满意度和签字仍经业务服务统一提交，不把通知送达当完成。 |
| 7 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDeliverableSubmissionController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDeliverableSubmissionController.java) | 可复用 | ACC 兼容入口已委托统一交付件服务；保留计划/对象版本、文件引用和业务结果精确来源。 |
| 8 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDocumentSourcesController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDocumentSourcesController.java) | 可复用 | 读取既有来源定义，保持服务端权限和字典来源；不创建第二套交付件来源模型。 |
| 9 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverablechecklist/DeliverableChecklistController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverablechecklist/DeliverableChecklistController.java) | 需修复 | 原生 CRUD 缺完整项目范围及关联验收归属校验；补同租户/同项目引用、空范围返回空和写入守卫。 |
| 10 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureController.java) | 需修复 | 总览捕获图依赖错误后仍重复读取，导致页面失败；改用一次图观察，依赖不可用返回失败检查且不得提交。 |
| 11 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureProcessViewController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureProcessViewController.java) | 可复用 | 按申请ID解析租户内项目后复用正常闭环查询鉴权，未建立绕过 Owner 的流程详情旁路。 |
| 12 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java) | 可复用 | 公开问卷经摘要授权、有效期/消费状态、冻结问卷和文件归属验证；不复用后台登录权限替代客户授权。 |
| 13 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionResultController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionResultController.java) | 可复用 | 结果查询、精确文件、失效和导出委托现有服务；项目范围、结果版本、历史来源与补偿边界保留。 |
| 14 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTaskController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTaskController.java) | 可复用 | 任务分配、手动发起、独立采集及重采保留来源区分；不伪造验收完成或更改既有闭环条件。 |
| 15 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTemplateController.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTemplateController.java) | 需修复 | 发布服务提前递增带 @Version 的根版本且忽略写入条数；本次使用观察版本CAS并拒绝根/修订写入失败。 |
| 16 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/AcceptanceDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/AcceptanceDO.java) | 应退役 | V17 acc_acceptance_record 历史兼容模型，不转换为新版活动或有效报告；保留原表及原始事实。 |
| 17 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/ProjectDeliverableSubmissionDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/ProjectDeliverableSubmissionDO.java) | 应退役 | acc_project_deliverable_submission 已由 V374 迁移至统一交付存储；当前服务无 Mapper 调用，保留历史及迁移来源，不作为新写入基础。 |
| 18 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceActivityDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceActivityDO.java) | 可复用 | 活动根与当前报告指针、来源身份分离；支持既有 DIRECT/任务来源，不从旧单行验收推断新活动。 |
| 19 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportAttachmentDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportAttachmentDO.java) | 可复用 | 报告附件保存PLT精确版本、引用和事实版本；历史附件序列不覆盖。 |
| 20 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportVersionDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportVersionDO.java) | 可复用 | 报告修订保存前序、有效区间及发布人事实；已生效内容不可原位编辑。 |
| 21 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancescope/AcceptanceScopeBindingDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancescope/AcceptanceScopeBindingDO.java) | 可复用 | ACC 范围锁定事实独立于验收报告，绑定阶段快照和精确分配版本；不推断自动退出或解锁。 |
| 22 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/archivedocument/ArchiveDocumentDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/archivedocument/ArchiveDocumentDO.java) | 可复用 | 归档文档业务版本与乐观锁版本分别表达；归档历史和已登记材料归属保护保留。 |
| 23 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDO.java) | 可复用 | 现有原生证明根被成套确认工作流复用；状态命令、确认时间及归档事实继续由服务端管理。 |
| 24 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDeviceDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDeviceDO.java) | 可复用 | 证明设备明细仍归父证明；本次补父对象读取授权，已确认记录不重建历史设备清单。 |
| 25 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionPdfBindingDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionPdfBindingDO.java) | 可复用 | PDF绑定记录精确证明及文件版本，保留既有生成/下载来源，不把临时URL当最终归档事实。 |
| 26 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/deliverablechecklist/DeliverableChecklistDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/deliverablechecklist/DeliverableChecklistDO.java) | 可复用 | 固定字段及乐观锁列已存在；本次修复消费页面字段和同项目引用，未新增JSON字段或历史回填。 |
| 27 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureApplicationDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureApplicationDO.java) | 可复用 | 申请关联冻结检查快照及流程身份；正常闭环状态仍由申请服务和Owner确认。 |
| 28 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureReviewDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureReviewDO.java) | 可复用 | 保留每次评审及证据来源，未覆盖原审批历史或新造审批节点。 |
| 29 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureSnapshotDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureSnapshotDO.java) | 可复用 | 冻结项目/树/阶段与检查证据；快照通过不能绕过提交时重验。 |
| 30 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionBundleDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionBundleDO.java) | 可复用 | 成套确认关联证明与问卷的稳定身份；原生证明单独确认不能绕过已建立的成套关系。 |
| 31 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionSigningDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionSigningDO.java) | 可复用 | 保存一次客户签字、响应和授权的精确关联，重复提交沿用原事实。 |
| 32 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionAccessGrantDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionAccessGrantDO.java) | 可复用 | 仅存token摘要、授权版本及有效/消费事实；明文凭证不进入该模型。 |
| 33 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionCollectionTaskDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionCollectionTaskDO.java) | 可复用 | 区分来源、采集轮次、当前问卷/结果及分配事实；独立采集不伪造任务完成。 |
| 34 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireDO.java) | 可复用 | 冻结已选模板修订、问题和评分规则；后续模板发布不改写已有问卷。 |
| 35 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateDO.java) | 可复用 | 根的 @Version 定义正确；修复在发布服务如何使用观察版本，不改存储模型。 |
| 36 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateRevisionDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateRevisionDO.java) | 可复用 | 保留修订适用条件、优先级、冻结题目/阈值及有效区间；本次仅补发布写入失败检测。 |
| 37 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionRemediationFactDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionRemediationFactDO.java) | 可复用 | 重采依赖原结果与整改请求精确身份，追加新轮次并保留旧结果。 |
| 38 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseDO.java) | 可复用 | 响应保留冻结答案、来源和提交身份，公共/代填链路均保留幂等及归属校验。 |
| 39 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseFileDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseFileDO.java) | 可复用 | 响应文件引用含PLT版本与事实版本；失效或错归属文件不得作为有效证据。 |
| 40 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultDO.java) | 可复用 | 评分及通过结论由冻结问卷规则生成；失效使用独立事实版本，不覆盖原响应。 |
| 41 | [`pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultFileDO.java`](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultFileDO.java) | 可复用 | 结果文件精确关联来源序号、角色与版本；下载重新核验PLT可用性和权限。 |
| 42 | [`pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/controller/admin/ScenarioInstanceController.java`](../../pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/controller/admin/ScenarioInstanceController.java) | 需修复 | 运行与实例/结果清单缺实体授权；增加可信用户对象读取校验，并修复同步执行幂等身份冲突和事务原子性。 |
| 43 | [`pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/BindApprovalInstanceDO.java`](../../pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/BindApprovalInstanceDO.java) | 可复用 | 本地审批实例保存尝试键、定义/对象身份与决策状态；LocalApprovalBackend已有尝试身份冲突和终态保护。 |
| 44 | [`pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventCheckpointDO.java`](../../pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventCheckpointDO.java) | 可复用 | 按租户事件身份记录处理检查点；失败事件不推进游标，重试依赖结果幂等。 |
| 45 | [`pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventedInstanceDO.java`](../../pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventedInstanceDO.java) | 可复用 | 记录定义、事件和对象身份；事件重复处理不重复形成结果，实例清单已补对象范围裁剪。 |
| 46 | [`pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/InlineInstanceDO.java`](../../pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/InlineInstanceDO.java) | 可复用 | 已保存重放校验所需定义/对象身份；本次在后端补冲突校验及结果/实例同事务。 |
| 47 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/index.ts) | 可复用 | 原生与受控操作两条调用遵守同一活动/报告版本契约，保留If-Match、发布幂等键及精确附件读取。 |
| 48 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationActions.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationActions.ts) | 可复用 | 操作编码映射既有编辑/发布/撤销动作，不创造新的权限或状态。 |
| 49 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationResults.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationResults.ts) | 可复用 | 验证回执Owner、对象/修订身份及结果状态，不能把任意HTTP成功当业务完成。 |
| 50 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance/index.ts) | 应退役 | 旧单行验收API仅保兼容；修正日期/意见字段，不作为新版报告API。 |
| 51 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/archive-document/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/archive-document/index.ts) | 可复用 | 已有fileUrl/documentUrl、业务版本/乐观锁版本转换正确，保留兼容契约。 |
| 52 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/completion-certificate/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/completion-certificate/index.ts) | 可复用 | 成套确认、精确PDF及原生兼容调用保持分工，未把文件生成回执替代签署事实。 |
| 53 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/deliverable-checklist/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/deliverable-checklist/index.ts) | 需修复 | 错误字段及字符串version导致保存丢值/冲突；改用deliverableUrl、checkResult与数值型乐观锁version。 |
| 54 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/normal-closure/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/normal-closure/index.ts) | 可复用 | 闭环检查/提交/审批使用既有版本与幂等契约；总览依赖异常修复在服务层。 |
| 55 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/project-deliverable/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/project-deliverable/index.ts) | 可复用 | 兼容入口消费统一材料历史和referenceId，保留来源类型及精确结果身份。 |
| 56 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/satisfaction/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/satisfaction/index.ts) | 可复用 | 模板/任务/结果/公开授权调用与当前控制器对应；发布版本推进修复在Owner服务。 |
| 57 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/customerConfirmation.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/customerConfirmation.ts) | 可复用 | 开发环境手机入口将loopback主机替换为页面主机，保留配置API路径；不影响正式URL。 |
| 58 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/feedback.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/feedback.ts) | 可复用 | 反馈确认、复制草稿和导出保留Idempotency-Key/If-Match及统计快照字段。 |
| 59 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/online.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/online.ts) | 可复用 | 预检/采集分为预留、派发、刷新、取消；Secret仅随派发请求发送，不保存在连接模型。 |
| 60 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/request.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/request.ts) | 可复用 | 共用身份/租户拦截器，Business API从宿主根路径访问；WireLong避免ID强制数值化。 |
| 61 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/rules.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/rules.ts) | 可复用 | 规则修订、预检、安全审核、发布/停用对应现有端点与版本头，历史修订独立读取。 |
| 62 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/tasks.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/tasks.ts) | 可复用 | 任务/执行/报告/问题命令保留版本和幂等键；文件引用包含精确artifact/version/reference。 |
| 63 | [`yudao-ui/yudao-ui-admin-vue3/src/api/pms/integration/index.ts`](../../yudao-ui/yudao-ui-admin-vue3/src/api/pms/integration/index.ts) | 可复用 | 连接、任务、运行及来源映射通过INT正式API操作，ID使用string或number，不直接写跨模块表。 |
| 64 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance-report/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance-report/index.vue) | 可复用 | 新报告工作台复用现有报告面板和项目上下文，保持对象身份、只读和动作边界。 |
| 65 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance/index.vue) | 应退役 | 旧单行验收页面保留兼容；修复acceptanceDate/approveOpinion回显及动作权限/状态，不添加新入口。 |
| 66 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/archive-document/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/archive-document/index.vue) | 需修复 | 编辑按钮误用query权限且展示不存在的上传人/日期/校验值；改update权限并移除虚假字段。 |
| 67 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/completion-certificate/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/completion-certificate/index.vue) | 可复用 | 当前成套确认工作台保留项目隔离、证明和满意度统一签署、重开和精确PDF投递。 |
| 68 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/deliverable-checklist/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/deliverable-checklist/index.vue) | 需修复 | 旧清单编辑使用后端不存在字段；对齐附件/检查结果/乐观锁，保留统一交付件主面板和历史清单边界。 |
| 69 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/index.vue) | 可复用 | 复用成套确认工作台，转发项目、只读、dirty/leave契约，不建立并行满意度业务流程。 |
| 70 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/site-survey/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/site-survey/index.vue) | 可复用 | 恢复后的原工勘实体通过受控回执保存/重开；保留项目上下文、冻结表单、精确执行轮次和申请来源。 |
| 71 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/rules/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/rules/index.vue) | 可复用 | 草稿编辑与历史修订分离；未知写结果保留原请求，写成功读失败仅重读，权限和发布预检保留。 |
| 72 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/task-detail/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/task-detail/index.vue) | 可复用 | 保存/执行/报告/问题/归档按真实状态办理；固定原请求重试、文件Owner票据和未决操作离开保护保留。 |
| 73 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/tasks/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/tasks/index.vue) | 可复用 | 项目设备选择、分页与创建沿用正式服务；未知结果保留幂等请求，成功后导航真实任务。 |
| 74 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/workbench/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/workbench/index.vue) | 可复用 | 统计与待办来自服务端，区分读取失败和空集合；使用真实任务/问题ID导航。 |
| 75 | [`yudao-ui/yudao-ui-admin-vue3/src/views/pms/integration/index.vue`](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/integration/index.vue) | 可复用 | 连接/任务/运行/映射容器复用现有面板与URL状态，不自行承接数据写入。 |
