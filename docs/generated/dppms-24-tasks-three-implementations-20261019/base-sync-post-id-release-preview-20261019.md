# 验证库 ID 修正后：P16 来源映射修复与真实预览

核验时间：2026-10-19T16:53:08.692582+08:00。分支 `fix/dppms-migration-consolidation-20261012`；代码保存点 `6a56c3f2f`。

新虚基类24项目前 **17 PREVIEW_READY、7 FAILED**。本轮仅重跑6项受影响的新任务，共7次预览，全部到终态；P16先遇到映射缺陷，修复后再次预览成功。P30仍是0行空预览。其余任务沿用下表明确列出的已有回执；这不表示已完成真实同步，也不把预览计划数当作实际写入数。

## 本轮完成

- P22 Run `2112100511387959297`：读取343,858条，计划插入340,882条，2,976条零数量按既有裁决登记待处理；14,293条空部门业务码原样保留并登记。部门和订单行ID仍先为-1；真实目标的待定引用结构校验保持生效。
- P19 Run `2112100784705585154`：186,118条全量真实预览就绪；四项拓展属性继续暂不承接。
- P16 Run `2112103755224866818`：读取158,871条，包括79,429个主档与79,442条来源身份；计划新增主档79,429条。正式执行仍需独立绑定能承载多来源及正式父输入，预览未创建项目、最终客户、树或绑定。
- P16原实现依赖已批准的Readonly回退，但 `mapUnderscoreToCamelCase=true` 没有把原列接到Java属性 `columno12_readonly`，真实运行报 `P16_IMPLEMENTATION_UNMAPPED`。只在新P16来源XML中增加显式resultMap；原SQL文字、值域转换、空值回退规则及其他任务实现不变。依据为[已决规则](../../decisions/open-questions.md:235)。
- 新增原生MySQL临时表回归，旧映射复现 `expected 1 but was null`；修复后该用例与2项Mapper装配检查全部通过。真实P16回执进一步验证完整来源范围已越过该缺陷。

## 单独授权的环境数据修正

仅 `127.0.0.1:24306/npdms_reverify_20261014`：原1001至1040的40个既存项目改为900000000001001至900000000001040，关联技术引用和树路径同步调整，48表共1,570行。每一行的全部字段均与事先加密冻结的预期结果一致，业务状态、版本、历史载荷和审计时间保留。

用户明确允许一次维护中的触发器例外。首次数据事务因直接赋值MySQL生成列 `primary_project_id` 被拦截，已完整回滚并恢复11个UPDATE守卫；随后仅移除该生成列的直接赋值，由MySQL随project_id计算，受影响范围及预期结果不变，重试提交成功。33个守卫的定义、SQL mode、字符集和排序规则均恢复一致，22个INSERT/DELETE守卫连创建时间也未变化。没有关闭外键。

修正SQL、维护执行器和加密备份全部保留在工作树外，未加入产品同步任务、正式迁移文件或自动执行入口。输入计划的 `executed=false` 是执行前冻结记录；实际提交事实以[data-correction-proof.json](D:/CodexTaskArtifacts/project-detail-followup/project-id-release-data-only-20261019/data-correction-proof.json)为准。

## 七项未闭合任务及可执行前置

| 任务 | 当前真实终态与缺项 | 后续处理 |
|---|---|---|
| P10 | `DUPLICATE_CUSTOMER_CODE`；既有来源核查22组同码不同账号、35条缺名称及4条名称超长；ERP列已由先前单列授权补齐。 | CRM来源Owner提供同码账号及名称的正式处置；不自行合并、改码或跳过。 |
| P14 | `P14_DUPLICATE_BUSINESS_KEY_CONFLICT`；既有证据169组订单头及10组订单行载荷冲突，40条原行缺订单号。 | ERP来源Owner确认正式版本及缺失订单号；不按MAX任选冲突载荷。 |
| P15 | `P15_TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key`；全量条码4,283,326条同时超出当前100万任务/结构上限。 | 需要正式列和容量定义到位，并在获得真实同步授权后形成独立P24正式输入；本次触发器例外不授权这些DDL。 |
| P18 | `P18_PROJECT_COMPANY_NOT_AUTHORIZED`；同一来源桥接范围82,381个业务关系中，8个项目的源公司码及正式公司码同时缺失。 | 来源Owner补齐这8个项目的公司业务码，再按已有转换及当前授权范围匹配；不补造公司或扩大授权。项目48275至48282的清单见证据。 |
| P31 | `P31_CANONICAL_FINAL_CUSTOMER_REQUIRED:10032`；原项目10032的源最终客户名称存在，但正式项目及对应FINAL_CUSTOMER关系尚未产生。 | 依赖已审核P16的正式输入；目前禁止真实同步，因此保持阻断，不为了预览补造关系。 |
| P33 | `P33_SOURCE_CAPACITY`；批准全量1,063,717行，现有任务及数据库CHECK最多1,000,000。 | 需要正式容量配置和结构前置；保留完整范围，不裁剪记录。 |
| P35 | `源数据超过本次读取上限`；批准范围16,821个软件批次、1,569,757条版本观察，注册值及库CHECK上限均1,000,000；八项拓展属性继续暂缓。 | 需要正式容量定义到位后重跑；不自动变更约束、裁剪来源或恢复拓展属性写入。 |

