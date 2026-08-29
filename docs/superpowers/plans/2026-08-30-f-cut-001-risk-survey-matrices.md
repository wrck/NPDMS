# F-CUT-001 风险与调研关联矩阵 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有`CutoverConfigurationRevision`聚合内补齐CUT-09风险矩阵与CUT-10调研矩阵，使CUT-07/09/10能够联合校验、原子发布并形成可独立使用的V1配置基础。

**Architecture:** 保留现有三表和单一`DRAFT -> PUBLISHED -> DISABLED`生命周期；风险与调研页面只是完整聚合的类型投影。新增聚焦领域规则与两列前向数据契约，根Service在现有`validate/publish`流程中统一调用，任一矩阵失败时零写入并保持旧发布修订有效。

**Tech Stack:** Java 25、Spring Boot、MyBatis Plus/XML、MySQL 8.4、Flyway、Vue 3、TypeScript 6、Element Plus、Vitest、Playwright真实浏览器。

**Spec:** `docs/superpowers/specs/2026-08-30-f-cut-001-risk-survey-matrices-design.md`

## Global Constraints

- Requirement范围固定为`CUT-07@V1`、`CUT-09@V1`、`CUT-10@V1`；不实施CUT-01～06运行态、CUT-08或V2自动指派。
- 配置根、统一采集项与绑定规则仍由现有三表承载；不得新增风险或调研Owner表、独立API或独立生命周期。
- 五类双机检查基准固定为VSM 17、静默双机25、DRP 23、普通双机24、集群8，合计97项。
- 风险配置至少覆盖PRD列出的25个基准类别；允许正式后续扩展，不把25写成上限。
- 调研配置必须覆盖12类核心内容；绑定级`requiredResult`是必填/选填的权威值。
- 本地XLSX/HTML只引用名称、说明、界面格式和排序，不重复下载，不参与数量、规则、不一致或完成裁决。
- 已发布内容不可原位覆盖；只允许复制为新草稿，CAS冲突必须刷新完整聚合。
- 历史V128～V131不得修改；新迁移从当前空闲编号V132起串行落地。

---

## File Structure

- `CutoverRiskMatrixRules.java`：CUT-09最小类别、97项数量、专属组网、全覆盖必选与冲突校验。
- `CutoverSurveyMatrixRules.java`：CUT-10十二类、割接背景Schema、绑定级必填与规则冲突校验。
- `CutoverMatrixValidationContext.java`：发布时已启用字典值的不可变上下文，不访问数据库。
- `CutoverConfigurationRules.java`：保留通用CUT-07规则，只扩展采集项类别与绑定结果契约。
- `CutoverConfigurationServiceImpl.java`：读取字典上下文并编排三组规则；不承载矩阵细节。
- `V132__fcut001_matrix_contract.sql`：前向增加`business_category_code`与`required_result`。
- 初始化边界：沿用V129正式字典与示例组合；未在正式需求中定义名称的检查项不进入生产种子，97项完整能力由隔离验收数据验证。
- `cutoverMatrix.ts`：前端矩阵投影、基准计数和批量规则纯函数。
- `CutoverRiskMatrixEditor.vue`：风险与五类双机投影视图。
- `CutoverSurveyMatrixEditor.vue`：十二类调研与割接背景Schema投影视图。
- `CutoverConfigurationEditor.vue`：组合两个专用组件并按组定位发布错误。

---

### Task 1: CUT-09/CUT-10纯领域规则

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverMatrixValidationContext.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverRiskMatrixRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverSurveyMatrixRules.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverConfigurationRules.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverRiskMatrixRulesTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverSurveyMatrixRulesTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverMatrixFixtures.java`
- Modify: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverConfigurationRulesTest.java`

**Interfaces:**

