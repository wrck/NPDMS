# P18/P31原始查询、正式承载与真实预览复核

更新时间：2026-10-22T12:49:19.217788+08:00。工作树分支`fix/dppms-migration-consolidation-20261012`；目标仅`172.17.90.178:13306/npdms`，运行端口59284，新BASE套件。

本轮按用户新指示收敛为来源SQL处理简单默认、Java直接承接查询结果；不要求清洗原数据，不自行修改业务匹配键或补造事实。P18、P31本轮真实预览均完成；实际执行与重放边界按下文区分，预览、离线诊断和临时表测试均不冒充真实写入。

## 已修复的实现

- **P18审计文本默认在源查询完成。** 原空值经过Java的nonempty转换，最终违反正式creator/updater NOT NULL，是新套件的实现问题。合同行`NULLIF(actor,CAST('' AS BINARY))`保留原非空用户名，成员行`COALESCE(actor,'')`给最终空字符串默认；Java只保留既有合同优先、成员补缺的选择，移除空串转NULL和sys/异常数据处理来回转换。没有补账号、管理员或其他占位人，没有改公司/合同号。两条查询仍单表、零新增嵌套，不需要调整审计列或执行补丁DML。
- **P31原日期直接透传。** 删除倒置日期的Java判断、计数及警告分支，来源SQL和日期映射不变；没有日期清洗、CASE改日期、过滤行或NULL日期替换。原数据记录保留在本次证据中。正式授权、当前项目范围、绑定和已有历史保护保持。

70项定向检查通过。原生用例分别证明：保留正式日期CHECK时由数据库拒绝原值并回滚；仅连接内TEMP表允许原日期时，全部原值写入、独立绑定和重放成立，篡改保护仍生效。P18原生用例先复现creator cannot be null，再核验SQL默认、合同/成员优先级、sys/异常原文及纯空格保留、预览无写、真实TEMP插入与重放。没有为测试修改任何正式结构。

## 真实预览回执

| 任务 | Run | 状态 | 读取 | 规划插入 | 实际业务/绑定写入 |
| --- | --- | --- | ---: | ---: | ---: |
| BASE_P18 | 2113103699429675009 | PREVIEW_READY | 82,676 | 0 | 0 |
| BASE_P31 | 2113106188635852801 | PREVIEW_READY | 381,195 | 381,195 | 0 |

P18在公司授权为空时只读取已审核来源，不查询这些公司的正式合同或生成目标写入数；下文80,534条正式精确匹配来自另行获准的只读数据库核查。源记录及正式前置诊断不能冒充正式任务可写计划。

二进制空串比较前后的真实来源投影覆盖86,780条原合同行，两列差异均0；当前原数据无纯空白审计人，普通NULLIF与精确空串版在本轮数据上一致。仅在A2→A3精确空串比较阶段，包差异只有该XML比较表达式，其余当时已验证类和资源保持；后续A4另含四族吞吐类与两份Mapper XML调整，差异及计时见吞吐章节。该阶段真实预览与来源等价证明分别保留，不伪造新Run。

## P31真实执行与重放

| 阶段 | Run | 状态 | 读取 | 插入 | 更新 | 不变 |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| execute | 2113112457387982850 | COMPLETED | 381,195 | 381,195 | 0 | 0 |
| replay | 2113123404320161793 | COMPLETED | 381,195 | 0 | 0 | 381,195 |

P31已按用户明确批准仅在远端npdms移除chk_proj_member_dates，真实执行与重放均COMPLETED；381195条成员及独立绑定承接，152条倒置原日期、审计时间和角色逐项回读一致，重放计数与绑定指纹保持。

[actual原值核验](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/verify-p31-actual.json)；[replay原值核验](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/verify-p31-replay.json)；[真实执行回执](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/p31-actual-state.json)。

## P31吞吐调整与实测边界

