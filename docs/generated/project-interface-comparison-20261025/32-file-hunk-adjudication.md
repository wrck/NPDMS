# 32文件逐片段裁决及修复回执

原源提交：`4d149f34faec4b66e93373794693d6bbd738b716`。原测试提交：`c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`。后续源提交：`e0ae05247e6662c155c5503919506405eed07cf9`。32文件在原两份提交中内容一致。原始差异共69个片段。

本次27文件撤回错误迁入的同功能后续增量，5文件保留有证据的测试修复。恢复采用unified diff反向片段，经git apply --check后应用，无整文件覆盖。对应审批链/父级监听/样式和测试依赖同步撤回，不删除独立新增业务，不写数据库。

下面逐片段列出修改前差异、来源提交、具体裁决及修改后核对。新增行以源Git blame匹配，纯删除/移动片段以对应提交git show的删除行匹配；“文件来源”列全该文件后续提交，“片段命中”列本片段增删行命中的后续提交，不能把新源修复称为原提交丢失。

## F01 `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/ProjectDeviceInstallationFact.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F01-H01 `@@ -1,2 +1,6 @@`

片段命中：`196903868`。

裁决：恢复原源片段。移除后续审批重验链新增的设备归属版本投影及兼容构造器；原三字段契约恢复。

```diff
@@ -1,2 +1,6 @@
 package cn.iocoder.yudao.module.pms.asset.api.device.dto;
-public record ProjectDeviceInstallationFact(Long deviceId, String sn, String locationSnapshot) { }
+public record ProjectDeviceInstallationFact(Long deviceId, String sn, String locationSnapshot, Long projectAssignmentVersion) {
+    public ProjectDeviceInstallationFact(Long deviceId, String sn, String locationSnapshot) {
+        this(deviceId, sn, locationSnapshot, null);
+    }
+}
```

## F02 `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F02-H01 `@@ -318,7 +318,7 @@`

片段命中：`196903868`。

裁决：恢复原源片段。仅恢复安装设备锁查询原投影；租户、项目、逻辑删除、排序与FOR UPDATE保持。无DDL/DML。

```diff
@@ -318,7 +318,7 @@
     </select>
 
     <select id="selectProjectInstallationDevicesForUpdate" resultType="cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact">
-        SELECT id AS device_id, sn, location_snapshot FROM ast_device
+        SELECT id AS device_id, sn, location_snapshot, project_assignment_version FROM ast_device
         WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId} AND deleted = 0
         ORDER BY id FOR UPDATE
     </select>
```

## F03 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F03-H01 `@@ -271,16 +271,20 @@`

片段命中：`196903868`。

裁决：恢复原源片段。恢复原最终审批调用通用requireCurrent及原顺序；原候选资格、事务、节点/任务版本、拒绝分支和就绪校验仍在，不新增审批可读范围。

```diff
@@ -271,16 +271,20 @@
             return decisionResult(instance, task, current.getNodeNo());
         }
         validateAssessmentReview(current, input);
+        CutoverApprovalNodeDO next = approve ? nodeMapper.selectByInstanceAndNodeForUpdate(
+                new ApprovalNodeLockQuery(tenantId, instance.getId(), current.getNodeNo() + 1)) : null;
+        if (approve && next == null) {
+            if (byRole) readiness.requireCurrentForApproval(new cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.ApprovalRevalidation(
+                    task, instance.getId(), instance.getVersion(), current.getNodeNo()));
+            else readiness.requireCurrent(task);
+        }
         LocalDateTime now = LocalDateTime.now(clock);
         var operation = new CutoverOperationContext(actorId, correlationId, now);
         insertReviewItems(current, reviewItems, operation);
         workflow.decide(instance, current, input, actorId);
         updateNode(current, input, new DecisionAuditContext(operation, qualification.roleCandidate()));
         if (!approve) return rejectNew(instance, task, current, input, operation);
-        CutoverApprovalNodeDO next = nodeMapper.selectByInstanceAndNodeForUpdate(
-                new ApprovalNodeLockQuery(tenantId, instance.getId(), current.getNodeNo() + 1));
         if (next != null) return approveIntermediate(instance, task, current, next, operation);
-        readiness.requireCurrent(task);
         return approveFinal(instance, task, current, operation);
     }
```

## F04 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverContextSupport.java`

文件来源：e0ae05247e6662c155c5503919506405eed07cf9 fix(cutover): 允许未关联客户的割接等级手选。

修改后核对：文件内容与原源提交逐字一致。

### F04-H01 `@@ -61,6 +61,11 @@`

片段命中：`e0ae05247`。

裁决：恢复原源片段。撤回后续“无客户则NOT_CONFIGURED并允许手选”规则；不把后续新规则当成原行为。

```diff
@@ -61,6 +61,11 @@
 
     static CutoverCustomerLevelPort.CustomerLevelFact customerReference(
             CutoverCustomerLevelPort port, CutoverProjectContextPort.ProjectContextFact project) {
+        if (project.customerId() == null && (project.customerCode() == null || project.customerCode().isBlank())) {
+            // An absent association differs from a failed or unresolved customer lookup.
+            return CutoverCustomerLevelPort.CustomerLevelFact.builder().status("NOT_CONFIGURED")
+                    .customerName(project.customerName()).factVersion(0L).build();
+        }
         try {
             var fact = project.customerId() == null ? null : port.inspect(project.customerId());
             if (fact != null) return fact.toBuilder().customerCode(project.customerCode()).customerName(project.customerName()).build();
```

## F05 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverExecutionReadinessPolicy.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F05-H01 `@@ -13,13 +13,32 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回后续专用审批路径及其抽取；原PROJECT_EVENT就绪快照/项目/版本/设备集合校验完整恢复。

```diff
@@ -13,13 +13,32 @@
     private final CutoverReadinessPort readiness;
     private final CutoverTaskSnapshotStore snapshots;
 
+    public void requireCurrentForApproval(ApprovalRevalidation command) {
+        var task = command.task();
+        if (!"PROJECT_EVENT".equals(task.getIntakeSourceType())) return;
+        var expected = snapshots.readinessReference(task);
+        if (expected == null || !"READY".equals(expected.decision()) || !Objects.equals(task.getProjectId(), expected.projectId()))
+            throw new IllegalArgumentException("项目割接缺少有效的前序就绪快照");
+        var current = readiness.lockAndRevalidateForApproval(new cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query(
+                task.getTenantId(), task.getId(), task.getVersion(), command.approvalInstanceId(), command.approvalVersion(), command.nodeNo(),
+                task.getProjectId(), expected.snapshotId(), expected.snapshotVersion(), expected.deviceIds()));
+        requireSame(expected, current);
+    }
+
+    public record ApprovalRevalidation(CutoverTaskDO task, Long approvalInstanceId, Long approvalVersion, Integer nodeNo) { }
+
     public void requireCurrent(CutoverTaskDO task) {
         if (!"PROJECT_EVENT".equals(task.getIntakeSourceType())) return;
         var expected = snapshots.readiness(task);
         if (expected == null || !"READY".equals(expected.decision()) || !Objects.equals(task.getProjectId(), expected.projectId()))
             throw new IllegalArgumentException("项目割接缺少有效的前序就绪快照");
         var current = readiness.lockAndRevalidate(expected);
+        requireSame(expected, current);
+    }
+
+    private static void requireSame(CutoverReadinessPort.ReadinessFact expected, CutoverReadinessPort.ReadinessFact current) {
         if (current == null || !"READY".equals(current.decision())
+                || !Objects.equals(expected.projectId(), current.projectId())
                 || !Objects.equals(expected.snapshotId(), current.snapshotId())
                 || expected.snapshotVersion() != current.snapshotVersion()
                 || !Objects.equals(expected.deviceIds(), current.deviceIds()))
```

## F06 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverTaskCreationQueryService.java`

文件来源：e0ae05247e6662c155c5503919506405eed07cf9 fix(cutover): 允许未关联客户的割接等级手选；18a1d1876585f8dbec42922054a25a7cbfc42737 fix(cutover): 恢复客户编码解析与新建等级上下文。

修改后核对：文件内容与原源提交逐字一致。

### F06-H01 `@@ -4,7 +4,7 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。恢复原选项目读取客户服务等级方式及二字段返回；撤回提前完整等级上下文和客户缺省处理，设备解析路径不改。

```diff
@@ -4,7 +4,7 @@
 import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
 import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
 import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
-import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
+import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
 import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
 import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
 import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
```

### F06-H02 `@@ -31,6 +31,7 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。恢复原选项目读取客户服务等级方式及二字段返回；撤回提前完整等级上下文和客户缺省处理，设备解析路径不改。

```diff
@@ -31,6 +31,7 @@
     private final DeviceQueryApi devices;
     private final CutoverCustomerLevelPort customerLevels;
     private final CutoverTaskQueryService tasks;
+    private final CutoverProjectContextPort projectContexts;
 
     public List<ProjectBusinessContextApi.Option> projects(ProjectOptionsQuery query) {
         requireKeyword(query.keyword());
```

### F06-H03 `@@ -46,17 +47,10 @@`

片段命中：`18a1d1876`, `e0ae05247`。

裁决：恢复原源片段。恢复原选项目读取客户服务等级方式及二字段返回；撤回提前完整等级上下文和客户缺省处理，设备解析路径不改。

```diff
@@ -46,17 +47,10 @@
     public ProjectContext project(ProjectQuery query) {
         var ownerQuery = authorizedProject(query);
         var presentation = projects.presentation(ownerQuery);
-        var project = projects.inspect(ownerQuery);
-        String customerLevel = null;
-        if (project.customerId() != null) {
-            try {
-                var fact = customerLevels.inspect(project.customerId());
-                if (fact != null) customerLevel = fact.serviceLevelCode();
-            } catch (CutoverOwnerFactException exception) {
-                if (exception.code() != CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE) throw exception;
-            }
-        }
-        return new ProjectContext(presentation, customerLevel);
+        var project = projectContexts.inspect(new CutoverProjectContextPort.Inspection(
+                query.context().tenantId(), query.projectId(), ownerQuery.expectedScopeVersion()));
+        var customerFact = CutoverContextSupport.customerReference(customerLevels, project);
+        return new ProjectContext(presentation, customerFact.serviceLevelCode(), customerFact);
     }
 
     public ProjectDeviceShipmentApi.DevicePage devices(DevicePageQuery query) {
```

