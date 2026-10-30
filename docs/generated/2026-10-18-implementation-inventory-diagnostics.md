# 存量实现清单 94 项诊断处理记录

本记录来自 2026-10-17 清理后留下的 94 项诊断，按需求方“区分过期断言和真实问题并修复”授权在 2026-10-18 处理。它是实现与验证记录，不替代 PRD、SDS 或 Feature 完成状态。

后续已对其中75个生产文件逐项审查，形成61项可复用、9项需修复、5项应退役的实现判断，并处理确认缺陷；详见[75项存量实现审查](2026-10-18-legacy-75-implementation-review.md)。下表保留本轮94项诊断的原始类别，不用后续审查覆盖历史记录。

## 分类与处理

| 分类 | 数量 | 处理 |
|---|---:|---|
| 过期断言或误报 | 18 | 1 项注释被误当旧模板路由；13 项共享项目权限被误当旧入口；2 项前端测试误入生产清单；2 项历史切换错误要求当前 Feature 为 Done |
| 清单缺项 | 75 | 补登验收、执行绑定及工程、集成、巡检前端；使用既有待逐 Feature 审查分类，不把登记覆盖等同业务完成 |
| 生产权限缺陷 | 1 | 项目树版本、项目树变更模型改用当前项目查询权限；原权限已在 V72 退役 |

权限修复依据为 `ProjectMasterController` 当前树查询授权和 `V72` 对旧菜单权限的清除。`ProjectBusinessModelContributor` 仅修改两个描述的 `authorizationPolicyRef`。`PermissionBusinessAccessGuard` 的权限、登录身份及租户校验保持生效，新增测试覆盖允许、缺少权限和跨租户拒绝。

数据范围仍由 `LegacyProjectReadBindings` 将 `treeVersion` 绑定到 `rootProjectId`、将 `treeChange` 绑定到 `projectId`，经 `ProjectScopeApi` 解析当前可见项目；通用查询对空范围的 `IN` 条件直接返回空集合。目录可见不授予任何项目数据范围。

历史切换依据为 `tasks/features/F-PROJ-001.md` 的“Task 0～9 历史完成、修订019增量在途”声明。校验器继续检查有效 Requirement/Feature、主 Task、非空切换证据和非空替代路径，并新增替代路径实际存在性检查。这项检查额外发现 `LegacyRequirementAnalysisFixedSections` 指向拆分前的 API 目录，已改为 `pms-module-platform/pms-module-platform-api/.../dynamicform/**`，属于原 94 项之外的 1 项清单路径修正。

所有旧写路由、组合路径、退役类和退役表的运行面检查继续保留；共享权限码不再承担识别旧路由的职责。仅纠正两个模型的权限声明，通用服务端鉴权、数据范围规则及 Feature 状态保持原样。

## 清单补登范围

| 清单项 | 本次原诊断文件数 |
|---|---:|
| `AcceptanceUnclassifiedSurface` | 58 |
| `EngineeringUnclassifiedSurface` | 1 |
| `ExecutionBindingsUnclassifiedSurface` | 5 |
| `IntegrationUnclassifiedSurface` | 2 |
| `ServiceUnclassifiedSurface` | 9 |

上述项沿用兼容枚举 `BLOCKED_BY_SPEC` 和 `REVIEW_PER_FEATURE_BEFORE_REUSE`。其语义由清单说明文档界定为“存量复用尚需逐 Feature 认证”，不表示本次发现新的业务规格冲突，也不授予这些模块整体 Done。具体已认证对象仍由更长路径分类优先。

## 验证

原始清单诊断与定向测试修复前后回执均保留在 `.run/build/inventory-diagnostics-20261017/`。