P31性能收口：旧预览基线1280.963秒；A3首轮实际执行2058.91秒；A4批次500对现有381195条全量预览82.791秒，随后仅新P31批次在正式1..2000范围由500调至1000，A4批次1000全量预览67.624秒、该阶段重放175.953秒。批次调整已VERIFIED并保留审计及加密备份路径，其他任务配置、业务、原任务和Flyway保持；该调整非DDL，无代码改动或重启。没有重置数据，不能将优化后的预览或重放称为新包首次插入基准。

70项定向检查通过。来源SQL没有新增嵌套，381195条来源及152条倒置原日期全部保留。MANAGE范围每Run通过既有Owner API读取一次；每批身份、最新树版本与root path校验仍保留。新来源绑定最多500条一次INSERT VALUES，混合更新继续原条件WHERE并发保护；replayMap沿用同5字段业务键。

三次jcmd采样仅分别定位两次scope调用链、一次binding插入调用链，不据此声称耗时占比。批量树查询EXPLAIN使用既有索引，没有为性能新增DDL、权限或业务条件；唯一已执行DDL仍是明确批准的日期CHECK例外。

[批次500全量预览](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bulk-throughput/preview-performance.json)；[批次1000全量真实预览计时](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bulk-throughput/preview-performance-batch1000.json)；[批次审计及备份路径](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bulk-throughput/batch-size-adjustment.json)；[既有索引EXPLAIN](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bulk-throughput/batch-tree-explain.json)；[三次采样定位](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/p31-live-stack-samples.json)。

## A5全绑定历史回读收敛

A5最新真实全量预览58.675秒、execute重放171.441秒，两Run均读取/不变381195，其余写入计数0，业务及绑定CRC保持，152原值回读通过。A5仅缩小全绑定批次的目标回读ID范围，新来源仍原查重，缓存同一旧指纹；租户、项目和历史保护保持。原A3首插2058.91秒、A4两轮预览和首轮优化重放175.953秒回执保留；A5仅复核现有381195行，不重置数据，不提供新包首插基准。

70项最新检查；A4历史61项证明保留在bulk-throughput/green61-archive，不能用最新报告覆盖。A5预览与execute重放的真实Run见最终运行表，计时及零变更证明见机器结果。

| A5阶段 | Run | 状态 | 秒 | 读取/不变 |
| --- | --- | --- | ---: | ---: |
| preview | 2113125726450458625 | PREVIEW_READY | 58.675 | 381195/381195 |
| replay | 2113125980985991169 | COMPLETED | 171.441 | 381195/381195 |

[A5真实复核回执](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bound-history-read/recheck-state.json)；[A5原值核验](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bound-history-read/verify-p31-replay.json)；[A5最终保护](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/final-proof-a6.json)。

## A6正式范围查询消排序

A6共享正式范围查询保持同一选中Run的task_id供分页复用，原筛选、投影、排序及LIMIT500不变，仅增加等价task_id条件并固定既有源键索引连接顺序；EXPLAIN为task常量→binding源键range→project主键，无filesort，无新增索引或DDL。最新70项检查；真实预览57.983秒、重放59.684秒，两Run均381195读取/不变且0业务及绑定变化，152条原值保留。A5历史预览58.675秒/重放171.441秒及全部A3/A4回执继续保留；A6不提供新包首次插入基准。

| A6阶段 | Run | 状态 | 秒 | 读取/不变 |
| --- | --- | --- | ---: | ---: |
| preview | 2113129480075608065 | PREVIEW_READY | 57.983 | 381195/381195 |
| replay | 2113129731876454402 | COMPLETED | 59.684 | 381195/381195 |

[A6复核](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/published-scope-index/recheck-state.json)；[A6原值核验](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/published-scope-index/verify-p31-replay.json)；[消排序EXPLAIN](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/bound-history-read/published-scope-candidate4-explain.json)。

## P18离线诊断

82676条桥接原行归并为82381条业务关系：80534条精确匹配正式合同，1839条缺同公司正式合同，另8条原公司为NULL。1839条缺合同关系对应1826个公司/合同唯一键：1386个只有附加字段来源，426个三条P15输入都没有，14个只有其他公司ERP来源。按关系条数分别为1397、428、14。