### F06-H04 `@@ -113,6 +107,7 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。恢复原选项目读取客户服务等级方式及二字段返回；撤回提前完整等级上下文和客户缺省处理，设备解析路径不改。

```diff
@@ -113,6 +107,7 @@
     @lombok.Builder
     public record DevicePageQuery(CutoverTaskContext context, Long projectId, String keyword, int pageNo, int pageSize) {}
     public record ResolveQuery(CutoverTaskContext context, List<String> serialNumbers) {}
-    public record ProjectContext(ProjectBusinessContextApi.Presentation project, String customerServiceLevel) {}
+    public record ProjectContext(ProjectBusinessContextApi.Presentation project, String customerServiceLevel,
+            CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevelContext) {}
     public record ResolvedDevices(CutoverTaskViews.CreateContextData context, List<ProjectDeviceShipmentApi.Device> devices) {}
 }
```

## F07 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/port/CutoverReadinessPort.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F07-H01 `@@ -12,6 +12,11 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回后续专用审批方法；原inspect、lockAndRevalidate、readSnapshot保留。

```diff
@@ -12,6 +12,11 @@
 
     ReadinessFact readSnapshot(SnapshotReference reference);
 
+    default ReadinessFact lockAndRevalidateForApproval(
+            cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query query) {
+        throw new SecurityException("当前审批重验服务未接入");
+    }
+
     @Builder
     record SnapshotReference(Long tenantId, Long projectId, Long snapshotId, Long snapshotVersion,
                              List<Long> deviceIds) { }
```

## F08 `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApi.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F08-H01 `@@ -9,6 +9,10 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回后续审批专用方法；原公共读取、锁定重验和精确快照读取保留。

```diff
@@ -9,6 +9,10 @@
 /** EXE-06割接前实施就绪快照的公共只读事实。 */
 public interface ImplementationReadinessApi {
 
+    /** 原审批事务内对当前合格节点做受控重验；不继承发起人权限，不泄露Owner正文。 */
+    cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.CutoverApprovalReadinessResult
+        lockAndRevalidateForApproval(cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query query);
+
     ImplementationReadinessResult inspect(ImplementationReadinessQuery query);
 
     ImplementationReadinessResult lockAndRevalidate(ImplementationReadinessRevalidationQuery query);
```

## F09 `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/implementationreadiness/ImplementationReadinessService.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F09-H01 `@@ -26,6 +26,8 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回专用审批身份入口及为它抽取的ContextRead；恢复原通用项目/设备授权后重验，不扩大权限。

```diff
@@ -26,6 +26,8 @@
 
 @Service @RequiredArgsConstructor
 public class ImplementationReadinessService implements ImplementationReadinessApi, ImplementationReadinessEvaluationApi {
+    @jakarta.annotation.Resource
+    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi> approvalAccess;
     private final ImplementationReadinessSnapshotMapper snapshots;
     private final ImplementationReadinessSourceMapper sources;
     private final ProjectBusinessContextApi projects;
```

### F09-H02 `@@ -35,6 +37,38 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回专用审批身份入口及为它抽取的ContextRead；恢复原通用项目/设备授权后重验，不扩大权限。

```diff
@@ -35,6 +37,38 @@
     private final DeliveryScopePort delivery;
     private final SolutionTieredReviewService reviews;
     private final ImplementationReadinessSnapshotStore snapshotStore;
+
+    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
+    public CutoverApprovalReadinessResult lockAndRevalidateForApproval(
+            cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query query) {
+        if (query == null || TenantContextHolder.isIgnore() || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()))
+            throw failure(TENANT_CONTEXT_MISMATCH, "实施就绪租户身份不一致");
+        var access = approvalAccess.getIfAvailable();
+        if (access == null) throw new SecurityException("当前审批重验授权服务不可用");
+        access.requireCurrent(query);
+        var row = snapshots.selectById(query.snapshotId());
+        if (row == null || !Objects.equals(row.getTenantId(), query.tenantId())
+                || !Objects.equals(row.getProjectId(), query.projectId()) || !Long.valueOf(0L).equals(query.snapshotVersion()))
+            throw failure(SNAPSHOT_NOT_FOUND, "精确就绪快照不存在");
+        var frozen = snapshot(row);
+        var ids = frozen.context().devices().stream().map(DeviceFact::deviceId).sorted().toList();
+        if (!ids.equals(query.deviceIds().stream().sorted().toList()))
+            throw failure(SNAPSHOT_NOT_FOUND, "精确就绪快照设备范围不一致");
+        var requested = new ImplementationReadinessQuery(query.tenantId(), query.projectId(), frozen.context().devices().stream()
+                .map(value -> new ImplementationReadinessQuery.ExpectedDevice(value.deviceId(), value.serialNumber(), value.projectAssignmentVersion())).toList());
+        if (!Objects.equals(row.getDeviceScopeKey(), deviceKey(requested)))
+            throw failure(SNAPSHOT_NOT_FOUND, "精确就绪快照设备范围不一致");
+        var requirements = projects.lockCutoverReadinessRequirements(new ProjectBusinessContextApi.ReadinessQuery(
+                query.tenantId(), query.projectId(), frozen.context().projectScopeVersion()));
+        var all = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId())).stream()
+                .map(value -> {
+                    if (value.projectAssignmentVersion() == null) throw failure(OWNER_DATA_CORRUPTED, "设备归属版本不可用");
+                    return new DeviceFact(value.deviceId(), value.sn(), value.projectAssignmentVersion());
+                }).sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
+        var current = context(new ContextRead(requested, frozen.context().projectScopeVersion(), all, requirements, true));
+        var result = result(frozen, current);
+        return new CutoverApprovalReadinessResult(result.decision().name(), query.projectId(), frozen.snapshotId(), frozen.snapshotVersion(), ids);
+    }
 
     @Override @Transactional(rollbackFor = Exception.class)
     public ImplementationReadinessResult evaluate(ImplementationReadinessQuery query) {
```

### F09-H03 `@@ -131,6 +165,17 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回专用审批身份入口及为它抽取的ContextRead；恢复原通用项目/设备授权后重验，不扩大权限。

```diff
@@ -131,6 +165,17 @@
                         throw failure(OWNER_DATA_CORRUPTED, "AST 当前项目设备事实不一致");
                     return new DeviceFact(device.deviceId(), device.sn(), device.projectAssignmentVersion());
                 }).sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
+        var policyQuery = new ProjectBusinessContextApi.Query(query.tenantId(), actor(), query.projectId(), project.scopeVersion());
+        var requirements = lock ? projects.lockImplementationRequirements(
+                new ProjectBusinessContextApi.Revalidation(policyQuery, project.projectVersion())) : projects.implementationRequirements(policyQuery);
+        return context(new ContextRead(query, project.scopeVersion(), all, requirements, lock));
+    }
+
+    private record ContextRead(ImplementationReadinessQuery query, Long scopeVersion, List<DeviceFact> all,
+                               ProjectBusinessContextApi.ImplementationRequirements requirements, boolean lock) { }
+
+    private ImplementationReadinessContextFact context(ContextRead read) {
+        var query = read.query(); var all = read.all(); var requirements = read.requirements(); boolean lock = read.lock();
         var selected = query.expectedDevices().stream().map(expected -> {
             var fact = all.stream().filter(device -> Objects.equals(device.deviceId(), expected.deviceId())).findFirst()
                     .orElseThrow(() -> failure(INVALID_REQUEST, "设备已不属于当前项目"));
```

### F09-H04 `@@ -140,9 +185,6 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回专用审批身份入口及为它抽取的ContextRead；恢复原通用项目/设备授权后重验，不扩大权限。

```diff
@@ -140,9 +185,6 @@
                 throw failure(INVALID_REQUEST, "设备范围版本已变化，请重新校验");
             return fact;
         }).toList();
-        var policyQuery = new ProjectBusinessContextApi.Query(query.tenantId(), actor(), query.projectId(), project.scopeVersion());
-        var requirements = lock ? projects.lockImplementationRequirements(
-                new ProjectBusinessContextApi.Revalidation(policyQuery, project.projectVersion())) : projects.implementationRequirements(policyQuery);
         if (requirements == null) throw failure(OWNER_DATA_CORRUPTED, "PROJ冻结实施前置不可用");
         var applicable = requirements.requiredSourceCodes().stream().map(SourceCode::valueOf)
                 .collect(java.util.stream.Collectors.toUnmodifiableSet());
```

### F09-H05 `@@ -162,7 +204,7 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回专用审批身份入口及为它抽取的ContextRead；恢复原通用项目/设备授权后重验，不扩大权限。

```diff
@@ -162,7 +204,7 @@
         var tested = !applicable.contains(SourceCode.EXE_04) ? List.<ImplementationReadinessRules.Record>of()
                 : sources.jointTests(owner).stream().map(row -> new ImplementationReadinessRules.Record(
                 row.getId(), row.getVersion(), row.getEquipmentId(), Integer.valueOf(2).equals(row.getStatus()))).toList();
-        return new ImplementationReadinessContextFact(project.scopeVersion(), selected,
+        return new ImplementationReadinessContextFact(read.scopeVersion(), selected,
                 new ApprovedPlanFact(plan.getId(), plan.getVersion()), List.of(
                 applicable.contains(SourceCode.EXE_01) ? arrival(query, all, lock) : notApplicable(SourceCode.EXE_01),
                 applicable.contains(SourceCode.EXE_02) ? ImplementationReadinessRules.milestone(SourceCode.EXE_02, installed, all) : notApplicable(SourceCode.EXE_02),
```