P10/P14/P15/P33在本轮未受ID修正或P16映射变更影响，沿用既有有效回执及来源核查。P18、P31、P35是本轮重跑后新定位到的真实前置；没有把这些失败伪装成全部预览通过。

## 24项最新有效回执

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
| P15 | 2112051493786001409 | FAILED | 0 | 沿用有效回执 |
| P16 | 2112103755224866818 | PREVIEW_READY | 158,871 | 本轮重跑 |
| P18 | 2112100738312388610 | FAILED | 0 | 本轮重跑 |
| P19 | 2112100784705585154 | PREVIEW_READY | 186,118 | 本轮重跑 |
| P20 | 2112059695747514370 | PREVIEW_READY | 7,818 | 沿用有效回执 |
| P21 | 2112053188930744321 | PREVIEW_READY | 1,190 | 沿用有效回执 |
| P22 | 2112100511387959297 | PREVIEW_READY | 343,858 | 本轮重跑 |
| P23 | 2111466972774600705 | PREVIEW_READY | 35,501 | 沿用有效回执 |
| P24 | 2112057587128287234 | PREVIEW_READY | 81,547 | 沿用有效回执 |
| P25 | 2111480444740603906 | PREVIEW_READY | 462,213 | 沿用有效回执 |
| P30 | 2111481771424153602 | PREVIEW_READY | 0 | 沿用有效回执 |
| P31 | 2112100825604243458 | FAILED | 0 | 本轮重跑 |
| P33 | 2112057626944815105 | FAILED | 0 | 沿用有效回执 |
| P34 | 2111480509467103233 | PREVIEW_READY | 165,801 | 沿用有效回执 |
| P35 | 2112100834340978689 | FAILED | 0 | 本轮重跑 |
| P37 | 2111480525451595777 | PREVIEW_READY | 475,468 | 沿用有效回执 |

## 验证与隔离证据

- [本阶段24项结构化回执](base-sync-post-id-release-preview-20261019.json)；A/B两种实现及其历史验证记录保留，本轮不运行双线对照。
- [六项预览及终态证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-after-project-id-release-20261019/preview-proof-P22,P16,P18,P19,P31,P35.json)；[P16修复后预览](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p16-readonly-mapping-20261019/P16-preview.json)。
- [P18八项公司缺项](D:/CodexTaskArtifacts/project-detail-followup/base-sync-after-project-id-release-20261019/P18-company-source-missing.json)；[P31源最终客户存在性](D:/CodexTaskArtifacts/project-detail-followup/base-sync-after-project-id-release-20261019/P31-first-final-customer-source.json)；[P35批准范围全量计数](D:/CodexTaskArtifacts/project-detail-followup/base-sync-after-project-id-release-20261019/P35-full-source-capacity.json)。
- [原生失败用例日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p16-readonly-mapping-20261019/p16-readonly-20261019T164242.log)；[3项检查通过日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p16-readonly-mapping-20261019/p16-readonly-20261019T164416.log)；[候选单资源变更清单](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p16-readonly-mapping-20261019/candidate-manifest.json)。
- [最终保护证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p16-readonly-mapping-20261019/final-proof.json)：获准修正的1,570行全部字段仍符合冻结预期；原int_sync_task/run/binding分别46/209/211行逐字段不变；测试库6,734个项目身份对照未变；33个触发器定义完整；修正后的180项计数/最大ID/原任务版本观察保持一致。独立绑定0、非预览运行0，任务无占用，Flyway仍V473。
- 本轮仅一次59284重载，当前PID19892；自动迁移、Flowable结构更新、Quartz/Batch自动执行及事件绑定均关闭，迁移容器退出。除单独批准的移除/恢复UPDATE触发器外，没有其他DDL，没有Flyway、真实同步或59191操作。真实验收使用正常登录的API及数据库终态，新套件没有专属UI。

既有verification-matrix及各阶段SQL附件保留当时证据；C路线的当前终态以上述本阶段24项回执为准。字段查询、业务键、INSERT及后处理SQL语义未因本次环境修正改变，P16新增显式列映射见当前来源XML。
