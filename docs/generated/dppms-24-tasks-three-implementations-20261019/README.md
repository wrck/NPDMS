# 24项已决同步任务：三种实现、已有验证、字段及SQL

汇总日期：2026-10-19。工作树：`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`；分支：`fix/dppms-migration-consolidation-20261012`。本报告结合分支实现、既有候选/保存定义、真实预览回执及已执行测试日志。后续核查按授权仅修复新套件，原任务隔离保留，最新变更和证据见下方。

**共24项、每项3种实现、72份验证记录；原字段处置861行。当前不能称为“72项验收全部通过”。**

## 新P31全量真实预览完成后的最新结果

[base-sync-p31-original-interval-preview-20261019](base-sync-p31-original-interval-preview-20261019.md)及[24项当前回执](base-sync-p31-original-interval-preview-20261019.json)：新虚基类套件 **19 PREVIEW_READY、5 FAILED**。新P31完整381195条预览就绪，152条原区间倒置按本轮裁决原样保留、逐条登记，实际写入仍拒绝且日期/CHECK不改；P35全量1586578条通过。剩余P10/P14/P15/P18/P33的具体来源、正式输入及未决项已逐项列明。28项定向检查通过，原任务、历史、测试库保护核验保持；本次无DDL、迁移、真实同步或59191操作。P30仍为空预览。以前阶段的回执按原日期保留，当前C以上述JSON为准。

## 剩余七项复核后的结果（历史）

[base-sync-seven-blockers-followup-20261019](base-sync-seven-blockers-followup-20261019.md)及[24项当前回执](base-sync-seven-blockers-followup-20261019.json)：新虚基类套件 **18 PREVIEW_READY、6 FAILED**。P35全量1586578条通过；新P31错误联系人依赖按已有裁决修复，现有152条源区间倒置仍阻断。新P15/P33已越过获准结构/容量补丁，正式输入和源地址缺项已逐项定位；P10/P14/P18的来源与正式输入前置列明。P31新日期预览处理选项待回复，未自动扩写规则。26项定向检查通过；原任务、测试库及历史保护核验保持，无Flyway、真实同步、双线对照或59191操作。P30仍为0行空预览。旧阶段JSON/SQL保留历史，本阶段JSON提供当前C终态。

## ID修正及P16映射修复后的结果（历史）

[本阶段逐项报告](base-sync-post-id-release-preview-20261019.md)及[24项当前回执](base-sync-post-id-release-preview-20261019.json)：新虚基类套件为 **17 PREVIEW_READY、7 FAILED**。P22全量343858条、P19全量186118条已就绪；P16修复已批准Readonly回退的结果映射后，158871条真实预览就绪。剩余7项的来源、正式输入或结构/容量缺项逐项列明；P30仍是0行空预览。验证库40个项目及关联技术ID已按单独授权修正，维护材料在工作树外；原任务保留，未执行迁移、真实同步或双线对照，59191未操作。旧阶段的verification-matrix及各附件保留原回执，本阶段JSON提供C路线的当前终态。

## P22订单行延后回填后的结果（历史）

[本轮逐项报告](base-sync-p22-order-line-preview-20261019.md)：24项仍为14 PREVIEW_READY、10 FAILED。P22原查询直接带入原pm_project.column001，两个引用ID先-1；正式订单行缺失已不阻断读源，当前真实预览为缺原部门编码，正数量缺项14293条。2976条零数量按已有裁决列待处理；七类77项有效检查通过，最新19项受影响重跑。剩余十项处理依据及执行结构限制逐项列明；无DDL、迁移或真实同步。

## 部门编码与暂缓扩展阶段结果（历史）

[base-sync-department-code-preview-20261019](base-sync-department-code-preview-20261019.md)：当前24项14 PREVIEW_READY、10 FAILED；本轮4项真实预览均到终态。P10 ERP列已按单列授权补齐，当前为真实客户源数据冲突；P19/P35扩展暂缓后被40项原ID冲突保护；P22改按租户/部门码匹配、非空ID先-1，现缺正式订单行，另14525条缺原部门编码。96项定向检查通过，仅59284重载；无真实同步、其他迁移或原任务操作。

