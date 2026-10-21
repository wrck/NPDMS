# P18公司授权依据、缺失范围及真实预览

状态：**来源预览闭合，正式写入前置仍保留**。本报告是当前代码与隔离库真实证据，不改变PRD、ADR或正式授权规则；此前失败回执按原时点保留。

## 结论与正式依据

公司授权不是十项目试点的固定限制。正式合同查询和关系维护必须按操作者的当前公司范围执行；新BASE_P18错误地把这项正式COM保护无条件用于已批准的整表来源预览。现已纠正该实施边界：来源照常读取，未授权公司的正式合同/关系不查询、不规划写入，只登记待授权输入。整表审核不授予当前COM权限。

- [ADR-0038](../../decisions/0038-commerce-contract-administrator-company-scope.md)第1～6项：SYSTEM拥有UserCompanyDepartmentScope；当前companyCode精确集合约束合同目录、订单及项目合同关系维护；空范围拒绝明细/写入，功能权限不扩大公司范围，不能从合同或历史项目关系推导授权。
- [已批准17列整表提案](../2026-10-12-dppms-project-contract-relation-table-proposal.md)：整表承接、来源归并及历史审计已获审核，同时明确保留当前授权、外键、日期和唯一键。十个项目是当时的验证参数，不是整表SQL白名单。
- [普通关系维护ContractRelationCommandService](../../../pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractRelationCommandService.java:36)在写入前检查项目MANAGE、当前公司范围及合同所属公司（第38～58行）。这条普通业务路径不依赖试点名单。
- [既有迁移Owner](../../../pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/ProjectContractRelationMigrationService.java:33)在第42、72、103、144行检查正式引用解析/关系预览及写入的公司范围；其preview检查的是正式目标。本轮仅追溯，未调用或改动该原Owner。

原先报告将未覆盖001/002/003一并判为“整表来源预览必须阻断”不准确；该结论现撤回，正式COM授权依据本身保持。

## 具体代码及来源

| 位置 | 修复前行为与本次处理 |
|---|---|
| [P18.createParameters，第18行](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:18) | 最初在6a56c3f2f加入getActiveScopes，空范围无条件失败。现在仅来源预览允许空集合；真实执行仍拒绝。API异常不吞掉。 |
| [P18.syncDataInsertBefore，第68～83行](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:68) | 非空原公司不在当前范围时曾无条件抛P18_PROJECT_COMPANY_NOT_AUTHORIZED；dabeb0df6只处理了缺公司/缺合同预览，遗漏这条分支。现在预览登记待授权，且不将该公司加入正式合同查询键；实际同步仍按同一错误码拒绝。 |
| [P18，第62行](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:62) | 所有原项目ID仍调用BaseSyncProjectReferences。既存正式项目的身份、状态、代码/公司一致性及当前管理权均保留。 |
| [P18，第80、94行](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:80) | 已有独立业务或原始来源绑定不能用“待授权”掩盖，继续拒绝；不接管原任务/旧绑定。 |
| [P18，第98、107行](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:98) | 按公司汇总待授权业务关系；待输入原行计入read，不生成目标行、合同ID、插入/更新/未变数。 |
| [BaseSyncService，第91行起](../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/BaseSyncService.java:91) | 真实登录、租户、用户有效性、pms:integration:execute、pms:commerce:contract:relate及本租户来源连接检查保持。 |

[来源XML](../../../pms-module-integration/src/main/resources/base-sync-source/P18SourceMapper.xml)保持不变：pm_project_contract、pm_project_group、pm_project_group_relationship、规范pm_project目录四份查询各一次；目录附带t_company原公司编码。projectType='10'是用服类型，不是十项目数量或名单。三份原表查询无派生嵌套，项目目录一层；本次没有增加SQL、备份表、镜像或回填。

本次候选包以此前运行包为底，仅10个P18类族条目存在差异；P18两个XML以及其他全部包条目保持，包含P10/P14/P33、原任务、API、配置和迁移资源。代码来源及候选逐项清单在证据目录。

## 当前缺失范围

核查于2026-10-20 02:50（Asia/Shanghai）完成。实际actor=1，两条当前有效scope均为DPTECH-DEMO；全库19名用户、38条有效scope也只覆盖DPTECH-DEMO。system_company中001、002、003均存在，但公司资料不等于账号获得授权。

| 公司编码 | 原始复合来源身份 | 归并后业务关系 | 当前缺项 |
|---|---:|---:|---|
| 001 | 80,650 | 80,374 | 无当前有效范围 |
| 002 | 1,823 | 1,804 | 无当前有效范围 |
| 003 | 195 | 195 | 无当前有效范围 |
| NULL | 8 | 8 | 源公司缺失 |
| 合计 | 82,676 | 82,381 | 82,373组待正式授权，8组待公司事实 |

已知公司部分共82,668条原始身份、82,373个业务关系缺当前公司范围。源公司转换继续使用既有01→001、02→002、03→003规则；未知非空编码仍拒绝，未将来源编码或整表审核直接变成权限。

8条缺公司记录完整身份如下；pm_project.compId、t_company.id及compCode均为空，当前没有可用的同原ID正式公司事实补入：

