# 原用户名与原项目ID承接后的真实预览

本页为原ID/用户名阶段的冻结结果；P10缺列、P19/P35扩展和P22项目办事处前置已经后续明确修订。最新真实终态见[base-sync-department-code-preview-20261019](base-sync-department-code-preview-20261019.md)。

[返回24项总表](README.md) · [原13项首次核查及P21修复](base-sync-13-failures-followup-20261019.md)

**当前24项：14 PREVIEW_READY、10 FAILED；原13项失败中P08、P20、P21已解除。P30仍为0行空预览，不计非空业务完成。**

本节依据最新明确指令替代前一阶段“审计用户名必须先有正式账号、项目必须先做ID映射才能预览”的判断。已审核的项目归并、历史保护及权限继续执行，没有重做已裁决审批。

## 已实现的字段规则

- 字符串审计字段creator/updater直接保留原用户名，包含大小写、空白、空串和NULL；不查询平台用户、P04来源绑定或填占位。新套件公共基类一次填充本批原值。
- 项目主表proj_project.id、root_id、code_root_id承接pm_project中已选主档的原projectId；下游project_id使用对应原主档ID。按类型/精确编码选主档、保留旧别名的原审批继续生效。其他实体的技术主键不变。
- 原ID已存在时，保留编码、类型、租户、状态及管理权限核验。未绑定的既存项目不覆盖/接管，也不暗改为新ID。实际缺父项目的只读规划不伪造项目版本、组织或授权，回执保留FORMAL_PARENT_VALIDATION_DEFERRED。
- P31/P33/P35预览一次读取与P16相同的完整源项目范围，快照标记0，不能冒充正式完成Run；真实执行仍核本套件完整COMPLETED及正式父数据。P31历史成员的原用户编码保留在member_code，数字user_id保持NULL，authorization_confirmed=false。
- P18四份平坦源查询继续复用；原项目目录同时读取公司码，沿既有01→001等转换核公司范围，无逐行账号/公司回查。必要合同、订单行、设备及扩展定义不以原项目ID替代。

## 逐项真实结果及必要缺项

