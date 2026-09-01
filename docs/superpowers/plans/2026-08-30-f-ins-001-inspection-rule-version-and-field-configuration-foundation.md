# F-INS-001 巡检规则版本与字段配置基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在`pms-module-service`内建立Inspection领域唯一的巡检规则稳定身份与不可变revision真值，完成八字段、命令列表、产品类型适用范围、安全审核、原子发布、停用、历史读取和工程师选择闭环，同时保持旧`srv-rule`实现不变。

**Architecture:** 新实现使用独立`inspectionrule`包和`/api/v1/pms/inspection-rules`接口，不修改、不双写旧`pms_srv_rule`、旧Controller、旧页面和旧权限。规则稳定身份、revision、命令、产品类型快照与安全审核事实分别持久化；领域纯规则负责字段、命令、正则、阈值和秘密扫描，应用Service负责租户、专用权限、AST前置、CAS、摘要、事务和审计编排。AST设备产品分类公开查询契约是发布和选择闭环的前置门禁；安全审核由服务端再次校验`pms:inspection-rule:security-review`，不解析角色贡献关系，不在Inspection内建立第二套产品类型或角色主数据。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus/XML、MySQL 8.4、Flyway 11、JUnit 5、Mockito、Vue 3.5、TypeScript 6、Element Plus、pnpm 9.15、Vitest、Chrome DevTools真实浏览器。

**Locked Inputs:**

- 锁定实施输入提交：`68bc56ec`；该提交包含PRD V1.8修订010、当前F-INS-001 Feature Spec与SDS、F-AST-002公开契约及其Implementation Done状态；实施前必须确认该提交是当前HEAD祖先，且下列正式输入未被后续未评审变更替代

- Requirement：`INS-03@V2=PARTIAL`、`INS-09@V2=FULL`、`NFR-02@V2`支撑
- Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`
- 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
- Feature Ready：`READY / GO NPDMS-FINS001-FEATURE-READY-20260830-01`
- 正式SDS：`docs/design/04-module-design.md`、`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`14-security-design.md`、`20-test-design.md`
- 查询规范：`docs/coding/database-query-interface.md`

---

## 1. 实施边界

- 只实现INS-03的规则维护、发布、只读选择和历史解释子闭环，以及INS-09全部八字段配置义务；不实现INS-01任务创建、任务规则快照、INS-02执行、INT-12下发、INS-04预检、INS-05报告、INS-06问题、INS-07归档或INS-08误报。
- 新实现位于现有SRV物理模块`pms-module-service`，不创建空`-api`模块，不依赖其他模块的`-biz`、Service、Mapper、Repository或业务表。
- 旧`pms_srv_rule`、`SrvRuleController`、`SrvRuleServiceImpl`、旧前端`srv-rule`页面、旧菜单、旧字典和旧权限保持原样；新实现不删除、不改名、不代理、不双写。
- 正式状态只允许`DRAFT -> PUBLISHED -> DISABLED`；客户端不得直接提交状态，发布和停用只能通过action API。
- 已发布和已停用revision只读；修改必须复制为同一稳定身份的新草稿。
- 单命令超时默认30秒，只允许1～30秒正整数；不实现31秒及以上或任何超时审批分支。
- 发布时必须重新校验字典、AST产品类型、安全审核摘要、正则、阈值、命令顺序、秘密扫描、租户、权限和CAS；任一失败保持草稿，旧发布revision继续有效。
- 产品类型由AST公开契约提供；Inspection只保存稳定编码和发布时显示名称快照，不新增产品类型表或从`ast_*`表直读。
- 安全审核只记录具备`pms:inspection-rule:security-review`专用权限的当前用户对命令与正则内容摘要作出的结论；不新增审批流程、节点、固定组织角色或规则生命周期状态。
- 所有新增查询遵守“一场景一Query对象”；简单单表查询使用`LambdaQueryWrapperX`，联表、动态集合、锁查询和并发发布SQL进入Mapper XML；禁止SQL注解、`${}`和`.last(...)`。
- 新能力按“最小正向实现 -> 补充定向测试 -> 运行定向测试 -> 必要整改 -> 完整回归”推进；修复既有缺陷时可先补可稳定复现该缺陷的测试。
- 每个Task先完成该工作单元列出的最小实现，再补同Task所列测试并运行；静态保护门禁可在实现前建立，但不得因新目录或新文件尚不存在而失败。
- 本计划不授权提交。各Task中的提交信息仅表示建议逻辑分组，执行时除非用户另行明确要求，不运行`git commit`。

## 2. 前置门禁与依赖图

```text
Task 1 静态实施门禁与唯一性检查
  -> Task 2 AST产品分类外部Gate与API边界预验收
  -> Task 3 安全审核专用权限与内容摘要契约
  -> Task 4 纯领域规则
  -> Task 5 前向Schema、字典、菜单和受控迁移
  -> Task 6 DAL与查询契约
  -> Task 7 草稿、整体保存、复制和无副作用校验
  -> Task 8 安全审核、原子发布、停用与幂等/CAS
  -> Task 9 工程师可选规则投影
  -> Task 10 管理端API与页面
  -> Task 11 自动化、真实MySQL和构建验证
  -> Task 12 真实浏览器验收
  -> Task 13 追溯、自审和Feature收口
```

- Task 2仅验收AST Owner独立交付的公开契约、API形状、模块依赖和后续消费所需事实字段，不创建Inspection生产消费组件，也不创建、修改或迁移任何AST文件。未知、停用、未解析、跨租户、空设备范围及契约不可用下的Inspection真实失败关闭，分别由Task 7/8发布预检与发布、Task 9工程师选择的生产入口验证。对应AST Feature Spec与当前Task未建立时标记`BLOCKED_BY_SPEC`并登记`docs/decisions/open-questions.md`；不得宣称发布、选择或Feature闭环完成。
- Task 3未通过时，不得以硬编码角色、仅前端按钮替代服务端审核守卫。审核主体采用租户内显式授予`pms:inspection-rule:security-review`的动态权限包成员，不要求追溯“哪个角色贡献权限”，不新增固定角色。
- Task 5最终Flyway编号必须在实施当日重新扫描；当前按已存在V1～V131且F-CUT-001计划占用V132～V133，预留V134～V136。若编号已占用，只允许前向改为新的连续空闲编号，不修改已执行迁移。

