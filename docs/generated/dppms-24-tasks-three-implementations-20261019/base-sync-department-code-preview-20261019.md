# 部门编码、-1后置回填及暂缓扩展：真实预览复核

本页为上一阶段冻结结论；当前P22已解除正式订单行读源前置，最新原部门编码及实际回执以[后续报告](base-sync-p22-order-line-preview-20261019.md)为准。

2026-10-19；仅独立虚基类套件。当前24项为 **14 PREVIEW_READY、10 FAILED**。本轮P10、P19、P22、P35均已跑到真实终态；其余20项复用最后有效回执。没有把源码完成或临时表测试写成全量预览成功。

## 本轮已实施与授权边界

- ERP客户码列已按用户“允许仅补这一列”执行，类型沿用V481的varchar(50) NULL，库为59284专用npdms_reverify_20261014。[批准时的单列SQL](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/P10-erp-customer-code-only-20261019.sql)保留原生成时注释；实际执行状态见[执行证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/erp-column-applied.json)。未执行V481其他语句、Flyway或其他正式DDL。
- P19四个状态属性、P35八个软件历史属性暂不读取、校验或写入；不发布定义、不创建镜像、不迁到绑定JSON。原相关方、基础软件批次/版本的不可变及失败回滚校验保留。
- P22使用原主档column001，沿用既有旧编码转换；按租户及精确部门编码匹配，部门目录本次运行只读一次。正式项目元数据仅在存在时核对，缺少项目办事处快照不再阻止预览。
- P22新增department_id先填-1。真实执行路径在基础批次全部完成后仅调用一次关联UPDATE，限定-1、租户、编码、同连接BASE_P22、本次active_run_id与PRODUCT_SCOPE独立绑定；不会更新其他任务/运行或已有正数ID。预览没有调用该UPDATE。保留update_time，派生ID单独校验，回填后重放仍有效。
- 仅59284从PID47868重载为PID42260。[冻结清单](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/candidate-manifest.json)仅38个独立P19/P22/P35类或XML条目变动；原任务、公共API、其他配置与迁移资源保持冻结包条目原件。

## 四项真实预览

| 任务 | Run | 终态 / read | 当前实际结果 |
|---|---|---|---|
| [P10](P10/README.md) | 2112067330504867842 | FAILED / 0 | CRM客户原记录存在未决问题：DUPLICATE_CUSTOMER_CODE |
| [P19](P19/README.md) | 2112071091293958146 | FAILED / 0 | PROJECT_FORMAL_IDENTITY_INVALID |
| [P22](P22/README.md) | 2112071134759530497 | FAILED / 0 | P22_ASSOCIATED_ORDER_LINE_NOT_UNIQUE:1 |
| [P35](P35/README.md) | 2112071158990024705 | FAILED / 0 | PUBLISHED_PROJECT_CURRENT_IDENTITY_CHANGED |

失败回执read=0不代表源表为空；下列聚合查询独立核实了完整源范围。回执原件：[P10](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P10-preview.json), [P19](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P19-preview.json), [P22](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P22-preview.json), [P35](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/P35-preview.json)。

## 已核实的来源和必要关联

- P10实际SQL Server来源59601行：22组非空客户码重复、44条记录，22组的AccountId和名称均不同；另有35条缺名称、4条名称超长，子秒版本0条。不能把重复码当作同一客户自动合并。各计数未证明互斥，不直接相加。
- P22第一条源记录id=1、原主档ID37400、部门码162022存在；相同订单号/行号/产品码在正式订单行匹配0条，完整正式项目合同订单关联也0条。因此本次真实错误是缺输入，不是重复候选。
- P22完整原范围343858条，其中14525条原部门编码为NULL，其余所有非空编码均匹配当前租户目录。原NULL编码不能映射到目录中无编码的其他部门；-1只用于待回填的数值部门ID。
- 当前重新核查1001至1040共40个原项目ID，目标项目编码全部与源不同；无覆盖或重新编号。P19/P35新错误来自这项身份保护，已不再要求扩展定义。
只读现场证据：[source-prerequisites.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/source-prerequisites.json)；[terminal-gaps.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/terminal-gaps.json)。

## 24项最新有效回执