| 任务 | 最新Run | 终态 / read | 结论 |
|---|---|---|---|
| [P08](#p08) | 2112057345712537601 | PREVIEW_READY / 612 | 已解除账号回查阻断；612条预览通过。 |
| [P10](#p10) | 2112051433660653569 | FAILED / 0 | 目标结构缺项。 |
| [P14](#p14) | 2112051434155581442 | FAILED / 0 | 原表业务键载荷冲突。 |
| [P15](#p15) | 2112051493786001409 | FAILED / 0 | 目标列、容量及必要正式回款输入。 |
| [P16](#p16) | 2112057425274290177 | FAILED / 0 | 原ID与既存其他项目碰撞。 |
| [P18](#p18) | 2112059597156204546 | FAILED / 0 | 原ID命中的既存项目身份不符。 |
| [P19](#p19) | 2112059647936643074 | FAILED / 0 | 原ID命中的既存项目身份不符，另缺四属性定义。 |
| [P20](#p20) | 2112059695747514370 | PREVIEW_READY / 7818 | 已解除项目来源映射阻断；7818条预览通过。 |
| [P21](#p21) | 2112053188930744321 | PREVIEW_READY / 1190 | 此前实现修复保留；1190条预览通过。 |
| [P22](#p22) | 2112059747408756737 | FAILED / 0 | 实现SQL已修复；当前缺正式办事处快照。 |
| [P31](#p31) | 2112057614999437313 | FAILED / 0 | 原ID命中的当前项目编码/类型不同。 |
| [P33](#p33) | 2112057626944815105 | FAILED / 0 | 全量源记录超过现有容量，另有必要设备/地址上下文。 |
| [P35](#p35) | 2112057745329045506 | FAILED / 0 | 正式八属性定义缺项。 |

<a id="p08"></a>

### P08

creator/updater原样保留用户名；本项不再依赖P04正式账号。

真实回执：`无错误或警告`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P08-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p08)。

<a id="p10"></a>

### P10

cus_customer_master.erp_customer_code仍不存在；需既有批准结构到位，本轮不迁移。

真实回执：`P10目标缺少已审核列cus_customer_master.erp_customer_code；不执行迁移`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P10-current-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p10)。

<a id="p14"></a>

### P14

本轮源核对169个表头键、10个明细键冲突，后者40行缺订单号。需来源权威逐键修正或提供已有可执行消歧依据，不能任取、拼接、占位或跳过。

真实回执：`P14_DUPLICATE_BUSINESS_KEY_CONFLICT`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P14-current-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p14)。

<a id="p15"></a>

### P15

缺shipment_contract_source_key；4283326条源条码超过100万库约束；新P24预览81547不产生正式回款输入。已批准结构/CHECK及正式输入到位后才可解除。

真实回执：`P15_TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P15-current-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p15)。

<a id="p16"></a>

### P16

1001至1040共40个原主档ID已被不同编码的项目占用；拒绝覆盖、接管或改号。需目标数据维护方解除占用，真实写入另保留多来源索引前置。

真实回执：`P16_PROJECT_ID_ALREADY_OWNED`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P16-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p16)。

<a id="p18"></a>

### P18

同一批低段ID已属于其他编码/类型；原用户名回查已取消，原公司码按已批准转换并核公司范围，未绕过当前项目权限或正式合同前置。需先解除碰撞，后续合同输入仍单独核验。

真实回执：`PROJECT_FORMAL_IDENTITY_INVALID`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P18-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p18)。

<a id="p19"></a>

### P19

先解除项目ID占用；PRJ/project的shipment_status、shipment_status_updated_at、execution_status、execution_status_updated_at正式定义及配置ID仍未提供。不得发布假定义或改生命周期。

真实回执：`PROJECT_FORMAL_IDENTITY_INVALID`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P19-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p19)。

<a id="p20"></a>

### P20

project_id按对应原主档ID规划，回执保留FORMAL_PARENT_VALIDATION_DEFERRED；不代表必要父项目、正式外键及真实写入已经验收。

真实回执：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P20-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p20)。

<a id="p21"></a>

### P21

454租赁+736配置原记录完整预览；两表project_id可空结构尚未就绪仍阻断实际执行，不清空或插入缓存。

真实回执：`P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/P21-repaired-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p21)。

<a id="p22"></a>

### P22

精确BINARY比较消除混用排序规则1267；真实重验现在为P22_PROJECT_OFFICE_UNRESOLVED。项目ID已按原值规划，必要办事处版本、正式合同/订单行仍需真实输入，未通过的后续检查不计成功。

真实回执：`P22_PROJECT_OFFICE_UNRESOLVED`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/P22-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p22)。

<a id="p31"></a>

### P31

预览已改为同P16筛选规则的完整源范围，不再要求先正式执行P16。实际1001至1040占用导致当前身份核验拒绝；联系人后续仍需正式FINAL_CUSTOMER。历史成员保留member_code、user_id为空，不新增当前授权。

真实回执：`PUBLISHED_PROJECT_CURRENT_IDENTITY_CHANGED`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P31-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p31)。

<a id="p33"></a>

### P33

同一批准范围1063717条，超过任务和库CHECK上限100万；即使只统计非空安装地址也有1037715条，不能据此截断/过滤。需相应容量结构及本任务配置可承载全量；后续仍核真实SN设备、当前管理权限、项目选择及不可变地址，不猜外键。

真实回执：`P33_SOURCE_CAPACITY`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P33-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p33)。

<a id="p35"></a>

### P35

只读源范围已可读取，当前明确缺AST/SoftwareVersionRecord的conp/boot/cpld/pcb各自_bak/_change八属性定义及配置ID。主批次ID传递保留；后续真实项目/权限检查未冒称完成。

真实回执：`P35_FORMAL_AST_SoftwareVersionRecord_EIGHT_ATTRIBUTE_DEFINITION_REQUIRED`；[运行原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P35-preview.json)。

[旧十项目中本项成功逻辑及证据](pilot-ten-projects/tasks-and-processing.md#p35)。

## 本次解除与仍受保护的边界

P08读取612条、P20读取7818条；受公共审计变更影响的P24重验81547条通过，均未产生正式写入或来源绑定。P21此前1190条预览继续有效。READY表示读源及本次规划校验完成，不等于外键、正式同步或UI业务闭环完成。

P16冲突已按源主档选择与当前目标主键求交逐项核实：1001～1040共40个原ID均命中不同编码。例：原1001对应16201814112401N-0，目标1001为PROJ-2026-001 / NETWORK_INTEGRATION。需要目标数据维护方解除占用；本轮不删除数据、移动原项目或改号。明细见[project-id-collision-evidence.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/project-id-collision-evidence.json)。

P22曾真实失败于混用排序规则1267；在连接内临时表复现后，只修改新P22的部门编码关联为CAST(... AS BINARY)精确比较，未改数据库排序规则。重验已进入办事处快照校验，SQL错误消除，后续必要业务输入未伪装通过。证据：[P22-target-query-evidence.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P22-target-query-evidence.json)、[P22-string-collations.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P22-string-collations.json)。

P33完整批准范围的1063717条与1037715条非空安装地址统计仅用于定位容量；没有据此排除记录、放大任务范围或绕过上限。当前注册值与库CHECK均100万，结构和容量前置需要外部到位，不能在本轮无迁移约束下完成全量预览。见[P33-capacity-evidence.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/P33-capacity-evidence.json)。

正式定义目录仍只有SOL/REQUIREMENT_ANALYSIS，所需PRJ/project四属性和AST/SoftwareVersionRecord八属性不存在。P10/P15所需列和P15容量缺项、P14逐键载荷冲突的本轮证据保留在前一阶段报告。本次没有将“缺P04正式账号”继续当作原用户名审计的阻断。

## 验证、实际加载和保护观察

原用户名/原ID修订先以失败测试复现旧行为；P22也以实际回执和2项原生MySQL临时表失败复现。最终以下90项全部通过，0失败、0错误、0跳过，保留租户、权限、不可变历史、碰撞拒绝和失败回滚检查。最初P22测试编译时的Integer/Long断言歧义已修正，未计作业务失败复现。

| 测试类 | tests / failures / errors / skipped |
|---|---|
| BaseSyncDefaultsTest | 37 / 0 / 0 / 0 |
| BaseSyncIsolationTest | 15 / 0 / 0 / 0 |
| BaseSyncMapperWiringTest | 2 / 0 / 0 / 0 |
| OriginalAuditNamesTest | 1 / 0 / 0 / 0 |
| CacheSourceNullabilityTest | 2 / 0 / 0 / 0 |
| IndependentScopeMySqlTest | 5 / 0 / 0 / 0 |
| NewHistoryTaskMySqlTest | 7 / 0 / 0 / 0 |
| NewTaskPreconditionsTest | 3 / 0 / 0 / 0 |
| OriginalIdentityPolicyTest | 5 / 0 / 0 / 0 |
| OriginalProjectScopeMySqlTest | 1 / 0 / 0 / 0 |
| P22SnapshotCollationMySqlTest | 2 / 0 / 0 / 0 |
| ProjectCachePreviewMySqlTest | 3 / 0 / 0 / 0 |
| RemainingTaskMySqlTest | 7 / 0 / 0 / 0 |

日志：[p21-20261019T135110.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/p21-20261019T135110.log)。原ID/用户名旧行为失败日志：[p21-20261019T133819.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/p21-20261019T133819.log)；P22原生排序规则失败日志：[p21-20261019T134941.log](D:/CodexTaskArtifacts/project-detail-followup/base-sync-pilot-audit-20261019/p21-20261019T134941.log)。原生测试只使用连接内临时表，其他公共验证使用隔离H2/装配测试，不等于正式业务写入验收。

最新指令后执行10次受影响新套件预览，再重跑P18/P19/P20/P22共4次；仅59284加载2次修复包。连同此前13项首次核查及P21修复，本轮合计28次预览、3次59284专用加载。P08首个请求已结束后监测脚本遇到BIT表示差异，只补读该Run终态；没有重复请求或补造API响应。

最后180项保护观察无差异：业务表及原任务计数/最大ID、原任务版本/启用标记、新绑定0、非预览运行0；24任务均空闲。该观察不是全字段相等校验。Flyway仍V473，Flyway、Flowable结构更新、Quartz、Batch及事件驱动自动执行保持关闭；迁移容器仍exited。未操作原任务、迁移、真实同步、正式后置、定义发布或59191。

真实预览使用正常登录后的新套件API和数据库终态，新套件没有专属UI，本次不冒称浏览器任务业务闭环。最终证据：[final-proof.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/final-proof.json)；加载白名单：[candidate-manifest.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-20261019/candidate-manifest.json)、[candidate-manifest.json](D:/CodexTaskArtifacts/project-detail-followup/base-sync-original-identifiers-p22-20261019/candidate-manifest.json)。

[最新变更SQL原件摘录](base-sync-original-identifiers-preview-20261019.sql)；[结构化逐项结果](base-sync-original-identifiers-preview-20261019.json)。摘录及生成脚本均不执行SQL。

