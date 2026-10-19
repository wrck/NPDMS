# 剩余P10、P14、P18、P33逐项复核

核验时间：2026-10-19T20:58:58.873040+08:00。既定工作树`project-detail-followup`，分支`fix/dppms-migration-consolidation-20261012`，实现基线`08a8ecf38`。

**四项均已重新执行真实预览并到失败终态，当前24项为20 PREVIEW_READY、4 FAILED。** 本轮未发现能在已有审核内直接修复的实现或配置缺陷；阻断来自仍未裁决的来源事实、正式授权或字段承载。未降低校验，未用“读取成功”代替任务预览通过。其余20项（含P15）未重跑，P15的4706900条通过回执沿用；P30仍为既有0行空预览。

<a id="p10"></a>

## P10：CRM客户身份及名称

真实SQL Server视图`DPtech_v_account`仍有59601条：22组相同客户码对应44个不同AccountId/名称，35条缺名称，4条名称超长（130、131、131、143字符），合计83个问题身份。客户码、ERP码、版本精度及状态均无新增问题；其他已映射文本长度在目标容量内。已批准的ERP单列确实存在，当前阻断不是补列遗漏。

正式客户以租户＋客户码定位，名称列`VARCHAR(128) NOT NULL`，正常客户/CRM写入也要求名称。十项目历史只证明当时合法范围，未批准整表同码客户合并、取最新AccountId、编号代名称、截断或略过。

所需外部处理：CRM来源Owner对22组44个AccountId给出编码归属或正式纠正；补齐35个身份的权威名称；对4个超长名称给出完整正式承载规则（如需扩大字段及业务校验须另行裁决）或正式源纠正。不能套用P16的项目名称回退。
[83个身份、长度及当前列证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P10-current-audit.json)；[只读质量SQL](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P10-source-quality.sql)；[逐身份索引SQL](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P10-source-issue-index.sql)。

<a id="p14"></a>

## P14：订单执行单承载与40条缺订单号

91572条原表头归并91239个业务键，169键涉及410条不同内容；本次再次确认全部仅`orderExecNumber`不同，其余9项参与冲突判断的业务属性一致，单组最多3个执行单号。既有`splitSoleAgentLendOrderInfo`来源追溯表明它们是执行单展开关系，不是普通时间版本；正式表头`execution_no`为单值，不能将P15按订单号选客户的裁决扩成同一订单内任选执行单。

380605条原行归并379522键，其中10个冲突键包含40条缺订单号原行。按同公司、来源、订单类型、实际执行单号精确查表头，38条无候选；原行47615只有候选SO079963，原行101146只有候选SO102972。候选只证明执行单相同，没有独立上游原订单行身份依据，未据此补号。旧“40条发货引用纠正”与本批40条ERP缺订单号明细属于不同来源身份，不能套用。

所需新裁决/来源输入：Q023明确多执行单的正式单值选取规则或多关系承载；来源Owner按全部40个原行ID提供可核实订单号或精确关联依据。不能取最大原ID、拼接执行单、写固定JSON、先写-1订单号或丢弃执行单。
[169组410原记录、40原行和两个候选](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P14-current-audit.json)；本轮仅SELECT，原过程和原任务未执行。

<a id="p18"></a>

## P18：公司资料已存在，缺的是正式授权及8个原公司值

四份独立原表查询返回86780合同、82261组、85472成员桥和79429规范项目，展开82676个原关系身份，归并82381个业务关系。公司001/002/003分别80374/1804/195条，合计82373条；另8条公司缺失。

正式`system_company`已经存在001、002、003。本次检查操作者1及全库全部有效范围：19个用户共38条有效范围均只覆盖DPTECH-DEMO，未找到可直接使用的001/002/003正式授权上下文。读取身份使用现有正常认证，不冒用其他用户、不自动授予公司权限。历史十项目通过不构成整表当前授权，原project_id承接也不改变授权边界。

缺公司项目48275～48282的原`pm_project.compId`全部为NULL，并非新Mapper关联丢失。这8项已获准在只读预览登记缺项，不重新要求裁决或将其恢复为预览硬前置；当前实际阻断仍是非空公司的授权。所需外部处理是提供8个项目的可核实公司归属，以及由有权主体提供覆盖001/002/003的正式执行上下文或按正式流程配置授权；本轮不创建范围。

