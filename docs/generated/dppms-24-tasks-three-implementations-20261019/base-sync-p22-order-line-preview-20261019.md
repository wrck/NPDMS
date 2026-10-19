# P22订单行延后回填、原项目部门编码及十项缺项

2026-10-19，独立新虚基类套件。当前24项 **14 PREVIEW_READY、10 FAILED**。本轮只重跑受影响的新P22，两次均已到真实终态；其余23项保留最后有效回执。最新Run **2112084563050270722** 为FAILED：`P22_DEPARTMENT_CODE_UNRESOLVED:NULL`。正式订单行缺失已解除为读源前置，当前确实缺原始部门编码。

## 已按现有审核实施

- 原部门编码在原查询中通过`l.projectId = p.projectId`直接带入`p.column001`。没有第二次原项目查询或新联表；归并主档只继续提供已审核的项目身份，不代替原项目部门。
- `order_line_id=-1`、`department_id=-1`。新源行不查询正式订单行、合同关系或从正式订单数量反推原数量。公司小表和本租户部门目录各读一次；公司码复用既有转换。
- 订单后处理一次UPDATE：同租户、同连接、当前新P22运行及PRODUCT_SCOPE绑定，且仅order_line_id=-1、未确认分配；以公司/订单号/行号/产品码精确匹配新P14的SALES_LINE独立绑定和已完成非预览运行。沿用P14实际JSON数组SHA256业务键，唯一目标才更新；缺失/跨ERP歧义保持-1并登记，已有正数ID不更新。
- 部门后处理同样仅限当前新P22独立绑定、department_id=-1、同租户精确编码及目录版本。两种后处理保留审计时间、原值基线和重放保护；预览不调用。
- 已追溯[DPPMS-SCOPE-039用户裁决](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/decisions/open-questions.md:2270)：零数量早已有“保留正数约束、列待处理、其余正数继续”的决定，不能整项当成未裁决。现已修复并用混合批次/负数失败测试验证；全量2976条清单保留。

## 真实预览与来源缺项

| P22回执 | 终态 / 回执read | 实际错误 |
|---|---|---|
| [2112081593361432578](D:/CodexTaskArtifacts/project-detail-followup/base-sync-order-line-deferred-20261019/P22-preview.json) | FAILED / 0 | P22_DEPARTMENT_CODE_UNRESOLVED:NULL |
| [2112084563050270722](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/P22-preview.json) | FAILED / 0 | P22_DEPARTMENT_CODE_UNRESOLVED:NULL |

失败回执read=0不代表源表为空。下列数字来自独立的完整范围SELECT，不冒充API已成功读完：

- 原批准范围343858条；零数量2976条，数量为空或负数均0条；正数量行的订单号/行号/产品码缺失0条。
- 原项目部门编码空14525条，其中正数量14293条。首条正数量缺项源id=6644、原/归并项目ID=49746，原pm_project.column001为NULL。全部缺项的源行/原项目ID已输出；所有非空部门码均能精确匹配本租户。
- 当前数据的原项目与选中主档部门在正数量范围内差异0条；新增原生SQL测试特意制造两者不同，以及原值NULL/主档有值，验证始终取原部门且不猜回退。

依据：[全量聚合与码表匹配](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/original-department-source-evidence.json)、[14293条原部门缺项](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/missing-original-department-rows.json)、[2976条零数量清单](D:/CodexTaskArtifacts/project-detail-followup/base-sync-order-line-deferred-20261019/P22-reviewed-pending-source-rows.json)。全量零数量数量来自只读清单；当前失败API尚未持久化整表零数量汇总。

## 真实执行前置与预览边界

真实目标仍有`fk_scope_order_line(tenant_id,order_line_id)`，且order_source_system/order_company_code/order_type三列为NOT NULL，不能承载待回填的-1及未知快照。实现将其作为执行前校验；预览只登记结构警告，继续读源。本轮没有关闭外键、塞入-1父记录、填假ERP/订单类型或执行DDL。[实际结构证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/pending-order-schema-evidence.json)。