## F10 `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectBusinessContextApi.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F10-H01 `@@ -18,6 +18,11 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回后续内部ReadinessQuery/专用方法；原项目事实契约保留。

```diff
@@ -18,6 +18,11 @@
     /** 已授权项目当前冻结计划的实施前置适用性，不读取其他模块业务表。 */
     ImplementationRequirements implementationRequirements(Query query);
     ImplementationRequirements lockImplementationRequirements(Revalidation query);
+
+    /** 内部Owner事实。调用方必须已核验当前CUT审批实例及节点，只用于IMP原事务重验，不得返回给用户。 */
+    ImplementationRequirements lockCutoverReadinessRequirements(ReadinessQuery query);
+
+    record ReadinessQuery(Long tenantId, Long projectId, Long expectedScopeVersion) { }
 
     record ImplementationRequirements(Long planVersionId, Long planVersion, boolean configured,
                                       java.util.Set<String> requiredSourceCodes) {
```

## F11 `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectBusinessContextApiImpl.java`

文件来源：1969038688295e7b45f24497e85ae5c0f8cbff6e fix(cutover): 限定角色审批的内部就绪重验范围。

修改后核对：文件内容与原源提交逐字一致。

### F11-H01 `@@ -94,6 +94,27 @@`

片段命中：`196903868`。

裁决：恢复原源片段。撤回后续专用项目事实锁入口；原通用事实读取授权及锁定方法保留。

```diff
@@ -94,6 +94,27 @@
     }
 
     @Override
+    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
+    public ImplementationRequirements lockCutoverReadinessRequirements(ReadinessQuery query) {
+        if (query == null || TenantContextHolder.isIgnore() || query.tenantId() == null
+                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
+                || query.projectId() == null || query.projectId() <= 0 || query.expectedScopeVersion() == null)
+            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
+        Long actor = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
+        if (actor == null || actor <= 0) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
+        // 锁住真实项目树并校验版本；该专用内部事实不把审批资格转换成通用项目权限。
+        var scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
+                .tenantId(query.tenantId()).subjectUserId(actor).anchorProjectId(query.projectId())
+                .actionCode(ProjectScopeApi.ACTION_VIEW).expectedScopeVersion(query.expectedScopeVersion()).build());
+        if (scope == null || !Objects.equals(scope.treeVersion(), query.expectedScopeVersion()))
+            throw exception(PROJECT_TREE_VERSION_CONFLICT);
+        var project = projects.selectByIdForUpdate(query.projectId());
+        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId()))
+            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
+        return implementationRequirements(project);
+    }
+
+    @Override
     public ServiceManagers serviceManagers(Query query) {
         var context = inspect(query);
         var rows = members.selectParticipantFacts(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery.builder()
```

## F12 `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/task.ts`

文件来源：18a1d1876585f8dbec42922054a25a7cbfc42737 fix(cutover): 恢复客户编码解析与新建等级上下文。

修改后核对：文件内容与原源提交逐字一致。

### F12-H01 `@@ -61,9 +61,12 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。恢复原CreationProjectContext类型别名，配套恢复原后端响应；不改变WireLong精度类型。

```diff
@@ -61,9 +61,12 @@
 export const getCreationOptions = () => request.get<IntakeOption[]>({ url: '/api/v1/pms/cutover-tasks/creation-options' })
 
 export type { ProjectOption as CreationProjectOption, ProjectPresentation as CreationProjectPresentation,
-  ProjectDevice as CreationDevice, ProjectContext as CreationProjectContext } from '@/api/pms/project/selection'
+  ProjectDevice as CreationDevice } from '@/api/pms/project/selection'
 import type { ProjectOption as CreationProjectOption, ProjectDevice as CreationDevice,
-  ProjectContext as CreationProjectContext } from '@/api/pms/project/selection'
+  ProjectContext } from '@/api/pms/project/selection'
+export interface CreationProjectContext extends ProjectContext {
+  customerServiceLevelContext: import('./types').CustomerServiceLevelContext
+}
 export interface ResolvedCreationDevices { context: CreateContextData; devices: CreationDevice[] }
 
 export const searchCreationProjects = (keyword = '') =>
```

## F13 `yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/BusinessViewHost.vue`

文件来源：8e6cc41223db605eab1676fa14c42e1efba4ec5e fix(cutover): 改善只读展示与审批入口导航。

修改后核对：文件内容与原源提交逐字一致。

### F13-H01 `@@ -22,8 +22,8 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原操作表前置判定、阻塞原因字段及原文输出，移除后续文案映射和解释；不改操作宿主鉴权。

```diff
@@ -22,8 +22,8 @@
         <el-table-column prop="label" label="业务操作" min-width="120" />
         <el-table-column label="业务权限" width="120"><template #default="{ row }">{{ row.ownerPermitted ? '允许' : active.resolvedContext.businessObjectId == null ? '选择对象后校验' : '未获授权' }}</template></el-table-column>
         <el-table-column label="项目执行" width="100"><template #default="{ row }">{{ row.executionPermitted ? '允许' : '不可办理' }}</template></el-table-column>
-        <el-table-column prop="pre.outcome" label="前置判定" width="160" />
-        <el-table-column prop="reason" label="阻塞原因" min-width="180" />
+        <el-table-column label="前置判定" width="160"><template #default="{ row }">{{ ({ MATCHED: '满足', NO_ADDITIONAL_RULE: '无附加条件', NOT_MATCHED: '未满足', UNKNOWN: '暂无法判定' })[row.pre.outcome] || row.pre.outcome }}</template></el-table-column>
+        <el-table-column label="操作说明" min-width="180"><template #default="{ row }"><span :title="row.reason">{{ operationReason(row) }}</span></template></el-table-column>
       </el-table>
     </section>
     <el-alert v-if="operationError" :title="operationError" type="error" :closable="false" show-icon />
```

### F13-H02 `@@ -53,6 +53,14 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原操作表前置判定、阻塞原因字段及原文输出，移除后续文案映射和解释；不改操作宿主鉴权。

```diff
@@ -53,6 +53,14 @@
 const capture = (): BusinessViewTarget => ({ registration: { ...props.registration }, resolvedContext: { ...props.resolvedContext },
   allowedActions: [...(props.allowedActions || [])], readonly: props.readonly })
 const active = shallowRef(capture())
+const operationReason = (row: { operationCode: string; reason?: string }) => {
+  if (row.reason === 'OWNER_OPERATION_FORBIDDEN' && active.value.resolvedContext.businessObjectId != null
+      && active.value.registration.ownerContext === 'CUT'
+      && ['CUT.CUTOVER_TASK.CREATE', 'CUT.CUTOVER_APPROVAL.CREATE'].includes(row.operationCode))
+    return '新建入口不适用于已关联任务；已有任务按下方业务权限办理。'
+  return ({ OWNER_OPERATION_FORBIDDEN: '当前用户未获此操作授权', NODE_EXECUTION_FORBIDDEN: '当前节点不可再办理',
+    OPERATION_PRECONDITION_NOT_MATCHED: '操作前置条件未满足', OPERATION_RUNTIME_NOT_INSTALLED: '操作服务暂不可用' })[row.reason || ''] || row.reason || '无'
+}
 const operation = useOperationHost(active, () => emit('changed'))
 provide(operationClientKey, operation.client)
 provide(operationCheckKey, operation.waitForCheck)
```

## F14 `yudao-ui/yudao-ui-admin-vue3/src/components/UserTag/index.vue`

文件来源：目标工作区测试修复；源后续无此改动。

修改后核对：保留片段所在文件与本轮修改前SHA256一致。

### F14-H01 `@@ -16,11 +16,11 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。browser/configuration-detail-user-id-fixed.txt；UserTag.runtime.spec.ts：2112914651746513004保持原值，真实页面显示周文成

```diff
@@ -16,11 +16,11 @@
 })
 
 // 模块级缓存：避免同一用户ID重复请求
-const userCache = new Map<number, string>()
+const userCache = new Map<string, string>()
 
 const displayName = ref<string>('')
 
-const loadUserName = async (id: number) => {
+const loadUserName = async (id: string) => {
   if (!id) {
     displayName.value = '-'
     return
```

### F14-H02 `@@ -44,7 +44,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。browser/configuration-detail-user-id-fixed.txt；UserTag.runtime.spec.ts：2112914651746513004保持原值，真实页面显示周文成

```diff
@@ -44,7 +44,7 @@
 watch(
   () => props.userId,
   (val) => {
-    if (val) loadUserName(Number(val))
+    if (val) loadUserName(String(val))
     else displayName.value = '-'
   },
   { immediate: true }
```

## F15 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/questionnaire.vue`

文件来源：0499e2e3fcc6fe63addab55078b8b6016ae4b37d fix(acceptance): 跳过已处理采集的过期待办重试。

修改后核对：文件内容与原源提交逐字一致。

### F15-H01 `@@ -6,10 +6,11 @@`

片段命中：`0499e2e3f`。

裁决：恢复原源片段。撤回后续submitted历史引导文案/有效期显隐/一表两表提示改动；原submissionLocked、禁写和提交防重保留。

