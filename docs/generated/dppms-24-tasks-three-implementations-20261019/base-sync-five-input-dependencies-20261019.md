# 剩余五项：正式输入依赖与真实预览核查

核验时间：2026-10-19T19:38:55.532829+08:00。工作树与分支保持既定`project-detail-followup` / `fix/dppms-migration-consolidation-20261012`。

**当前24项为19 PREVIEW_READY、5 FAILED。** 本轮修复P15/P18/P33的来源预览过早依赖正式输入，并核实实际失败原因。P15按追加裁决过滤两个原字段同时为空的1行后再次真实运行；P18/P33保留授权及原地址规则，不能将依赖延后写成预览通过。P10/P14沿用本日有效来源核查与失败回执。P30仍为0行空预览。

## 实现与真实写入边界

- P15：预览先读四类原始历史，合同阶段用三份单表查询，每份仅执行一次；保留原字段优先级、原更新时间及来源键排序和客户冲突规则。实际执行仍要求独立BASE_P24非预览COMPLETED，并读取事务内正式输入。P24实际81547条均保留原CRM来源，P15本地归并误筛DPPMS已纠正；同租户、同连接、独立P24绑定限定保持。

- P18：公司码用于当前范围和精确合同查找，不是关系表新增字段。仅预览可登记缺公司/正式合同，不伪造合同ID、目标行或插入/未变数；非空未授权公司仍拒绝，既有来源绑定和关系保护保持。真实写入的非空合同ID、同租户外键及唯一约束不变。

- P33：仅预览可登记缺正式设备并保留SN，不创建设备ID或虚假插入数；正式项目尚未存在时延后当前设备选择。既存项目继续核验当前选择。实际写入仍要求正式项目、设备、当前授权及位置Owner；原日期、地址容量、已有安装历史和串货边界保持。

- 追加裁决：仅在新P15的fb_contract原表查询过滤`contract_id`与`contract_code`同时NULL/空白的记录，原120639行中排除1行，保留120638行；只缺一项仍保留。没有源库删除、占位键、改写原任务或将环境修复放入产品代码。

## P10

CRM源59601行：22组相同客户码对应44个不同AccountId/名称，另35行缺名称、4行名称超长（130/131/131/143），共83个问题身份。ERP目标列已按此前单列授权补齐，当前失败不是缺列。需要CRM来源Owner按原AccountId提供客户码归属及完整名称处理，不任意合并、改码、截断或套用项目名称回退。

[83项完整来源身份索引](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P10-source-issue-index.json)。本轮未重跑不受修改影响的任务，原失败Run `2112067330504867842`保持。

## P14

91572个原表头形成91239个业务键，169键有410条不同执行单展开内容；380605原订单行形成379522个键，10个冲突键涉及40条缺订单号的原行。已追溯旧`splitSoleAgentLendOrderInfo`，不能把这些内容当成简单时间版本。Q023仍未裁决每组多执行单的单值/多关系承载及缺失订单身份，不能取最大原ID或组合多个MAX消歧。需要ERP来源Owner逐键提供正式选取/承接依据及40条订单号。

[本日源冲突核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P14-current-source-conflicts.json)。原失败Run `2112051434155581442`保持；旧任务的SO078894目标冲突只作历史证据，不操作原目标或绑定。

## P15

原先的正式P24全局预览阻断已移到真实执行。第一轮Run `2112132307764813825`失败`P15_RAW_SOURCE_KEY_MISSING`，只读追溯证实是1条双空原记录；随后用户明确批准在查询中过滤。

过滤后最新Run `2112144970750287873`：**FAILED**，持久化read=0，回执`P15_CONTRACT_CUSTOMER_CONFLICT`。失败回执的read=0不代表源表为空，也不将独立来源统计冒充任务已成功读取/提交。各步骤的实际到达和读取数见下方运行日志证据。

四类来源原统计：发货合同120639（本次获准过滤1行后120638）、包裹156368、条码4283326、设备关系65046。发货关系原创建时间为VARCHAR(50)的7位小数日期，已修正JDBC直接转LocalDateTime失败；全量65046条低于毫秒的位均为0，Java精确解析并拒绝任何有效精度损失，原更新时间字符串原样保留。原保修年限无格式/范围问题、包裹无倒置保修区间、条码无NULL SN或负保修月数。

单表发货优化不改变原范围及字段；慢旧预览按已批准59284重载中断，并经既有`/previews/recover`接口保留FAILED/中断回执，不是成功预览，也未新写恢复功能。原查询两处关联均有索引，运行采样显示约195秒仅发送199787行，线程在等待源端读取；最终实际耗时见回执。三个合同来源查询实际只读返回ERP订单91565、发货字段组109578、应收字段组81540，形成81522个合同组，389组客户冲突。三份查询均无派生表嵌套。此389是当前C按已批准客户码归一化后的分组数，不替代旧A/B的历史问题数量。