## 原用户名及原项目ID阶段结果

[该阶段逐项报告](base-sync-original-identifiers-preview-20261019.md)：24项为14 PREVIEW_READY、10 FAILED；原13项中的P08、P20、P21已解除。P08为612条、P20为7818条，P24受影响重验81547条通过。P22排序规则SQL错误已修复，当前缺正式办事处快照；1001～1040共40个原项目ID与既存项目冲突，P33全量1063717条超过现有100万约束。该阶段判断按当时证据保留；最新结果以上一节为准。

## 首次核查阶段结果（原ID修订前）

[13项逐项核查、P21修复与真实预览](base-sync-13-failures-followup-20261019.md)：P21完整读取1190条后PREVIEW_READY；原13项失败剩12项。该阶段新虚基类24项为12 PREVIEW_READY、12 FAILED，P30仍为空预览。14次新套件预览、1次59284专用重载、22项定向检查的证据均已列明；没有迁移、真实同步或原任务操作。

## 当前三种实现与结论

| 方式 | 具体实现 | 已有验证结论 |
|---|---|---|
| A 原通用引擎 / Owner适配器 | 原任务原身份候选；19业务适配器 + 5 TABLE_MAPPING | 当前24份候选未全部保存/重跑；下面保留历史组件结果，不冒充当前候选验收 |
| B 独立直写 | 独立ID与来源绑定；Direct*写入器，部分任务仍复用GenericSyncEngine | 最新既有回执16 PREVIEW_READY、7 FAILED、1历史非预览SUCCESS（P01）；READY中有空预览及全ISSUE |
| C 新虚基类 | BASE_Pxx + AbstractSyncTask/DefaultSyncTask + 专用XML + 独立int_base_sync_* | 19 PREVIEW_READY、5 FAILED；P30为0行空预览。新P31全量381195条就绪并登记152个原日期问题，28项检查通过；P35全量1586578条就绪；未做真实同步 |

三种方式是现存实现路线，不新增任务，不运行双线业务对照。原37项组件按既有合并结论组成24项：P01←01/02/03，P04←04/05/06/07，P08←08/09，P15←15/26/27/28/29，P16←16/17，P31←31/32，P35←35/36，其余单项保留。

## 历史测试环境：十项目同步任务与处理逻辑

原十项目的同步使用已保存SyncTask与逐项目领域Owner命令；历史主环境为59282/19082。它们作为历史补充列出，不算当前三种实现之外新增的一套任务，也不替代当前72份验证记录。

- [原十项目完整汇总、项目身份与已有执行/重放结果](pilot-ten-projects/README.md)
- [按24项业务归类的原任务ID、来源→正式表、前置/处理/后置逻辑](pilot-ten-projects/tasks-and-processing.md)
- [41项历史已保存任务及配置版本](pilot-ten-projects/saved-task-inventory.md)
- [当时的逐项目Owner命令和历史实现源码](pilot-ten-projects/owner-commands.md)
- [后续另一批十项目的续保/多合同同步任务与执行顺序](pilot-ten-projects/renewal-ten-projects.md)

当时共同流程为：限定项目及来源范围 → 冻结原值与目标基线 → 共享组织/账号/字典及正式父数据 → 逐项目预览 → 领域Owner或已保存任务写入 → 正式字段读回 → UNCHANGED/REPLAY_COMPLETED重放及正常业务页面检查。没有统一SyncTask的成员、安装、出厂、软件、许可等对象保留其命令证据，不捏造任务ID。

原十项目、后续续保十项目、共享基础数据、0行样本和后来的整表任务分别记录；最早44类资料快照不等于正式业务承接完成。附件只整理历史SQL与回执，本次未执行任何查询、同步、迁移或服务操作。

## 24项已有验证总表