- Consumes: 现有`CutoverConfigurationRules.ItemDefinition`、`BindingRule`、`ValidationError`。
- Produces: `CutoverMatrixValidationContext(Set<String> cutoverTypeCodes, Set<String> networkModeCodes, Set<String> deviceTypeCodes, Set<String> levelCodes)`；两个规则类均暴露`static List<ValidationError> validate(List<ItemDefinition>, List<BindingRule>, CutoverMatrixValidationContext)`。
- Contract extension: `ItemDefinition`增加`String businessCategoryCode`和`Map<String,Object> interfaceSchema`；`BindingRule`增加`Boolean requiredResult`。`null`只表示旧草稿尚未补齐，发布校验必须拒绝，不做静默默认。
- Test fixture: `CutoverMatrixFixtures`提供`riskItems(Map<String,Integer>)`、`completeRiskItems()`、`rulesMissingDevice(String,String)`、`completeSurveyItems()`、`completeSurveyItemsWithout(String)`、`surveyRules()`、`surveyRule(Boolean)`、`rule(String,String,Boolean)`和`context()`，全部返回Task 1定义的领域record，不访问Spring或数据库。

- [ ] **Step 1: 写风险矩阵失败测试**

```java
@Test
void rejectsWrongDualMachineCountsAndCrossModeBinding() {
    var errors = CutoverRiskMatrixRules.validate(
            Fixtures.riskItems(Map.of("VSM", 16, "SILENT_DUAL", 25,
                    "DRP_DUAL", 23, "NORMAL_DUAL", 24, "CLUSTER", 8)),
            List.of(Fixtures.rule("DUAL_VSM_001", "NORMAL_DUAL", true)),
            Fixtures.context());
    assertTrue(errors.stream().anyMatch(e -> e.message().contains("VSM双机应为17项")));
    assertTrue(errors.stream().anyMatch(e -> e.message().contains("不得跨所属组网模式")));
}

@Test
void rejectsMissingAllSituationCoverage() {
    var errors = CutoverRiskMatrixRules.validate(
            Fixtures.completeRiskItems(),
            Fixtures.rulesMissingDevice("SYSTEM_LOG", "ADX"),
            Fixtures.context());
    assertTrue(errors.stream().anyMatch(e -> e.location().contains("coverage")
            && e.message().contains("ADX")));
}
```

- [ ] **Step 2: 运行风险规则测试并确认失败原因**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverRiskMatrixRulesTest test`

Expected: FAIL，原因是`CutoverRiskMatrixRules`和扩展record尚不存在；不能接受编译之外的偶然失败。

- [ ] **Step 3: 实现风险基准与有限组合覆盖算法**

```java
public final class CutoverRiskMatrixRules {
    public static final Map<String, Integer> DUAL_COUNTS = Map.of(
            "VSM", 17, "SILENT_DUAL", 25, "DRP_DUAL", 23,
            "NORMAL_DUAL", 24, "CLUSTER", 8);
    public static final Set<String> REQUIRED_RISK_CATEGORIES = Set.of(
            "CURRENT_VERSION_BULLETIN", "TARGET_VERSION_BULLETIN",
            "DUAL_CONFIG_CONSISTENCY", "FILTER_NAT_QOS_COMPILE_COUNT",
            "COMPILE_LIMIT_ASSESSMENT", "SESSION_SYNC", "DUAL_CONTROLLER_VERSION",
            "PACKAGE_MD5", "MAJOR_PROJECT_SPARES", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
            "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
            "CONFIG_BACKUP", "DYNAMIC_TABLE_COLLECTION", "MTU_JUMBO_FRAME",
            "HUNDRED_G_FEC", "LONG_CONNECTION", "SECOND_PASS_DEVICE", "STP",
            "F5_DEFAULT", "ADWARE_DEFAULT", "ROOM_OPERATION_COMMITMENT");
    public static final Set<String> ALL_SITUATION_REQUIRED = Set.of(
            "CURRENT_VERSION_BULLETIN", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
            "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
            "CONFIG_BACKUP", "ROOM_OPERATION_COMMITMENT");
}
```

实现时只展开`cutoverTypeCodes × deviceTypeCodes × {A,B,C}`有限集合；空条件不计覆盖。`TARGET_VERSION_BULLETIN`只能绑定`VERSION_UPGRADE`，`DUAL_MACHINE_CHECK`规则中的`NETWORK_MODE`必须等于采集项`subtableCode`。

- [ ] **Step 4: 写调研矩阵失败测试**

```java
@Test
void rejectsMissingCoreCategoryAndInvalidBackgroundDependency() {
    var items = Fixtures.completeSurveyItemsWithout("BUSINESS_SUMMARY");
    items.getFirst().interfaceSchema().put("visibleWhenField", "UNKNOWN_FIELD");
    var errors = CutoverSurveyMatrixRules.validate(items, Fixtures.surveyRules(), Fixtures.context());
    assertTrue(errors.stream().anyMatch(e -> e.message().contains("BUSINESS_SUMMARY")));
    assertTrue(errors.stream().anyMatch(e -> e.message().contains("条件字段不存在")));
}

