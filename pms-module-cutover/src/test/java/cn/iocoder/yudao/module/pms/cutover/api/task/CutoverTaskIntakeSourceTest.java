package cn.iocoder.yudao.module.pms.cutover.api.task;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessEvaluationApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverTaskIntakeSourceTest {
    final CutoverTaskApplicationService application = mock(CutoverTaskApplicationService.class);
    final CutoverIntakeSourceMapper sources = mock(CutoverIntakeSourceMapper.class);
    final CutoverFrozenDeviceMapper devices = mock(CutoverFrozenDeviceMapper.class);
    final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final DeviceScopeFactApi deviceScope = mock(DeviceScopeFactApi.class);
    final ImplementationReadinessEvaluationApi readiness = mock(ImplementationReadinessEvaluationApi.class);
    final CutoverTaskIntakeApiImpl api = new CutoverTaskIntakeApiImpl(application, sources, devices,
            projects, scopes, permissions, deviceScope, readiness);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(2L).setTenantId(1L), new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(2L, "pms:cutover-task:create")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(3L, 4L, Set.of(3L), Set.of()));
        when(projects.inspect(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(1L)
                .projectId(3L)
                .projectVersion(5L)
                .scopeVersion(4L)
                .projectCode("P3")
                .projectName("项目")
                .customerId(6L)
                .customerCode("C6")
                .customerName("客户")
                .departmentId(7L)
                .departmentCode("D7")
                .departmentName("部门").build());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void itrCreatesThroughSameApplicationWithoutImplementationReadiness() {
        when(application.create(any())).thenReturn(new CutoverTaskCommandResult(10L, "CUT-10", "P2", "GRADE_CONFIRMING", 0L, false));
        assertEquals(10L, api.create(command("任务")).taskId());
        verify(application).create(argThat(value -> "ITR".equals(value.intakeSourceType()) && value.actorId().equals(2L)));
        verifyNoInteractions(readiness, deviceScope);
    }

    @Test void repeatedSourceReturnsArchivedTaskAndItsFrozenDeviceScope() {
        existing();
        var result = api.create(command("任务"));
        assertTrue(result.replayed()); assertEquals("ARCHIVED", result.taskStatus());
        assertEquals(10L, result.taskId());
        verifyNoInteractions(application, readiness, deviceScope);
    }

    @Test void sameSourceWithChangedContentIsAnIdentityConflict() {
        existing();
        var error = assertThrows(CutoverTaskIntakeException.class, () -> api.create(command("变更任务")));
        assertEquals(CutoverTaskIntakeException.Code.SOURCE_IDENTITY_CONFLICT, error.code());
        verifyNoInteractions(application, readiness);
    }

    @Test void projectPermissionIsRequiredEvenForReplay() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(3L, 4L, Set.of(), Set.of()));
        assertThrows(SecurityException.class, () -> api.create(command("任务")));
        verifyNoInteractions(sources, application, readiness);
    }

    private CutoverTaskIntakeCommand command(String name) {
        return new CutoverTaskIntakeCommand("ITR", 3L, List.of(" sn-1 "), 2L, "CFG", name, "背景", "TYPE", "DUAL",
                LocalDateTime.of(2026, 10, 20, 9, 0), "correlation", "ITR", "INC-10", null);
    }
    private void existing() {
        var c = command("任务"); var task = new CutoverTaskDO(); task.setId(10L); task.setProjectId(3L);
        task.setTaskNo("CUT-10"); task.setCurrentStage("P6"); task.setTaskStatus("ARCHIVED"); task.setVersion(20L);
        task.setCutoverType(c.cutoverType()); task.setNetworkMode(c.networkMode()); task.setConfigurationCode(c.configurationCode());
        task.setTaskName(c.taskName()); task.setBackground(c.background()); task.setScheduledTime(c.scheduledTime());
        when(sources.find(any())).thenReturn(task);
        var device = new CutoverTaskDeviceScopeDO(); device.setSerialNumberSnapshot("SN-1"); device.setActiveMarker(null);
        when(devices.byTask(any())).thenReturn(List.of(device));
    }
}
