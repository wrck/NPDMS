# 新P31原日期保留：全量真实预览完成

核验时间：2026-10-19T18:09:05.083868+08:00。工作树和分支延续既定`project-detail-followup` / `fix/dppms-migration-consolidation-20261012`。

**24项新虚基类任务当前19 PREVIEW_READY、5 FAILED。** 新P31 Run `2112123456126668802`完整读取381195条，计划插入381195条，未写入业务或来源绑定。152条倒置日期按本轮明确裁决保留并逐条登记，不能把预览就绪理解为日期已修正或可以正式写入。P30仍是0行空预览。

## 本次处理

- 用户明确选择“仅预览保留原值并登记，继续全量预览（建议）”。只调整新BASE_P31的预览区间处理，原字段、15列源查询、34列目标INSERT、四类角色归属、项目及当前权限保护保持。角色60仍为未确认PROJ成员；原任务、原CUS联系人和旧绑定保留。

- 倒置区间在预览中原样规划，并按原ID、原项目、起止日期逐条写入本次任务日志；回执汇总`P31_SOURCE_INTERVAL_PENDING:152`。现有问题摘要容量1000保持，逐条明细不挤入摘要或静默丢失，未新增表列。

- 152条全部日志与冻结来源逐项核对，原角色和日期再从原库只读复查一致：角色60有145条、项目经理5条、销售2条；77条同日、75条结束日期更早。实际写入仍会被倒置区间校验拒绝，目标`chk_proj_member_dates`及审计精度规则没有改变。

- 先执行失败用例复现旧预览阻断，随后28项定向检查通过（15项隔离、2项Mapper装配、11项原生历史任务检查）。新增用例以160条倒置记录验证逐条日志、完整计数及预览不写入，并保留真实写入失败回滚、审计精度、历史指纹、人工变化、旧联系人保护。

- 仅59284一次已授权重载，候选仅5个新P31类文件有差异；SQL/XML及其他任务未变。本次没有DDL、迁移或真实同步。此前获准的包件列和三项容量补丁已在[上一阶段](base-sync-seven-blockers-followup-20261019.md)单独留证，不再次执行。

## 剩余五项可执行前置

| 任务 | 已核实缺项 | 处理条件 |
|---|---|---|
| P10 | CRM59601条，22组44条同码不同AccountId和名称，35条缺名称，4条名称长度130/131/131/143。83个问题身份已逐项列出。 | CRM Owner按原AccountId和客户业务编码提供同码归属、缺名称和超长名称的正式处理；不自动选行、改码、截断或套用项目名称回退。 |
| P14 | 91572原表头归并91239键，169键有410条执行单展开冲突；380605原行归并379522键，10个冲突键涉及40条缺订单号原行。 | 按Q023提供每组多执行单的正式单值/多关系承接裁决及40条原行的订单身份依据；不以MAX原ID、任意订单行或拼接多个MAX来消歧。SO078894的旧任务目标冲突只保留历史，不操作原目标或绑定。 |
| P15 | 已获准补列及容量500万，4283326条原条码通过容量检查。新套件无BASE_P24非预览COMPLETED，com_contract_receivable、com_shipment_contract_reference及com_contract均无正式输入；ERP正式订单仅1条SEED。 | 保留前置；完成可用正式P14/P24输入后才能验证本地合同归并阶段。当前禁止真实同步，不能生成假COMPLETED、将预览当正式输入、跳过第五阶段或复用原任务数据来宣布全量通过。 |
| P18 | 82381个业务关系中8个项目48275至48282的源公司码与正式公司码同时缺失；原合同50747至50754、成员49844至49851逐项列明。正式合同仍为0。 | 提供这8个项目可核实的公司业务码及对应正式合同输入，再按已决01→001等转换与当前授权匹配。不按合同猜公司，不用历史绑定产生授权。实际执行另受多来源目标索引前置约束，本轮不改索引。 |
| P33 | 完整来源1063717条已越过容量。SQL筛查26002条NULL/普通空格空地址，6111条超过安装列255字符，最长571；29条含串货字段，已批准暂缓。原SN共1046853个，验证库无DPPMS来源设备或新套件正式设备绑定。 | 空地址须由既有安装规则负责人明确承接含义；超长地址须明确正式列/API的完整承接，不能截断或改写成JSON固定字段。串货保持本轮不迁，不扩张为转移功能；不擅自排除整条来源。原始设备、当前项目选择及位置Owner输入齐备后按原规则复验。 |

