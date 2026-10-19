# 新P15：按订单类型及最大订单号选取合同客户

核验时间：2026-10-19T20:43:20.359552+08:00。分支`fix/dppms-migration-consolidation-20261012`。

**新P15已完成4706900条全量真实预览，终态PREVIEW_READY。原389组客户冲突全部消歧，24项最新终态为20 PREVIEW_READY、4 FAILED。** 其余23项没有重跑；P30仍是既有0行空预览。预览通过不表示真实同步。

## 已批准的选客逻辑

需求方先确认“最大”指正向销售订单号而非金额，再补充“逻辑相同，合同客户根据订单类型选取对应最大订单号的所属客户”。按公司编码＋合同号归并：有正向订单（orderType=0）时取最大正向订单号，仅退货（orderType=1）时取最大退货订单号；客户编码及名称来自同一选定订单。同类型最大号仍出现不同客户时继续拒绝，不按技术ID或最大客户码代选。未创建逐合同特例。

91565条原订单归并81522个合同：80302组按最大正向订单号选客，1220组按最大退货订单号选客。原389组冲突全部消歧，未排除任何合同或修改来源。两份此前仅有退货的合同亦按通用规则处理：

| 公司/合同 | 类型 | 最大订单号 | 选定客户码 |
|---|---|---|---|
| 001 / 51020140121A | 1 | 562 | C00010 |
| 001 / 51020131018A | 1 | 351 | C00011 |

## SQL与前后置处理

- 原ERP订单仍只做一次平坦SELECT，补带orderType/orderNumber；在原有全量合同步骤中选取并归并。三份合同来源查询各执行一次，原库SQL嵌套层数未增加。
- 正式输入com_sales_order查询同步选客规则，在已有嵌套层内使用DENSE_RANK，保留同类型最大号全部记录供冲突校验。公司/合同及订单号按精确值比较，租户、逻辑删除、来源范围和空值条件保持。正式输入路径只做原生MySQL临时表验证，未执行真实同步。
- 不回查客户ID，不创建客户或正式输入，不把环境数据修正加入任务。原码归一化、少量引用每轮一次、发货原键、日期精度、原绑定及不可变历史保护保持。真实执行仍要求自身正式P24前置。
- [当前C查询及INSERT/UPDATE摘录](P15/C-base-sync.sql)、[完整动态XML](P15/C-base-sync-mappings.json)、[字段衔接](P15/current-base-field-bindings.md)、[前后置逻辑](P15/processing.md)。列出的DML不是本轮已执行SQL。

## 真实预览

Run `2112161183148769281`，API code=0，终态`PREVIEW_READY`，用时318.707秒。五阶段读取量均由运行日志和最终回执证明：

| 步骤 | 实际完整读取 |
|---|---:|
| ShipmentContract | 120638 |
| Package | 156368 |
| ShipmentEvent | 4283326 |
| DeviceRelationship | 65046 |
| Contract | 81522 |
| 合计 | 4706900 |

合同三份来源实际返回91565条ERP订单、109578发货字段组、81540应收字段组，归并81522组，sourceIssues=0；目标/绑定查询继续按500组拆批。
[最终真实回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-order-type-customer-20261019/P15-preview.json)；[五阶段证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-order-type-customer-20261019/P15-runtime-step-proof.json)；[389组选择核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-order-type-customer-20261019/P15-customer-selection-source-audit.json)。

首次仅正向选客的Run `2112158780395544577`完整读取原始历史后因两份退货合同FAILED，该历史回执保留；补充裁决后本次重新完整预览，没有改写旧回执。

## 验证与保护

修改前的新套件先复现两项正向选客失败；补充裁决后，再复现仅退货选客失败。最终33项受影响检查全部通过：

| 检查类 | 通过 |
|---|---:|
| P15ContractInputMySqlTest | 13 |
| BaseSyncMapperWiringTest | 2 |
| BaseSyncIsolationTest | 15 |
| PrerequisiteCompletionMySqlTest | 3 |

覆盖正向/仅退货最大订单号、较大技术ID/客户码/退货号不覆盖正向、原表与正式输入口径一致、精确排序、正向及退货同最大号多客户拒绝、选中客户格式错误；保留原任务隔离、原历史及实际执行前置。原生MySQL夹具均为连接级临时表。

本轮按已有授权仅两次重载59284：首次候选仅独立P15类族及两份XML的15个条目有变化；补充裁决候选在该基础上仅14个同范围条目有变化。local,dac及Flyway/Flowable/Quartz/Batch/事件关闭项保持。180项保护一致；原46任务、209运行、211绑定逐字段未变；此前单独批准修正的1570行及33个触发器保持；测试库6734条项目身份保持。独立绑定0、非预览Run0、活动Run0，Flyway仍V473。无新DDL、数据修正、迁移、真实同步或59191操作。
[完整保护核验](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-order-type-customer-20261019/final-proof.json)。

## 其余四项

| 任务 | 尚需外部依据 |
|---|---|
| P10 | CRM的83个来源身份问题：44条同码不同账号/名称、35条缺名称、4条名称超长；需归属及完整名称处理依据 |
| P14 | 169个订单键/410条执行单展开冲突，另10个行键/40条缺订单号；需Q023正式承载及订单身份依据 |
| P18 | 001/002/003公司授权上下文缺失，影响82373条；另8条原公司码缺失。未增授权限 |
| P33 | 26002条原空地址、6111条超255字符，需既有安装规则承接裁决；设备/项目等正式输入是实际写入前置，串货仍按暂缓结论 |

以上四项来源依据及既有失败回执沿用[五项核查报告](base-sync-five-input-dependencies-20261019.md)，未改动其任务或重跑。