@Test
void rejectsBindingWithoutRequiredResult() {
    var errors = CutoverSurveyMatrixRules.validate(
            Fixtures.completeSurveyItems(), Fixtures.surveyRule(null), Fixtures.context());
    assertTrue(errors.stream().anyMatch(e -> e.location().endsWith("requiredResult")));
}
```

- [ ] **Step 5: 运行调研规则测试并确认失败原因**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverSurveyMatrixRulesTest test`

Expected: FAIL，原因是`CutoverSurveyMatrixRules`尚不存在。

- [ ] **Step 6: 实现十二类与割接背景Schema规则**

```java
public static final Set<String> CORE_SURVEY_CATEGORIES = Set.of(
        "CUTOVER_BACKGROUND", "BUSINESS_SUMMARY", "IMPACT_SCOPE",
        "CONTINUITY_REQUIREMENT", "INTERRUPTION_COUNT", "CURRENT_TOPOLOGY",
        "DEVICE_LOCATION_PLAN", "INTERFACE_INTERCONNECT_PLAN", "IP_VLAN_PLAN",
        "PERFORMANCE_BASELINE", "CONNECTIVITY_TEST_CASE", "VENDOR_CONFIG_TRANSLATION");
public static final Set<String> BACKGROUND_FIELDS = Set.of(
        "solvesOnlineIssue", "issueTicketNo", "issueHandler",
        "repeatCutover", "firstCutoverOwner", "backgroundDescription");
```

`CUTOVER_BACKGROUND`必须含上述六字段；`issueTicketNo/issueHandler`依赖`solvesOnlineIssue == true`，`firstCutoverOwner`依赖`repeatCutover == true`。具体组合优先级高于通配组合；相同采集项、相同条件、相同优先级而`requiredResult`不同必须报冲突。

- [ ] **Step 7: 运行全部配置领域测试**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverConfigurationRulesTest,CutoverRiskMatrixRulesTest,CutoverSurveyMatrixRulesTest test`

Expected: PASS，且至少覆盖五类数量不足/超出、跨子表、25类缺口、必选覆盖缺口、公告条件、十二类缺口、Schema引用和必填冲突。

- [ ] **Step 8: 提交领域规则**

```bash
git add pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverMatrixValidationContext.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverRiskMatrixRules.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverSurveyMatrixRules.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverConfigurationRules.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverRiskMatrixRulesTest.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverSurveyMatrixRulesTest.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverMatrixFixtures.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverConfigurationRulesTest.java
git commit -m "feat(cutover): 增加风险与调研矩阵发布规则"
```

---

### Task 2: 前向数据契约与完整聚合映射

**Files:**

- Create: `sql/migrations/V132__fcut001_matrix_contract.sql`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/configuration/CutoverChecklistItemDefinitionRevisionDO.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/configuration/CutoverChecklistBindingRuleRevisionDO.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/configuration/vo/CutoverConfigurationSaveReqVO.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java`
- Modify: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java`

**Interfaces:**

- Consumes: Task 1扩展后的`ItemDefinition`与`BindingRule`。
- Produces: `ItemVO.businessCategoryCode: String`；`BindingRuleVO.requiredResult: Boolean`；两字段经`create/update/get/copy`完整往返。
- Database: `business_category_code varchar(64) NULL`与`required_result bit(1) NULL`先允许旧历史为空；发布规则拒绝新发布为空，历史读取保持兼容。

- [ ] **Step 1: 写聚合往返失败测试**

```java
@Test
void copyShouldPreserveCategoryAndBindingRequiredResult() {
    when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
    when(itemMapper.selectListByRevision(any())).thenReturn(List.of(item("SYSTEM_LOG")));
    when(ruleMapper.selectListByRevision(any())).thenReturn(List.of(rule(true)));
    when(revisionMapper.selectLatestByCode(any())).thenReturn(draft(10L));
    when(revisionMapper.insert(any())).thenAnswer(invocation -> {
        invocation.<CutoverConfigurationRevisionDO>getArgument(0).setId(11L);
        return 1;
    });
    service.copyRevision(10L, 0);
    verify(itemMapper).insert(argThat(row -> "SYSTEM_LOG".equals(row.getBusinessCategoryCode())));
    verify(ruleMapper).insert(argThat(row -> Boolean.TRUE.equals(row.getRequiredResult())));
}
```

- [ ] **Step 2: 运行Service定向测试确认失败**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverConfigurationServiceImplTest#copyShouldPreserveCategoryAndBindingRequiredResult test`