## 3. 文件职责

| 路径 | 职责 |
|---|---|
| `pms-module-service/.../domain/inspectionrule` | 状态、不变量、正则预算、阈值、命令顺序、秘密扫描和内容摘要输入规范 |
| `pms-module-service/.../service/inspectionrule` | 聚合编排、租户/权限/AST/审核/CAS/事务/幂等与审计 |
| `pms-module-service/.../dal/dataobject/inspectionrule` | 五张正式目标表DO |
| `pms-module-service/.../dal/mysql/inspectionrule` | 场景化Query、Mapper与并发锁SQL |
| `pms-module-service/.../controller/admin/inspectionrule` | `/api/v1/pms/inspection-rules` HTTP契约和权限注解 |
| AST Owner独立Feature/Task交付的`pms-module-asset-api`产品分类契约 | Task 2仅作外部Gate输入与消费验收；本计划不创建、不修改、不迁移AST文件 |
| `sql/migrations/V134...V136` | 新表、字典/菜单/权限/示例数据、旧完整记录受控前向迁移与不完整记录旧兼容只读保护 |
| `yudao-ui/.../api/pms/service/inspection-rule` | 新API类型、If-Match与action请求 |
| `yudao-ui/.../views/pms/service/inspection-rule` | revision管理页、编辑器、审核记录、发布校验和工程师选择演示入口 |
| `scripts/tests/test_fins001_*` | 唯一计划、旧实现保护、迁移、Owner边界、查询与秘密静态门禁 |
| `tasks/features/F-INS-001.md` | Technical Plan和Implementation Done唯一状态记录 |

---

### Task 1: 建立实施输入、唯一计划与旧实现保护门禁

**Files:**

- Create: `scripts/tests/test_fins001_plan_and_scope.py`
- Create: `scripts/tests/test_fins001_legacy_preservation.py`
- Create: `scripts/tests/test_fins001_owner_and_query_boundary.py`

- [ ] **Step 1: 编写唯一Technical Plan检查**

测试扫描`docs/superpowers/plans`、`features`和仓库Markdown，断言F-INS-001只有本文件一个当前Technical Plan；允许Feature Spec、Task和索引引用该路径，不允许第二个`f-ins-001`计划或任何并行临时副本。

```python
PLAN = "docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md"
assert Path(PLAN).is_file()
assert current_plan_candidates("F-INS-001") == [PLAN]
```

- [ ] **Step 2: 编写旧实现保护检查**

将以下旧资产纳入相对锁定实施输入提交的Git差异保护，并让测试在本Feature实施期间拒绝已提交、暂存、未暂存或未跟踪变化：

```text
pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/srvrule/SrvRuleController.java
pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/srvrule/SrvRuleServiceImpl.java
pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/srvrule/SrvRuleMapper.java
yudao-ui/yudao-ui-admin-vue3/src/api/pms/service/srv-rule/index.ts
yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/srv-rule/index.vue
sql/migrations/V14__pms_service_tables.sql
sql/migrations/V15__pms_service_menus.sql
sql/migrations/V16__pms_business_button_permissions.sql
sql/migrations/V19__pms_test_data.sql
sql/migrations/V20__pms_test_data_expansion.sql
sql/migrations/V43__pms_dict_types.sql
```

测试按复用审计锁定旧后端Controller/Service/Mapper/DO/VO目录、旧前端API与页面目录，以及V14/V15/V16/V19/V20/V43迁移文件相对锁定输入提交均无已提交、暂存、未暂存或未跟踪变化；同时扫描新`inspectionrule`包，禁止出现对旧`SrvRuleService`、`SrvRuleMapper`的依赖或对`pms_srv_rule`的运行时写入。

- [ ] **Step 3: 编写Owner与查询规则检查**

扫描新增Service/Mapper/XML：允许访问`srv_inspection_rule*`五张Inspection自有表和公开`-api`；禁止直接出现`ast_`、`proj_`、`cus_`、`cut_`业务表，禁止SQL注解、`${}`、`.last(...)`、Mapper接收Controller VO或`Map`查询条件。查询方法默认只允许零或一个参数，主键及稳定复合唯一键例外必须显式白名单，不得统一放行两个参数。测试还必须核对本Task列出的测试资产均由当前Plan认领，且当前Task保留`INS-03/INS-09` Requirement ID。

- [ ] **Step 4: 运行静态保护测试**

Run:

```powershell
python -m unittest scripts.tests.test_fins001_plan_and_scope scripts.tests.test_fins001_legacy_preservation scripts.tests.test_fins001_owner_and_query_boundary
```

Expected：唯一计划、锁定提交和旧文件SHA检查立即PASS；Owner/查询扫描对尚不存在的新目录视为PASS。后续Task均在最小实现完成后补充并运行对应定向测试。

- [ ] **Step 5: 建议逻辑分组**

建议提交信息：`test(service): 建立F-INS-001实施边界门禁`

---

### Task 2: 预验收AST设备产品分类外部Gate与API边界

**Files:**

