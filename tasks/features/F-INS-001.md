# F-INS-001 巡检规则版本与字段配置基础

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> Technical Plan Gate：`PASS / NPDMS-FINS001-TECHPLAN-20260830-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：`Q-FINS001-005/006`阻断Task 8安全审核有效性、发布放行和生产审核入口；停用切片与内部发布CAS基础已分别通过独立GO，当前最近Gate仍为Task 8
> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`
> 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`
> 锁定实施输入提交：`27b5b4b3`
> master集成映射：`来源分支PRD修订009～012统一收口为master修订011；来源Flyway V148～V150重编号为master V173～V175`

## 当前最小工作单元

* Task 8为当前最小工作单元：实现安全审核、原子发布、停用、幂等和审计。Task 7已通过`NPDMS-FINS001-TASK7-SECOND-REVIEW-20260902-01`；Task 8发布事务必须重新读取基础平台字典与AST权威名称并刷新发布快照，不得信任草稿名称。

## 已完成

* 已读取PRD V1.8、工程链、文档治理、SRV领域规格及巡检相关SDS。

* 已确认最近适用Gate为Feature Ready，INS-03与INS-09应合并为一个纵向业务Feature。

* 已完成旧规则后端、前端、迁移、菜单、字典和测试审计，结论为`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。

* 已由独立裁决关闭30秒上限冲突，并由master `CHG-PRD-2026-09-02-011`统一承接：只允许1～30秒，不建设未定义的超30秒审批分支。

* 已在正式SDS冻结规则状态、八字段、命令从属关系、产品适用关系、安全审核事实、权限、API、数据、页面和验收边界；master修订011补齐草稿/发布完整性、`NUMBER`和`PASSED/REJECTED`。

* 已明确第三方采集平台、设备凭证和任务执行不在本Feature实现范围。

* 已生成并自审唯一Technical Plan，覆盖AST外部Gate、安全审核、后端、迁移、前端、测试、真实浏览器和追溯收口；历次NO-GO问题已整改，独立复审GO。

* 已通过master `CHG-PRD-2026-09-02-011`和`F-AST-002` Spec/Task关闭AST状态源缺口；F-INS-001仅验收消费，不允许Inspection猜测产品类型、直读AST业务表或实现CRM/MES连接器。

* Task 1已建立仓库级唯一计划与临时副本扫描、锁定输入祖先及正式输入漂移检查、复用审计旧资产相对锁定提交的Git差异保护、Owner/查询/Mapper参数边界及显式Requirement ID追溯门禁；9项Python定向测试PASS。

* Task 2已验收F-AST-002 Implementation Done证据、Inspection专用双查询API形状、Query身份边界、结果事实字段及Service仅依赖`pms-module-asset-api`边界；3项Maven契约测试实际执行PASS，22模块Reactor BUILD SUCCESS，依赖树仅包含`pms-module-asset-api`。

* 唯一Technical Plan已明确Task 2只做外部Gate/API边界预验收，不核销未知、停用、未解析、跨租户、空设备范围或AST异常下的Inspection生产消费行为；这些义务分别在Task 7/8发布预检与发布、Task 9工程师选择入口实现后验证；`git diff --check` PASS。

* Task 3已冻结巡检侧`InspectionRuleExplicitAuthorizationApi`端口；守卫核对当前租户、用户、专用权限码和`RBAC_PERMISSION`并失败关闭，System侧适配器及守卫Bean装配按外部能力后置，未提供虚假默认实现。

* Task 3纯内容摘要只覆盖按执行顺序规范化的命令、超时、继续策略和预期正则，拒绝重复或非正数顺序，输出小写64位SHA-256；7项Java定向测试、9项Python门禁和38模块server package PASS，独立复审GO。

* Task 4已完成revision纯领域校验：正式机器码、受限正则预算与禁止结构、四类Secret扫描和稳定错误码均已实现；17项定向测试、35项service全量测试、9项Python门禁、22模块测试Reactor、38模块server package、追溯与差异检查PASS；独立复审核销编号`NPDMS-FINS001-TASK4-CLOSEOUT-REVIEW-20260901-01`。

* Task 7已实现新稳定身份草稿创建、DRAFT整体保存、已发布/停用revision复制和无副作用发布预检：数据库唯一约束兜底身份并发，CAS失败不替换从属行，命令和产品类型在事务内硬替换；四入口服务层维护权限守卫、字典/AST失败关闭及权威名称候选已补齐。Java定向46项、service全量69项（其中MySQL默认跳过8项）、Python门禁24项、22模块package和diff检查PASS；随后真实`npdms_test` MySQL 8项以`Skipped: 0`独立执行PASS。故障注入仅存在于测试`TestApplication`。