```diff
@@ -6,10 +6,11 @@
           <img :src="logo" alt="DPtech 迪普科技" />
           <span v-if="questionnaire" class="packet-status" :class="{ submitted: questionnaire.submitted || outcome }">{{ questionnaire.submitted || outcome ? '已提交' : '客户确认' }}</span>
         </div>
-        <h1 id="questionnaire-title">{{ outcome ? '感谢您的反馈' : questionnaire?.completionCertificate ? '完工确认与满意度调查' : '项目满意度调查' }}</h1>
+        <h1 id="questionnaire-title">{{ outcome || questionnaire?.submitted ? '感谢您的反馈' : questionnaire?.completionCertificate ? '完工确认与满意度调查' : '项目满意度调查' }}</h1>
         <p v-if="questionnaire?.completionCertificate" class="packet-project">{{ questionnaire.completionCertificate.name }}</p>
-        <p class="packet-intro">{{ questionnaire?.completionCertificate ? '请核对完工内容并评价本次服务，最后一次签字确认两份表单。' : '请评价本次项目交付服务，填写后签字提交。' }}</p>
-        <p v-if="questionnaire" class="packet-expiry">链接有效至 {{ formatDate(questionnaire.expiresAt) }}。提交后内容不可修改。</p>
+        <p v-if="questionnaire?.submitted || outcome" class="packet-intro">您的评价与签字已保存，以下内容为提交记录，不可修改。</p>
+        <p v-else class="packet-intro">{{ questionnaire?.completionCertificate ? '请核对完工内容并评价本次服务，最后一次签字确认两份表单。' : '请评价本次项目交付服务，填写后签字提交。' }}</p>
+        <p v-if="questionnaire && !questionnaire.submitted && !outcome" class="packet-expiry">链接有效至 {{ formatDate(questionnaire.expiresAt) }}。提交后内容不可修改。</p>
       </header>
       <el-skeleton v-if="loading" :rows="7" animated aria-label="正在加载确认表单" />
       <el-result v-else-if="errorMessage" icon="warning" title="问卷暂不可用" :sub-title="errorMessage" />
```

### F15-H02 `@@ -27,14 +28,14 @@`

片段命中：`0499e2e3f`。

裁决：恢复原源片段。撤回后续submitted历史引导文案/有效期显隐/一表两表提示改动；原submissionLocked、禁写和提交防重保留。

```diff
@@ -27,14 +28,14 @@
           <CompletionCertificateSheet :certificate="questionnaire.completionCertificate" :show-title="false" :show-hint="false" />
         </section>
         <section id="survey-section" ref="surveySection" class="packet-section survey-section" aria-labelledby="survey-title">
-          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '2' : '1' }}</span><div><h2 id="survey-title">满意度调查表</h2><p>请按实际体验评价，每一项选择一个选项。</p></div></div>
+          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '2' : '1' }}</span><div><h2 id="survey-title">满意度调查表</h2><p>{{ questionnaire.submitted ? '以下为已提交的服务评价。' : '请按实际体验评价，每一项选择一个选项。' }}</p></div></div>
           <QuestionnaireTable ref="questionFormApi" :definition="definition" :answers="answers" :disabled="submissionLocked" :show-signature="false" />
         </section>
         <section id="signature-section" ref="signatureSection" class="packet-section file-section" aria-labelledby="signature-title">
-          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '3' : '2' }}</span><div><h2 id="signature-title">{{ questionnaire.completionCertificate ? '统一签字确认' : '客户签字确认' }}</h2><p>{{ questionnaire.completionCertificate ? '仅需签字一次，同时确认以上完工证明与满意度调查表。' : '请由客户本人核对并签字。' }}</p></div></div>
+          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '3' : '2' }}</span><div><h2 id="signature-title">{{ questionnaire.completionCertificate ? '统一签字确认' : '客户签字确认' }}</h2><p>{{ questionnaire.submitted ? '已保存的客户签字。' : questionnaire.completionCertificate ? '仅需签字一次，同时确认以上完工证明与满意度调查表。' : '请由客户本人核对并签字。' }}</p></div></div>
           <div class="customer-confirmation-form"><form-create v-model="signatureValues" :rule="signatureRules" :option="questionFormOption" /></div>
           <div v-if="!questionnaire.submitted" class="attachment-field"><p>补充附件（可选）</p><el-upload multiple :auto-upload="false" :disabled="submissionLocked" :on-change="onAttachmentChange" :on-remove="onAttachmentRemove"><el-button>选择附件</el-button></el-upload></div>
-          <el-alert v-if="questionnaire.submitted" type="success" :closable="false" :title="`两份表单已统一提交 · ${formatDate(questionnaire.signedAt)}`" />
+          <el-alert v-if="questionnaire.submitted" type="success" :closable="false" :title="`${questionnaire.completionCertificate ? '两份表单已统一提交' : '评价已提交'} · ${formatDate(questionnaire.signedAt)}`" />
           <el-checkbox v-else-if="questionnaire.completionCertificate" v-model="confirmCompletionCertificate" :disabled="submissionLocked" class="joint-confirmation">我已核对完工证明及满意度调查表，以上签字同时确认两份表单。</el-checkbox>
         </section>
         <footer v-if="!questionnaire.submitted" class="submission-actions">
```

## F16 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverApprovalPanel.vue`

文件来源：8e6cc41223db605eab1676fa14c42e1efba4ec5e fix(cutover): 改善只读展示与审批入口导航。

修改后核对：文件内容与原源提交逐字一致。

### F16-H01 `@@ -53,7 +53,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原客户服务等级显示（仅NOT_CONFIGURED译名）；原冻结评估内容不变。

```diff
@@ -53,7 +53,7 @@
           <div class="cut-form-field"><label>操作复杂度等级</label><span class="cut-form-value cut-form-value--plain">{{ levelLabels[view.sourceSnapshot.assessment.operationComplexityLevel] || view.sourceSnapshot.assessment.operationComplexityLevel }}</span></div>
           <div class="cut-form-field"><label>隐患风险等级</label><span class="cut-form-value cut-form-value--plain">{{ levelLabels[view.sourceSnapshot.assessment.hiddenRiskLevel] || view.sourceSnapshot.assessment.hiddenRiskLevel }}</span></div>
           <div class="cut-form-field"><label>备件是否申请</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.assessment.sparePartApplied ? '是' : '否' }}</span></div>
-          <div class="cut-form-field"><label>客户服务等级</label><span class="cut-form-value cut-form-value--plain">{{ view.sourceSnapshot.assessment.customerServiceLevelCode === 'NOT_CONFIGURED' ? '未配置' : view.sourceSnapshot.assessment.customerServiceLevelCode }}</span></div>
+          <div class="cut-form-field"><label>客户服务等级</label><span class="cut-form-value cut-form-value--plain">{{ ({ NOT_CONFIGURED: '未配置', STANDARD: '标准服务' })[view.sourceSnapshot.assessment.customerServiceLevelCode] || view.sourceSnapshot.assessment.customerServiceLevelCode }}</span></div>
           <div class="cut-form-field"><label>人工确定割接等级</label><span class="grade-badge">{{ view.sourceSnapshot.assessment.manualGrade }}级</span></div>
         </div>
       </details>
```

## F17 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverChecklistPanel.vue`

文件来源：8e6cc41223db605eab1676fa14c42e1efba4ec5e fix(cutover): 改善只读展示与审批入口导航。

修改后核对：文件内容与原源提交逐字一致。

### F17-H01 `@@ -85,7 +85,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原说明平铺；不改题目、必填、来源与答案。

```diff
@@ -85,7 +85,7 @@
             <el-table-column label="采集项及核查说明" min-width="1">
               <template #default="{ row }">
                 <div v-if="isTableItem(row)" class="stacked-table-item">
-                  <div class="stacked-table-item__heading"><span>{{ row.itemName }}</span><span v-if="row.required" class="cut-required"> *</span><p v-if="row.itemDescription" class="item-description">{{ row.itemDescription }}</p><small v-if="row.sourceCode === 'CUSTOM'" class="cut-muted">自定义项</small></div>
+                  <div class="stacked-table-item__heading"><span>{{ row.itemName }}</span><span v-if="row.required" class="cut-required"> *</span><details v-if="row.itemDescription" class="item-description"><summary>填写说明与来源</summary><p>{{ row.itemDescription }}</p></details><small v-if="row.sourceCode === 'CUSTOM'" class="cut-muted">自定义项</small></div>
                   <CutoverChecklistField
                     :item="row" :direct-value="answers[row.stableItemKey] || ''" :readonly="readonly" :table-row="true"
                     :validation-message="validationKey === row.stableItemKey ? validationMessage : undefined"
```

### F17-H02 `@@ -95,7 +95,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原说明平铺；不改题目、必填、来源与答案。

```diff
@@ -95,7 +95,7 @@
                     @external-feedback="setExternalFeedback" @external-load="loadExternal" @external-save="saveExternalFeedback"
                     @collection="requestCollection" @remove="removeCustom" @refresh="refreshAfterCollection" />
                 </div>
-                <template v-else><span>{{ row.itemName }}</span><span v-if="row.required" class="cut-required"> *</span><p v-if="row.itemDescription" class="item-description">{{ row.itemDescription }}</p><small v-if="row.sourceCode === 'CUSTOM'" class="cut-muted">自定义项</small></template>
+                <template v-else><span>{{ row.itemName }}</span><span v-if="row.required" class="cut-required"> *</span><details v-if="row.itemDescription" class="item-description"><summary>填写说明与来源</summary><p>{{ row.itemDescription }}</p></details><small v-if="row.sourceCode === 'CUSTOM'" class="cut-muted">自定义项</small></template>
               </template>
             </el-table-column>
             <el-table-column label="检查结果／反馈内容" min-width="2">
```

## F18 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverClosurePanel.vue`

文件来源：8e6cc41223db605eab1676fa14c42e1efba4ec5e fix(cutover): 改善只读展示与审批入口导航。

修改后核对：文件内容与原源提交逐字一致。

### F18-H01 `@@ -8,7 +8,8 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原禁用结果单选与采集记录默认展开；不改归档状态/附件/写权限。

