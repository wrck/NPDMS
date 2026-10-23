package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectOperationContextResolverTest {
    private final TaskBusinessAccess access = mock(TaskBusinessAccess.class);
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectNodeExecutionMapper nodes = mock(ProjectNodeExecutionMapper.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    private final ProjectNodeExecutionDO round = new ProjectNodeExecutionDO();
    private final ProjectPlanVersionDO historical = new ProjectPlanVersionDO();
    private final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    private ProjectOperationContextResolver resolver;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(8L).setTenantId(7L), new MockHttpServletRequest());
        task.setId(2L); task.setTenantId(7L); task.setProjectId(1L); task.setCode("SURVEY"); task.setStatus("DONE");
        when(access.read(2L, 7L, 8L)).thenReturn(task);
        var project = new ProjectMasterDO(); project.setId(1L); project.setTenantId(7L); project.setActivePlanVersionId(12L);
        when(projects.selectById(1L)).thenReturn(project);
        var effective = new ProjectPlanVersionDO(); effective.setId(12L); effective.setTenantId(7L);
        effective.setProjectId(1L); effective.setStatus("EFFECTIVE");
        when(plans.selectEffective(any())).thenReturn(effective);
        round.setId(3L); round.setTenantId(7L); round.setProjectId(1L); round.setPlanVersionId(11L);
        round.setNodeKind("TASK"); round.setNodeInstanceId(2L); round.setNodeKey("task:SURVEY"); round.setContractId(4L);
        round.setStatus("DONE"); round.setEndedAt(LocalDateTime.now());
        when(nodes.selectCurrent(any())).thenReturn(List.of(round));
        var definition = new TemplateExecutionSnapshot.TaskContract(); definition.setNodeKey("task:SURVEY"); definition.setCode("SURVEY");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setComponentKey("original-survey");
        definition.setBinding(binding); snapshot.setTasks(List.of(definition));
        historical.setId(11L); historical.setTenantId(7L); historical.setProjectId(1L); historical.setStatus("SUPERSEDED");
        historical.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(plans.selectById(11L)).thenReturn(historical);
        resolver = new ProjectOperationContextResolver(access, mock(ProjectManualCreationService.class), projects,
                plans, nodes, mock(ProjectRuntimeGraphMapper.class), executions, mock(ProjectScopeApi.class), mock(PermissionApi.class));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void completedRoundReadsItsFrozenBindingWithoutGrantingExecutionOrChangingHistory() {
        String original = JsonUtils.toJsonString(round);
        var result = resolver.resolve(1L, "TASK", 2L, null);
        assertNull(result.reason()); assertEquals("original-survey", result.binding().getComponentKey());
        assertFalse(result.permitted()); assertNull(result.selection()); assertSame(round, result.round());
        assertEquals(original, JsonUtils.toJsonString(round)); verifyNoInteractions(executions);
        verify(nodes, never()).updateById(any(ProjectNodeExecutionDO.class));
    }
    @Test void activeOldPlanAndUnendedRoundStillFailStale() {
        task.setStatus("IN_PROGRESS"); round.setStatus("ACTIVE"); round.setEndedAt(null);
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
        task.setStatus("DONE"); round.setStatus("DONE");
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
        verify(plans, never()).selectById(anyLong()); verifyNoInteractions(executions);
    }
    @Test void historicalPlanCannotCrossTenantProjectOrUseAnUnpublishedDraft() {
        historical.setTenantId(9L);
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
        historical.setTenantId(7L); historical.setProjectId(9L);
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
        historical.setProjectId(1L); historical.setStatus("DRAFT");
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
        verifyNoInteractions(executions);
    }
    @Test void expectedWritableContextCannotBeReusedForHistoricalRead() {
        var result = resolver.resolve(1L, "TASK", 2L, new ProjectBusinessExecutionSelection(null, null));
        assertEquals("EXECUTION_VERSION_CONFLICT", result.reason()); assertFalse(result.permitted()); assertNull(result.selection());
        verifyNoInteractions(executions);
    }
    @Test void unavailableFrozenNodeAndMissingPlanDoNotFallBackToNewPlan() {
        snapshot.getTasks().getFirst().setNodeKey("task:other"); historical.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertEquals("FROZEN_NODE_NOT_UNIQUE", resolver.resolve(1L, "TASK", 2L, null).reason());
        when(plans.selectById(11L)).thenReturn(null);
        assertEquals("EXECUTION_CONTEXT_STALE", resolver.resolve(1L, "TASK", 2L, null).reason());
    }
    @Test void authorizationFailurePreventsReadingAnyFrozenPlan() {
        when(access.read(2L, 7L, 8L)).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(1L, "TASK", 2L, null));
        verifyNoInteractions(plans, nodes, executions);
    }
}