| 检查 | 实际结果 | 回执 |
|---|---|---|
| `python -B -X utf8 scripts/validate_implementation_baseline_inventory.py` | PASS，94 项原诊断消除，新增替代路径错误也已修正 | `final-validator-output.json` |
| `python -B -X utf8 scripts/tests/test_implementation_baseline_inventory.py` | 36/36 通过；包含旧路由回流、缺少证据、替代代码不存在等失败场景 | `final-inventory-test-suite.json` |
| `ProjectTreeCatalogPermissionTest`（工作树固定工具链和互斥锁） | 修复前 1 项失败、2 项通过；修复后 3/3 通过 | `ProjectTreeCatalogPermissionTest-before-fix-cases.json`、`ProjectTreeCatalogPermissionTest-after-fix-cases.json` |
| `scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests=true') -AllowOfflineRuntime` | BUILD SUCCESS，2:51；22 项既有修复保留校验通过。构建不重复运行上面已经通过的定向测试 | `frozen-backend-build.log` |
| 独立空库初始化 | 官方 B498/V499/V500，461 表、238 触发器；Flyway migrate、validate、重复 migrate 通过 | `browser-evidence/verify-output.txt` |
| 真实 Chromium：普通用户有当前项目查询权限 | UI 登录 userId=2 / INVPOSITIVE，目录显示两个目标模型，分别点击进入详情；模型描述均 HTTP 200 / code 0，字段数 8、10 | `browser-evidence/browser-acceptance.json`、`positive-catalog.png`、`positive-treeVersion.png`、`positive-treeChange.png` |
| 真实 Chromium：普通用户缺少项目查询权限 | UI 登录 userId=3 / INVNEGATIVE，目录中两模型隐藏；直接访问两个模型描述均 HTTP 403 / code 1010006005 | `browser-evidence/browser-acceptance.json`、`negative-catalog.png` |
| 无项目范围时的数据分页 | 正例用户查询两模型，均 HTTP 200 / code 0 / members=[] / COMPLETE；没有新增业务项目夹具 | `browser-evidence/browser-acceptance.json` |

修复前的权限正例失败表现为 Mockito 严格桩校验发现实际申请退役权限，与仅配置当前权限的测试条件不符；修复后两模型均按当前项目权限通过，缺权与跨租户两类拒绝测试继续通过。新产物清单为 `.run/build/releases/e70ce0ba-6b33-4a55-be1f-4c01bb56ab94/manifest.json`，固定 JDK 25 与 `local,dac` 配置。浏览器运行于自有 59299/18199，数据库为 `npdms_release_inventory_4382153354db`；两用户独立登录上下文，均有目录权限，均没有超级管理员角色或退役的 `pms:project-tree:query` 权限。截图与返回码已人工复核，两个上下文的页面异常和模型请求失败均为 0。

隔离准备中的 JDBC 参数、登录定位器及测试脚本错误均在最终验收前修正，未计为业务通过。测试角色按平台 `filterDisableMenus` 规则补齐目标菜单的启用祖先链，未修改产品的权限过滤规则。系统角色与用户通过正式 System API 创建；隔离管理员的临时凭据恢复只作用于本次空库夹具，口令与 Token 不进入验收 JSON。

本轮验证覆盖清单治理、模型目录和模型描述的授权修复，以及空库无项目范围的查询响应；不外推这些模块的全部业务完成，也不替代带业务数据的完整项目树验收。未部署或重启 59191/48182，未提交或推送。

复核后已清理本次 schema、唯一测试数据库账号、浏览器、59299/18199 应用进程及自有 Redis/Flyway 资源；只撤销共享迁移账号在本次 schema 上新增的授权，保留该账号与原有权限。59299、18199、24389 已释放，既有 MySQL/Redis 保持健康，59191/48182 未触碰。本次两份后端日志均已删除 SQL 参数行并通过凭据模式检查；截图、脱敏请求回执和失败脚本留存。清理证据为 `.run/build/inventory-diagnostics-20261017/browser-evidence/cleanup-final.json`。

## 逐项记录

序号与 `diagnostic-map.json`、原始 `validator-output.log` 一致。源路径按当前工作树记录；匹配行、方法、路由和旧切换元数据可在该 JSON 中追溯。