这些是来源覆盖事实，不是要求清洗数据或发明新的业务前置。如完整原关系也要求落正式关系表，既有contract_id NOT NULL与租户外键是当前承载限制；不能共享-1占位，也不能跨公司补合同。已有项目主档和树均齐备，不应列为缺项。

原公司NULL的8条关系原值如下，完整来源键和时间详见P18-P31-raw-source-formal-prerequisites-20261022.json：

| 原项目ID | 原项目编码 | 合同号 | 合同来源ID | 成员来源ID | 原公司 |
| --- | --- | --- | --- | --- | --- |
| 48275 | 16202020093001N-1 | 31020210107A14 | 50747 | 49844 | NULL |
| 48276 | 16202221060805N-1 | 31020210622A30 | 50748 | 49845 | NULL |
| 48277 | 16202221032704N-2 | 31020210803A12 | 50749 | 49846 | NULL |
| 48278 | 16202620070207N-1 | 31020211021A04 | 50750 | 49847 | NULL |
| 48279 | 16200221082304N-1 | 31020211027A12 | 50751 | 49848 | NULL |
| 48280 | 16202421110402N-1 | 31020211112A11 | 50752 | 49849 | NULL |
| 48281 | 16202421070802N-1 | 31020220311A16 | 50753 | 49850 | NULL |
| 48282 | 16202421120802N-1 | 31020220314A13 | 50754 | 49851 | NULL |

P18-P31-raw-source-formal-prerequisites-20261022.json保留1839条缺合同关系映射及1826个唯一键覆盖概要，不复制三条P15全量输入或全部来源快照。

## P31原值证据

当前发布范围79442个来源别名、79429个项目，成员原行381195条。倒置日期152条，其中同日77条、跨日75条；角色10为2条、30为5条、60为145条。152条原ID、项目ID、角色、effectiveFrom/effectiveTo及createTime/updateTime均已完整保存在P18-P31-raw-source-formal-prerequisites-20261022.json，未改原值。

源四个日期字段均为datetime(0)，范围内不存在毫秒值，不能将本批问题称为精度错误。其余381043行区间正常，这仅表示区间检查结果，不等于其他校验或实际导入通过。

正式CHECK为chk_proj_member_dates，表达式为effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from。本轮已按唯一明确批准执行DROP CHECK；执行凭据见机器结果，实际同步完成状态见真实回执。PROJ其他业务入口及当前授权检查保持，不能由该历史承接调整推断为新增当前权限。

## 正式元数据与授权边界

重载前保护快照中59284为UP，监听PID49940（仅历史基线，当前PID见最终证明）；79429项目root_id均为自身，最新ACTIVE树版本与自路径均齐备，缺项为0。独立P16已有完成回执及79442条来源别名绑定。目标成员表与P31绑定在只读审计时均为0。

当前actor1没有有效公司范围。001/002/003有效范围覆盖用户数597/395/0，其中启用账号594/395/0；能同时覆盖三公司的现有启用账号为0。不能冒用其他账号。super_admin功能权限回退不等于公司数据范围；这些仅是元数据核验，未模拟实际管理API授权。

保护快照原任务、原运行、原绑定计数均为0，记录了10条Flyway历史和新24项配置；P22/P33非preview请求0、activeRuns0。P22/P33实际继续hold，B不执行，不做迁移，不动原任务和59191。


## P18具体缺配样例与正式依据

| 原项目ID / 编码 | 原公司 / 合同号 | 原合同:成员来源键 | 精确未匹配原因 |
| --- | --- | --- | --- |
| 4551 / 31220120216E-0 | 001 / 31220120216E | 4552:4551 | 仅现有发运/应收附加字段来源，没有同公司ERP合同主输入 |
| 9099 / 16202215080702-0 | 001 / 31020150813K | 9142:9098 | 三条已批准P15输入均没有此合同 |
| 26805 / 16200918052401N-1 | 001 / 33020190318A01 | 26900:26388 | ERP仅有公司002的订单Z33，不能跨公司匹配 |

