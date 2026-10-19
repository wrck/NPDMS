# 剩余七项复核：新P31纠正、容量补丁及真实预览

核验时间：2026-10-19T17:55:45.626704+08:00。工作树`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`，分支`fix/dppms-migration-consolidation-20261012`。

**新虚基类24项为18 PREVIEW_READY、6 FAILED。** 本轮对新P31/P15/P35/P33各执行一次真实API预览，均已到终态；P35全量1586578条通过。P30仍为0行空预览。其余有效回执明确沿用，未重跑原任务或双线对照。预览计划不等于正式同步或业务验收完成。

## 本轮修复和已执行例外

- 新P31漏用了已决历史人员规则：原角色60应为项目成员历史，不能继续依赖最终客户相关方。已仅修改`P31MemberContactSyncTask`及新目标XML，10/20/30/60全部进入`proj_project_member_assignment`，60保留原角色值，授权标志始终false。姓名、编码、电话、邮箱、来源、区间及审计按原规则承接，不创建账号、授权、主责或CUS联系人。新套件如已有自身CONTACT_HISTORY绑定仍拒绝自动搬移；旧任务与旧联系人不操作。

- 原生临时表先复现旧CUS前置失败；修复后26项检查通过（15项隔离、2项Mapper装配、9项历史任务MySQL），覆盖角色60字段保存、无当前授权、重放/人工变化保护、旧绑定不改写及失败事务回滚。真实P31已越过旧前置，当前152条源日期问题保留为失败。

- 用户单独批准并已执行：仅`npdms_reverify_20261014`补`com_shipment_package.shipment_contract_source_key VARCHAR(128) NULL`；任务CHECK上限按既有V484为500万；仅新P15/P33/P35的max_rows改500万。没有运行Flyway、其他迁移或真实同步。结构、任务原值及维护SQL均留在工作树外，未写入产品自动处理。

- 新P35 Run `2112116707562823682`：16821个批次＋1569757条版本观察，read及计划插入均为1586578，PREVIEW_READY；原项目ID及八项暂缓扩展规则保持，业务写入0。

## 七项逐项结果和可执行前置

| 任务 | 最新终态 / 归因 | 已核实缺项及下一步 |
|---|---|---|
| P10 | FAILED / SOURCE_AUTHORITY_REQUIRED | CRM59601条，22组44条同码不同AccountId和名称，35条缺名称，4条名称长度130/131/131/143。83个问题身份已逐项列出。 CRM Owner按原AccountId和客户业务编码提供同码归属、缺名称和超长名称的正式处理；不自动选行、改码、截断或套用项目名称回退。 |
| P14 | FAILED / BLOCKED_BY_SPEC | 91572原表头归并91239键，169键有410条执行单展开冲突；380605原行归并379522键，10个冲突键涉及40条缺订单号原行。 按Q023提供每组多执行单的正式单值/多关系承接裁决及40条原行的订单身份依据；不以MAX原ID、任意订单行或拼接多个MAX来消歧。SO078894的旧任务目标冲突只保留历史，不操作原目标或绑定。 |
| P15 | FAILED / FORMAL_INPUT_REQUIRED | 已获准补列及容量500万，4283326条原条码通过容量检查。新套件无BASE_P24非预览COMPLETED，com_contract_receivable、com_shipment_contract_reference及com_contract均无正式输入；ERP正式订单仅1条SEED。 保留前置；完成可用正式P14/P24输入后才能验证本地合同归并阶段。当前禁止真实同步，不能生成假COMPLETED、将预览当正式输入、跳过第五阶段或复用原任务数据来宣布全量通过。 |
| P18 | FAILED / SOURCE_COMPANY_AND_FORMAL_CONTRACT_REQUIRED | 82381个业务关系中8个项目48275至48282的源公司码与正式公司码同时缺失；原合同50747至50754、成员49844至49851逐项列明。正式合同仍为0。 提供这8个项目可核实的公司业务码及对应正式合同输入，再按已决01→001等转换与当前授权匹配。不按合同猜公司，不用历史绑定产生授权。实际执行另受多来源目标索引前置约束，本轮不改索引。 |
| P31 | FAILED / IMPLEMENTATION_CORRECTED_DATE_POLICY_PENDING | 已纠正新任务错误CUS联系人依赖，角色60全部改为PROJ未确认成员。381195条原人员历史有152条区间倒置：60角色145条、项目经理5条、销售2条；77条同日、75条结束日期更早，时间精度均为秒。 原日期及CHECK保持。新提出仅预览保留原日期、逐条登记并继续的选项仍待回复；尚未实施该预览例外。真实写入继续拒绝倒置区间。原任务/9条历史CUS联系人不迁移、不改绑。 |
| P33 | FAILED / BLOCKED_BY_SPEC_AND_FORMAL_DEVICE_REQUIRED | 完整来源1063717条已越过容量。SQL筛查26002条NULL/普通空格空地址，6111条超过安装列255字符，最长571；29条含串货字段，已批准暂缓。原SN共1046853个，验证库无DPPMS来源设备或新套件正式设备绑定。 空地址须由既有安装规则负责人明确承接含义；超长地址须明确正式列/API的完整承接，不能截断或改写成JSON固定字段。串货保持本轮不迁，不扩张为转移功能；不擅自排除整条来源。原始设备、当前项目选择及位置Owner输入齐备后按原规则复验。 |
| P35 | PREVIEW_READY / 容量已修复 | 全量1586578条真实预览通过，未真实写入。 |