| 原projectId | 原projectCode | 原合同号 | contractSourceId:memberId | 原compId |
|---|---|---|---|---|
| 48275 | 16202020093001N-1 | 31020210107A14 | 50747:49844 | NULL |
| 48276 | 16202221060805N-1 | 31020210622A30 | 50748:49845 | NULL |
| 48277 | 16202221032704N-2 | 31020210803A12 | 50749:49846 | NULL |
| 48278 | 16202620070207N-1 | 31020211021A04 | 50750:49847 | NULL |
| 48279 | 16200221082304N-1 | 31020211027A12 | 50751:49848 | NULL |
| 48280 | 16202421110402N-1 | 31020211112A11 | 50752:49849 | NULL |
| 48281 | 16202421070802N-1 | 31020220311A16 | 50753:49850 | NULL |
| 48282 | 16202421120802N-1 | 31020220314A13 | 50754:49851 | NULL |

## 全量真实预览

仅运行独立新BASE_P18（任务2111504251568156674）：

| Run | 开始/结束（Asia/Shanghai） | 终态 | read | inserted / updated / unchanged / deleted |
|---|---|---|---:|---|
| 2112256535977709570 | 2026-10-20 02:56:16.673 ～ 02:56:29.325 | PREVIEW_READY | 82,676 | 0 / 0 / 0 / 0 |

真实预览API返回code=0，已核实数据库自然终态、开始/结束时间及active_run_id清空。82,676个原始身份归并为82,381个业务关系；没有伪造正式关系、合同ID、来源绑定或写入预测。

回执原文：

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED
P18_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target
P18_COMPANY_AUTHORIZATION_PENDING:001=80374,002=1804,003=195
P18_COMPANY_SOURCE_PENDING:8
P18_CONTRACT_REFERENCE_PENDING:82381
```

最后一项指“尚未核验的正式引用”，不能解释为已经按公司逐合同证明全部缺失：未授权公司根本未进入此次任务的正式COM查询。单独只读库容量统计表明com_contract当前0条，也未据此跳过实际写入所需的精确匹配。

24项任务的最新Run现在均为PREVIEW_READY。本轮只重跑P18；其他23项沿用各自已记录的真实回执，候选包内实现不变。该状态说明来源读取预览已完成，不代表所有正式表输入、授权、容量及写入路径均已就绪。

## 实际写入仍需的具体前置

| 缺项 | 正式依据与可执行处理 |
|---|---|
| 覆盖001/002/003的合法执行上下文（82,373组） | ADR-0038及P18执行分支仍强制要求。应由有权主体按现有授权生命周期提供对应公司范围和操作权限；本轮不自动新增scope、不冒用其他账号、不放宽公司范围。 |
| 8原项目公司事实 | 由权威来源补充公司，或在同原ID正式项目存在后采用其已核验公司事实。不能从合同号推断、不填占位；来源预览已保留8条。 |
| 真实正式项目和管理权 | 原ID必须对应正确的同租户正式项目、状态、类型/编码及当前MANAGE范围。缺父对象在来源预览可延后，不等于实际执行可省去。 |
| 正式合同输入 | 当前com_contract有效行0。实际写入前须按同租户、已授权公司及原合同号精确匹配唯一正式合同；本轮不运行真实同步补输入。 |
| 独立绑定多来源容量 | uk_int_base_sync_binding_target仍是(tenant_id,task_id,object_key,target_id)唯一索引，不能同时保存同一目标下本次需要的多个来源。实际执行在写入前继续拒绝；本轮不改索引、不运行DDL或迁移。 |

P10的name仍为VARCHAR(128)，P33的install_location仍为VARCHAR(255)，依用户指令另行处理。两项既定完整原文承接规则不变，本轮不改容量或重问裁决。

## 验证与隔离证据

- 修复前失败测试真实复现P18_PROJECT_COMPANY_NOT_AUTHORIZED；修复后30项定向检查全过（身份/授权13、独立隔离15、Mapper装配2）。覆盖未授权公司不查询正式合同、混合公司只查授权子集、空范围禁止执行、既有绑定和项目MANAGE保护、未知原公司拒绝。
- 原46任务、209运行、211绑定的全部字段摘要逐行比较无差异；180项正式数据计数/最大ID/原任务版本等保护无变化。本轮新来源绑定0、无真实同步Run、无在途任务。
- 仅按已有授权重启59284，当前PID25036。Flyway、Flowable自动结构更新、Quartz、Batch及事件驱动关闭；迁移容器处于exited。npdms_domain_test及59191未操作。
- 无迁移、DDL、真实同步、公司授权/来源数据修正或业务后处理。没有以重跑无关检查代替真实预览。

[机器可读结果](P18-company-authorization-audit-preview-20261020.json)。原始审计、失败/成功测试、候选清单、运行回执及全字段保护证明位于`D:/CodexTaskArtifacts/project-detail-followup/base-sync-p18-authorization-audit-20261020/`；生成与检查入口为audit-p18-completeness.py、test.py、freeze.ps1、reload.ps1、preview.py和verify-protection.py。