| 序号 | 原诊断对象 | 分类 | 结论与处理 |
|---:|---|---|---|
| 1 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/ProjectTemplateController.java` | 过期断言/误报 | 命中第 45 行普通注释 project-template；正式路由为 /pms/project-templates。只以有路径边界的旧路由判断退役入口。 |
| 2 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectmember/OrdinaryProjectMemberController.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 3 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectmember/ProjectManagerMemberController.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 4 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectmember/ProjectMemberController.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 5 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagebusiness/ProjectStageBusinessController.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 6 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCustomerCorrectionService.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 7 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmember/OrdinaryProjectMemberService.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 8 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagebusiness/ProjectStageApprovalCommandService.java` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 9 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/unified-members/index.ts` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 10 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/contacts/index.vue` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 11 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/detail/StageApprovalPanel.vue` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 12 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/members/ProjectMembersPanel.vue` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 13 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/projects/index.vue` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 14 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/tree/ProjectTreePanel.vue` | 过期断言/误报 | 当前成员、阶段、客户或继承入口复用正式项目权限；不再把共享权限码单独当成旧写入口，旧路由及组合调用继续禁止。 |
| 15 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/model/ProjectBusinessModelContributor.java` | 生产缺陷 | treeVersion/treeChange 描述仍使用退役权限 pms:project-tree:query；改用当前 pms:project:query，保留通用权限和租户校验。 |
| 16 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptance/AcceptanceController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 17 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/acceptancereport/AcceptanceReportController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 18 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 19 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/archivedocument/ArchiveDocumentFileController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 20 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionCertificateController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 21 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/completioncertificate/CompletionConfirmationWorkflowController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 22 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDeliverableSubmissionController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 23 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverable/ProjectDocumentSourcesController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 24 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/deliverablechecklist/DeliverableChecklistController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 25 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 26 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/normalclosure/NormalClosureProcessViewController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 27 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 28 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionResultController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 29 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTaskController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 30 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/controller/admin/satisfaction/SatisfactionTemplateController.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 31 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/AcceptanceDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 32 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptance/ProjectDeliverableSubmissionDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 33 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceActivityDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 34 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportAttachmentDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 35 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancereport/AcceptanceReportVersionDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 36 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/acceptancescope/AcceptanceScopeBindingDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 37 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/archivedocument/ArchiveDocumentDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 38 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 39 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionCertificateDeviceDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 40 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/completioncertificate/CompletionPdfBindingDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 41 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/deliverablechecklist/DeliverableChecklistDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 42 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureApplicationDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 43 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureReviewDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 44 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/normalclosure/NormalClosureSnapshotDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 45 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionBundleDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 46 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/CompletionSatisfactionSigningDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 47 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionAccessGrantDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 48 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionCollectionTaskDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 49 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 50 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 51 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionQuestionnaireTemplateRevisionDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 52 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionRemediationFactDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 53 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 54 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResponseFileDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 55 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 56 | `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/dal/dataobject/satisfaction/SatisfactionResultFileDO.java` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 57 | `pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/controller/admin/ScenarioInstanceController.java` | 清单缺项 | 补登 `ExecutionBindingsUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 58 | `pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/BindApprovalInstanceDO.java` | 清单缺项 | 补登 `ExecutionBindingsUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 59 | `pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventCheckpointDO.java` | 清单缺项 | 补登 `ExecutionBindingsUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 60 | `pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/EventedInstanceDO.java` | 清单缺项 | 补登 `ExecutionBindingsUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 61 | `pms-module-execution-bindings/src/main/java/cn/iocoder/yudao/module/pms/bindings/dal/dataobject/InlineInstanceDO.java` | 清单缺项 | 补登 `ExecutionBindingsUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 62 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 63 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationActions.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 64 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance-report/operationResults.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 65 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/acceptance/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 66 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/archive-document/archiveDocumentContract.runtime.spec.ts` | 过期断言/误报 | 前端测试被当作生产 API；排除测试候选，保留运行代码扫描及负例测试。 |
| 67 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/archive-document/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 68 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/completion-certificate/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 69 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/deliverable-checklist/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 70 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/normal-closure/index.spec.ts` | 过期断言/误报 | 前端测试被当作生产 API；排除测试候选，保留运行代码扫描及负例测试。 |
| 71 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/normal-closure/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 72 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/project-deliverable/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 73 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/satisfaction/index.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 74 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/customerConfirmation.ts` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 75 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/feedback.ts` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 76 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/online.ts` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 77 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/request.ts` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 78 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/rules.ts` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 79 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/inspection/tasks.ts` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 80 | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/integration/index.ts` | 清单缺项 | 补登 `IntegrationUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 81 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance-report/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 82 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 83 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/archive-document/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 84 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/completion-certificate/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 85 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/deliverable-checklist/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 86 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/index.vue` | 清单缺项 | 补登 `AcceptanceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 87 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/site-survey/index.vue` | 清单缺项 | 补登 `EngineeringUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 88 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/rules/index.vue` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 89 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/task-detail/index.vue` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 90 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/tasks/index.vue` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 91 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/inspection/workbench/index.vue` | 清单缺项 | 补登 `ServiceUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 92 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/integration/index.vue` | 清单缺项 | 补登 `IntegrationUnclassifiedSurface`；沿用 `BLOCKED_BY_SPEC`，复用仍须逐 Feature 审查。 |
| 93 | `tasks/features/F-PROJ-001.md` | 过期断言/误报 | F-PROJ-001 Task 0～9 历史完成有效，修订 019 增量 IN_PROGRESS 不撤销旧切换；继续要求 Feature Task、切换证据和实际替代实现。 |
| 94 | `tasks/features/F-PROJ-001.md` | 过期断言/误报 | F-PROJ-001 Task 0～9 历史完成有效，修订 019 增量 IN_PROGRESS 不撤销旧切换；继续要求 Feature Task、切换证据和实际替代实现。 |