P31的新日期问题已单独询问，回复前维持现有校验；这不重新打开角色60归属或其他已裁决问题。P33串货已暂缓，不因发现29条记录而要求扩展迁移范围。P15最后的本地合同查询依赖正式商务数据，多步骤预览不会产生前序业务行；不能把未验证阶段称为完成。

## 24项当前回执

| 任务 | Run | 终态 | read | 本轮处理 |
|---|---|---|---:|---|
| P01 | 2111504172950122498 | PREVIEW_READY | 208 | 沿用有效回执 |
| P04 | 2111517837875126273 | PREVIEW_READY | 12,632 | 沿用有效回执 |
| P08 | 2112057345712537601 | PREVIEW_READY | 612 | 沿用有效回执 |
| P10 | 2112067330504867842 | FAILED | 0 | 沿用有效回执 |
| P11 | 2111436809982627841 | PREVIEW_READY | 8,443 | 沿用有效回执 |
| P12 | 2111436813732335618 | PREVIEW_READY | 34,976 | 沿用有效回执 |
| P13 | 2111515526163517442 | PREVIEW_READY | 99,895 | 沿用有效回执 |
| P14 | 2112051434155581442 | FAILED | 0 | 沿用有效回执 |
| P15 | 2112116695495811073 | FAILED | 0 | 本轮重跑 |
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
| P31 | 2112116680425676801 | FAILED | 0 | 本轮重跑 |
| P33 | 2112116980817534977 | FAILED | 0 | 本轮重跑 |
| P34 | 2111480509467103233 | PREVIEW_READY | 165,801 | 沿用有效回执 |
| P35 | 2112116707562823682 | PREVIEW_READY | 1,586,578 | 本轮重跑 |
| P37 | 2111480525451595777 | PREVIEW_READY | 475,468 | 沿用有效回执 |

## 来源、映射和SQL

- [P10最新SQL Server源统计](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P10-current-source-quality.json)；[83个客户原身份及问题标志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P10-source-issue-index.json)；[只读定位SQL](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P10-source-issue-index.sql)。

- [P14当前源冲突计数](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P14-current-source-conflicts.json)；[原410条完整来源、旧目标历史证据](../2026-10-13-dppms-order-conflicting-originals.json)。Q023的多执行单处置仍未裁决；原任务目标冲突不作为新套件自动修复范围。

- [P18八项公司缺项原身份](D:/CodexTaskArtifacts/project-detail-followup/base-sync-after-project-id-release-20261019/P18-company-source-missing.json)；[P15/P18/P33当前正式输入及目标列](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/formal-input-prerequisites.json)。

- [P31全部152条倒置日期](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P31-original-interval-audit.json)；[按角色、同日与目标CHECK分类](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P31-interval-classification.json)。[P31当前SQL](P31/C-base-sync.sql)、[完整动态XML与34列参数](P31/C-base-sync-mappings.json)、[15列原查询与字段衔接](P31/current-base-field-bindings.md)、[业务键](P31/business-keys.md)及[前后置](P31/processing.md)已随本次新任务更新；A/B原件保留。

- [P33全量源基线分类及每项首20个原身份](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P33-source-baseline-audit.json)；[6111条超长地址长度分布](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P33-address-capacity-details.json)；[逐项只读定位SQL](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P33-actionable-source-queries.sql)。其他任务字段/SQL未变，原阶段附件保持其历史日期，当前C终态以本报告JSON为准。

## 验证与隔离

- [四项预览及180项基线对照](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/preview-proof-P31,P15,P35,P33.json)；[两条ALTER和三项容量实际执行证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/capacity-and-package-proof.json)；[候选包仅新P31类族及目标XML变化](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/candidate-manifest.json)。

- [修复前失败日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/seven-blockers-20261019T173250.log)；[26项定向检查通过日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/seven-blockers-20261019T173529.log)；[最终逐字段保护证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/final-proof.json)。原任务/运行/绑定分别46/209/211行逐字段不变；原ID修正的1570行全部字段符合冻结预期；33个触发器定义完整，22个INSERT/DELETE触发器含创建时间不变。测试库6734个项目的六列身份对照保持；没有对测试库执行写入。

- 180项业务计数/最大ID/原任务版本观察与修正后基线一致。独立绑定0、独立非预览运行0、任务占用0；Flyway仍V473。仅59284一次授权重载，PID29804；自动迁移、Flowable结构更新、Quartz/Batch自动执行和事件绑定关闭，迁移容器退出，59191未操作。

本报告由工作树外`base-sync-seven-blockers-followup-20261019/render-followup.py`读取上述回执和当前新P31 XML生成；不是新的业务或DDL执行入口。
