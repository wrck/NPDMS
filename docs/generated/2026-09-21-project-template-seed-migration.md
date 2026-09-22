# 项目模板种子替换与升级验证

需求依据：PM-03、PM-11、PRD 3.2、F-PROJ-009；2026-09-21需求方确认新库同时移除旧模板相关示例项目及依赖数据，已有库仅停用旧示例并保留历史。

## 交付

目标工作树：`codex/domain-migration`。新增V336、两份生成脚本、新库Compose覆盖文件、验收配置资产、六个场景草稿及针对性验证。原历史SQL、原V208/V209/V331生成器和其他任务修改未覆盖。

| 种子 | 状态 | 范围 |
| --- | --- | --- |
| FPROJ009_DELIVERABLE_FIX_20260921 | ACTIVE，原PUBLISHED修订 | 原验收S1—S6，14任务、11门禁、29交付件（20必需）及4个业务视图 |
| PRD_20260921_DS_ENG | DRAFT | 直签工程，启动会、初验、终验 |
| PRD_20260921_DS_GEN | DRAFT | 直签普通，无启动会，初验、终验；实施方式不额外限定 |
| PRD_20260921_CH_ENG | DRAFT | 非直签工程，启动会、现场验货、终验 |
| PRD_20260921_CH_DIR | DRAFT | 非直签原厂直服，无启动会、无初验 |
| PRD_20260921_CH_SUP | DRAFT | 非直签原厂督导，现场服务单、终验 |
| PRD_20260921_PRE | DRAFT | 仅S0、S4及配置调试、业务联调；明确选择 |

原验收来源：[交付件修复验收记录](2026-09-21-project-deliverable-fix-acceptance.md)，提交`ba0db4542`，项目`992203060011 / PJT2026000009`，修订`993009001603`。派生场景不能继承该项目的浏览器验收结论。

## 实际验证

使用既有`npdms-domain-test-mysql-1`容器内本次创建的隔离验证Schema，未迁移实际应用数据库。验证结束后已删除本次创建的验证Schema，保留日志证据。

- 空库Flyway实际执行279个迁移至V336成功；最终7个模板（1 ACTIVE、6 DRAFT）、4个业务视图，明确移除的旧示例项目数量为0。
- 同一空库初始化结果再次执行Flyway，校验通过，无待执行迁移。
- MySQL隔离验证：V336重复执行不新增、不重复增加旧根版本；精确匹配旧示例停用；不同租户及不同creator不受影响；旧PUBLISHED修订和模拟项目冻结快照保持原值。
- MySQL失败路径：新种子身份冲突被拒绝，旧根保持原状态、无部分插入；新库入口拒绝非空Schema。
- `python -m unittest scripts.tests.test_project_template_seed_pack`：5项通过，覆盖验收快照保持、场景差异及引用完整性、升级写边界、SQL分隔、初始化路径结构保持。
- `ProjectTemplateSeedPackTest`：使用实际`TemplateCompiler.compileVersioned`编译原验收文档和6个新场景，1项测试通过；这是结构与规则编译验证，不等于发布或业务验收。
- 两份生成脚本的`--check`用于复核生成内容。完整初始化首次暴露的V161旧示例依赖、V289重复改名已在新库路径中修复并重跑通过，原历史迁移检查未降低。

本地证据位于`.run/template-seed-fresh-flyway.log`、`.run/template-seed-fresh-replay.log`、`.run/template-seed-fresh-counts.txt`及项目模块Surefire报告。

## 完成边界

脚本交付阶段未在实际应用库执行V336；后续经需求方授权已执行，结果见下节。未发布6个派生场景，未重新进行浏览器验收，也未通过SQL部署闭环流程。原闭环BPMN已随资产保存，目标运行环境须通过既有BPM入口部署并核对审核人映射；S0指派、计划审批、满意度时点和督导业务绑定仍须在场景发布时逐项核对。

操作方式、路径区别和重复生成命令见[模板种子说明](../../sql/template-seeds/README.md)。

## 2026-09-21 经授权执行 npdms-domain-test

需求方明确要求执行`npdms-domain-test`迁移。核对Compose项目标签、容器和库名后，在`npdms-domain-test-mysql-1 / npdms_domain_test`成功执行V330、V331、V332、V333、V334、V336，当前版本V336。V335此前已执行，V330—V334按乱序迁移补齐；设备领域V337未纳入本次模板迁移。

沿用该库上一轮已验证的`.run/device-entry-flyway`历史执行目录，追加本次6个SQL至`.run/template-seed-domain-flyway`，通过Compose覆盖文件`.run/template-seed-domain-flyway.yaml`执行。未使用新库初始化路径，未改写或repair历史记录。预校验仅允许这批已核对的Pending迁移；实际migrate和迁移后validate使用正常校验。

- 6个迁移全部成功；迁移后Flyway校验通过，重复执行返回`No migration necessary`。
- 导入1个ACTIVE验收模板、6个DRAFT场景及4个业务视图。
- 15个精确名单旧示例最终均为RETIRED：本轮停用14个，另1个此前已停用。用户模板`DETAIL_REMEDIATION_20260920`仍为ACTIVE。
- 迁移前后一致性逐行比对：90个项目、16个既有PUBLISHED修订、8个计划版本、56个阶段执行合同、117个任务执行合同，共287条记录，内容未变。

执行证据：`.run/template-seed-domain-migrate.log`、`.run/template-seed-domain-validate.log`、`.run/template-seed-domain-replay.log`、`.run/template-seed-domain-history-before.json`和`.run/template-seed-domain-result.txt`。此次为数据库迁移完成，不表示派生草稿已发布或目标环境浏览器验收完成。