```diff
@@ -8,7 +8,8 @@
           <CutoverClosureForm ref="closureFormRef" v-model="content" :closure-id="closure.closureId" :editable="canSave && !writing"
             @upload-pending="attachmentPending = $event" @attachment-completed="onAttachmentCompleted" />
           <h4 class="cut-section-title">8. 割接结果<span class="cut-required"> *</span></h4>
-          <el-radio-group :model-value="selectedResult ?? undefined" :disabled="!canAttemptSubmit || writing" data-testid="closure-final-result" aria-label="割接最终结果" @update:model-value="selectedResult = $event as 'SUCCESS' | 'FAILED'">
+          <p v-if="closure.closureStatus === 'SUBMITTED'" data-testid="closure-final-summary">{{ selectedResult === 'SUCCESS' ? '割接成功' : selectedResult === 'FAILED' ? '割接失败' : '未记录' }}</p>
+          <el-radio-group v-else :model-value="selectedResult ?? undefined" :disabled="!canAttemptSubmit || writing" data-testid="closure-final-result" aria-label="割接最终结果" @update:model-value="selectedResult = $event as 'SUCCESS' | 'FAILED'">
             <el-radio value="SUCCESS">成功</el-radio><el-radio value="FAILED">失败</el-radio>
           </el-radio-group>
           <p class="cut-help result-help">成功与失败均可归档；项目、资产和外部系统的处理结果以实际回执为准。</p>
```

### F18-H02 `@@ -22,7 +23,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。恢复原禁用结果单选与采集记录默认展开；不改归档状态/附件/写权限。

```diff
@@ -22,7 +23,7 @@
         </footer>
         </Teleport>
       </details>
-      <details v-if="closure.closureId !== null" open class="cut-card collection-details">
+      <details v-if="closure.closureId !== null" :open="closure.closureStatus !== 'SUBMITTED' || closure.collectionEvidence.length > 0" class="cut-card collection-details">
         <summary class="cut-card__header"><h3>采集记录与失败恢复</h3><span class="cut-muted">{{ closure.collectionEvidence.length }} 条记录</span></summary>
         <div class="cut-card__body">
           <CutoverClosureEvidencePanel :task-id="taskId" :closure-id="closure.closureId" :evidence="closure.collectionEvidence" :devices="devices"
```

## F19 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-create/index.vue`

文件来源：18a1d1876585f8dbec42922054a25a7cbfc42737 fix(cutover): 恢复客户编码解析与新建等级上下文。

修改后核对：文件内容与原源提交逐字一致。

### F19-H01 `@@ -155,20 +155,35 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。撤回提前额外请求客户等级及其消息/空设备保留逻辑；恢复原项目选择/设备解析顺序。

```diff
@@ -155,20 +155,35 @@
 const changeProject = async (value: WireLong | null | undefined) => {
   const next = value ? String(value) : ''
   if (locked.value || props.projectId && next !== String(props.projectId)) return false
-  if (next === selectedProjectId.value && projectContext.value && !projectError.value) return true
+  if (next === selectedProjectId.value && projectContext.value && !projectError.value
+      && assessmentCustomerLevel.value && assessmentCustomerLevel.value.status !== 'UNAVAILABLE') return true
   if (selectedDevices.value.length && next !== selectedProjectId.value) {
     try { await message.confirm('切换项目会移除当前已选设备，请确认后重新选择。') } catch { return false }
   }
-  ++projectSequence
+  const sequence = ++projectSequence
   assessmentCustomerLevel.value = null
   assessmentLevelDeviceIds.value = []
   assessmentModel.manualCustomerServiceLevelCode = null
   selectedProjectId.value = next
   selectedDevices.value = []
   const context = await loadProjectInformation(next)
+  if (sequence !== projectSequence) return false
   if (!context) return !next
   if (!projectOptions.value.some(option => String(option.id) === next)) {
     projectOptions.value.unshift({ id: context.project.projectId, code: context.project.projectCode, name: context.project.projectName, customerName: context.project.customerName })
+  }
+  try {
+    const levelContext = await CutoverApi.getCreationProject(next)
+    if (sequence !== projectSequence || selectedProjectId.value !== next) return false
+    if (String(levelContext.project.projectId) !== next) throw new Error('PROJECT_CONTEXT_MISMATCH')
+    assessmentCustomerLevel.value = levelContext.customerServiceLevelContext
+  } catch {
+    if (sequence === projectSequence) {
+      assessmentCustomerLevel.value = { status: 'UNAVAILABLE', customerId: null, customerCode: null,
+        customerName: null, serviceLevelRevisionId: null, serviceLevelCode: null, factVersion: 0,
+        effectiveFrom: null, effectiveTo: null }
+      message.warning('客户服务等级暂时无法获取，请重新选择项目后重试。')
+    }
   }
   return true
 }
```

### F19-H02 `@@ -179,7 +194,7 @@`

片段命中：`18a1d1876`。

裁决：恢复原源片段。撤回提前额外请求客户等级及其消息/空设备保留逻辑；恢复原项目选择/设备解析顺序。

```diff
@@ -179,7 +194,7 @@
 }
 watch(() => selectedDevices.value.map(device => String(device.deviceId)).join(','), async () => {
   const devices = [...selectedDevices.value]
-  if (!devices.length) { assessmentCustomerLevel.value = null; assessmentLevelDeviceIds.value = []; return }
+  if (!devices.length) { assessmentLevelDeviceIds.value = []; return }
   if (assessmentCustomerLevel.value && devices.every(device => assessmentLevelDeviceIds.value.includes(String(device.deviceId)))) return
   const projectId = selectedProjectId.value
   const serials = devices.map(device => device.serialNumber)
```

## F20 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-detail/index.vue`

文件来源：目标工作区测试修复；源后续无此改动。

修改后核对：保留片段所在文件与本轮修改前SHA256一致。

### F20-H01 `@@ -111,7 +111,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：已完成审批任务导致P6只读已真实复现；限定closureOnly后双附件V1正式归档，历史阶段仍只读

```diff
@@ -111,7 +111,7 @@
 const CutoverClosurePanel = defineAsyncComponent(() => import('../components/CutoverClosurePanel.vue').then(module => module.default))
 const CutoverPlanPanel = defineAsyncComponent(() => import('../components/CutoverPlanPanel.vue').then(module => module.default))
 
-const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean }>()
+const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean; closureOnly?: boolean }>()
 const emit = defineEmits<{ close: []; changed: [] }>()
 const message = useMessage()
 const planPanel = ref<InstanceType<typeof CutoverPlanPanel>>()
```

### F20-H02 `@@ -119,7 +119,8 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：已完成审批任务导致P6只读已真实复现；限定closureOnly后双附件V1正式归档，历史阶段仍只读

```diff
@@ -119,7 +119,8 @@
 const approvalPanel = ref<InstanceType<typeof CutoverApprovalPanel>>()
 const stageActions = shallowRef<HTMLElement>()
 const detail = ref<CutoverTaskDetail | null>(null)
-const readonly = computed(() => !!props.readonly || !!detail.value?.approvalReadOnly)
+const readonly = computed(() => !!props.readonly || !!detail.value?.approvalReadOnly ||
+  (!!props.closureOnly && (detail.value?.task.currentStage !== 'P6' || selectedStage.value !== 'P6')))
 const detailLoading = ref(false)
 const detailError = ref('')
 const selectedStage = ref<CutoverApi.CutoverStage | null>(null)
```

## F21 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/tasks/index.vue`

文件来源：8e6cc41223db605eab1676fa14c42e1efba4ec5e fix(cutover): 改善只读展示与审批入口导航。

修改后核对：文件内容与原源提交逐字一致。

### F21-H01 `@@ -1,5 +1,11 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。撤回后续审批导航替代页及查询跳过；原列表鉴权由服务器维持，不补账号权限。

```diff
@@ -1,5 +1,11 @@
 <template>
   <main class="cutover-task-list" data-testid="cutover-task-list">
+    <el-result
+      v-if="!canQueryTasks" icon="info" title="割接审批入口"
+      :sub-title="canOpenApprovalTodos ? '普通任务列表需要任务查询权限；割接审批请从我的待办进入。' : '当前账号没有割接任务查询权限或审批待办入口。'">
+      <template #extra><el-button v-if="canOpenApprovalTodos" type="primary" @click="router.push('/bpm/task/todo')">查看我的审批待办</el-button></template>
+    </el-result>
+    <template v-else>
     <ContentWrap>
       <el-form :model="query" inline @submit.prevent="search">
         <el-form-item v-if="checkPermi(['pms:project:query'])" label="项目">
```

### F21-H02 `@@ -18,6 +24,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。撤回后续审批导航替代页及查询跳过；原列表鉴权由服务器维持，不补账号权限。

```diff
@@ -18,6 +24,7 @@
       <CutoverTaskTable :rows="rows" :loading="loading" :error="error" @open="openTask" />
       <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="loadPage" />
     </ContentWrap>
+    </template>
   </main>
 </template>
```

### F21-H03 `@@ -31,6 +38,8 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。撤回后续审批导航替代页及查询跳过；原列表鉴权由服务器维持，不补账号权限。

```diff
@@ -31,6 +38,8 @@
 defineOptions({ name: 'PmsCutoverTaskList' })
 const route = useRoute()
 const router = useRouter()
+const canQueryTasks = computed(() => checkPermi(['pms:cutover-task:query']))
+const canOpenApprovalTodos = computed(() => checkPermi(['bpm:task:query']))
 const rows = ref<CutoverTaskListItem[]>([])
 const total = ref(0)
 const loading = ref(false)
```

### F21-H04 `@@ -50,6 +59,7 @@`

片段命中：`8e6cc4122`。

裁决：恢复原源片段。撤回后续审批导航替代页及查询跳过；原列表鉴权由服务器维持，不补账号权限。

```diff
@@ -50,6 +59,7 @@
   } finally { if (current === projectSequence) projectsLoading.value = false }
 }
 const loadPage = async () => {
+  if (!canQueryTasks.value) return
   const current = ++sequence
   loading.value = true
   error.value = ''
```

## F22 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/training/index.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F22-H01 `@@ -139,6 +139,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回源后续联系人空列表说明；原选择、授权及确认流程不变。

```diff
@@ -139,6 +139,7 @@
               clearable
               class="!w-full"
               placeholder="从项目客户联系人中选择"
+              no-data-text="当前可见范围暂无该项目的客户联系人，请核对客户及联系人维护权限"
               :disabled="!form.projectId"
               @change="onContactSelect"
             >
```