单元格采用“终态 / 回执read”；read不能跨实现求和或直接比较业务覆盖。A列一律指历史，当前候选仍未验；P04保留4个历史组件结果。FAILED/read0可以是读源或前置失败，不证明空表。具体Run、时间、摘要、错误、测试日志均在任务页及机器可读矩阵中。

| 任务 | 已决业务 | A 当前候选未验；以下为历史 | B 独立直写最新既有回执 | C 新虚基类最新既有回执 |
|---|---|---|---|---|
| [P01](P01/README.md) | 公司、部门及主管 | PREVIEW_READY / 180 | SUCCESS / 211；历史真实执行 | PREVIEW_READY / 208 |
| [P04](P04/README.md) | 账号、角色、授权和数据范围 | P04:FAILED / 1,566；P05:FAILED / 31；P06:FAILED / 0；P07:FAILED / 0 | PREVIEW_READY / 5,015 | PREVIEW_READY / 12,632 |
| [P08](P08/README.md) | 基础字典 | FAILED / 612 | FAILED / 612 | PREVIEW_READY / 612 |
| [P10](P10/README.md) | CRM客户 | PREVIEW_READY / 59,601；ISSUE=83 | FAILED / 0 | FAILED / 0 |
| [P11](P11/README.md) | CRM产品 | PREVIEW_READY / 8,441 | PREVIEW_READY / 8,441 | PREVIEW_READY / 8,443 |
| [P12](P12/README.md) | DPPMS缺失产品补齐 | PREVIEW_READY / 34,976 | PREVIEW_READY / 34,976 | PREVIEW_READY / 34,976 |
| [P13](P13/README.md) | CRM执行单 | PREVIEW_READY / 99,895；ISSUE=99,895（全行） | PREVIEW_READY / 1,895 | PREVIEW_READY / 99,895 |
| [P14](P14/README.md) | 销售订单与订单行 | PREVIEW_READY / 467,190；ISSUE=2,138 | PREVIEW_READY / 465,804；ISSUE=1,102 | FAILED / 0 |
| [P15](P15/README.md) | 合同、发货引用、包裹、发货事件和设备关系 | PREVIEW_READY / 3,848,169；ISSUE=814 | PREVIEW_READY / 3,848,169；ISSUE=814 | FAILED / 0 |
| [P16](P16/README.md) | 有效项目主档及全部旧身份 | FAILED / 0 | FAILED / 0 | PREVIEW_READY / 158,871 |
| [P18](P18/README.md) | 项目合同关系 | PREVIEW_READY / 82,676；ISSUE=82,676（全行） | PREVIEW_READY / 82,676；ISSUE=82,676（全行） | FAILED / 0 |
| [P19](P19/README.md) | 项目相关方及状态资料 | PREVIEW_READY / 265,547；ISSUE=265,547（全行） | PREVIEW_READY / 265,547；ISSUE=265,547（全行） | PREVIEW_READY / 186,118 |
| [P20](P20/README.md) | 项目真实产品 | PREVIEW_READY / 7,818；ISSUE=7,818（全行） | PREVIEW_READY / 7,818；ISSUE=7,818（全行） | PREVIEW_READY / 7,818 |
| [P21](P21/README.md) | 项目租赁产品及配置树 | PREVIEW_READY / 1,294；ISSUE=1,294（全行） | FAILED / 1,190 | PREVIEW_READY / 1,190 |
| [P22](P22/README.md) | 项目产品实施范围 | PREVIEW_READY / 343,858；ISSUE=343,858（全行） | PREVIEW_READY / 343,858；ISSUE=343,858（全行） | PREVIEW_READY / 343,858 |
| [P23](P23/README.md) | 合同回款计划 | FAILED / 0 | PREVIEW_READY / 35,501 | PREVIEW_READY / 35,501 |
| [P24](P24/README.md) | 合同实际回款 | PREVIEW_READY / 79,182 | PREVIEW_READY / 79,182 | PREVIEW_READY / 81,547 |
| [P25](P25/README.md) | 逐次续保 | PREVIEW_READY / 453,443 | PREVIEW_READY / 453,443 | PREVIEW_READY / 462,213 |
| [P30](P30/README.md) | 设备主档及项目归属 | NOT_PREVIEWED | PREVIEW_READY / 0；空预览 | PREVIEW_READY / 0；空预览 |
| [P31](P31/README.md) | 成员历史、历史联系人及获准授权 | NOT_PREVIEWED | FAILED / 0 | PREVIEW_READY / 381,195；152条原日期待处理 |
| [P33](P33/README.md) | 普通安装历史及获准当前位置 | NOT_PREVIEWED | FAILED / 0 | FAILED / 0 |
| [P34](P34/README.md) | 出厂软件版本历史 | NOT_PREVIEWED | PREVIEW_READY / 165,801 | PREVIEW_READY / 165,801 |
| [P35](P35/README.md) | 软件变更批次与版本历史 | NOT_PREVIEWED | FAILED / 0 | PREVIEW_READY / 1,586,578 |
| [P37](P37/README.md) | 设备许可历史 | NOT_PREVIEWED | PREVIEW_READY / 475,468 | PREVIEW_READY / 475,468 |



