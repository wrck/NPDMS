package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectExecutionHistoryService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProcessContextResolver;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实消费服务及序列化器，外围持久化替身不计为数据库事务验证。 */
class TemplateSnapshotConsumersTest {
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final ProjectGateReferenceInstanceMapper references = mock(ProjectGateReferenceInstanceMapper.class);
    private final PlatformBusinessEventApi events = mock(PlatformBusinessEventApi.class);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"executionSchemaVersion\":3}", "{\"executionSchemaVersion\":4}",
            "{\"executionSchemaVersion\":\"2\"}", "{\"executionSchemaVersion\":2.0}"})
    void allThreeConsumersRejectUninterpretableVersionBeforeActing(String json) {
        var plan = plan(json, "EFFECTIVE");
        when(plans.selectEffective(any())).thenReturn(plan);
        when(plans.selectHistory(any())).thenReturn(List.of(plan));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        var history = new ProjectExecutionHistoryService(scopes, permissions, plans, executions);
        assertThrows(RuntimeException.class, () -> history.get(9L, 1L));
        verifyNoInteractions(executions);

        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        project.setActivePlanVersionId(51L); project.setLifecycleStatus("ACTIVE");
        var resolver = new ProjectStageGateProcessContextResolver(plans, graph, references, executions);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project, 22L));
        verifyNoInteractions(graph, references, executions);

        when(executions.selectCurrent(any())).thenReturn(List.of(round()));
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        assertThrows(RuntimeException.class, () -> scheduler.scheduleFromNode(9L, "STAGE", 11L));
        verifyNoInteractions(events);
    }

    @Test
    void supportedNewAndLegacyHistoryRetainsItsOwnNamesAndDoesNotEmitEvents() {
        var snapshot = TemplateVersionSnapshotTest.snapshot();
        String frozen = JsonUtils.toJsonString(snapshot);
        var old = plan(frozen, "SUPERSEDED");
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(plans.selectHistory(any())).thenReturn(List.of(old));
        when(executions.selectHistory(any())).thenReturn(List.of(round()));
        var history = new ProjectExecutionHistoryService(scopes, permissions, plans, executions);

        assertEquals("阶段0", history.get(9L, 1L).rounds().getFirst().name());
        assertEquals(frozen, old.getExecutionSnapshot());
        snapshot.setExecutionSchemaVersion(2);
        old.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertEquals("阶段0", history.get(9L, 1L).rounds().getFirst().name());
        verify(plans, never()).selectEffective(any());
        verify(plans, never()).updateById(any(ProjectPlanVersionDO.class));
        verifyNoInteractions(events);
    }

    @Test
    void typedSchedulerCannotBypassNewFormatValidationEvenWithNoActiveRounds() {
        var snapshot = TemplateVersionSnapshotTest.snapshot();
        snapshot.getRulePrograms().clear();
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(9L, 51L, snapshot, null));
        verifyNoInteractions(executions, events, plans);
    }

    @Test
    void supportedTypedSnapshotWithoutTimeConditionsDoesNotInventTimerEvents() {
        var snapshot = TemplateVersionSnapshotTest.snapshot();
        when(executions.selectCurrent(any())).thenReturn(List.of(round()));
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        assertDoesNotThrow(() -> scheduler.schedule(9L, 51L, snapshot, null));
        verifyNoInteractions(events, plans);
    }

    private ProjectPlanVersionDO plan(String json, String status) {
        var plan = new ProjectPlanVersionDO(); plan.setId(51L); plan.setProjectId(9L); plan.setTenantId(7L);
        plan.setStatus(status); plan.setRevisionNo(1); plan.setExecutionSnapshot(json); return plan;
    }

    private ProjectNodeExecutionDO round() {
        var round = new ProjectNodeExecutionDO(); round.setId(31L); round.setPlanVersionId(51L);
        round.setNodeKind("STAGE"); round.setNodeKey("stage:S0"); round.setNodeInstanceId(11L);
        round.setRoundNo(1); round.setStatus("PENDING"); round.setCurrentMarker(1); return round;
    }
}
