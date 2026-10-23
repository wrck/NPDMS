# P14严格父键与NULL承接、P15合同真实执行和重放完成

更新时间：2026-10-22T10:19:18.526318+08:00。工作树分支`fix/dppms-migration-consolidation-20261012`，仅远端测试库`172.17.90.178:13306/npdms`的新BASE套件。

**P14、P15均已完成真实预览、真实执行和重放。** P14新增92,890个订单头、387,154条明细，包含1,580条父引用为NULL的原明细；P15新增83,105份合同，原4,625,378条历史保持不变。两项重放新增、更新、删除均为0。当前24项预览23 READY/1 FAILED；真实执行20 COMPLETED/2 FAILED/2暂缓，重放20 COMPLETED/2未运行/2暂缓。

## P14严格匹配及未匹配原行写入

父匹配键始终为tenant_id＋source_system＋company_code＋order_no＋原line_type对应头order_type；明细自身唯一键另含line_no。相同订单号的其他类型头，即使只有一条，也不能作为父头。本轮最终运行包中的完整键表达式与原代码一致，没有跨类型或跨来源、公司回退。

按“未匹配行按缺失值写入”的裁决，未匹配原行全部写入，并保存独立来源绑定。order_id及只能从父头取得的字段保持NULL，原订单号、行号、类型、数量等直接字段保留；必填order_type承接本行原line_type。逐条记录来源ID与完整键，回执提示P14_ORDER_REFERENCE_PENDING:1580，不作为整项阻断，不跳过记录、不造父头或占位ID、不改匹配条件。已有目标的租户、删除、绑定、指纹及来源版本保护保持，已有有效引用不自动清空或回填。

仅查询已拆分的pm_order_data_from_erp、pm_order_line_from_erp，各单表读取一次，没有回查总代借货来源。物理头93,223归并为92,890；物理行388,207归并为387,154。

| 缺项类型 | 原始行数 | 缺少的不同完整父键 |
| --- | ---: | ---: |
| 只有其他类型头 | 337 | 90 |
| 无同来源、公司、订单号的头 | 1,243 | 172 |

337条均为行类型1、同号仅有头类型0。原行47373：D365/001/SO079848/行1、类型1、数量-2，已写入正式行2113088077987676162，order_id=NULL；同号原头12117类型0，未被采用。逐条核验全部1,580条已落库、有独立绑定、原映射字段一致。再核验全部387,154条正式明细：NULL父引用1,580，原类型改变0，非空父引用违反给定业务键0。

正式com_sales_order_line.order_id本身可空，同租户父外键保持；order_type为NOT NULL。本轮没有为NULL承接执行DDL或降低约束。此前“同号唯一头回退”和“跳过未匹配原行”的两个候选均未部署，已撤回，不曾造成正式运行或数据写入。

## P15合同生成及历史保护

此前实际合同步骤读取未生成的P14正式头，导致合同0组而历史步骤仍COMPLETED。新P15统一预览和实际的三条既有单表原始查询、一次加载及Java归并；按已审规则选择正向订单最大订单号的客户，无正向时按对应退货最大订单号。P24同租户同连接正式完成前置、客户冲突、来源优先级、目标归属、绑定和不可变历史保护保持。

本轮正式com_contract确认83,105条；五个步骤分别读取ShipmentContract 120,638、Package 156,368、ShipmentEvent 4,283,326、DeviceRelationship 65,046、Contract 83,105，总计4,708,483。原4,625,378条历史及绑定计数/指纹核对保持，合同步骤已实际生成，不能再将前序原始历史完成回执误认为合同完成。预览中的旧来源提示保留原回执，真实执行已通过P24前置并生成合同，不覆盖历史证据。

## 本轮真实回执

| 任务/阶段 | 状态 | 读取 | 新增 | 更新 | 未变 | Run |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| BASE_P14/preview | PREVIEW_READY | 480,044 | 480,044 | 0 | 0 | 2113085785393692674 |
| BASE_P14/execute | COMPLETED | 480,044 | 480,044 | 0 | 0 | 2113086119482589186 |
| BASE_P14/replay | COMPLETED | 480,044 | 0 | 0 | 480,044 | 2113090487393026050 |
| BASE_P15/preview | PREVIEW_READY | 4,708,483 | 83,105 | 0 | 4,625,378 | 2113072052650901506 |
| BASE_P15/execute | COMPLETED | 4,708,483 | 83,105 | 0 | 4,625,378 | 2113076309290647554 |
| BASE_P15/replay | COMPLETED | 4,708,483 | 0 | 0 | 4,708,483 | 2113081125584764930 |

## 24项当前结果及剩余前置