## F23 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/training/public-confirm.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F23-H01 `@@ -4,7 +4,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回源后续已确认说明和字体样式；原status=2禁用、无重复提交/归档保护保持。

```diff
@@ -4,7 +4,7 @@
       <header>
         <span class="service-label">现场培训 · 客户确认</span>
         <h1 id="training-title">{{ record?.status === 2 ? '感谢您的确认' : '请评价本次培训' }}</h1>
-        <p>感谢您参与本次现场培训。欢迎核对培训内容、填写评价并反馈意见，手写签字确认后将归档至项目交付件。</p>
+        <p>{{ record?.status === 2 ? '以下为已确认的培训内容、评价和签字，记录已归档，不可修改。' : '感谢您参与本次现场培训。请核对培训内容、填写评价并签字确认。' }}</p>
       </header>
       <el-skeleton v-if="loading" :rows="7" animated />
       <el-result
```

### F23-H02 `@@ -36,7 +36,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回源后续已确认说明和字体样式；原status=2禁用、无重复提交/归档保护保持。

```diff
@@ -36,7 +36,7 @@
           :closable="false"
           :title="confirmedTitle"
         />
-        <div class="customer-confirmation-form">
+        <div class="customer-confirmation-form" :class="{ 'confirmed-record': record.status === 2 }">
           <form-create
             v-model="values"
             v-model:api="formApi"
```

### F23-H03 `@@ -255,4 +255,10 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回源后续已确认说明和字体样式；原status=2禁用、无重复提交/归档保护保持。

```diff
@@ -255,4 +255,10 @@
     padding: 16px;
   }
 }
+.confirmed-record :deep(.el-input.is-disabled .el-input__inner),
+.confirmed-record :deep(.el-textarea.is-disabled .el-textarea__inner),
+.confirmed-record :deep(.el-radio.is-disabled.is-checked .el-radio__label) {
+  color: var(--el-text-color-primary);
+  -webkit-text-fill-color: var(--el-text-color-primary);
+}
 </style>
```

## F24 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/detail/TaskStateActions.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F24-H01 `@@ -42,10 +42,13 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。恢复原完成条件提示和异常显示；不改COMPLETE命令、服务端完成条件或版本。

```diff
@@ -42,10 +42,13 @@
   busy.value = true; error.value = ''
   try {
     const result = await executeTaskAction(taskId, action, body, version, intent.keyFor({ taskId, action, version, body }))
-    if (action === 'COMPLETE' && result.status !== 'DONE') message.warning('完成条件尚未满足，任务状态未改变')
+    if (action === 'COMPLETE' && result.status !== 'DONE') {
+      error.value = '完成条件尚未满足，任务状态未改变。请核对本任务要求的交付件、业务结果及完成条件。'
+      message.warning(error.value)
+    }
     else message.success('任务操作已保存')
     emit('changed', result)
-  } catch (failure: any) { error.value = failure?.message || '任务操作未成功，可刷新核对或重试' }
+  } catch (failure: any) { error.value = (typeof failure === 'string' ? failure : failure?.message) || '任务操作未成功，可刷新核对或重试' }
   finally { busy.value = false }
 }
 defineExpose({ isBusy: () => busy.value })
```

## F25 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-closure/NormalClosureProcessView.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F25-H01 `@@ -10,13 +10,11 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续正常闭环/节点/结果翻译、日期格式和技术证据折叠；恢复原审批信息展示，原审批历史不变。

```diff
@@ -10,13 +10,11 @@
     <template v-if="detail">
       <h3>正常闭环申请</h3>
       <el-descriptions :column="1" border>
-        <el-descriptions-item label="申请 ID">{{ detail.application.id }}</el-descriptions-item>
-        <el-descriptions-item label="项目 ID">{{ detail.projectId }}</el-descriptions-item>
         <el-descriptions-item label="真实闭环来源阶段">{{
           detail.application.fromStage
         }}</el-descriptions-item>
         <el-descriptions-item label="闭环类型">{{
-          detail.application.closureType
+          detail.application.closureType === 'NORMAL' ? '正常闭环' : detail.application.closureType
         }}</el-descriptions-item>
         <el-descriptions-item label="申请状态">{{
           statusLabel(detail.application.status)
```

### F25-H02 `@@ -31,11 +29,15 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续正常闭环/节点/结果翻译、日期格式和技术证据折叠；恢复原审批信息展示，原审批历史不变。

```diff
@@ -31,11 +29,15 @@
           detail.application.reviewerUserId
         }}</el-descriptions-item>
         <el-descriptions-item label="提交时间">{{
-          detail.application.submittedAt
+          formatDate(detail.application.submittedAt)
         }}</el-descriptions-item>
         <el-descriptions-item label="决定时间">{{
-          detail.application.decidedAt || '尚无决定'
+          detail.application.decidedAt ? formatDate(detail.application.decidedAt) : '尚无决定'
         }}</el-descriptions-item>
+      </el-descriptions>
+<details class="technical-evidence"><summary>来源与流程标识</summary><el-descriptions :column="1" border>
+        <el-descriptions-item label="申请 ID">{{ detail.application.id }}</el-descriptions-item>
+        <el-descriptions-item label="项目 ID">{{ detail.projectId }}</el-descriptions-item>
         <el-descriptions-item label="实际流程实例 ID">{{
           detail.application.processInstanceId
         }}</el-descriptions-item>
```

### F25-H03 `@@ -48,7 +50,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续正常闭环/节点/结果翻译、日期格式和技术证据折叠；恢复原审批信息展示，原审批历史不变。

```diff
@@ -48,7 +50,7 @@
         <el-descriptions-item label="业务 Key">{{
           detail.application.businessKey
         }}</el-descriptions-item>
-      </el-descriptions>
+      </el-descriptions></details>
       <h3>申请冻结的条件校验</h3>
       <el-descriptions v-if="detail.snapshot" :column="1" border>
         <el-descriptions-item label="快照 ID">{{ detail.snapshot.id }}</el-descriptions-item>
```

### F25-H04 `@@ -59,7 +61,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续正常闭环/节点/结果翻译、日期格式和技术证据折叠；恢复原审批信息展示，原审批历史不变。

```diff
@@ -59,7 +61,7 @@
           detail.snapshot.checkedBy
         }}</el-descriptions-item>
         <el-descriptions-item label="校验时间">{{
-          detail.snapshot.checkedAt
+          formatDate(detail.snapshot.checkedAt)
         }}</el-descriptions-item>
       </el-descriptions>
       <el-table :data="checks" empty-text="暂无可展示的校验项" border>
```

### F25-H05 `@@ -70,26 +72,27 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续正常闭环/节点/结果翻译、日期格式和技术证据折叠；恢复原审批信息展示，原审批历史不变。

```diff
@@ -70,26 +72,27 @@
         <el-table-column prop="reason" label="原因" min-width="180" />
         <el-table-column prop="subjectId" label="来源对象 ID" min-width="180" />
       </el-table>
-      <h3>实际流程与候选人冻结证据</h3>
-      <pre class="process-evidence">{{ detail.application.processEvidence || '暂无流程证据' }}</pre>
+      <details class="technical-evidence"><summary>查看实际流程与候选人冻结证据</summary>
+      <pre class="process-evidence">{{ detail.application.processEvidence || '暂无流程证据' }}</pre></details>
       <h3>历史审核记录</h3>
       <el-table
         :data="detail.reviews"
         empty-text="暂无已归档审核记录；当前待办请查看 BPM 流程记录"
         border
       >
-        <el-table-column prop="taskDefinitionKey" label="实际节点 Key" min-width="180" />
+        <el-table-column label="审批节点" min-width="180"><template #default="{ row }"><span :title="row.taskDefinitionKey">{{ ({ serviceManagerReview: '服务经理审核', materialReview: '材料审核' })[row.taskDefinitionKey] || row.taskDefinitionKey }}</span></template></el-table-column>
         <el-table-column prop="taskId" label="任务 ID" min-width="180" />
         <el-table-column prop="reviewerUserId" label="实际处理人 ID" min-width="160" />
-        <el-table-column prop="outcome" label="处理结果" width="120" />
+        <el-table-column label="处理结果" width="120"><template #default="{ row }">{{ ({ APPROVE: '同意', REJECT: '驳回' })[row.outcome] || row.outcome }}</template></el-table-column>
         <el-table-column prop="reason" label="审核意见" min-width="180" />
-        <el-table-column prop="reviewedAt" label="处理时间" min-width="180" />
+        <el-table-column label="处理时间" min-width="180"><template #default="{ row }">{{ formatDate(row.reviewedAt) }}</template></el-table-column>
       </el-table>
     </template>
   </ContentWrap>
 </template>
 <script setup lang="ts">
 import { onBeforeUnmount, ref, watch } from 'vue'
+import { formatDate } from '@/utils/formatTime'
 import {
   getNormalClosureApplication,
   type ApplicationDetail,
```

## F26 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectApprovalDrawer.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F26-H01 `@@ -1,5 +1,5 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续closed事件，与父级监听同步恢复；原抽屉和BPM详情不变。

```diff
@@ -1,5 +1,5 @@
 <template>
-  <el-drawer v-model="visible" title="审批记录" size="min(1200px, 100%)" append-to-body destroy-on-close>
+  <el-drawer v-model="visible" title="审批记录" size="min(1200px, 100%)" append-to-body destroy-on-close @closed="emit('closed')">
     <div class="project-approval-detail">
       <ProcessInstanceDetail v-if="visible && instanceId" :key="instanceId" :id="instanceId" />
     </div>
```

### F26-H02 `@@ -10,6 +10,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续closed事件，与父级监听同步恢复；原抽屉和BPM详情不变。

```diff
@@ -10,6 +10,7 @@
 import { defineAsyncComponent, ref } from 'vue'
 
 const ProcessInstanceDetail = defineAsyncComponent(() => import('@/views/bpm/processInstance/detail/index.vue').then(module => module.default))
+const emit = defineEmits<{ closed: [] }>()
 const visible = ref(false)
 const instanceId = ref('')
 const open = (id: string) => {
```

