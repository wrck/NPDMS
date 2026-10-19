# 新虚基类13项失败：依据旧十项目成功逻辑复核与修复

本页记录原ID/用户名修订前的首次核查；账号及项目ID前置判断已被后续明确指令替代，当前结果见[最新报告](base-sync-original-identifiers-preview-20261019.md)。

[返回24项总表](README.md) · [历史十项目](pilot-ten-projects/README.md)

2026-10-19。13项均逐一重跑真实API预览并记录数据库终态；修复新P21后只重跑受影响P21。**P21由FAILED转为PREVIEW_READY，读取1190条；剩12项当前失败。24项最新为12 PREVIEW_READY、12 FAILED，READY仍包含P30的0行空预览。**

本次不是重新裁决，也没有通过取消授权、猜正式ID、丢原值或填占位来消除失败。原任务和三种实现中的A/B未运行、未保存、未修改。旧十项目只作为历史实现/完成证据，不查询或重放旧试点环境，不做双线业务对照。

## P21的实现修复及真实预览

两张展示缓存目标表的project_id仍NOT NULL，已批准的可空结构尚未到位。旧实现于读源前中止所有运行；该结构是实际写入前置，不能因此阻止一个不清空、不插入缓存的只读预览。新P21的执行分支仍在任何清理前原样拒绝，预览改为明确登记执行结构警告并继续原字段验证。没有迁移或修改任何正式列。

| 项目 | 本次证据 |
|---|---|
| 原失败Run | 2112051621519335425 / FAILED / read0 |
| 修复后Run | 2112053188930744321 / PREVIEW_READY / preview=true |
| 原表完整读取 | 租赁454 + 配置736 = 1190 |
| 动作统计 | 拟INSERT1190；UPDATE0；UNCHANGED0；DELETE0；不是正式新增 |
| 结构警告 | P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable |
| 耗时 | 0.4秒；以原回执起止时间另核 |

代码：[P21只调整预览/执行结构前置](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java:25)。

失败测试先复现旧行为：3项中1失败、1错误；修复后定向22项全通过，0失败、0错误、0跳过。原生MySQL连接内临时表验证两源、重复行、非空结构、源精度错误、预览无缓存/绑定写入，以及执行分支清理前拒绝；共享隔离和Mapper装配检查保留。未把临时表测试当正式同步或外键验收。

| 已执行测试类 | tests / failures / errors / skipped |
|---|---|
| BaseSyncIsolationTest | 15 / 0 / 0 / 0 |
| BaseSyncMapperWiringTest | 2 / 0 / 0 / 0 |
| CacheSourceNullabilityTest | 2 / 0 / 0 / 0 |
| ProjectCachePreviewMySqlTest | 3 / 0 / 0 / 0 |

测试日志：[p21-20261019T132433.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/p21-20261019T132433.log)；[测试保护记录](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/test-protection.json)。

## 逐项当前结果、旧逻辑与外部前置

旧十项目成功不等于当前验证库具备同样的正式输入。正式数据必须已经存在，不能由上游预览产生。具体按旧输入、当前失败、必要条件、处理结论列出。FAILED/read0不证明未查询原表或来源为空。