* Task 8停用切片已实现独立停用权限、当前`PUBLISHED -> DISABLED`聚合锁与If-Match/CAS、平台幂等重放/冲突、成功审计及`REQUIRES_NEW`拒绝审计；真实`npdms_test` MySQL证明停用业务写、幂等完成和成功审计共同提交，成功审计或幂等完成故障时共同回滚且只保留拒绝审计。相关真实MySQL矩阵25项`Failures: 0 / Errors: 0 / Skipped: 0`，补充跨租户与权限依赖异常后聚焦单元14项通过，`git diff --check` PASS，提交终审`GO NPDMS-FINS001-TASK8-DISABLE-SUBMIT-GO-20260902-02`。

* Task 8内部发布CAS基础已实现共享聚合锁、权威字典/产品类型名称快照更新、旧当前发布版停用与目标草稿发布CAS；草稿保存同步采用相同锁顺序。真实`npdms_test` MySQL 17项全部通过，其中4项证明原子换版、最终写故障整体回滚、两个草稿并发最多一个成功及保存/发布无混合快照；service非IT单测93项和23模块package通过，独立终审`GO NPDMS-FINS001-TASK8-PUBLISH-CAS-FOUNDATION-GO-20260902-01`。该基础无公开Service/Controller调用方，不代表发布放行。

* master已从`feat-inspection-feature-xkjuCC@7fe168af`选择性接收Task 4～7、Task 8停用及内部发布CAS基础，来源迁移V148～V150重编号为V173～V175；未接收源工作树未提交变更，也未把分支Gate转记为Feature Done。master复验PRD语义/基线与DU校验PASS，F-INS Python门禁24项PASS，JDK 25 Maven定向测试85项`Failures: 0 / Errors: 0 / Skipped: 17`且23模块Reactor `BUILD SUCCESS`；17项MySQL用例因本次未启用外部测试库而保持跳过，沿用来源提交内已记录的真实MySQL证据但不将本次结果伪报为重跑通过。

## 首轮Technical Plan评审核销

| 原问题                  | 整改位置                      | 核销方式                                                     |
| -------------------- | ------------------------- | -------------------------------------------------------- |
| 1. 锁定规格提交未产生         | Plan Locked Inputs、Task头部 | 历史锁定值`829a00ac`已由包含修订010及F-AST-002 Done的实施输入`68bc56ec`替代 |
| 2. AST DTO、文件和责任不闭合  | Plan Task 2、Q-FINS001-002 | Owner边界已明确并转外部Gate；AST交付缺口持续阻断发布/选择/Done，F-INS仅验收消费      |
| 3. 安全审核依赖无法提供的角色贡献解析 | Plan Task 3、8             | 改为服务端专用权限守卫，不解析角色贡献关系                                    |
| 4. 幂等与审计未绑定平台公开API   | Plan Task 8               | 复用`PlatformCommandExecutionApi`、`OperationAuditApi`      |
| 5. 产品类型示例可能猜造        | Plan Task 2、5             | 只引用AST Owner批准值；未确认则阻断                                   |
| 6. 前端测试位置不符合惯例       | Plan Task 10              | 测试放在页面目录`inspection-rule.spec.ts`                        |
| 7. 静态门禁伪造RED         | Plan实施边界、Task 1           | 新目录不存在时PASS；新能力先实现后测试                                    |
| 8. 分页名称与产品类型筛选语义不明   | Plan Task 6               | 固定`ruleNameKeyword`包含匹配和XML `EXISTS`                     |

## 阻断

`Q-FINS001-005`待确认同一revision与摘要多次审核的生效/覆盖语义；`Q-FINS001-006`待确认显式RBAC审核授权事实Provider及是否批准扩展Yudao System公开API。两项仅阻断安全审核生产入口、审核有效性选择和发布放行；Task 8停用、平台幂等、拒绝/成功审计及发布CAS基础可独立继续。Task 4～7均已有独立GO。

## 已知边界

* 旧接口、页面、菜单和旧类保持不变且不双写；本Feature交付旧`pms_srv_rule`可证明字段的受控前向迁移，不完整记录进入迁移问题或兼容只读。

* 附件或旧页面只帮助取得名称和界面样式，缺行、缺名或数量差异不构成阻断。

* `srv_inspection_task_rule_snapshot`及INS-01/02运行时消费后置，不提前实现。

* Yudao基础平台未获明确允许不得修改；仅复用其现有通用能力。

## 检查点

基线=master修订011；当前Gate=Task8；证据=来源`7fe168af`的停用与内部发布CAS基础已选择性集成并在master完成JDK25构建/85项定向测试；阻塞=Q-FINS001-005审核生效语义、Q-FINS001-006显式RBAC Provider；下一步=裁决后闭合安全审核与完整发布，未裁决前不得增加生产入口或宣称Feature Done。