此前已裁决统一客户码的31020220620A23、31120220620A37不在这389组内，不重复提请裁决；另两份仅限既有正式Owner补建的范围不自动扩张。需要Q024的其余合同客户归属依据逐组消歧；不取MIN客户、不补造正式主档。P14/P24正式输入仅在未来实际写入时仍为必要条件，本次不运行它们补齐数据。

[过滤后实际预览回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-lookup-preview-20261019/P15-preview.json)；[389组合同客户问题清单](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-empty-contract-filter-20261019/P15-contract-source-preview-audit.json)；[五阶段实际运行日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-lookup-preview-20261019/runtime-sanitized.log)。

### P15最终日志读取证明

| 步骤 | 完整读取数 |
|---|---:|
| ShipmentContract | 120638 |
| Package | 156368 |
| ShipmentEvent | 4283326 |
| DeviceRelationship | 65046 |
| 四段原始历史合计 | 4625378 |
| 合同归并组（未通过客户冲突校验） | 81522 |

四段完整通过；第五段实际读入91565原订单、109578发货字段组、81540应收字段组，归并81522合同并统计389组来源问题。最终目标校验按500组处理，不再发生栈溢出。总耗时327.901秒，Run保持FAILED而非PREVIEW_READY；业务写入及来源绑定均为0。

[逐阶段计数与真实终态证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-lookup-preview-20261019/P15-runtime-step-proof.json)。

## P18

最新Run `2112132107189002241`仍为`P18_PROJECT_COMPANY_NOT_AUTHORIZED`。四份原表查询分别为86780合同、82261项目组、85472成员桥、79429规范项目，归并82381个业务关系。当前有效公司范围属于操作者1，两条均为DPTECH-DEMO；源公司001的80374条、002的1804条、003的195条，合计82373条均不在当前范围。另8个原项目48275～48282的公司码缺失，合同原ID50747～50754、成员桥49844～49851。

此前仅列8条空公司码不能解释当前失败，本轮已纠正定位。需要覆盖001/002/003的正式授权上下文或有权主体完成正式授权，再核对8条公司来源。不能因系统管理员登录或本轮预览许可自动增授公司范围。正式合同当前为0；缺合同本身可先登记预览，真实写入仍受外键及多来源目标索引约束，不改索引或创建输入。

[当前授权和来源公司分布](D:/CodexTaskArtifacts/project-detail-followup/base-sync-five-input-dependencies-20261019/first-preview-failure-audit.json)；[真实预览回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-five-input-dependencies-20261019/P18-preview.json)。

## P33

最新Run `2112132166609707009`仍为`P33_SOURCE_BASELINE_INVALID`，本次在原地址规则处失败，尚未到正式设备登记分支。完整范围1063717条已通过500万容量；本日源SQL筛出26002条NULL/普通空格空地址、6111条超过安装列255字符（最长571）。29条串货字段已有暂缓决定，本轮不扩写转移功能或擅自排除整条来源。

需要安装规则负责人给出空地址承接含义及超长地址的正式完整承载方案；不能截断、填占位或改成固定业务JSON。原SN有1046853个不同值，验证库无DPPMS正式设备或独立设备绑定；设备/项目选择/位置Owner是未来真实写入前置，已从来源预览硬前置区分，不把缺设备列为当前地址失败的原因。

[原地址及串货来源核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-seven-blockers-followup-20261019/P33-source-baseline-audit.json)；[真实预览回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-five-input-dependencies-20261019/P33-preview.json)。

## 本轮验证与隔离

52项受影响检查通过（7类，0失败/错误/跳过）；未因普通文档变化重跑无关测试。先复现P15正式前置、P18缺合同、P33缺设备的旧预览阻断；追加过滤用例先失败，再核实仅双空被排除、只缺一项仍保留。原生MySQL验证CRM应收参与本套件归并、未绑定数据不参与，以及原表字段与正式输入归并结果一致；缺引用不造ID/计数、非空未授权公司拒绝、实际写入/历史保护等反向检查保留。

| 检查类 | tests | failures/errors/skipped |
|---|---:|---|
| BaseSyncIsolationTest | 15 | 0/0/0 |
| BaseSyncMapperWiringTest | 2 | 0/0/0 |
| OriginalIdentityPolicyTest | 7 | 0/0/0 |
| PrerequisiteCompletionMySqlTest | 3 | 0/0/0 |
| NewTaskPreconditionsTest | 3 | 0/0/0 |
| P15ContractInputMySqlTest | 8 | 0/0/0 |
| NewHistoryTaskMySqlTest | 14 | 0/0/0 |