已同时核对DPPMS-SCOPE-040既有裁决和实际索引：uk_scope_version只约束allocation_confirmed=true，历史false不占该唯一槽位；该处已正确，不列为新阻断。[索引/CHECK证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/scope-constraints-evidence.json)。

先后次序为原值批量承接→当前运行部门关联一次→订单业务键/独立绑定关联一次。真实执行尚需正式结构具备待定引用承载能力；P14真实输入未产生时，订单引用继续待定。原有分配/版本/不可变数据和授权保护保留。

## 十项仍未闭合及可执行处理

| 任务 | 已核清缺项与后续处理 |
|---|---|
| [P10](P10/README.md) | ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。 |
| [P14](P14/README.md) | 源订单表头91572行/91239键，169组载荷冲突；订单行380605行/379522键，10组冲突，40条原行缺订单号。保留冲突校验，需ERP来源Owner确认正式版本及缺失订单号，不能用MAX任意选值。 |
| [P15](P15/README.md) | com_shipment_package.shipment_contract_source_key仍缺失；4283326条条码超过注册值/库CHECK的100万，代码500万上限不会自动修改配置和结构。已有补丁仅准备，未获本轮其他DDL例外；正式P24输入也未产生。 |
| [P16](P16/README.md) | 当前40个原项目ID（1001至1040）均被其他编码项目占用。遵守原projectId承接，禁止覆盖或重新编号；需提供无冲突的正式承接环境或冲突归属处理证据。 |
| [P18](P18/README.md) | 原项目ID承接仍被既存异编码项目占用；共用同一40项现场证据，不再把账号目录或P16预览当成正式父数据。待冲突归属解决后重验实际合同关系。 |
| [P19](P19/README.md) | 四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。 |
| [P22](P22/README.md) | 订单行ID及部门ID均先-1，正式订单行缺失不再阻断原表预览。原pm_project.column001直接带入后，全范围343858行中14525行缺编码，正数量缺编码14293行，首条源行6644/原项目49746；其余非空码均能按租户匹配。需来源补齐这些原始部门业务键，不能猜部门或用数值-1代替业务码。2976条零数量按DPPMS-SCOPE-039既有裁决登记待处理。真实执行另需能承载待定订单引用的正式结构，以及后续新P14业务键/独立SALES_LINE绑定；当前未做DDL或同步。 |
| [P31](P31/README.md) | 当前正式项目身份与原ID范围不同，仍受同一40项冲突保护；不补造user_id，不由历史成员授予当前权限。待正式项目身份正确后再验历史联系人所需最终客户等真实关联。 |
| [P33](P33/README.md) | 原批准范围1063717行，1037715行带地址，均超过当前100万任务/元数据约束。需已审核容量配置与对应结构到位；不得裁剪范围。后续真实设备、地址及授权能力仍需单独核验。 |
| [P35](P35/README.md) | 八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。 |

P10来源、40项项目ID冲突、目标列及容量CHECK已在本轮只读复核；P14源冲突、P15条码全量和P33容量基数沿用未变的既有证据，没有声称本轮重跑这些任务。依据：[remaining-prerequisites.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-order-line-deferred-20261019/remaining-prerequisites.json)、[source-prerequisites.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-order-line-deferred-20261019/source-prerequisites.json)、[source-evidence.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/source-evidence.json)。P19/P35扩展仍暂缓；不因这些缺项恢复扩展写入。

## 24项最新有效回执