上述五项未因P31预览规则改变而重跑或放宽。P14多执行单、P33空/超长地址等既有未决保持；已裁决的串货暂缓、项目原ID、原用户名和拓展属性暂缓规则不重复询问。P15/P18/P33所需正式输入不得用预览、旧任务数据或假完成回执替代。

## 24项最新有效回执

| 任务 | Run | 终态 | read | 本阶段 |
|---|---|---|---:|---|
| P01 | 2111504172950122498 | PREVIEW_READY | 208 | 沿用有效回执 |
| P04 | 2111517837875126273 | PREVIEW_READY | 12,632 | 沿用有效回执 |
| P08 | 2112057345712537601 | PREVIEW_READY | 612 | 沿用有效回执 |
| P10 | 2112067330504867842 | FAILED | 0 | 沿用有效回执 |
| P11 | 2111436809982627841 | PREVIEW_READY | 8,443 | 沿用有效回执 |
| P12 | 2111436813732335618 | PREVIEW_READY | 34,976 | 沿用有效回执 |
| P13 | 2111515526163517442 | PREVIEW_READY | 99,895 | 沿用有效回执 |
| P14 | 2112051434155581442 | FAILED | 0 | 沿用有效回执 |
| P15 | 2112116695495811073 | FAILED | 0 | 沿用有效回执 |
| P16 | 2112103755224866818 | PREVIEW_READY | 158,871 | 沿用有效回执 |
| P18 | 2112100738312388610 | FAILED | 0 | 沿用有效回执 |
| P19 | 2112100784705585154 | PREVIEW_READY | 186,118 | 沿用有效回执 |
| P20 | 2112059695747514370 | PREVIEW_READY | 7,818 | 沿用有效回执 |
| P21 | 2112053188930744321 | PREVIEW_READY | 1,190 | 沿用有效回执 |
| P22 | 2112100511387959297 | PREVIEW_READY | 343,858 | 沿用有效回执 |
| P23 | 2111466972774600705 | PREVIEW_READY | 35,501 | 沿用有效回执 |
| P24 | 2112057587128287234 | PREVIEW_READY | 81,547 | 沿用有效回执 |
| P25 | 2111480444740603906 | PREVIEW_READY | 462,213 | 沿用有效回执 |
| P30 | 2111481771424153602 | PREVIEW_READY | 0 | 沿用有效回执 |
| P31 | 2112123456126668802 | PREVIEW_READY | 381,195 | 新P31重跑 |
| P33 | 2112116980817534977 | FAILED | 0 | 沿用有效回执 |
| P34 | 2111480509467103233 | PREVIEW_READY | 165,801 | 沿用有效回执 |
| P35 | 2112116707562823682 | PREVIEW_READY | 1,586,578 | 沿用有效回执 |
| P37 | 2111480525451595777 | PREVIEW_READY | 475,468 | 沿用有效回执 |

## 证据与保护边界

- [真实API及数据库终态](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/P31-preview.json)；[完整152条原ID/项目/角色/日期及逐条登记核验](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/registered-original-intervals.json)；[本次逐条问题日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/runtime-sanitized.log)。当前原表查询及INSERT继续见[P31当前SQL](P31/C-base-sync.sql)、[实际映射](P31/current-base-field-bindings.md)和[前后置](P31/processing.md)。没有增加业务表关联UPDATE。

- [修复前失败日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/p31-interval-20261019T180307.log)；[28项检查通过日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/p31-interval-20261019T180352.log)；[仅P31候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/candidate-manifest.json)；[预览基线对照](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/preview-proof-P31.json)。

- [最终逐字段保护证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p31-original-interval-preview-20261019/final-proof.json)：原int_sync_task/run/binding仍46/209/211行逐字段不变，获准ID修正的1570行全部字段符合冻结预期；33个触发器定义完整，22个INSERT/DELETE守卫含创建时间保持；测试库6734个项目的六列身份对照未变，本次没有向测试库写入。180项业务计数/最大ID/原任务版本观察保持。

- 独立绑定0、非预览运行0、占用任务0，Flyway仍V473；59284 PID 38508。自动迁移、Flowable结构更新、Quartz/Batch自动执行与事件绑定全部关闭，迁移容器退出，59191未操作。旧A/B证据及上一阶段18/6结果保持原时间，新C当前终态以上述JSON为准。

生成器：工作树外`base-sync-p31-original-interval-preview-20261019/render-followup.py`。只整理已完成回执，不是同步或DDL入口。