## F27 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDeviceConfigLogDrawer.vue`

文件来源：目标工作区测试修复；源后续无此改动。

修改后核对：保留片段所在文件与本轮修改前SHA256一致。

### F27-H01 `@@ -8,6 +8,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。project-template-transfer-20261024/config-log-loading-performance.md；ProjectDeviceConfigLogDrawer.runtime.spec.ts：慢源不阻塞另一源、关闭/切换取消旧结果、权限分别检查

```diff
@@ -8,6 +8,7 @@
   >
     <div data-testid="device-config-log-drawer">
       <el-button :loading="loading" class="mb-4" @click="load">刷新</el-button>
+      <p v-if="pendingSources" role="status">正在加载其余配置日志…</p>
       <el-alert
         v-for="error in errors"
         :key="error"
```

### F27-H02 `@@ -78,6 +79,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。project-template-transfer-20261024/config-log-loading-performance.md；ProjectDeviceConfigLogDrawer.runtime.spec.ts：慢源不阻塞另一源、关闭/切换取消旧结果、权限分别检查

```diff
@@ -78,6 +79,7 @@
 const deviceId = ref<number>()
 const serialNumber = ref('')
 const loading = ref(false)
+const pendingSources = ref(0)
 const errors = ref<string[]>([])
 interface LogRow {
   key: string
```

### F27-H03 `@@ -107,35 +109,48 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。project-template-transfer-20261024/config-log-loading-performance.md；ProjectDeviceConfigLogDrawer.runtime.spec.ts：慢源不阻塞另一源、关闭/切换取消旧结果、权限分别检查

```diff
@@ -107,35 +109,48 @@
   parseStates.value = {}
   const canQueryArchive = checkPermi(['pms:device-log-archive:query'])
   const canQueryLegacy = checkPermi(['pms:device:query'])
-  const [archive, legacy] = await Promise.allSettled([
-    canQueryArchive ? ArchiveApi.history(props.projectId, currentDeviceId) : Promise.resolve([]),
-    canQueryLegacy ? DeviceApi.getConfigurationLogs(currentDeviceId) : Promise.resolve([])
-  ])
-  if (current !== sequence) return
+  pendingSources.value = Number(canQueryArchive) + Number(canQueryLegacy)
+  const projectId = props.projectId
   if (!canQueryArchive && !canQueryLegacy) errors.value.push('暂无配置日志查看权限')
-  if (archive.status === 'fulfilled') {
-    rows.value.push(
-      ...archive.value.map((file: ArchiveApi.LogFile) => ({
+  const sourceFinished = () => {
+    if (current !== sequence) return
+    --pendingSources.value
+    rows.value.sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
+    loading.value = rows.value.length === 0 && pendingSources.value > 0
+  }
+  await Promise.all([
+    canQueryArchive ? (async () => {
+      try {
+        const files = await ArchiveApi.history(projectId, currentDeviceId)
+        if (current !== sequence) return
+        rows.value.push(...files.map((file: ArchiveApi.LogFile) => ({
         key: `archive:${file.id}`,
         name: file.fileName,
         source: file.sourceType === 'MANUAL_UPLOAD' ? '手工上传' : '采集回传',
         time: file.archivedAt,
         file
-      }))
-    )
-  } else errors.value.push('设备归档日志加载失败，请刷新重试')
-  if (legacy.status === 'fulfilled') {
-    rows.value.push(
-      ...legacy.value.map((log: DeviceApi.DeviceConfigurationLogVO) => ({
+        })))
+      } catch {
+        if (current === sequence) errors.value.push('设备归档日志加载失败，请刷新重试')
+      } finally { sourceFinished() }
+    })() : Promise.resolve(),
+    canQueryLegacy ? (async () => {
+      try {
+        const logs = await DeviceApi.getConfigurationLogs(currentDeviceId)
+        if (current !== sequence) return
+        rows.value.push(...logs.map((log: DeviceApi.DeviceConfigurationLogVO) => ({
         key: `legacy:${log.id}`,
         name: log.configType || '配置日志',
         source: log.sourceSystem || '—',
         time: log.collectedAt,
         legacy: log
-      }))
-    )
-  } else errors.value.push('历史配置日志加载失败，请刷新重试')
-  rows.value.sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
+        })))
+      } catch {
+        if (current === sequence) errors.value.push('历史配置日志加载失败，请刷新重试')
+      } finally { sourceFinished() }
+    })() : Promise.resolve()
+  ])
+  if (current !== sequence) return
   loading.value = false
   await Promise.all(
     rows.value
```

### F27-H04 `@@ -179,6 +194,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。project-template-transfer-20261024/config-log-loading-performance.md；ProjectDeviceConfigLogDrawer.runtime.spec.ts：慢源不阻塞另一源、关闭/切换取消旧结果、权限分别检查

```diff
@@ -179,6 +194,7 @@
   if (!value) {
     ++sequence
     loading.value = false
+    pendingSources.value = 0
     rows.value = []
     errors.value = []
     parseStates.value = {}
```

## F28 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectFlowPanel.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F28-H01 `@@ -293,9 +293,9 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续回读终止条件、提示及闭环refresh联动；恢复原任务同步行为，不改任务状态机和P6修复。

```diff
@@ -293,9 +293,9 @@
         >推进至 {{ readiness.nextStage }}{{ nextStageName ? ` ${nextStageName}` : '' }}</el-button>
       </template>
       <template v-else-if="workbench && !taskPending && !loadError">
-        <span v-if="pageSyncing" role="status">业务已保存，正在同步任务状态…</span>
+        <span v-if="pageSyncing && workbench.trustedTargetRef !== '/pms/project-management/project-master-detail'" role="status">业务已保存，正在同步任务状态…</span>
         <TaskStateActions
-          v-else-if="taskActionWorkbench"
+          v-if="!pageSyncing && taskActionWorkbench"
           ref="stateActionsRef" :workbench="taskActionWorkbench" :business-bound="businessBound"
           :business-fact-version="businessFactVersion" :before-action="requestLeave" @changed="handleBusinessChanged">
           <ProjectTaskDetailsEditor v-if="taskActionWorkbench.allowedActions.includes('UPDATE_PROGRESS')" ref="detailsRef" :workbench="taskActionWorkbench" :before-action="requestLeave" @changed="handleBusinessChanged" />
```

### F28-H02 `@@ -439,7 +439,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续回读终止条件、提示及闭环refresh联动；恢复原任务同步行为，不改任务状态机和P6修复。

```diff
@@ -439,7 +439,7 @@
 })
 const loadError = ref('')
 const businessRef = ref<InstanceType<typeof TaskBusinessPanel>>()
-const pageEmbedRef = ref<{ requestLeave?: () => Promise<boolean> }>()
+const pageEmbedRef = ref<{ requestLeave?: () => Promise<boolean>; refresh?: () => Promise<unknown> }>()
 const stageBusinessRef = ref<InstanceType<typeof StageBusinessPanel>>()
 // 阶段业务绑定类型由 StageBusinessPanel 装载后上报；STAGE_NATIVE 阶段不渲染业务办理区
 const stageBindingType = ref<string>()
```

### F28-H03 `@@ -685,6 +685,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续回读终止条件、提示及闭环refresh联动；恢复原任务同步行为，不改任务状态机和P6修复。

```diff
@@ -685,6 +685,7 @@
   if (taskId == null) return
   const token = sequence, refresh = ++embeddedRefresh
   const previousVersion = workbench.value?.task.version
+  const endUserEntry = workbench.value?.trustedTargetRef === '/pms/project-management/project-master-detail'
   // 联系人规则由提交后的事件重评；给异步完成留出回读窗口，期间不展示旧版本任务动作。
   const attempts = ['/customer-asset/customer-contact', '/pms/project-management/project-master-detail']
     .includes(workbench.value?.trustedTargetRef || '') ? 16 : 1
```

### F28-H04 `@@ -697,7 +698,8 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续回读终止条件、提示及闭环refresh联动；恢复原任务同步行为，不改任务状态机和P6修复。

```diff
@@ -697,7 +698,8 @@
       const next = await TaskWorkbenchApi.getTaskWorkbench(taskId)
       if (!stillCurrent()) return
       workbench.value = next
-      if (next.task.version !== previousVersion || ['DONE', 'CLOSED', 'CANCELLED'].includes(next.task.status || '')) break
+      if (['DONE', 'CLOSED', 'CANCELLED'].includes(next.task.status || '')
+          || !endUserEntry && next.task.version !== previousVersion) break
     }
     await loadWorkspace(token)
     if (stillCurrent()) emit('changed')
```

### F28-H05 `@@ -724,6 +726,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续回读终止条件、提示及闭环refresh联动；恢复原任务同步行为，不改任务状态机和P6修复。

```diff
@@ -724,6 +726,7 @@
     await Promise.all([loadWorkspace(token), taskId == null ? undefined : loadTaskWorkbench(taskId, token)])
     if (current(token) && props.selection?.kind === 'stage') await tasks.reload()
     if (!current(token)) return false
+    if (pageEmbed.value === ProjectNormalClosurePanel) await pageEmbedRef.value?.refresh?.()
     loadError.value = ''
     return true
   } catch {
```

## F29 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F29-H01 `@@ -166,6 +166,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续暴露refresh；原内部刷新/校验/提交及权限保持。

```diff
@@ -166,6 +166,7 @@
 const refresh = () => {
   if (!busy.value && !disposed) return load()
 }
+defineExpose({ refresh })
 const check = async () => {
   if (!overview.value || !allowed('CHECK')) return
   const value = { ...overview.value },
```

## F30 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/StageGateResultsPanel.vue`

文件来源：ce6b78654615adebd84ef4c5a198fb41ba443446 fix(project): 刷新审批结果并保留历史只读展示。

修改后核对：文件内容与原源提交逐字一致。