| 任务 | Run | 终态 / read | 本轮重跑 |
|---|---|---|---|
| [P01](P01/README.md) | 2111504172950122498 | PREVIEW_READY / 208 | 复用有效回执 |
| [P04](P04/README.md) | 2111517837875126273 | PREVIEW_READY / 12,632 | 复用有效回执 |
| [P08](P08/README.md) | 2112057345712537601 | PREVIEW_READY / 612 | 复用有效回执 |
| [P10](P10/README.md) | 2112067330504867842 | FAILED / 0 | 是 |
| [P11](P11/README.md) | 2111436809982627841 | PREVIEW_READY / 8,443 | 复用有效回执 |
| [P12](P12/README.md) | 2111436813732335618 | PREVIEW_READY / 34,976 | 复用有效回执 |
| [P13](P13/README.md) | 2111515526163517442 | PREVIEW_READY / 99,895 | 复用有效回执 |
| [P14](P14/README.md) | 2112051434155581442 | FAILED / 0 | 复用有效回执 |
| [P15](P15/README.md) | 2112051493786001409 | FAILED / 0 | 复用有效回执 |
| [P16](P16/README.md) | 2112057425274290177 | FAILED / 0 | 复用有效回执 |
| [P18](P18/README.md) | 2112059597156204546 | FAILED / 0 | 复用有效回执 |
| [P19](P19/README.md) | 2112071091293958146 | FAILED / 0 | 是 |
| [P20](P20/README.md) | 2112059695747514370 | PREVIEW_READY / 7,818 | 复用有效回执 |
| [P21](P21/README.md) | 2112053188930744321 | PREVIEW_READY / 1,190 | 复用有效回执 |
| [P22](P22/README.md) | 2112071134759530497 | FAILED / 0 | 是 |
| [P23](P23/README.md) | 2111466972774600705 | PREVIEW_READY / 35,501 | 复用有效回执 |
| [P24](P24/README.md) | 2112057587128287234 | PREVIEW_READY / 81,547 | 复用有效回执 |
| [P25](P25/README.md) | 2111480444740603906 | PREVIEW_READY / 462,213 | 复用有效回执 |
| [P30](P30/README.md) | 2111481771424153602 | PREVIEW_READY / 0 | 复用有效回执 |
| [P31](P31/README.md) | 2112057614999437313 | FAILED / 0 | 复用有效回执 |
| [P33](P33/README.md) | 2112057626944815105 | FAILED / 0 | 复用有效回执 |
| [P34](P34/README.md) | 2111480509467103233 | PREVIEW_READY / 165,801 | 复用有效回执 |
| [P35](P35/README.md) | 2112071158990024705 | FAILED / 0 | 是 |
| [P37](P37/README.md) | 2111480525451595777 | PREVIEW_READY / 475,468 | 复用有效回执 |

P30的PREVIEW_READY仍为0行；当前没有真实同步，因此预览不会制造正式父输入。P21的1190条已就绪仍带执行结构警告；P04/P13等既有多来源结构前置也未因本轮单列授权而放开。

## 十项未闭合问题与可执行处理

| 任务 | 具体缺项与处理 |
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

上述条目是输入、结构或身份边界的明确缺项，不是再次征求已裁决事项；本轮不越过它们执行迁移或真实同步。P22缺少的正式订单输入应由新独立P14形成或由既有正式Owner提供正确数据，不能触碰原任务来补齐。

## 当前SQL与验证证据

- [P19/P22/P35当前真实源/目标XML，含-1关联更新](base-sync-department-code-preview-20261019.sql)。P22来源仍为一层原有派生表；部门后处理为平坦联表，不新增深层嵌套。
- 15类96项定向检查全部通过，0失败/错误/跳过：[deferred-20261019T143648.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/deferred-20261019T143648.log)。P19延后状态不读源；P35基础历史不可变和后批失败回滚；P22覆盖同租户精确编码、目录只读一次、预览不回填、-1限定、其他连接/任务/运行隔离、审计时间与重放基线、目录变更全事务回滚。
- 96项中的写入仅在连接级临时表；临时范围表保留真实列、唯一键和CHECK，去除临时表不支持的外键。正式订单行等前置另外由实际预览核查，临时测试不冒充完整正式外键验收。
- [最终保护证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-deferred-extensions-20261019/final-proof.json)：180项计数/最大ID/原任务版本观察一致，24任务无占用；独立绑定0、非预览运行0、Flyway仍473、迁移容器退出、自动执行标志关闭。这些观察不证明全表逐行或全结构相等；已明确执行的ERP单列是唯一正式结构例外。
- 新套件无专属任务UI；本轮为正常账号真实API调用、真实来源查询和数据库运行终态，不声称任务UI业务验收。