- Read only: AST Owner独立Feature Spec、当前Task、公开`pms-module-asset-api`契约及其交付证据
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/integration/asset/AssetProductTypeContractTest.java`
- Modify: `pms-module-service/pom.xml`，仅在外部公开契约已正式交付后增加`pms-module-asset-api`消费依赖

**Required Gate input:**

```text
AST Owner独立Feature Spec
AST Owner当前Task
pms-module-asset-api公开产品分类查询契约
AST Owner契约自动化与真实数据来源证据
```

- [ ] **Step 1: 核验AST独立Feature与Task**

确认AST Owner已建立独立Feature Spec和当前Task，明确产品分类系统Owner、稳定编码、显示名称、存在/停用事实、设备授权查询、来源版本、CRM/MES映射、租户与数据范围、降级和审计。当前仓库若缺少任一正式资产，将Task 2标记`BLOCKED_BY_SPEC`并登记`docs/decisions/open-questions.md`；不得在F-INS-001计划、分支或迁移中代建AST契约、字段、投影、种子或测试，也不得以现有设备摘要、`conpType`、产品编码、型号、旧`pms_product_type`或手工数据猜测补齐。

- [ ] **Step 2: 核验外部Gate交付证据**

仅在AST独立Feature/Task已建立后，验收其公开契约位于`pms-module-asset-api`且输入输出不暴露DO；批量查询须对每个请求编码返回存在/启用事实，授权设备查询须按租户与设备范围返回稳定产品类型编码和显示名称，未知编码不返回猜测名称，无法访问的设备不泄露其是否存在。AST实现测试、迁移和来源映射证据由AST Owner任务维护，本Task只引用，不修改。

- [ ] **Step 3: 补充Inspection API边界契约测试**

测试只覆盖专用双查询API形状、Query不携带`tenantId/serviceIdentity`、空集合规范化、结果DTO具备后续发布与选择所需的存在/启用/名称/解析/同步事实，以及Service只依赖`pms-module-asset-api`。不得用测试私有判定函数、替身或AST Provider测试冒充Inspection生产失败关闭；真实消费行为留在Task 7/8/9对应生产入口实现后验证。

- [ ] **Step 4: 运行Inspection API边界契约测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -am -Dtest=AssetProductTypeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected：外部Gate已具备时PASS，且Maven报告确认`AssetProductTypeContractTest`实际执行；本Task只关闭AST外部交付、API形状和模块依赖前置，不关闭Task 7/8/9的生产消费验收。外部Feature/Task或契约未建立时保持`BLOCKED_BY_SPEC`，不以跳过、替身或本Feature内AST改动冒充通过。

- [ ] **Step 5: 建议逻辑分组**

建议提交信息：`test(service): 预验收AST产品类型API边界`

---

### Task 3: 实现专用权限动态授权与内容摘要契约

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleExplicitAuthorizationApi.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleSecurityReviewPermissionGuard.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleContentDigestService.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleSecurityReviewPermissionGuardTest.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleContentDigestServiceTest.java`

- [ ] **Step 1: 实现最小解析与摘要服务**

巡检模块定义`InspectionRuleExplicitAuthorizationApi`端口，按当前租户、当前登录用户和专用权限码查询“显式RBAC授予”事实；守卫不得直接复用会对超级管理员或权限跳过上下文自动放行的通用`SecurityFrameworkService.hasPermission`。本Task只冻结巡检侧端口、未注册容器的守卫和消费测试，不直读`system_*`表、不修改Yudao基础平台、不实现System侧适配器；适配器及守卫Bean装配待外部授权能力完备后继续实施，当前不得提供虚假默认实现。审核事实中的`authorizationType`固定为`RBAC_PERMISSION`，`authorizationSourceId`仅在平台自然提供稳定来源时保存。

- [ ] **Step 2: 补充专用权限定向测试**

测试必须证明：守卫只接受端口返回的当前租户、当前用户、专用权限码显式授权事实；无授权、租户/用户/权限码不匹配均失败关闭，维护、发布或平台管理员身份不得由守卫自行推断通过。不追溯或硬编码“哪个角色贡献权限”，审核事实保存审核用户、权限码和平台可提供的稳定授权来源ID；平台暂不能返回来源ID时留空，不伪造角色编码。

```java
assertThrows(SecurityException.class, () -> guard.check(actorWithManageOnly()));
assertDoesNotThrow(() -> guard.check(actorWithExplicitReviewPermission()));
```

- [ ] **Step 3: 补充规范化摘要定向测试**

摘要输入只包含按执行顺序稳定排序后的命令内容、超时、继续/停止决定和预期结果正则；revision绑定由审核事实中的revision标识单独承担，摘要不包含revision键、数据库ID、维护时间或显示名称。重复或非正数执行顺序失败关闭。相同业务内容产生相同SHA-256，不同命令、顺序、超时、继续策略或正则必须改变摘要。

```text
command[1].content
command[1].timeoutSeconds
command[1].continueOnTimeout
expectedResultRegex
```

- [ ] **Step 4: 运行定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -am -Dtest=InspectionRuleSecurityReviewPermissionGuardTest,InspectionRuleContentDigestServiceTest test
```

Expected：PASS；摘要为小写64位十六进制，任何秘密值不得进入日志或断言输出。

- [ ] **Step 5: 建议逻辑分组**

建议提交信息：`feat(service): 冻结巡检规则安全审核契约`

---

### Task 4: 实现巡检规则纯领域校验

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRevisionRules.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRegexValidator.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleSecretScanner.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRevisionRulesTest.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRegexValidatorTest.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleSecretScannerTest.java`

**Required domain records:**

```java
public record CommandDefinition(
        String stableCommandKey,
        String content,
        int executionOrder,
        int timeoutSeconds,
        boolean continueOnTimeout) {
}

public record ThresholdDefinition(
        String dataType,
        String operator,
        BigDecimal value,
        String unit) {
}

public record ValidationError(String location, String code, String message) {
}
```

- [ ] **Step 1: 实现最小领域规则**

完成状态、八字段、命令顺序与超时、正则预算、阈值和秘密扫描的最小纯领域实现。

- [ ] **Step 2: 补充状态和八字段定向测试**

覆盖只有草稿可编辑、检测ID必填、规则名称必填、十类分类、三级严重级别、描述、排序、至少一个产品类型、正则和阈值完整性。

- [ ] **Step 3: 补充命令边界定向测试**

覆盖至少一条命令、顺序从1连续且不重复、稳定命令键不重复、空命令拒绝、默认30秒、1和30通过、0和31拒绝、继续/停止决定必须冻结。

- [ ] **Step 4: 补充正则预算定向测试**