## 历史测试环境十项目补充

已补[原十项目的同步任务与处理逻辑](pilot-ten-projects/README.md)，含十个原→正式项目身份、24业务归类、41项当时已保存任务、逐项目Owner命令、冻结SQL/历史源码及已有回执。另将[后来续保/多合同的另一批十项目](pilot-ten-projects/renewal-ten-projects.md)单列。它们是历史补充，不增加上方72份当前实现验证记录，不把旧材料快照或旧试点批准扩大为全量完成。

## 各任务交付内容

点击上表任务进入详情，每项均提供：

1. 三种实现的验证结果、任务/运行ID、错误与已完成测试证据；代码存在、测试通过、预览就绪和真实同步分开记录。
2. 业务主键、来源技术键、原表PK、正式表PK/唯一索引。各实现不同的keys逐项列明。
3. `field-mapping.md`：原表861行字段映射/明确排除/转换/待关联处置；`write-field-bindings.md`：三种方式实际INSERT列与参数。
4. `single-table-projections.sql`：逐原表的原值审阅查询，每份SELECT只一张原表；`A-original.sql` / `B-direct.sql` / `C-base-sync.sql`：现有实际来源查询、目标前置SELECT、INSERT、更新/删除和已有临时目录SQL。
5. `processing.md`：前后置逻辑、实际关联UPDATE/DELETE清单，新基类关键钩子原文，以及Java/API处理边界。

单表审阅投影明确以`raw__`标记，尚未进行Java转换/加密/授权/目标关联，不替代运行SQL的范围筛选。Owner/ORM及通用JDBC动态语句没有固定原件时仅给有依据且明确标记的模板；不会编造一份已实现的INSERT SELECT或UPDATE JOIN。原始MyBatis节点另存JSON，避免展开后的注释丢掉choose/foreach语义。

现有Mapper目录包含相关共享查询声明，报告保留源文件/statement及行号；声明存在不等于本任务曾执行。目录中1255条按任务/方式归属摘录的声明/模板包含重复引用，不是1255个唯一SQL或数据库执行次数。

## 必须保留的差异与问题

- P04：新基类已有获批历史任职承载，`isSysUser`按“系统预设用户”保留在`custom_info.systemPresetUser`，不授管理员权限。新预览read12632、insert12605只是四步骤动作；1176逻辑账号、330任职不能与旧5015读数混用。A/B不借用C修复结论。
- P11：实际CRM来源为SQL Server `DPtech_v_product_info_single`。新基类读取8443原行含2个重复编码；A/B旧预览8441是既有归并范围，不代表相同SQL。
- P15：A当前候选是本地合同输入与COM Owner收尾；B是五阶段流式旧全量预览；C是独立五步骤。B历史3848169/ISSUE814不验证A新Owner收尾。
- P25：A当前单表读源后仍以本地发货/父合同判资格；B已有453443条限定范围预览；C整表462213条、不回填设备ID。三者范围差异不能解释为少读/多写修复已经共同验收。
- P30：B/C虽PREVIEW_READY，但read0；缺正式DPPMS发货输入，不能记为设备业务闭环完成。
- P37：B/C整表475468条已有真实预览，sourceId472122空码按原始空串加密并登记。正式设备/合同关联可空，所有关联待需要时处理；不引入旧试点Owner。A旧路径未接入已批整表边界，不能把旧配置缺项重新归为整表未授权。
- P31/P33/P35：C已修正为读取自身P16完整非预览COMPLETED范围，不再依赖旧发布表；当前缺的是本套件正式输入。A/B旧完整发布前置保持各自独立。