仅59284按已有授权五次重载：首次候选仅新P15/P18/P33类族及P15 XML有30项差异；追加过滤候选仅P15来源XML有1项差异；单表发货候选为15项新P15类族/来源XML差异，日期转换候选为14项同范围差异；合同复合键查询候选为14项新P15类族/目标XML差异。完整合同归并后，绑定及目标查询按原batchSize拆批，避免将81522组业务键构成长SQL，客户冲突规则不改。目标定位改用等价的(company_code,contract_no)复合键IN，保留租户、空集合和锁定条件；500键在租户拦截器和512KB栈环境校验。最后PID见保护JSON，local,dac及Flyway/Flowable/Quartz/Batch/事件开关均保持关闭。连接级临时夹具不改变正式结构，本轮正式库DDL、迁移及真实同步均为0。

180项计数/最大ID/原任务版本保护一致；原46任务、209运行、211绑定逐字段未变；先前单独批准修正的1570行仍与全部预期字段一致；33个只读触发器定义及22个INSERT/DELETE创建时间保持；测试库6734条项目技术身份保持。新套件绑定0、非预览Run0、活动Run0，Flyway仍V473。59191未操作。

缺引用分支由原生检查证明，P18/P33的真实运行尚未越过各自授权/地址阻断；不宣称任务专属浏览器或全量写入验收通过。新套件无专属业务UI，本轮真实预览证据来自正常认证的59284 API与数据库终态。

[最终完整保护证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-lookup-preview-20261019/final-proof.json)；[首次候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-five-input-dependencies-20261019/candidate-manifest.json)；[追加过滤候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-empty-contract-filter-20261019/candidate-manifest.json)；[分批及单表发货候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-query-batching-20261019/candidate-manifest.json)；[日期转换候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-relationship-time-preview-20261019/candidate-manifest.json)；[合同复合键候选差异](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-contract-lookup-preview-20261019/candidate-manifest.json)。

## 24项最新有效回执

| 任务 | Run | 终态 | read | 本轮 |
|---|---|---|---:|---|
| P01 | 2111504172950122498 | PREVIEW_READY | 208 | 沿用有效回执 |
| P04 | 2111517837875126273 | PREVIEW_READY | 12632 | 沿用有效回执 |
| P08 | 2112057345712537601 | PREVIEW_READY | 612 | 沿用有效回执 |
| P10 | 2112067330504867842 | FAILED | 0 | 沿用有效回执 |
| P11 | 2111436809982627841 | PREVIEW_READY | 8443 | 沿用有效回执 |
| P12 | 2111436813732335618 | PREVIEW_READY | 34976 | 沿用有效回执 |
| P13 | 2111515526163517442 | PREVIEW_READY | 99895 | 沿用有效回执 |
| P14 | 2112051434155581442 | FAILED | 0 | 沿用有效回执 |
| P15 | 2112144970750287873 | FAILED | 0 | 已重跑 |
| P16 | 2112103755224866818 | PREVIEW_READY | 158871 | 沿用有效回执 |
| P18 | 2112132107189002241 | FAILED | 0 | 已重跑 |
| P19 | 2112100784705585154 | PREVIEW_READY | 186118 | 沿用有效回执 |
| P20 | 2112059695747514370 | PREVIEW_READY | 7818 | 沿用有效回执 |
| P21 | 2112053188930744321 | PREVIEW_READY | 1190 | 沿用有效回执 |
| P22 | 2112100511387959297 | PREVIEW_READY | 343858 | 沿用有效回执 |
| P23 | 2111466972774600705 | PREVIEW_READY | 35501 | 沿用有效回执 |
| P24 | 2112057587128287234 | PREVIEW_READY | 81547 | 沿用有效回执 |
| P25 | 2111480444740603906 | PREVIEW_READY | 462213 | 沿用有效回执 |
| P30 | 2111481771424153602 | PREVIEW_READY | 0 | 沿用有效回执 |
| P31 | 2112123456126668802 | PREVIEW_READY | 381195 | 沿用有效回执 |
| P33 | 2112132166609707009 | FAILED | 0 | 已重跑 |
| P34 | 2111480509467103233 | PREVIEW_READY | 165801 | 沿用有效回执 |
| P35 | 2112116707562823682 | PREVIEW_READY | 1586578 | 沿用有效回执 |
| P37 | 2111480525451595777 | PREVIEW_READY | 475468 | 沿用有效回执 |