### F30-H01 `@@ -9,7 +9,8 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续历史诊断说明、historical参数与PROJECT_NOT_ACTIVE文案；原诊断/历史业务数据不改。

```diff
@@ -9,7 +9,8 @@
     <p v-if="loading" role="status">正在读取本轮门禁结果…</p>
     <el-alert v-else-if="error" :title="error" type="warning" :closable="false" show-icon />
     <template v-else-if="workbench">
-      <p class="hint">第 {{ workbench.executionRound }} 轮 · 当前规则结果；刷新不会推进阶段或发起审批。</p>
+      <el-alert v-if="historical" title="项目已结束，当前办理条件不再适用。以下为当前规则诊断，历史完成及审批记录保持原样。" type="info" :closable="false" show-icon />
+      <p v-else class="hint">第 {{ workbench.executionRound }} 轮 · 当前规则结果；刷新不会推进阶段或发起审批。</p>
       <p v-if="!workbench.gates.length" role="status">当前阶段无准入条件限制。</p>
 
       <!-- 准入/准出条件：与交付件清单同款分节行格式；每条引用的满足结果来自按下标对齐的规则条件诊断 -->
```

### F30-H02 `@@ -103,6 +104,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续历史诊断说明、historical参数与PROJECT_NOT_ACTIVE文案；原诊断/历史业务数据不改。

```diff
@@ -103,6 +104,7 @@
   projectId: number
   stageCode: string
   projectVersion?: number
+  historical?: boolean
   /** 面板标题：项目主档侧栏按完成条件口径展示，其余用法保持阶段门禁条件 */
   title?: string
   /** 项目实例视图：用于把引用编码解析为业务名称/交付件实例；缺省时回退展示编码 */
```

### F30-H03 `@@ -207,6 +209,7 @@`

片段命中：`ce6b78654`。

裁决：恢复原源片段。撤回后续历史诊断说明、historical参数与PROJECT_NOT_ACTIVE文案；原诊断/历史业务数据不改。

```diff
@@ -207,6 +209,7 @@
 const handleDeliverableChanged = async () => { await load(); emit('changed') }
 // 原因码中文名：仅覆盖各门禁事实提供者已登记的取值，未登记的原样展示
 const REASON_LABELS: Record<string, string> = {
+  PROJECT_NOT_ACTIVE: '项目已结束，当前办理条件不再适用',
   TASK_NOT_FOUND: '任务不存在',
   TASK_NOT_DONE: '任务未完成',
   MILESTONE_NOT_FOUND: '里程碑不存在',
```

## F31 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/TaskBusinessPanel.vue`

文件来源：目标工作区测试修复；源后续无此改动。

修改后核对：保留片段所在文件与本轮修改前SHA256一致。

### F31-H01 `@@ -14,6 +14,11 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：CUT/CUTOVER_APPROVAL、冻结组件、QUERY与既有关联共同限定，不恢复项目任务写动作

```diff
@@ -14,6 +14,11 @@
         :project="{ ...project, id: project.id == null ? undefined : legacyOwnerId(project.id) }"
         :readonly="maintenanceReadonly"
         @changed="ownerChanged"
+      />
+      <CutoverTaskDetail
+        v-else-if="completedCutover && project && selectedObject"
+        ref="cutoverRef" :task-id="selectedObject" :project-id="project.id"
+        closure-only :readonly="maintenanceReadonly" @changed="ownerChanged"
       />
       <BusinessViewHost
         v-else-if="registration && project"
```

### F31-H02 `@@ -52,6 +57,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：CUT/CUTOVER_APPROVAL、冻结组件、QUERY与既有关联共同限定，不恢复项目任务写动作

```diff
@@ -52,6 +57,7 @@
 import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
 import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
 import ProjectMembersBusinessView from '@/components/BusinessView/ProjectMembersBusinessView.vue'
+const CutoverTaskDetail = defineAsyncComponent(() => import('@/views/pms/cutover/task-detail/index.vue').then(module => module.default))
 import * as BusinessApi from '@/api/pms/project/task-business'
 import type { TaskBusinessContext, TaskBusinessObject } from '@/api/pms/project/task-business'
 import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
```

### F31-H03 `@@ -84,6 +90,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：CUT/CUTOVER_APPROVAL、冻结组件、QUERY与既有关联共同限定，不恢复项目任务写动作

```diff
@@ -84,6 +90,7 @@
 const registration = ref<BusinessViewRegistrationVO>()
 const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
 const membersRef = ref<InstanceType<typeof ProjectMembersBusinessView>>()
+const cutoverRef = ref<InstanceType<typeof CutoverTaskDetail>>()
 // 业务视图宿主的首判定完成状态（expose 的 viewSettled）：宿主未挂载或执行检查中视为未就绪
 const hostSettled = computed(() => hostRef.value?.viewSettled === true)
 const loading = ref(false), dirty = ref(false)
```

### F31-H04 `@@ -116,6 +123,13 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：CUT/CUTOVER_APPROVAL、冻结组件、QUERY与既有关联共同限定，不恢复项目任务写动作

```diff
@@ -116,6 +123,13 @@
   context.value?.ownerActions.includes('QUERY'))
 const maintenanceReadonly = computed(() => Boolean(props.readonly || loading.value || error.value ||
   context.value?.recoverableError || !sameTask(context.value?.taskId, props.taskId)))
+// 审批完成只结束项目任务；关联割接的P6继续由割接Owner授权和状态机办理。
+const completedCutover = computed(() => props.completed &&
+  sameTask(context.value?.taskId, props.taskId) && context.value?.ownerContext === 'CUT' &&
+  context.value?.objectType === 'CUTOVER_APPROVAL' &&
+  registration.value?.componentKey === 'CUT_CUTOVER_APPROVAL_WORKBENCH' &&
+  context.value?.ownerActions.includes('QUERY') &&
+  context.value.links.some(row => row.objectId === selectedObject.value))
 // Owner pages enforce each row's state. Automatic links can lag behind a saved draft.
 // 只读（未进入阶段/已完成/执行不允许）只保留 QUERY：视图照常渲染，写操作由 Owner 页面隐藏
 const ownerActions = computed(() => {
```

### F31-H05 `@@ -219,6 +233,7 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md：CUT/CUTOVER_APPROVAL、冻结组件、QUERY与既有关联共同限定，不恢复项目任务写动作

```diff
@@ -219,6 +233,7 @@
 }
 const requestLeave = async () => {
   try {
+    if (cutoverRef.value) return await cutoverRef.value.saveBeforeLeave()
     const host = membersRef.value || hostRef.value
     if (!host) return !dirty.value
     return (await host.requestLeave()) !== false
```

## F32 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/saveArrivalReceipt.ts`

文件来源：目标工作区测试修复；源后续无此改动。

修改后核对：保留片段所在文件与本轮修改前SHA256一致。

### F32-H01 `@@ -30,7 +30,8 @@`

片段命中：目标测试修复片段；无后续源新增行命中。

裁决：保留测试修复。stage-workflow-retest-result.md及saveArrivalReceipt.runtime.spec.ts：首次上传省略artifactId/referenceVersion，既有版本上传仍带预期版本

```diff
@@ -30,7 +30,8 @@
     }
     attempt.initialized ||= await initializeUpload({
       modeCode: form.signDocumentArtifactId ? 'ADD_VERSION' : 'CREATE_ARTIFACT',
-      artifactId: form.signDocumentArtifactId, expectedReferenceVersion: form.signDocumentReferenceVersion,
+      ...(form.signDocumentArtifactId ? { artifactId: form.signDocumentArtifactId,
+        expectedReferenceVersion: form.signDocumentReferenceVersion } : {}),
       ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: String(form.id),
       purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'arrival-sign-document', categoryCode: 'ARRIVAL_SIGN_DOCUMENT',
       fileName: attempt.file.name, declaredSizeBytes: attempt.file.size,
```

## 配套依赖处理

下列15个文件只处理直接依赖；不是另外自动扩大业务核查范围。撤回专用审批链的新API/实现/DTO及专用测试，因为它们替换既有最终审批处理，不是独立纯新增业务；原通用审批、资格/租户/版本/冻结/就绪失败测试保留。

| 文件 | 裁决 |
|---|---|
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/framework/adapter/CutoverOwnerAdapters.java` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverExecutionReadinessPolicyTest.java` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverTaskCreationQueryServiceTest.java` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/implementationreadiness/ImplementationReadinessServiceTest.java` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/tasks/index.spec.ts` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-create/taskCreationDetail.spec.ts` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-engineering-api/pom.xml` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApiContractTest.java` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverChecklistField.vue` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverClosureForm.vue` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue` | 撤回32文件中源后续同功能迁入的直接依赖或对应验收断言；保留原授权/历史/文件保护和纯新增能力 |
| `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalReadinessAccessApi.java` | 上轮错误迁入的专用审批链新增文件；属于替换既有审批处理的依赖，非独立新增业务；随调用链撤回，原审批安全测试保留 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalReadinessAccessApiImpl.java` | 上轮错误迁入的专用审批链新增文件；属于替换既有审批处理的依赖，非独立新增业务；随调用链撤回，原审批安全测试保留 |
| `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalReadinessAccessApiImplTest.java` | 上轮错误迁入的专用审批链新增文件；属于替换既有审批处理的依赖，非独立新增业务；随调用链撤回，原审批安全测试保留 |
| `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/CutoverApprovalReadinessResult.java` | 上轮错误迁入的专用审批链新增文件；属于替换既有审批处理的依赖，非独立新增业务；随调用链撤回，原审批安全测试保留 |

培训与满意度的历史禁写/防重复提交测试保留，仅移除已撤回后续文案的断言。ImplementationReadinessApi契约检查保留原readSnapshot方法，未退回过期“两方法”断言。

## 验证与运行边界

前端/后端结果及服务候选见 [本次验证回执](32-file-verification.md)。浏览器未完成前不得标为逐项业务验收通过；原已闭环项目仅回看，不重开、不重写历史。