使用JDK正则语法编译校验，并以结构复杂度预算拒绝明显嵌套量词、过长表达式和可预见灾难性回溯模式；不在校验阶段对不可信大文本执行无上限匹配。

- [ ] **Step 5: 补充秘密扫描定向测试**

最小扫描面覆盖私钥头、认证头、URL内嵌凭证和明确密码赋值模式；结果只返回字段位置和稳定错误码，不回显命中的秘密正文。

- [ ] **Step 6: 运行定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -Dtest=InspectionRuleRevisionRulesTest,InspectionRuleRegexValidatorTest,InspectionRuleSecretScannerTest test
```

Expected：PASS；错误位置使用`commands[0].timeoutSeconds`、`threshold.operator`、`productTypes[0]`等稳定路径。

- [ ] **Step 7: 建议逻辑分组**

建议提交信息：`feat(service): 实现巡检规则revision领域校验`

---

### Task 5: 建立前向Schema、正式字典、菜单权限、示例数据和旧字段受控迁移

**Files:**

- Create: `sql/migrations/V134__fins001_inspection_rule_revision.sql`
- Create: `sql/migrations/V135__fins001_inspection_rule_seed_and_menu.sql`
- Create: `sql/migrations/V136__fins001_legacy_rule_forward_migration.sql`
- Create: `scripts/tests/test_fins001_migrations.py`

- [ ] **Step 1: 实施前重新扫描Flyway编号**

Run:

```powershell
Get-ChildItem sql/migrations/V*.sql | ForEach-Object { if ($_.Name -match '^V(\d+)__') { [int]$Matches[1] } } | Sort-Object | Select-Object -Last 20
```

Expected：确认V134～V136仍连续空闲；如已占用，将本Task三个文件整体顺延到新的连续空闲编号，并同步本计划、Task引用和测试，不修改历史迁移。

- [ ] **Step 2: 创建V134目标表**

使用生成列`current_published_marker`仅在`status_code='PUBLISHED'`时取1，并建立`uk(tenant_id, rule_id, current_published_marker)`；状态CHECK仅允许`DRAFT/PUBLISHED/DISABLED`。命令表保存稳定命令键、内容、顺序、1～30秒超时和继续决定；产品类型表保存编码与发布名称快照；审核表保存摘要、审核用户、权限码、可选稳定授权来源ID、结论和审核时间，不保存秘密。

- [ ] **Step 3: 创建V135字典、菜单、权限和示例数据**

新增十类检测分类、三级严重级别、独立“巡检规则版本”菜单以及六个权限：

```text
pms:inspection-rule:query
pms:inspection-rule:manage
pms:inspection-rule:security-review
pms:inspection-rule:publish
pms:inspection-rule:disable
pms:inspection-rule:select
```

示例数据使用高段ID或专用前缀、`creator='fins001-seed'`，至少覆盖：草稿、当前发布、已停用历史、多命令顺序、1秒/30秒边界、继续/停止、不同分类/严重级别。产品类型编码和适用/不适用组合只能引用已通过Task 2外部Gate验收、由AST Owner独立Feature/Task批准并落库的明确测试种子；Gate仍为`BLOCKED_BY_SPEC`时不得创建产品类型相关示例数据，禁止使用旧字典、手工替身或猜造值。示例安全审核使用测试用户与专用权限快照及不可逆摘要，不预置生产角色、不包含高风险命令或秘密。

- [ ] **Step 4: 创建V136旧规则受控迁移**

只迁移全部正式字段与安全审核事实均可由权威来源完整证明的记录。当前旧`pms_srv_rule.content`是非结构化长文本，缺少十类分类、八字段、命令顺序、产品类型和安全审核事实，固定视为不完整记录：保留在旧接口、旧页面和旧表的兼容只读路径，不写入任何`srv_inspection_rule*`目标表，不生成草稿、发布revision、迁移问题对象或新增兼容标识。禁止解析示例文本、旧字典或其他弱证据猜造命令、阈值、产品类型及审核事实；旧记录保持原始值和来源追溯。

- [ ] **Step 5: 补充Schema静态定向测试**

断言五张表及关键约束存在：

```text
srv_inspection_rule
srv_inspection_rule_revision
srv_inspection_rule_command_revision
srv_inspection_rule_product_type_revision
srv_inspection_rule_security_review
```

关键约束包括：租户内检测ID永久唯一、规则内revision号唯一、revision内命令顺序唯一、revision内产品类型唯一、安全审核引用唯一、一个规则最多一个当前发布revision、乐观锁版本、租户字段、审计字段和软删除字段。

- [ ] **Step 6: 空库与重复迁移验证**

Run:

```powershell
.\scripts\test-infrastructure.ps1 reset
docker compose -p npdms-50eb-test run --rm migrate validate
docker compose -p npdms-50eb-test run --rm migrate migrate
python -m unittest scripts.tests.test_fins001_migrations
```

Expected：Flyway成功到最终编号；五表、约束、字典、菜单、权限、示例和受控迁移断言PASS；重复`migrate`无新增变更。

- [ ] **Step 7: 建议逻辑分组**

建议提交信息：`feat(service): 增加巡检规则版本前向Schema`

---

### Task 6: 实现DO、场景化Query、Mapper和并发查询

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/InspectionRuleDO.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/InspectionRuleRevisionDO.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/InspectionRuleCommandRevisionDO.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/InspectionRuleProductTypeRevisionDO.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/InspectionRuleSecurityReviewDO.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/query/InspectionRuleRevisionPageQuery.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/query/InspectionRuleChildrenQuery.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/query/SelectableInspectionRuleQuery.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/*Mapper.java`
- Create: `pms-module-service/src/main/resources/mapper/inspectionrule/InspectionRuleRevisionMapper.xml`
- Create: `pms-module-service/src/main/resources/mapper/inspectionrule/SelectableInspectionRuleMapper.xml`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/InspectionRuleMapperContractTest.java`

- [ ] **Step 1: 实现简单单表查询**

主键、租户内检测ID和规则内revision号使用稳定唯一键查询；分页的名称筛选字段固定为`ruleNameKeyword`并采用包含匹配；产品类型筛选字段固定为`productTypeCode`，通过XML `EXISTS`检查revision产品类型关系。其余简单条件使用`LambdaQueryWrapperX`，稳定排序`rule_id, revision_no desc, id desc`。

- [ ] **Step 2: 实现XML查询**

将以下查询放入XML：发布时锁定规则与当前发布revision、按revision批量加载命令/产品类型/有效审核、工程师可选规则联表投影。所有动态集合使用`#{}`和`<foreach>`，空集合在Service或Mapper入口返回空。