| 项目 | 最新Run | 本次终态 / read | 归类 |
|---|---|---|---|
| [P08](#p08) | 2112051432817598465 | FAILED / 0 | 正式审计账号输入 |
| [P10](#p10) | 2112051433660653569 | FAILED / 0 | 目标结构 |
| [P14](#p14) | 2112051434155581442 | FAILED / 0 | 来源冲突 |
| [P15](#p15) | 2112051493786001409 | FAILED / 0 | 目标结构、容量和正式P24输入 |
| [P16](#p16) | 2112051494360621058 | FAILED / 0 | 正式P04来源身份 |
| [P18](#p18) | 2112051502157832193 | FAILED / 0 | 正式账号及项目/合同输入 |
| [P19](#p19) | 2112051535838093313 | FAILED / 0 | 正式项目及四属性定义 |
| [P20](#p20) | 2112051575306493953 | FAILED / 0 | 正式项目父关联 |
| [P21](#p21) | 2112053188930744321 | PREVIEW_READY / 1190 | 已修复的预览流程；正式执行仍缺结构 |
| [P22](#p22) | 2112051622005874690 | FAILED / 0 | 正式项目及范围引用 |
| [P31](#p31) | 2112051643648483330 | FAILED / 0 | 本套件完整正式P16范围 |
| [P33](#p33) | 2112051644151799809 | FAILED / 0 | 本套件完整正式范围及设备/位置上下文 |
| [P35](#p35) | 2112051644671893506 | FAILED / 0 | 本套件完整范围及八属性定义 |

<a id="p08"></a>

### P08

旧成功逻辑：旧字典Owner按精确正式账号保存原审计；原ID542/system及空114只是既定窄例外。不能推广为任何缺失人员都填admin。

当前依据：本次仍缺w02611；已核w02611、x01861均无正式账号。九个已审核审计账号当前均未找到，需正式P04账号结果。

结论及可执行前置：保留；当前数据前置，不新增账号、不删原审计、不扩大例外。

本次回执：`原审计人员缺少已核实的平台账号，禁止猜配：w02611`。

历史任务：`2109351154645135362`、`2109371486114320385`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p08)。

当前回执原件：[P08-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P08-current-preview.json)。

<a id="p10"></a>

### P10

旧成功逻辑：旧客户任务是共用前置，十项目材料不能证明CRM整表迁入；新任务必须完整承接已审核ERP客户码。

当前依据：cus_customer_master.erp_customer_code仍不存在。实际目标查询/INSERT需要该列，不能只取消前置后宣称目标校验通过。

结论及可执行前置：保留；等待既有批准结构到位，未执行V481或任何DDL。

本次回执：`P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移`。

历史任务：`2102221608959250434`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p10)。

当前回执原件：[P10-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P10-current-preview.json)。

<a id="p14"></a>

### P14

旧成功逻辑：旧十项目10订单/105订单行精确唯一，保留版本与完整载荷保护；没有由旧成功导出的整表任取冲突行规则。

当前依据：本次全源核对：91572表头原行→91239业务键，其中169键载荷冲突；380605明细原行→379522键，其中10个冲突键的40行缺订单号。

结论及可执行前置：保留；由来源权威修正或提供已有逐键消歧依据，不拼接多个MAX、不占位、不跳过。当前MAX聚合仅在payloadVariants=1时可接受，冲突仍拒绝。

本次回执：`P14_DUPLICATE_BUSINESS_KEY_CONFLICT`。

历史任务：`2101587859478949890`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p14)。

当前回执原件：[P14-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P14-current-preview.json)。

<a id="p15"></a>

### P15

旧成功逻辑：旧试点合同/发货/包件及回款已经正式存在；B旧五阶段预览不验证C本地合同输入。真实父来源键及完整回款是必要事实。

当前依据：缺com_shipment_package.shipment_contract_source_key；源条码4283326条，任务max_rows=1000000且库CHECK上限100万；同源BASE_P24无非预览COMPLETED回执。

结论及可执行前置：保留；已批准列/容量CHECK到位后才可配置500万；还须另行获准的P24正式输入。本轮不修改任务容量绕过CHECK。

本次回执：`P15_TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key`。

历史任务：`2107037031219654657`、`2101891886242299906`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p15)。

当前回执原件：[P15-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P15-current-preview.json)。

<a id="p16"></a>

### P16

旧成功逻辑：旧十项目已有正式账号与来源映射，再由主档Owner保留37列和全部原身份；预览不产生账号来源事实。

当前依据：本次精确缺USER_FND:79:x00270；新套件来源绑定0、P04非预览完成0。普通82个项目不等于已核实DPPMS身份，不能套原数字ID。

结论及可执行前置：保留；需本套件正式P04输入。多来源目标唯一索引仍是实际写入前置，预览已按此前修复仅警告。

本次回执：`P16_FORMAL_ACCOUNT_SOURCE_BINDING_MISSING:USER_FND:79:x00270`。

历史任务：`2108608417343934465`、`2109622534918856706`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p16)。

当前回执原件：[P16-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P16-current-preview.json)。

<a id="p18"></a>

### P18

旧成功逻辑：旧关系Owner要求正式项目/合同、精确审计和组织范围；整组已裁决归并保留每条原桥身份。

当前依据：首个缺x00270。九个缺失审计账号均在已有P04范围：j01441、j05517、t04026、w02611、w02799、x00270、x01861、x05179、y00439。随后仍须真实项目、合同及公司授权。

结论及可执行前置：保留；不是新账号范围待裁决。新套件组装/归并与既有四查询拆分保留，实际写入另需多来源索引结构。

本次回执：`原审计人员缺少已核实的平台账号，禁止猜配：x00270`。

历史任务：`2109363204544983041`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p18)。

当前回执原件：[P18-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P18-current-preview.json)。

<a id="p19"></a>

### P19

旧成功逻辑：旧十项目55相关方和10组状态均有正式项目；四状态属性是正式扩展，不是项目生命周期。

当前依据：本次缺正式项目来源身份；proj_legacy_detail_source=0、新套件绑定0。定义目录仅SOL/REQUIREMENT_ANALYSIS，缺PRJ/project四属性及有效配置ID。

结论及可执行前置：保留；需要正式项目身份、shipment_status、shipment_status_updated_at、execution_status、execution_status_updated_at定义和pms.base-sync.project-state-definition-id。未发布定义或写状态机。

本次回执：`FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:76,75,83,87,86,85,84,81,6864,6892,7875,7879`。

历史任务：`2109968219214897153`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p19)。

当前回执原件：[P19-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P19-current-preview.json)。

<a id="p20"></a>

### P20

旧成功逻辑：旧十项目真实产品来源为0，仅证明空输入不造数据；不能用空样本证明整表非空清单已承接。

当前依据：本次来源已读到22945、24284等项目键，正式身份解析失败；目标project_id是必要父引用，不能去掉或用原项目ID替代。

结论及可执行前置：保留；待正式项目/身份到位，继续核完整上游来源、数量、重复及不可变清单。未凭缺前置推定后续检查通过。

本次回执：`FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:22945,24284,28998,27490,26918,29967,36237,33718,33382,34863,34779,36979`。

历史任务：`2109865269478027265`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p20)。

当前回执原件：[P20-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P20-current-preview.json)。

<a id="p21"></a>

### P21

旧成功逻辑：旧十项目两缓存来源均0；展示缓存不需要虚构project_id。当前全源非空，仍应原字段、重复行完整预览。

当前依据：修复前把project_id可空结构校验用于所有运行，导致只读预览未读源即失败。该结构确实挡真实写入，但不需要阻止无DML预览。

结论及可执行前置：只调整新P21：执行分支仍在清理前拒绝；预览登记P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable后继续。454租赁+736配置=1190全量真实预览通过，无缓存或绑定写入。

本次回执：`P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable`。

历史任务：`2109865270430134273`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p21)。

当前回执原件：[P21-repaired-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P21-repaired-preview.json)。

<a id="p22"></a>

### P22

旧成功逻辑：旧十项目105范围+105配套明细基于正式项目、合同关系、订单行和办事处快照；未确认历史不等于当前分配。

当前依据：本次缺正式项目身份。目标还需精确合同/订单行组合、数量及办事处版本，当前未走到这些后续检查。

结论及可执行前置：保留；正式P16输入后按既有合同、行、办事处契约核验；allocationConfirmed仍false，不凭旧试点审批扩大分配。

本次回执：`FORMAL_PROJECT_SOURCE_IDENTITY_MISSING:37400,38882,38883,41315,41931,41953,41954,42167,42500,42878,42879,42880`。

历史任务：`2109829769958453249`；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p22)。

当前回执原件：[P22-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P22-current-preview.json)。

<a id="p31"></a>

### P31

旧成功逻辑：旧成员/联系人命令指定已正式承接的十项目；23条确认授权是当时逐项目批准，不扩展到整表历史。

当前依据：缺BASE_P16完整非预览COMPLETED范围。新套件不再读取旧发布表，当前自身绑定和正式完成回执均0。联系人后续还需正式FINAL_CUSTOMER。

结论及可执行前置：保留；新独立完整范围及当前项目管理范围为必要前置。历史成员不确认当前授权，联系人不接管主联系人。

本次回执：`COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED`。

历史任务：当时是逐项目Owner命令，没有统一SyncTask ID；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p31)。

当前回执原件：[P31-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P31-current-preview.json)。

<a id="p33"></a>

### P33

旧成功逻辑：旧948安装历史以正式项目、SN设备和不可变地址为依据；20既有较新位置保持，只补928缺位置。

当前依据：本次先缺新P16完整正式范围。之后仍需正式SN设备、合法项目设备选择及地址契约；当前DPPMS正式发货0，不能据3条其他发货造设备链。

结论及可执行前置：保留；没有重新引入旧试点Owner或旧发布依赖，不猜设备ID，不接管已有当前位置。后续依赖未到达不计已验证。

本次回执：`COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED`。

历史任务：当时是逐项目Owner命令，没有统一SyncTask ID；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p33)。

当前回执原件：[P33-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P33-current-preview.json)。

<a id="p35"></a>

### P35

旧成功逻辑：旧6批次/659版本/5272属性有正式项目与对应正式扩展；原Bak/Change不丢、不转升级完成事件。

当前依据：本次先缺新P16完整正式范围；另无AST/SoftwareVersionRecord八属性正式定义，仅存在SOL/REQUIREMENT_ANALYSIS。

结论及可执行前置：保留；所需conp/boot/cpld/pcb各自_bak/_change定义及pms.base-sync.software-history-definition-id不能用其他实体代替。批次父ID仍在本次执行后处理中传递，已有原生测试证明，不误判为缺失实现。

本次回执：`COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED`。

历史任务：当时是逐项目Owner命令，没有统一SyncTask ID；[当时前后置及完成证据](pilot-ten-projects/tasks-and-processing.md#p35)。

当前回执原件：[P35-current-preview.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P35-current-preview.json)。

## 已核实的外部条件与后续顺序

- 结构：cus_customer_master.erp_customer_code、com_shipment_package.shipment_contract_source_key不存在；两张缓存project_id不可空；int_base_sync_task的max_rows CHECK仍限定1～1000000。P15源4283326条，不能仅改配置绕过现有CHECK。
- 账号/正式输入：所需九个审计账号均未找到；新套件来源绑定0；P04/P16/P24非预览运行0；proj_legacy_detail_source=0；DPPMS正式发货0。普通项目82条、普通发货3条不等于已核实的DPPMS输入。
- 定义：当前只有SOL/REQUIREMENT_ANALYSIS；没有PRJ/project四属性或AST/SoftwareVersionRecord八属性定义。配置ID不能指向其他实体。
- 来源：P14冲突169表头键、10明细键依旧存在。旧十项目的唯一性、正式前值及成功回执不能自动提供这些冲突的取舍。

后续顺序：来源权威处理P14冲突；已批准结构到位后，另行获准的正式P04/P24/P16输入才可解除对应下游前置；相应扩展定义由正式提供方就绪后再配置。以上是具体缺项，不是在本轮请求重做已裁决业务审批，也不授权迁移、真实同步或定义发布。

## 验证与隔离

仅按已有59284授权重载一次：新包只改变P21类及内部类共6个条目，旧包保留；原任务类、共享框架、SQL、配置和全部迁移资源保持原冻结字节。加载前校验准确旧PID/端口/JAR和新任务空闲；没有停用其他服务。

构建及定向测试沿用JDK25/Maven、本工作树依赖库与同一构建锁。初始化、Quartz/Batch、事件驱动和自动迁移保持关闭，Flyway/Flowable结构更新显式false，迁移容器exited，Flyway仍V473。

最终180项保护观察无差异，包含业务表/原任务计数和最大ID、原任务版本/启用标记、正式新绑定及非预览计数；24个新任务均空闲，新套件绑定0、非预览运行0。此为指定观察项保护，不宣称全字段校验。真实预览结论来自正常登录后的真实API及数据库终态；新套件无专属UI，不冒称UI业务闭环。

证据：[最终保护与24任务回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/final-proof.json)；[加载包范围](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/candidate-manifest.json)；[目标结构/账号/正式输入](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/target-preconditions.json)；[正式定义及输入](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/formal-input-evidence.json)；[来源冲突及原表计数](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/source-evidence.json)。

本报告生成命令：`python -X utf8 D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/render_followup.py`。该命令只读取既有回执/文件并更新汇总，不执行SQL、预览或服务操作。