## C当前10项失败及外部处理条件

以[最新部门编码/扩展暂缓报告](base-sync-department-code-preview-20261019.md)及实际终态为准；P10缺列、P19/P35扩展定义、P22项目办事处快照已不再列为本轮预览阻断。

| 任务 | 具体缺项及处理 |
|---|---|
| P10 | ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。 |
| P14 | 源订单表头91572行/91239键，169组载荷冲突；订单行380605行/379522键，10组冲突，40条原行缺订单号。保留冲突校验，需ERP来源Owner确认正式版本及缺失订单号，不能用MAX任意选值。 |
| P15 | com_shipment_package.shipment_contract_source_key仍缺失；4283326条条码超过注册值/库CHECK的100万，代码500万上限不会自动修改配置和结构。已有补丁仅准备，未获本轮其他DDL例外；正式P24输入也未产生。 |
| P16 | 当前40个原项目ID（1001至1040）均被其他编码项目占用。遵守原projectId承接，禁止覆盖或重新编号；需提供无冲突的正式承接环境或冲突归属处理证据。 |
| P18 | 原项目ID承接仍被既存异编码项目占用；共用同一40项现场证据，不再把账号目录或P16预览当成正式父数据。待冲突归属解决后重验实际合同关系。 |
| P19 | 四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。 |
| P22 | 正式订单行缺失：首条源记录id=1的精确订单号/行号/产品码在目标匹配0条，完整项目合同关联也为0条。343858条范围中14525条缺原部门编码，其余非空编码均可按租户匹配。需正式订单输入及缺失部门业务键；数值部门ID用-1不替代缺失编码。 |
| P31 | 当前正式项目身份与原ID范围不同，仍受同一40项冲突保护；不补造user_id，不由历史成员授予当前权限。待正式项目身份正确后再验历史联系人所需最终客户等真实关联。 |
| P33 | 原批准范围1063717行，1037715行带地址，均超过当前100万任务/元数据约束。需已审核容量配置与对应结构到位；不得裁剪范围。后续真实设备、地址及授权能力仍需单独核验。 |
| P35 | 八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。 |

## 汇总文件和证据来源

- [72份验证记录JSON](verification-matrix.json)
- [全部任务SQL分类索引](sql-catalogue.json)
- [公共前后置、共享测试及边界](shared-framework.md)
- [提取覆盖与告警](coverage.json)
- [生成来源和版本](generation.json)
- [报告完整性检查](report-validation.json)

主要来源：

- [A当前24份原候选复核与169项分批测试](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/migration-task-capabilities-20261015/original-task-remediation-20261017.md)
- [B独立直写24项及历史回执](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/24-independent-direct-tasks-20261017.md)
- [C新基类24项、后续十二项复核及177项测试](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/standalone-base-sync-20261017.md)
- [A当前候选配置目录](D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands)
- [B最新既有终态](D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/owned-only-status.json)
- [C十二项后续回执来源](D:/CodexTaskArtifacts/project-detail-followup/base-sync-blockers-20261018-wave9/twelve-current-results.json)
- [2026-10-14原表/目标结构快照](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/schema.json)

初次汇总只整理文档。2026-10-19后续13项核查已执行14次新套件真实预览、1次59284专用重载和22项定向检查，仅修改新P21预览结构前置。未执行迁移、真实同步、正式后置关联或定义发布，未操作原任务或59191；本次实时证据与历史回执分别标明。