- [ ] **Step 3: 补充Mapper契约定向测试**

断言分页只接收`InspectionRuleRevisionPageQuery`；子项批量读取只接收`InspectionRuleChildrenQuery`；可选规则只接收`SelectableInspectionRuleQuery`。权限产品类型集合为空时必须直接返回空，不得省略条件扩大范围。

- [ ] **Step 4: 运行契约与静态门禁**

Run:

```powershell
mvn.cmd -pl pms-module-service -Dtest=InspectionRuleMapperContractTest test
python -m unittest scripts.tests.test_fins001_owner_and_query_boundary
```

Expected：PASS；不出现SQL注解、`${}`、`.last(...)`、长位置参数或跨模块表。

- [ ] **Step 5: 建议逻辑分组**

建议提交信息：`feat(service): 实现巡检规则版本持久化契约`

---

### Task 7: 实现草稿、整体保存、复制和无副作用发布预检

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/vo/*`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRuleRevisionService.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRuleRevisionServiceImpl.java`
- Modify: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRuleRevisionServiceImplTest.java`

- [ ] **Step 1: 实现最小应用Service**

创建和保存只做本地可验证校验；保存产品类型输入时可保留用户选择编码，但发布预检必须通过AST重新解析有效编码和权威显示名称。整体保存采用单事务替换从属草稿行；CAS更新影响行数不是1时抛版本冲突。

- [ ] **Step 2: 补充草稿与整体保存定向测试**

覆盖新稳定身份草稿、同一稳定身份新revision、租户内检测ID冲突、非草稿拒绝保存、`If-Match`陈旧拒绝、命令与产品类型整体替换、任何失败事务回滚。

- [ ] **Step 3: 补充复制定向测试**

复制已发布/停用revision必须保留八字段、命令稳定键/顺序/超时/继续策略和产品类型编码/名称快照，但生成新草稿revision号，不复制发布、停用或安全审核事实。

- [ ] **Step 4: 补充预检定向测试**

预检返回全部字段级错误，不写revision、不写审核、不改变版本；通过真实`InspectionAssetProductTypeApi`调用覆盖有效、停用、未知编码和契约不可用，任一不可用事实在产品类型位置返回稳定依赖错误，草稿仍可继续编辑。

- [ ] **Step 5: 运行定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -Dtest=InspectionRuleRevisionServiceImplTest test
```

Expected：PASS；预检无副作用，历史revision不可修改，旧Service无交互。

- [ ] **Step 6: 建议逻辑分组**

建议提交信息：`feat(service): 实现巡检规则草稿与发布预检`

---

### Task 8: 实现安全审核、原子发布、停用、幂等和审计

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRulePublicationService.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRulePublicationServiceImpl.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/InspectionRulePublicationServiceImplTest.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/inspectionrule/InspectionRulePublicationMySqlIntegrationTest.java`
- Reuse: `pms-module-platform-api/.../command/PlatformCommandExecutionApi.java`
- Reuse: `pms-module-platform-api/.../audit/OperationAuditApi.java`

- [ ] **Step 1: 实现事务编排**

审核、发布和停用统一通过`PlatformCommandExecutionApi.execute`，scopeCode分别固定为`INSPECTION_RULE_SECURITY_REVIEW`、`INSPECTION_RULE_PUBLISH`、`INSPECTION_RULE_DISABLE`，requestDigest由规范化业务请求计算；禁止新增Inspection私有幂等表或直读`plt_*`。发布operation内顺序固定为：锁定规则稳定身份与当前发布revision -> 校验CAS -> 加载完整草稿 -> 本地领域校验 -> AST批量重验并刷新名称快照 -> 计算摘要 -> 校验有效审核事实 -> 停用旧当前发布 -> CAS发布新revision。`SuccessFacts`只携带安全摘要、聚合键和必要事件；额外失败/拒绝审计通过`OperationAuditApi`写safeDetail。平台命令契约负责幂等、成功审计与领域写入同事务；任一步异常回滚全部业务写入。

- [ ] **Step 2: 审计数据最小化**

`PlatformCommandExecutionApi.SuccessFacts.detailSnapshot`与`OperationAuditApi.safeDetail`只记录八字段结构化摘要、命令内容摘要、审核引用、发布/停用、失败码、操作者和时间；不得记录密码、私钥、认证头、完整命令正文或正则敏感内容。

- [ ] **Step 3: 补充安全审核定向测试**

覆盖无审核权限、跨租户、非草稿、拒绝结论、摘要不一致和重复请求。重复相同幂等键与相同载荷返回同一审核结果；相同键不同载荷拒绝。

- [ ] **Step 4: 补充发布原子性定向测试**

覆盖有效审核发布成功、审核缺失/拒绝/失效/摘要不一致失败、通过真实`InspectionAssetProductTypeApi`批量重验时AST未知/停用/契约不可用失败、旧发布版本在新发布成功后才停用、任何失败不产生半发布。

- [ ] **Step 5: 补充并发发布MySQL定向测试**

使用两个独立事务同时发布同一规则的两个草稿，断言最多一个成功，最终只有一个`PUBLISHED`，失败请求保持草稿或明确冲突，旧有效版本不会提前停用。

- [ ] **Step 6: 补充停用与幂等定向测试**

只有当前`PUBLISHED`可停用；陈旧`If-Match`拒绝；重复同一幂等键不重复写审计；停用后历史读取仍可用。

- [ ] **Step 7: 运行单元与MySQL集成定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -Dtest=InspectionRulePublicationServiceImplTest,InspectionRulePublicationMySqlIntegrationTest test
```

Expected：PASS；数据库唯一约束和Service CAS共同保证单一当前发布revision。

- [ ] **Step 8: 建议逻辑分组**

建议提交信息：`feat(service): 实现巡检规则安全审核与原子发布`

---

### Task 9: 实现工程师可选规则投影

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/SelectableInspectionRuleService.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/SelectableInspectionRuleServiceImpl.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/SelectableInspectionRuleServiceImplTest.java`

- [ ] **Step 1: 实现AST授权设备查询**

Service从服务端认证上下文取得当前用户，构造`AuthorizedDeviceProductTypeQuery(subjectUserId, deviceIds)`并调用`InspectionAssetProductTypeApi.getAuthorizedDeviceProductType`取得可信产品类型；不接受客户端直接提交产品类型作为授权依据。

- [ ] **Step 2: 实现只读投影**

只返回当前`PUBLISHED`且产品类型匹配的规则摘要：稳定检测ID、revisionId/revisionNo、检测分类、检测项目、严重级别、排序、适用产品类型；不返回审核内部信息和秘密命令正文。后续INS-01/02需要命令清单时通过独立受权契约读取，不在本Feature提前开放执行接口。

- [ ] **Step 3: 补充选择范围定向测试**

通过真实`InspectionAssetProductTypeApi`调用覆盖授权设备当前产品类型精确匹配、跨租户设备、无设备范围、未知/停用/未解析产品类型、契约不可用、不适用规则、已停用规则、历史发布规则和空产品类型集合；无权或不可见设备返回空且不泄露存在性。

- [ ] **Step 4: 运行定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -Dtest=SelectableInspectionRuleServiceImplTest test
```

Expected：PASS；空权限/范围返回空或稳定拒绝，不因省略筛选扩大结果。

- [ ] **Step 5: 建议逻辑分组**

建议提交信息：`feat(service): 增加巡检规则授权选择投影`

---

### Task 10: 实现新管理端API、管理页面与选择视图

**Files:**

- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/InspectionRuleController.java`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/InspectionRuleControllerContractTest.java`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/service/inspection-rule/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/components/InspectionRuleRevisionEditor.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/components/InspectionRuleCommandTable.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/components/InspectionRuleValidationPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/components/SelectableInspectionRulePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/inspection-rule/inspection-rule.spec.ts`

- [ ] **Step 1: 实现Controller和前端API**

Controller只做Schema校验、权限注解和可信登录上下文传递，Service重复执行租户、状态、角色组、CAS和设备范围守卫。前端API统一携带`If-Match`，不使用`any`动态调用action。

- [ ] **Step 2: 补充Controller契约定向测试**

冻结以下接口与权限：

```text
GET  /api/v1/pms/inspection-rules/revisions
POST /api/v1/pms/inspection-rules/revisions
GET  /api/v1/pms/inspection-rules/revisions/{revisionId}
PUT  /api/v1/pms/inspection-rules/revisions/{revisionId}
POST /api/v1/pms/inspection-rules/revisions/{revisionId}/actions/copy
POST /api/v1/pms/inspection-rules/revisions/{revisionId}/actions/validate
POST /api/v1/pms/inspection-rules/revisions/{revisionId}/actions/record-security-review
POST /api/v1/pms/inspection-rules/revisions/{revisionId}/actions/publish
POST /api/v1/pms/inspection-rules/revisions/{revisionId}/actions/disable
GET  /api/v1/pms/inspection-rules/selectable?deviceId={deviceId}
```

写命令使用`If-Match`；审核、发布和停用使用`Idempotency-Key`；请求体不含`statusCode/tenantId/publishedAt/disabledAt`可写字段。

- [ ] **Step 3: 实现revision管理页**

列表按检测ID、名称、分类、严重级别、产品类型和状态筛选；详情分为基本信息、命令列表、判定规则、适用产品、发布校验和安全审核。已发布/停用字段全部禁用，“新建修订”是唯一修改入口。

- [ ] **Step 4: 实现可访问性与响应式**

错误同时使用图标、文本和字段定位，不只用颜色；按钮有可见名称；命令表支持键盘添加、删除和排序；320/768使用全屏抽屉或纵向布局，1024/1440保留双栏；提供加载、空数据、失败和权限不足状态。

- [ ] **Step 5: 补充前端组件定向测试**

覆盖草稿可编辑、发布/停用只读、复制为草稿、0/31秒字段错误、命令顺序重排、验证错误定位、审核权限按钮、发布权限按钮和不可选规则禁用。

- [ ] **Step 6: 运行前端定向检查**

Run:

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm exec vitest run src/views/pms/service/inspection-rule/inspection-rule.spec.ts
pnpm exec eslint src/api/pms/service/inspection-rule src/views/pms/service/inspection-rule
pnpm exec prettier --check "src/api/pms/service/inspection-rule/**/*.ts" "src/views/pms/service/inspection-rule/**/*.{vue,ts}"
pnpm exec stylelint "src/views/pms/service/inspection-rule/**/*.vue"
```

Expected：PASS，无`any`action调用、无不受控`v-html`、无秘密写入localStorage/sessionStorage。

- [ ] **Step 7: 建议逻辑分组**

建议提交信息：`feat(ui): 增加巡检规则版本管理工作台`

---

### Task 11: 完成自动化、真实MySQL、模块构建、Lint和TypeScript验证

**Files:**

- Modify: `pms-module-service/src/test/...`仅补齐本Feature发现的覆盖缺口
- Modify: `scripts/tests/test_fins001_*`仅补齐静态门禁缺口

- [ ] **Step 1: 运行全部F-INS-001后端测试**

Run:

```powershell
mvn.cmd -pl pms-module-service -am -Dtest=AssetProductTypeContractTest,InspectionRuleSecurityReviewPermissionGuardTest,InspectionRuleContentDigestServiceTest,InspectionRuleRevisionRulesTest,InspectionRuleRegexValidatorTest,InspectionRuleSecretScannerTest,InspectionRuleMapperContractTest,InspectionRuleRevisionServiceImplTest,InspectionRulePublicationServiceImplTest,InspectionRulePublicationMySqlIntegrationTest,SelectableInspectionRuleServiceImplTest,InspectionRuleControllerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected：Task 2外部Gate已关闭时PASS；必须检查Surefire报告，确认`AssetProductTypeContractTest`及其余列出的F-INS目标测试均实际执行且通过。AST实现测试由其独立Feature/Task维护，不纳入本计划命令；Gate为`BLOCKED_BY_SPEC`时不得以跳过该消费测试宣称完整回归通过。