实际写入还需自身正式项目/精确公司合同输入（当前正式合同0），以及经批准的版本/结构更新处理`uk_int_base_sync_binding_target`单目标唯一索引与多原身份汇聚的冲突。它们已被区分为写入前置，不是当前授权失败的替代解释；预览保持缺合同登记及结构警告，不创建正式父数据、不执行DDL。
[全部公司范围分布、8个原项目及索引](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P18-current-audit.json)；[实际写入前置证据](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/formal-execution-prerequisites.json)。

<a id="p33"></a>

## P33：安装地址仍缺正式承接裁决

完整原项目范围1063717条。24142条原地址为NULL，1849条为一个空格、11条为两个空格，合计26002条空地址；6111条超过安装列255字符，分布如下：

| 原地址字符数 | 条数 |
|---|---:|
| 571 | 5013 |
| 496 | 284 |
| 356 | 338 |
| 306 | 36 |
| 277 | 50 |
| 275 | 390 |

原ID、SN、创建时间均无缺失，日期无超精度或更新时间倒置。29条串货/转移字段仍按既定暂缓处理，不重新提请、不扩写转移功能，也未擅自排除整条来源。

既有安装裁决是“有地址”记录使用状态2，原创建时间作为安装/生效时间，并保护较新位置；全历史空地址及超长值明确仍待裁决。安装`install_location VARCHAR(255)`虽可空，不能由物理可空推导为空地址记录已安装。`ast_address.full_address VARCHAR(1024)`能容纳最长571字符，但不能仅因此自行把原安装字段改为NULL、截断或改成只在快照内承载。

所需新裁决：26002条空地址的实际安装含义、状态及原值承接；6111条超长值的完整正式承载位置/字段映射。设备、正式项目、当前选择及位置Owner属于未来写入前置；36个现有正式设备SN与原发货来源匹配0条。不能通过真实同步补数据来完成本轮预览。
[所有32115个问题原身份、地址分布及暂缓串货](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P33-current-audit.json)。问题身份数是多个条件的并集，含与地址问题重叠的串货，不将其当作额外全部新增问题。

## 四项真实预览终态

均通过正常认证调用59284独立套件预览API，仅生成新BASE运行回执。失败read_count=0不能解释为源表为空；这些运行均未获得PREVIEW_READY。

| 任务 | Run | 终态/错误 | 耗时（秒） |
|---|---|---|---:|
| P10 | 2112165966710439937 | FAILED / CRM客户原记录存在未决问题：DUPLICATE_CUSTOMER_CODE | 18.564 |
| P14 | 2112166044439281665 | FAILED / P14_DUPLICATE_BUSINESS_KEY_CONFLICT | 24.307 |
| P18 | 2112166146755133442 | FAILED / P18_PROJECT_COMPANY_NOT_AUTHORIZED | 12.515 |
| P33 | 2112166199544643585 | FAILED / P33_SOURCE_BASELINE_INVALID | 35.985 |

四项原始回执：[P10](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P10-preview.json)；[P14](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P14-preview.json)；[P18](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P18-preview.json)；[P33](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/P33-preview.json)。

## 运行、验证与隔离

开始时59284没有监听，数据库/Redis及19084已运行、迁移容器退出且没有活动任务。按已有授权仅启动上轮冻结包，未重建或重载代码；配置保持local,dac，Flyway/Flowable自动结构更新、Quartz、Batch及事件驱动作业关闭。没有产品实现或配置改动，因此不为本轮文档重复既有代码测试；执行了四项真实预览、逐项只读来源/授权/结构核查和最终保护验证。新套件无任务专属UI，本轮不宣称浏览器业务验收。

180项保护一致；原46任务、209运行、211绑定逐字段未变；先前单独批准修正的1570行仍逐字段一致；33触发器定义及22个INSERT/DELETE创建时间保持；测试库6734条项目技术身份保持。独立绑定0、非预览Run0、活动Run0；Flyway仍V473。无DDL、源数据或环境数据修正、授权写入、双线对照、迁移、真实同步或59191操作。
[完整保护核验](D:/CodexTaskArtifacts/project-detail-followup/base-sync-four-blockers-audit-20261019/final-proof.json)。