| 任务 | Run | 终态 / read | 本轮预览 |
|---|---|---|---|
| [P01](P01/README.md) | 2111504172950122498 | PREVIEW_READY / 208 | 沿用有效回执 |
| [P04](P04/README.md) | 2111517837875126273 | PREVIEW_READY / 12,632 | 沿用有效回执 |
| [P08](P08/README.md) | 2112057345712537601 | PREVIEW_READY / 612 | 沿用有效回执 |
| [P10](P10/README.md) | 2112067330504867842 | FAILED / 0 | 沿用有效回执 |
| [P11](P11/README.md) | 2111436809982627841 | PREVIEW_READY / 8,443 | 沿用有效回执 |
| [P12](P12/README.md) | 2111436813732335618 | PREVIEW_READY / 34,976 | 沿用有效回执 |
| [P13](P13/README.md) | 2111515526163517442 | PREVIEW_READY / 99,895 | 沿用有效回执 |
| [P14](P14/README.md) | 2112051434155581442 | FAILED / 0 | 沿用有效回执 |
| [P15](P15/README.md) | 2112051493786001409 | FAILED / 0 | 沿用有效回执 |
| [P16](P16/README.md) | 2112057425274290177 | FAILED / 0 | 沿用有效回执 |
| [P18](P18/README.md) | 2112059597156204546 | FAILED / 0 | 沿用有效回执 |
| [P19](P19/README.md) | 2112071091293958146 | FAILED / 0 | 沿用有效回执 |
| [P20](P20/README.md) | 2112059695747514370 | PREVIEW_READY / 7,818 | 沿用有效回执 |
| [P21](P21/README.md) | 2112053188930744321 | PREVIEW_READY / 1,190 | 沿用有效回执 |
| [P22](P22/README.md) | 2112084563050270722 | FAILED / 0 | 重跑 |
| [P23](P23/README.md) | 2111466972774600705 | PREVIEW_READY / 35,501 | 沿用有效回执 |
| [P24](P24/README.md) | 2112057587128287234 | PREVIEW_READY / 81,547 | 沿用有效回执 |
| [P25](P25/README.md) | 2111480444740603906 | PREVIEW_READY / 462,213 | 沿用有效回执 |
| [P30](P30/README.md) | 2111481771424153602 | PREVIEW_READY / 0 | 沿用有效回执 |
| [P31](P31/README.md) | 2112057614999437313 | FAILED / 0 | 沿用有效回执 |
| [P33](P33/README.md) | 2112057626944815105 | FAILED / 0 | 沿用有效回执 |
| [P34](P34/README.md) | 2111480509467103233 | PREVIEW_READY / 165,801 | 沿用有效回执 |
| [P35](P35/README.md) | 2112071158990024705 | FAILED / 0 | 沿用有效回执 |
| [P37](P37/README.md) | 2111480525451595777 | PREVIEW_READY / 475,468 | 沿用有效回执 |

P30仍为空预览，不能据此称业务完成。P21的1190条就绪仍保留执行结构警告，P04/P13等其他既有结构前置也未被本轮放开。

## SQL、验证及隔离证据

- [P22当前源查询、批量INSERT、关联UPDATE及辅助XML](base-sync-p22-order-line-preview-20261019.sql)；保留真实MyBatis节点与参数，不能整体当SQL脚本执行。来源只有原有一层派生表；订单后处理一层分组派生表，未增加深层嵌套。
- 七类77项有效定向结果全部通过、无跳过；其中最新19项重跑覆盖原部门来源语义、双方-1、目录只读一次、业务键/独立绑定、租户/任务/连接/运行隔离、歧义待定、正数ID保护、审计时间、重放及失败回滚。77取每类最新结果，不把76+19重复相加。测试日志见JSON。
- 写入用例只操作连接级临时表，待定引用fixture保留真实列/唯一键/CHECK但允许未知订单快照为NULL；不等于正式库FK验收。正式不兼容结构另由真实元数据与执行前失败用例验证。
- 仅59284授权重载3次，当前PID45184；本轮实际预览2次。最后一次候选仅改变P22来源XML，其前两次冻结仅变更独立P22类与XML。无迁移、DDL、真实同步、原任务操作或59191操作。
- [最终保护证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p22-original-department-20261019/final-proof.json)：180项计数/最大ID/原任务版本观察与本轮起点一致，任务无占用，独立绑定0、非预览运行0，Flyway仍473，迁移容器退出及自动执行标志关闭。这不是全表逐行或全结构相等的证明。ERP单列为上阶段已批准例外，本轮没有新增结构写入。
- 新套件无专属任务UI；本轮用正常登录的真实API及数据库终态验收，不声称任务专属浏览器闭环或全量预览通过。