- [ ] **Step 2: 运行service模块全量测试与构建**

Run:

```powershell
mvn.cmd -pl pms-module-service -am test
mvn.cmd -pl yudao-server -am package -DskipTests
```

Expected：PASS；现有`InspectionGovernanceGuardProviderTest`和旧`srv-rule`编译/行为不回归。

- [ ] **Step 3: 运行静态门禁和Flyway验证**

Run:

```powershell
python -m unittest scripts.tests.test_fins001_plan_and_scope scripts.tests.test_fins001_legacy_preservation scripts.tests.test_fins001_owner_and_query_boundary scripts.tests.test_fins001_migrations
docker compose -p npdms-50eb-test run --rm migrate validate
```

Expected：PASS；旧文件SHA不变，只有一个当前计划，无跨模块直读和查询规范违规。

- [ ] **Step 4: 运行前端全量类型、Lint和构建**

Run:

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm ts:check
pnpm lint
pnpm build:local
```

Expected：PASS；不得用全仓格式化修复无关文件，只修复本Feature新增文件引入的问题。

- [ ] **Step 5: 真实MySQL业务断言**

使用固定测试库验证：同租户检测ID唯一、revision号唯一、命令顺序唯一、产品类型唯一、单一当前发布、31秒CHECK/Service拒绝、并发发布最多一个成功、停用后历史可读、旧`pms_srv_rule`行数与内容未被新运行时写入。

- [ ] **Step 6: 建议逻辑分组**

建议提交信息：`test(service): 完成F-INS-001自动化验证`

---

### Task 12: 使用真实浏览器完成四档视口业务验收

**Files:**

- Create: `docs/engineering/evidence/f-ins-001-runtime-evidence.json`

- [ ] **Step 1: 启动固定验收基础设施**

Run:

```powershell
.\scripts\test-infrastructure.ps1 start
.\scripts\test-infrastructure.ps1 status
```

Expected：MySQL `23316`、Redis `26379`和Flyway测试基础设施健康；不停止开发环境`58080/18081`。

- [ ] **Step 2: 启动宿主机后端与前端**

使用验收端口主后端`59280`、跨租户负向后端`59282`、Vite`19081`；后端连接`npdms_test`，前后端均不运行在Docker。

- [ ] **Step 3: 验收正向主链**

真实浏览器完成：新建草稿 -> 填写八字段 -> 添加多命令 -> 设置1秒/30秒和继续/停止 -> 选择AST产品类型 -> 发布预检 -> 具备专用审核权限的用户记录安全审核 -> 发布 -> 工程师按授权设备选择 -> 复制新revision -> 修改后重新审核发布 -> 停用 -> 读取历史。

- [ ] **Step 4: 验收关键负向**

逐项验证：0秒、31秒、命令缺号/重复、危险正则、阈值缺单位、秘密文本、停用产品类型、无审核、摘要变化后旧审核失效、维护者自行审核、发布者无审核事实、陈旧`If-Match`、跨租户、无设备范围、不适用产品和直接写状态。所有失败必须无半发布且旧版本保持有效。

- [ ] **Step 5: 验收旧入口保护**

打开旧“巡检规则”页面，验证旧查询、编辑、发布/停用入口仍按原功能工作；新页面使用独立菜单和权限，双方不双写。只验证保护，不以旧页面行为裁决新Feature语义。

- [ ] **Step 6: 验收视口、控制台和刷新持久化**

在1440、1024、768、320宽度验证列表、编辑器、命令表、错误面板、审核和选择视图；刷新后草稿与发布状态来自后端持久化；控制台error、页面异常和意外失败HTTP均为0。

- [ ] **Step 7: 写最小运行证据**

`f-ins-001-runtime-evidence.json`只记录Requirement ID、构建/测试命令与结果、迁移最终版本、浏览器URL/端口、角色与租户测试矩阵、视口、关键对象ID、控制台/网络结果和时间；不复制秘密、密码、Token、私钥或完整命令敏感内容。

- [ ] **Step 8: 停止验收进程**

只停止本Task启动的`59280/59282/19081`宿主机进程，不执行`docker compose down`，不停止共享测试基础设施或开发端口。

- [ ] **Step 9: 建议逻辑分组**

建议提交信息：`test(service): 记录F-INS-001浏览器验收证据`

---

### Task 13: 更新追溯、执行自审并收口Feature状态

**Files:**

- Modify: `tasks/features/F-INS-001.md`
- Modify: `specs/features/README.md`
- Modify: `docs/traceability/requirement-matrix.md`，仅通过权威生成脚本更新
- Modify: `docs/traceability/requirement-version-coverage.json`，仅通过权威生成脚本更新

- [ ] **Step 1: 更新当前Feature任务**

Technical Plan Gate在本计划自审通过后记录为`PASS / NPDMS-FINS001-TECHPLAN-20260830-01`。实施期间逐项记录Task证据；只有全部Task、测试、迁移、浏览器和评审通过后，才能将Feature实施状态改为`IMPLEMENTATION_DONE`并记录唯一Implementation Done Gate。

- [ ] **Step 2: 生成Requirement追溯投影**

先定位并运行仓库权威脚本：

```powershell
py -3.13 -B scripts/generate_requirement_traceability.py `
  --prd docs/baseline/prd-v1.8.md `
  --domains specs/001-project-delivery-platform/domains `
  --features specs/features `
  --tasks tasks/features `
  --output docs/traceability/requirement-matrix.md `
  --coverage-output docs/traceability/requirement-version-coverage.json `
  --check
```