这些记录保持原样。既有正式关系表的contract_id为BIGINT NOT NULL，并受同租户合同外键约束；项目主档、树和P16范围已齐备。承接缺失合同ID的最小结构候选是允许该引用列为NULL，保留已匹配合同的现有外键，不新增虚构合同或共享-1记录；这属于正式模型/DDL变化，目前只列方案，未实现或执行。候选DDL为：

```sql
-- P18候选，仅供审阅，未授权执行；不在本轮P31单CHECK例外内。
ALTER TABLE `npdms`.`com_project_contract_relation`
  MODIFY COLUMN `contract_id` BIGINT NULL COMMENT '合同ID';
```

单列可空只是结构候选，不代表现有任务可以直接执行。若正式批准承接未解析关系，同步仍须按原项目/合同号业务键及独立来源绑定区分它们，不能把同一项目下多份未知合同按(project_id,NULL)归并；已有绑定按其目标ID精确回查。原合同号及原表定位继续以独立来源绑定追溯。本轮没有实施这些待明确的承载与匹配调整。

当前公司授权有正式依据，不是旧试点白名单：[ACCEPTED ADR-0038](../../decisions/0038-commerce-contract-administrator-company-scope.md)明确合同关系写入使用SYSTEM当前公司范围，空范围写操作拒绝、缺失公司不可推断。新P18代码对应createParameters的当前范围读取、requiredPermissions的contract:relate，以及syncDataFillData的公司精确校验；未使用旧试点Owner或固定项目白名单。查询原文与具体范围统计见配套SQL及JSON。

## 最小处理方案与真实执行边界

| 事项 | 当前正式限制 | 最小处理 |
| --- | --- | --- |
| P18当前执行上下文 | actor1当前公司范围0，全库003有效范围0 | 由正式授权流程提供可用执行上下文；不由同步任务创建授权或冒用账号，当前不重复已知失败的实际请求 |
| P18未匹配原关系 | contract_id为NOT NULL并有同租户合同外键，不能将未知ID写NULL或共享-1 | 原公司、合同号和来源身份保留，不改源、不跨公司匹配、不扩展P15补造合同。若要把未解析关系也落到该正式表，需明确允许缺合同ID的正式承载；当前不自动扩写关系模型或DDL，也不把缺配当成新的来源清洗要求 |
| P31原始日期 | 仅chk_proj_member_dates例外已明确批准并执行 | P31已按用户明确批准仅在远端npdms移除chk_proj_member_dates，真实执行与重放均COMPLETED；381195条成员及独立绑定承接，152条倒置原日期、审计时间和角色逐项回读一致，重放计数与绑定指纹保持。 |

P31已执行的唯一批准DDL（取消整张表此日期CHECK，其他约束保持）：

```sql
ALTER TABLE `npdms`.`proj_project_member_assignment`
  DROP CHECK `chk_proj_member_dates`;
```

没有用改日期、空值占位、过滤来源、自动建合同或放宽授权去使任务通过。P22/P33实际写入及重放继续暂缓，B稿不执行。无Flyway或其他迁移，不改原任务、原始表或59191。运行重载仅59284，且均在活动Run为空的边界。

## 保护与证据

最终保护核查见机器结果：原任务、原运行和原绑定计数保持；Flyway历史、其他新任务配置及正式结构变化按实际授权逐项核验；P31批次500→1000及其审计为唯一明确记录的配置差异，见批次调整凭据；P22/P33实际请求0、无活动Run。完整源码/运行包一致性、登录及健康状态以最终证明为准，不把本轮定向验证写成全仓构建或浏览器UI验收。

[逐项原始记录、覆盖分类与最终机器回执](P18-P31-raw-source-formal-prerequisites-20261022.json)；[来源和正式目标查询SQL](P18-P31-raw-source-formal-prerequisites-20261022.sql)。

[70项检查及隔离证据](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/published-scope-index/green-test-proof.json)；[实际回执](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/preview-runs.json)；[正式保护证明](D:/CodexTaskArtifacts/project-detail-followup/p18-p31-formal-prerequisites-20261022/final-proof-a6.json)。