Expected: FAIL，缺少DO/VO字段或映射。

- [ ] **Step 3: 新增V132前向列**

```sql
ALTER TABLE `cut_cutover_checklist_item_definition_revision`
  ADD COLUMN `business_category_code` varchar(64) DEFAULT NULL
    AFTER `item_type_code`,
  ADD KEY `idx_cut_config_item_category`
    (`tenant_id`, `configuration_revision_id`, `item_type_code`,
     `business_category_code`, `status_code`, `sort_order`);

ALTER TABLE `cut_cutover_checklist_binding_rule_revision`
  ADD COLUMN `required_result` bit(1) DEFAULT NULL AFTER `priority`;
```

- [ ] **Step 4: 完成DO、VO和Service双向映射**

在`replaceChildren`写入两列，在`toDetail`读取两列，在`toDomainItems/toDomainRules`传给Task 1的record；`toSaveRequest`继续复制完整列表，不新增并行DTO或矩阵保存接口。

- [ ] **Step 5: 运行Service测试与模块编译**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverConfigurationServiceImplTest test`

Expected: PASS。

Run: `mvn -pl pms-module-cutover -am -DskipTests compile`

Expected: PASS，无Mapper长位置参数、SQL注解或跨模块Service依赖。

- [ ] **Step 6: 提交数据契约**

```bash
git add sql/migrations/V132__fcut001_matrix_contract.sql pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/configuration/CutoverChecklistItemDefinitionRevisionDO.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/configuration/CutoverChecklistBindingRuleRevisionDO.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/configuration/vo/CutoverConfigurationSaveReqVO.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java
git commit -m "feat(cutover): 扩展矩阵类别与绑定必填契约"
```

---

### Task 3: 根Service联合校验与原子发布

**Files:**

- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/configuration/vo/CutoverConfigurationValidationRespVO.java`
- Modify: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java`

**Interfaces:**

- Consumes: `DictDataApi.getDictDataList`的启用割接类型、组网模式、设备类型与等级；Task 1两个校验器。
- Produces: 现有`validate(Long revisionId)`仍返回`valid + errors`；错误`location`前缀固定为`base.`、`risk.`、`survey.`，前端据此分组定位。API路径和权限不变。

- [ ] **Step 1: 写联合校验零写入失败测试**

```java
@Test
void publishShouldHaveNoWriteWhenRiskOrSurveyValidationFails() {
    when(revisionMapper.selectById(10L)).thenReturn(draftWithIncompleteMatrices(10L));
    when(itemMapper.selectListByRevision(any())).thenReturn(incompleteItems());
    when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
    stubEnabledDictionaries();
    assertThrows(ServiceException.class, () -> service.publish(10L, 0));
    verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
    verify(itemMapper, never()).hardDeleteByRevisionId(any());
    verify(ruleMapper, never()).hardDeleteByRevisionId(any());
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl pms-module-cutover -Dtest=CutoverConfigurationServiceImplTest#publishShouldHaveNoWriteWhenRiskOrSurveyValidationFails test`

Expected: FAIL，因为Service尚未调用两个矩阵校验器。

- [ ] **Step 3: 编排联合校验**

```java
public CutoverConfigurationValidationRespVO validate(Long revisionId) {
    var detail = get(revisionId);
    var context = loadMatrixContext();
    var dimensions = toDomainDimensions(detail.getDimensions());
    var items = toDomainItems(detail.getItems());
    var rules = toDomainRules(detail.getBindingRules());
    var sections = toDomainSections(detail.getPlanTemplateSections());
    var errors = new ArrayList<ValidationError>();
    addPrefixed(errors, "base", CutoverConfigurationRules.validate(
            dimensions, items, rules, sections));
    addPrefixed(errors, "risk", CutoverRiskMatrixRules.validate(items, rules, context));
    addPrefixed(errors, "survey", CutoverSurveyMatrixRules.validate(items, rules, context));
    validateDictionaryReferences(detail, errors);
    validateExternalSourceDefinitions(detail, errors);
    return new CutoverConfigurationValidationRespVO(errors.isEmpty(), errors.stream()
            .map(error -> new CutoverConfigurationValidationRespVO.ValidationErrorVO(
                    error.location(), error.message())).toList());
}
```

新增私有方法`CutoverMatrixValidationContext loadMatrixContext()`与`void addPrefixed(List<ValidationError>, String, List<ValidationError>)`。`loadMatrixContext`只保留`CommonStatusEnum.ENABLE`字典值；空字典形成可定位错误，不扩大覆盖集合。`publish`继续先完成全部只读校验，再执行停用旧发布修订与发布新修订；任一错误抛`CUTOVER_CONFIG_VALIDATION_FAILED`且不写数据库。

- [ ] **Step 4: 补CAS、复制与旧发布保护测试**

增加并通过以下测试：`publishShouldKeepCurrentPublishedWhenMatrixValidationFails`、`copyShouldPreserveAllMatrixChildren`、`updateShouldRejectStaleIfMatchBeforeReplacingChildren`、`validateShouldPrefixRiskAndSurveyLocations`。

- [ ] **Step 5: 运行后端全部配置测试**

Run: `mvn -pl pms-module-cutover -Dtest='*CutoverConfiguration*,CutoverRiskMatrixRulesTest,CutoverSurveyMatrixRulesTest' test`

Expected: PASS。

- [ ] **Step 6: 提交联合发布校验**

```bash
git add pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/configuration/vo/CutoverConfigurationValidationRespVO.java pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java
git commit -m "feat(cutover): 联合校验风险与调研矩阵发布"
```

---

### Task 4: 初始化边界与隔离验收数据

**Files:**

- No production migration.
- Runtime acceptance data is created through the existing root aggregate API in the isolated Task 6 environment and is not committed as business seed data.

**Interfaces:**

- Consumes: V129已提交的正式字典与最小示例组合、V132数据契约、正式PRD/SDS/Feature Spec的24个普通风险类别、五类97项计数与12类调研类别。
- Produces: 不新增生产主数据语义；隔离验收数据使用`ACCEPTANCE_*`稳定键和明确测试名称验证24个普通风险类别、五类17/25/23/24/8双机定义、12类调研及发布规则，用后即随隔离数据库销毁。

- [ ] **Step 1: 复核生产初始化边界**

确认V129继续只承载正式字典和示例组合；不得因XLSX/HTML的行数、缺名或差异新增业务名称、生产占位项或第二套完成口径。

- [ ] **Step 2: 在Task 6隔离环境创建验收数据**

通过现有配置根聚合API创建草稿，稳定键使用`ACCEPTANCE_RISK_*`、`ACCEPTANCE_DUAL_<MODE>_<001..NNN>`、`ACCEPTANCE_SURVEY_*`；双机项名称明确标识为“验收数据-<模式>-<序号>”，不得提交到迁移或宣称为正式业务名称。

- [ ] **Step 3: 验证精确基准与代表性组合**

验收数据必须覆盖五类17/25/23/24/8、24个普通风险类别、12类调研、八类所有情况必选显式覆盖、精确命中、部分限定、优先级让位、无匹配与停用不参与；服务端预检通过后才允许执行正向发布闭环。

Task 4不产生单独代码提交；验收数据与浏览器证据随Task 6记录。

---

### Task 5: 风险与调研专用矩阵界面

**Files:**

- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-config/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/cutoverMatrix.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/cutoverMatrix.spec.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverRiskMatrixEditor.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverSurveyMatrixEditor.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverConfigurationEditor.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/index.vue`

**Interfaces:**

- Consumes: `CutoverConfiguration.items`和`bindingRules`完整聚合；现有`updateDraft(revisionId, expectedVersion, data)`。
- Produces: `CutoverChecklistItem.businessCategoryCode?: string`与`CutoverBindingRule.requiredResult?: boolean`；纯函数`projectRiskMatrix(config)`、`projectSurveyMatrix(config)`、`applyBulkBinding(config, command)`；组件通过`v-model`修改同一个对象，不调用独立保存API。

- [ ] **Step 1: 写投影与批量编辑失败测试**

```ts
it('projects risk and survey without cloning aggregate ownership', () => {
  const config = fixtureConfiguration()
  expect(projectRiskMatrix(config).items.every((item) =>
    item.itemType === 'RISK' || item.itemType === 'DUAL_MACHINE_CHECK')).toBe(true)
  expect(projectSurveyMatrix(config).items.every((item) =>
    item.itemType === 'BUSINESS_SURVEY')).toBe(true)
})

it('bulk required edit updates binding results only', () => {
  const config = fixtureConfiguration()
  applyBulkBinding(config, { ruleKeys: ['RULE-1'], requiredResult: true })
  expect(config.bindingRules.find((rule) => rule.stableRuleKey === 'RULE-1')?.requiredResult)
    .toBe(true)
})
```

- [ ] **Step 2: 运行Vitest确认失败**

Run: `pnpm exec vitest run src/views/pms/cutover/cutover-config/components/cutoverMatrix.spec.ts`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

Expected: FAIL，投影纯函数尚不存在。

- [ ] **Step 3: 实现前端类型与纯函数**

```ts
export const DUAL_BASELINES: Record<string, number> = {
  VSM: 17,
  SILENT_DUAL: 25,
  DRP_DUAL: 23,
  NORMAL_DUAL: 24,
  CLUSTER: 8
}

export function projectRiskMatrix(config: CutoverConfiguration) {
  return {
    items: config.items.filter((item) =>
      item.itemType === 'RISK' || item.itemType === 'DUAL_MACHINE_CHECK'),
    rules: config.bindingRules.filter((rule) =>
      config.items.some((item) => item.stableItemKey === rule.stableItemKey &&
        (item.itemType === 'RISK' || item.itemType === 'DUAL_MACHINE_CHECK')))
  }
}
```

- [ ] **Step 4: 实现两个专用编辑器**

风险组件按24个普通风险类别和五类双机子表展示，五类标题显示`当前数/基准数`；支持批量设置割接类型、组网模式、设备类型、等级、必填、优先级和启停。调研组件按十二类展示，提供绑定级必填和割接背景六字段/两组条件可视化编辑；文件类只配置平台文件引用控件。

组件props固定为：

```ts
const props = defineProps<{
  readonly: boolean
  validationErrors: CutoverValidationError[]
}>()
const model = defineModel<CutoverConfiguration>({ required: true })
```

- [ ] **Step 5: 组合编辑器与分组错误定位**

在`CutoverConfigurationEditor.vue`新增“风险矩阵”“调研矩阵”页签；保留“统一采集项”作为基础维护入口。`risk.`错误自动打开风险页签，`survey.`错误自动打开调研页签，`base.`错误进入发布校验；只读状态传递到所有输入和批量动作。

- [ ] **Step 6: 运行前端验证**

Run: `pnpm exec vitest run src/views/pms/cutover/cutover-config/components/cutoverMatrix.spec.ts`

Run: `pnpm exec vue-tsc --noEmit --incremental --tsBuildInfoFile node_modules/.cache/vue-tsc/tsconfig.tsbuildinfo`

Run: `pnpm exec eslint src/api/pms/cutover/cutover-config/index.ts src/views/pms/cutover/cutover-config --cache --cache-location node_modules/.cache/eslint/`

Expected: 全部PASS；不得修改锁文件或新增依赖。

- [ ] **Step 7: 提交矩阵界面**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-config/index.ts yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/cutoverMatrix.ts yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/cutoverMatrix.spec.ts yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverRiskMatrixEditor.vue yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverSurveyMatrixEditor.vue yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/CutoverConfigurationEditor.vue yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/index.vue
git commit -m "feat(cutover): 增加风险与调研矩阵编辑界面"
```

---

### Task 6: 集成验证、真实浏览器与Feature收口

**Files:**

- Modify: `tasks/features/F-CUT-001.md`
- Modify: `specs/features/README.md`
- Regenerate: `docs/traceability/requirement-matrix.md`
- Regenerate: `docs/traceability/requirement-version-coverage.json`

**Interfaces:**

- Consumes: Task 1～5全部提交与固定测试环境。
- Produces: CUT-07/09/10自动化、MySQL、浏览器和追溯证据；只有所有证据通过才把Task置为`IMPLEMENTATION_COMPLETE`。

- [ ] **Step 1: 完成后端全量定向验证**

Run: `mvn -pl pms-module-cutover -am test`

Expected: PASS，旧`CutTask/CutRisk/CutPlan`测试行为不变。

- [ ] **Step 2: 完成前端类型、Lint和构建验证**

Run: `pnpm ts:check`

Run: `pnpm lint:eslint:check`

Run: `pnpm build:local`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

Expected: 全部PASS；使用现有`node_modules`与pnpm共享store，不重复下载。

- [ ] **Step 3: 使用当前仓库独立验收环境和端口启动宿主机应用**

使用独立Compose项目`npdms-e-fcut001-test`，数据库`npdms_fcut001_test`，MySQL端口`24316`、Redis端口`27379`；必须显式注入这些环境变量后再执行Compose，禁止复用其他worktree创建的同名容器。先只读确认后端`60280`和前端`20081`未被占用；若占用则重新选择空闲端口，不能停止开发端口`58080/18081`、既有验收端口`59280/19081`或其他任务进程。前端代理到当前验收后端端口。复用本机现有镜像和依赖，不重复下载。

Expected: `http://localhost:60280/actuator/health`为UP，`http://localhost:20081`可加载登录页；`docker inspect`证明Flyway只读迁移目录绑定到当前`E:\AICoding\Projects\NPDMS\sql\migrations`。

- [ ] **Step 4: 真实浏览器执行正负闭环**

按以下顺序保留截图、控制台与网络证据：

1. 登录后打开割接配置，复制当前已发布修订为草稿。
2. 风险页显示VSM `17/17`、静默双机`25/25`、DRP `23/23`、普通双机`24/24`、集群`8/8`。
3. 删除一条VSM项，预检必须在`risk.`位置报告`16/17`并保持旧发布修订有效。
4. 恢复后制造“所有情况必选”设备覆盖缺口，预检列出具体割接类型、设备类型和等级组合。
5. 调研页显示十二类；清空一条绑定的`requiredResult`或破坏背景条件引用，预检必须在`survey.`位置报错。
6. 恢复并成功发布；重新打开历史修订只读，刷新后新发布内容不丢失。
7. 使用无维护/发布权限账号分别验证查询可见而保存/发布被服务端拒绝。
8. 在320、768、1024、1440宽度检查项目名称、条件、计数和完整错误文本，无横向遮挡导致的不可操作项。

Expected: 无未处理控制台错误；请求只使用现有聚合API，CAS冲突时提示刷新而不覆盖另一页面修改。

- [ ] **Step 5: 重新生成追溯并自审**

Run: `python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json`

Run: `python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check`

Run: `git diff --check`

Expected: CUT-07/09/10均映射到F-CUT-001；未取得全部证据时保持`IN_PROGRESS`，全部通过后才派生`IMPLEMENTATION_COMPLETE`。

- [ ] **Step 6: 更新Feature证据并提交收口**

在`tasks/features/F-CUT-001.md`记录准确命令、结果、浏览器端口、截图路径和提交哈希；在`specs/features/README.md`只投影Task权威状态。自审确认未夹带CUT-03运行时、CUT-08、V2自动指派或旧`pms_cut_*`改造。

```bash
git add tasks/features/F-CUT-001.md specs/features/README.md docs/traceability/requirement-matrix.md docs/traceability/requirement-version-coverage.json
git commit -m "docs(cutover): 收口风险与调研矩阵实施证据"
```

---

## Technical Plan Gate

结论：`PASS / NPDMS-FCUT001-TECHPLAN-20260830-02`。

计划覆盖CUT-07/09/10同一配置聚合、两列必要前向契约、25个风险基准类别、五类97项双机检查、十二类调研、联合原子发布、完整种子、专用矩阵UI、权限负向、MySQL 8.4、真实浏览器和追溯收口。计划不建立第二Owner或生命周期，不修改历史迁移，不实施CUT-03运行时、CUT-08或V2自动指派。