Expected：`INS-03@V2`保持`PARTIAL`，`INS-09@V2`由F-INS-001覆盖；不得把NFR-02支撑关系误写为Feature完整覆盖，不得因Technical Plan完成提前派生Implementation Done。

- [ ] **Step 3: 建立Code/Test映射**

在Feature任务中引用：五张表迁移、Controller、领域规则、发布Service、选择Service、前端页面、单元测试、MySQL集成测试和浏览器证据。每项API、数据库变更和测试明确标注`INS-03`、`INS-09`或`NFR-02@V2`支撑关系。

- [ ] **Step 4: 执行范围自审**

检查未出现：INS-01任务表/快照写入、INS-02执行、INT-12下发、报告/问题/误报、超30秒审批、固定审批角色、Inspection产品类型主数据、跨模块直读、旧接口改造、V3或`OUT_OF_SCOPE`对象。

- [ ] **Step 5: 执行安全自审**

检查服务端权限和角色组守卫、租户与设备范围、摘要一致性、正则预算、秘密扫描、错误响应最小化、审计不含秘密、状态action API、CAS/幂等、失败关闭和历史不可变。

- [ ] **Step 6: 执行数据与迁移自审**

检查旧迁移未修改、最终编号无冲突、五表Owner正确、唯一/检查/索引完整、旧字段不推断、示例数据幂等且不冒充外部同步、空集合不扩大查询。

