package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentAcceptanceServiceTest {
    final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    final ProjectAcceptanceContextApi projects = mock(ProjectAcceptanceContextApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final IndependentAcceptanceService service = new IndependentAcceptanceService(activities, projects, commands, permissions);
    final AcceptanceReportCommands.Actor actor = new AcceptanceReportCommands.Actor(7L, 19L, "request");
    final IndependentAcceptanceService.Create request = new IndependentAcceptanceService.Create(80L, "FINAL", 4L, 3L);
    final ProjectAcceptanceContextApi.Context context = new ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "ACTIVE");

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(permissions.hasAnyPermissions(19L, "pms:acceptance:report:write")).thenReturn(true);
        when(projects.inspect(any())).thenReturn(context);
        when(projects.lock(any(), eq(4L), eq(3L))).thenReturn(context);
        when(activities.insert(any(AcceptanceActivityDO.class))).thenReturn(1);
        when(commands.execute(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var result = call.<Supplier<IndependentAcceptanceService.Result>>getArgument(3).get();
            var audit = call.<Function<IndependentAcceptanceService.Result, PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result);
            assertEquals(result.acceptanceId().toString(), audit.resourceKey());
            assertTrue(audit.businessEvents().isEmpty(), "Creating an activity is not a completed business result");
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }
    @AfterEach void clean() { TenantContextHolder.clear(); }

    @Test void createsProjectAcceptanceWithoutTaskContractStageOrDeliveryAndFreezesOriginAndPolicy() {
        var result = service.create(request, "key", actor);
        assertTrue(result.created()); assertEquals(80L, result.projectId()); assertEquals("FINAL", result.acceptanceType());
        var saved = org.mockito.ArgumentCaptor.forClass(AcceptanceActivityDO.class);
        verify(activities).insert(saved.capture());
        var row = saved.getValue(); assertNull(row.getProjectTaskId()); assertNull(row.getExecutionContractId()); assertNull(row.getDeliverableId());
        assertEquals("DIRECT", row.getOriginKind()); assertEquals("19:key", row.getOriginKey());
        assertEquals("PENDING", row.getActivityStatus()); assertTrue(IndependentAcceptancePolicy.validSnapshot(row.getRuleSnapshot()));
        assertTrue(row.getOriginSnapshot().contains("treeVersion"));
        var order = inOrder(projects, activities);
        order.verify(projects).lock(any(), eq(4L), eq(3L)); order.verify(activities).selectByIdentityForUpdate(any());
        order.verify(activities).insert(any(AcceptanceActivityDO.class));
    }
    @Test void existingProjectTypeIdentityIsReusedWithoutChangingLegacyProvenance() {
        var old = new AcceptanceActivityDO(); old.setId(55L); old.setProjectId(80L); old.setAcceptanceType("FINAL");
        old.setOriginKind("LEGACY_TASK"); old.setProjectTaskId(90L);
        when(activities.selectByIdentityForUpdate(any())).thenReturn(old);
        var result = service.create(request, "key", actor);
        assertEquals(55L, result.acceptanceId()); assertFalse(result.created()); assertEquals(90L, old.getProjectTaskId());
        verify(activities, never()).insert(any(AcceptanceActivityDO.class));
    }
    @Test void unauthorizedActorAndTenantMismatchCannotReachTheCommandOrProject() {
        when(permissions.hasAnyPermissions(19L, "pms:acceptance:report:write")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.create(request, "key", actor));
        assertThrows(RuntimeException.class, () -> service.create(request, "key", new AcceptanceReportCommands.Actor(8L, 19L, "wrong")));
        verifyNoInteractions(commands, projects, activities);
    }
    @Test void closedProjectOrStaleProjectContextCannotCreate() {
        when(projects.lock(any(), eq(4L), eq(3L))).thenReturn(new ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "NORMAL_CLOSED"));
        assertThrows(RuntimeException.class, () -> service.create(request, "key", actor));
        when(projects.lock(any(), eq(4L), eq(3L))).thenThrow(new IllegalArgumentException("ACCEPTANCE_PROJECT_CONTEXT_CHANGED"));
        assertThrows(RuntimeException.class, () -> service.create(request, "key", actor));
        verifyNoInteractions(activities);
    }
    @Test void completedReplayNeverReexecutesTheOwnerInsert() {
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                new IndependentAcceptanceService.Result(55L, 80L, "FINAL", true))).when(commands).execute(any(), any(), any(), any(), any());
        assertEquals(55L, service.create(request, "key", actor).acceptanceId()); verifyNoInteractions(activities);
    }
    @Test void conflictingReplayAndUnknownAcceptanceTypeCannotCreate() {
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.CONFLICT, null))
                .when(commands).execute(any(), any(), any(), any(), any());
        assertThrows(RuntimeException.class, () -> service.create(request, "key", actor));
        assertThrows(RuntimeException.class, () -> service.create(new IndependentAcceptanceService.Create(80L, "OTHER", 4L, 3L), "key", actor));
        verifyNoInteractions(activities);
    }
}