本轮只重跑受影响P14/P15；其他任务保留同一远端库已有回执，当前数据库最新Run ID已逐项核对。前序完整回执及原问题解释保留在[前序执行轮报告](remote-tenant-parser-p30-execution-20261022.md)。COMPLETED仅表示相应执行阶段完成，已有缺项提示仍保留。

| 任务 | 预览 | 真实执行 | 重放 |
| --- | --- | --- | --- |
| BASE_P01 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P04 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P08 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P10 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P11 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P12 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P13 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P14 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P15 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P16 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P18 | PREVIEW_READY | FAILED | NOT_RUN |
| BASE_P19 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P20 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P21 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P22 | PREVIEW_READY | DEFERRED | DEFERRED |
| BASE_P23 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P24 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P25 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P30 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P31 | PREVIEW_READY | FAILED | NOT_RUN |
| BASE_P33 | FAILED | DEFERRED | DEFERRED |
| BASE_P34 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P35 | PREVIEW_READY | COMPLETED | COMPLETED |
| BASE_P37 | PREVIEW_READY | COMPLETED | COMPLETED |

以下P18/P31及正式关系计数于2026-10-22T10:17:30.972963+08:00只读复核，无重复提交已失败或暂缓任务。

- **P18：正式授权及来源公司事实。** 当前账号1公司范围仍为0，所需001/002/003无当前正式授权，依据ADR-0038，不能视为旧试点条件而删去。原项目48275—48282共8条compId仍为NULL，需来源权威公司信息。合同83,105份已生成；授权前置仍失败，后续写入校验未经本轮重跑，不能预先宣称全部通过。
- **P31：原有效期倒置。** 当前仍152条：角色60共145、项目经理30共5、销售10共2。原裁决仅允许预览保留日期并登记；真实写入的日期有效性及正式CHECK保持，需要权威日期或新的明确承接裁决。父引用NULL规则不改写原日期。
- **P22：实际及重放按裁决暂缓。** B稿不执行，既有非空快照/即时外键等正式承载差异保留。P14已落库不等于允许运行P22。
- **P33：实际及重放按裁决暂缓。** 前序真实核查1,035,329条来源中1,035,324条匹配设备，扣除暂缓的1161000、1161002两条串货，1,035,322条缺正式项目设备选择关系；5条设备缺项来源ID为68590、72828、68689、139767、208442。当前项目—合同关系仍0，项目contract_no非空仍0；合同生成不等于建立正式项目选择关系，不绕过ProjectDeviceSelectionApi。尾空格SN的SQL/Java匹配问题已在前序修复，本轮不重复改SN或生成虚构关系。

**P14的1,580条缺父引用已按NULL成功承接，属于保留提示，不再列为任务失败或正常数据同步阻断。**

## 验证、隔离和证据

39项定向检查通过（P14 10、P15 14、独立套件隔离15），包含先复现旧跳过逻辑漏行、再核验NULL承接、精确父键、全部原行与独立绑定、已有行保护、真实写入及幂等。原生夹具仅使用连接内TEMPORARY表，未修改正式结构。本轮无新生产代码改动发生在冻结/测试之后。

最终检查：47张正式表定义（忽略自增计数）、Flyway历史及原任务计数保持；新任务配置仅本轮P14/P15合法运行时间戳变化。59284冻结包与源码/XML一致，健康UP、正式登录和权限初始化成功，活动Run为0。已知凭据扫描未发现明文进入变更文件。只按既有授权在空闲边界重启59284；59191未操作。

运行使用既有已验证冻结底包加新套件修复；整工作树整包的既有BPM类加载限制未在本轮扩大处理。本轮定向检查、真实API及数据库验收不等同全仓构建或浏览器UI验收。

P22/P33真实执行请求0，B稿不执行，无本轮DDL、Flyway或其他迁移；原任务和来源数据未改。本轮未提交或推送Git。

[全部1,580条原始记录、落库证据及24项机器结果](P14-strict-parent-and-P15-contract-followup-20261022.json)；[两张来源原表及严格父键查询SQL](P14-strict-parent-and-P15-contract-followup-20261022.sql)。

[最终运行/结构保护证明](D:/CodexTaskArtifacts/project-detail-followup/p14-p15-parent-contract-20261022/final-proof.json)；[全部缺父明细落库核验](D:/CodexTaskArtifacts/project-detail-followup/p14-p15-parent-contract-20261022/P14-null-reference-data-proof.json)；[P15五步骤及历史保护](D:/CodexTaskArtifacts/project-detail-followup/p14-p15-parent-contract-20261022/p15-final-proof.json)；[剩余前置只读复核](D:/CodexTaskArtifacts/project-detail-followup/p14-p15-parent-contract-20261022/remaining-prerequisites-final.json)。