- [ ] **Step 7: 执行计划自审**

搜索占位符、未完成标记、模糊交叉引用、未定义文件和互相矛盾的类型/接口；确认AST前置、安全审核、后端、迁移、前端、测试、浏览器和追溯均有独立Task与可验证结果。

- [ ] **Step 8: 最终工作树检查**

Run:

```powershell
git status --short
git diff --check
git diff -- docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md tasks/features/F-INS-001.md specs/features/README.md docs/traceability/requirement-matrix.md docs/traceability/requirement-version-coverage.json
```

Expected：无空白错误；所有变更可追溯到F-INS-001；未产生提交。

---

## 4. 验收覆盖矩阵

| Feature AC | 实施Task | 主要证据 |
|---|---|---|
| AC-FINS001-001～003 | Task 4、5、7、10 | 领域测试、Schema、草稿Service、页面 |
| AC-FINS001-004～006 | Task 4、7、11 | 命令/超时/正则/阈值测试 |
| AC-FINS001-007、012A | Task 3、8、10、12 | 专用权限守卫、摘要、审核API、浏览器权限负向 |
| AC-FINS001-008～010 | Task 5、7、8、12 | 不可变revision、复制、并发发布、停用历史 |
| AC-FINS001-011 | Task 2、9、12 | AST契约、设备授权选择测试、浏览器选择 |
| AC-FINS001-012 | Task 7、8、10、12 | 服务端权限、If-Match、状态字段拒绝 |
| AC-FINS001-013 | Task 1、5、11、12 | 旧文件保护、受控迁移、旧入口回归 |
| AC-FINS001-014 | Task 11、12 | 后端/迁移/前端/真实浏览器四视口证据 |

## 5. 主要风险与处理

- **AST产品类型契约当前缺失：** 这是已发现的正式规格与任务缺口。Task 2只验收AST Owner独立Feature/Task交付的公开契约和F-INS消费结果，不创建或修改AST文件；对应Feature Spec或当前Task未建立时标记`BLOCKED_BY_SPEC`并登记Open Question，发布和选择闭环保持阻断。
- **安全审核不绑定固定角色编码：** 不硬编码角色代码、不建设BPM。采用租户内专用`pms:inspection-rule:security-review`动态权限包；Service重复校验，审核事实保存用户、权限码及可获得的稳定授权来源，不要求解析角色贡献关系。
- **Flyway并行编号冲突：** 当前预留V134～V136，实施前和集成前各扫描一次；冲突只通过新增编号顺延解决，不修改已执行文件。
- **正则ReDoS与命令秘密：** 发布前执行语法、结构复杂度、长度预算和秘密扫描；错误不回显命中正文，运行时执行预算由后续INS-02负责，本Feature不伪装为执行引擎。
- **旧表字段不完整：** 固定留在旧接口、旧页面和旧表的兼容只读路径，不写入新目标表，不生成草稿、发布revision、迁移问题对象或新增兼容标识；仅完整可证记录才允许受控迁移，全程零猜测。
- **维护、审核、发布权限串权：** 三类权限独立，Service重复校验；平台管理员、维护者或发布者不因身份自动获得审核权。
- **并发发布半状态：** 使用数据库唯一当前发布约束、锁查询、CAS和单事务；先完成全部校验，再在同一事务停用旧版本并发布新版本。
- **计划越界到任务执行：** 可选投影不开放命令执行，不创建任务快照，不调用INT-12；后续F-INS-002/INS-02通过独立Feature消费已发布revision。

## 6. Technical Plan Gate

结论：`PASS / NPDMS-FINS001-TECHPLAN-20260830-01`。

本计划覆盖Feature Spec全部AC，并独立安排AST产品类型外部Gate、安全审核专用权限与摘要、纯领域规则、五表前向迁移、旧字段受控迁移、后端API与事务、工程师选择、前端管理、自动化、真实MySQL、真实浏览器四视口和追溯收口。计划保持`INS-03@V2=PARTIAL`、`INS-09@V2=FULL`边界，不实施任务、执行、采集、报告、问题、误报、超30秒审批或V3事项；旧实现保持不变。独立复审结论为GO；Technical Plan通过不表示AST依赖已交付、代码已实施、迁移已执行、测试已通过、浏览器已验收或Implementation Done已完成。