package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.*;
import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageApprovalCommandServiceTest {
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectStageBusinessQueryService contexts = mock(ProjectStageBusinessQueryService.class);
    final ProjectStageApprovalService approvals = mock(ProjectStageApprovalService.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final ProjectStageApprovalCommandService service = new ProjectStageApprovalCommandService(projects,contexts,approvals,scopes,permissions,commands);
    final ProjectStageExecutionContext execution = new ProjectStageExecutionContext(9L,1,11L,1,41L,1,21L,31L,2,3,true);
    final Submission submission = new Submission(Map.of("comment","private-value"),Map.of("review",List.of(12L)));
    final Fact running = new Fact(Outcome.NOT_SATISFIED,"RUNNING","pi","review:1",null);
    final ProjectMasterDO project = new ProjectMasterDO();
    PlatformCommandExecutionApi.SuccessFacts recorded;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(7L); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(permissions.hasAnyPermissions(1L,"pms:project:update")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        context("NOT_STARTED",false);
        when(approvals.start(any(),any(),any(),any(),anyString(),any())).thenReturn(running);
        when(commands.execute(any(),anyString(),eq(Fact.class),any(),any())).thenAnswer(call -> {
            var fact = ((Supplier<Fact>)call.getArgument(3)).get();
            recorded = ((Function<Fact,PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(fact);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,fact);
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void context(String status, boolean readonly) {
        var fact = new Fact("UNKNOWN".equals(status) ? Outcome.UNKNOWN : "APPROVED".equals(status) ? Outcome.SATISFIED : Outcome.NOT_SATISFIED,
                status,null,"review:1",null);
        when(contexts.getContext(eq(9L),eq("PREP"),any())).thenReturn(new StageBusinessContext(9L,11L,"PREP",41L,1,"APPROVAL",
                null,null,readonly ? Set.of("QUERY") : Set.of("QUERY","APPROVAL"),readonly,null,execution,new View("review","review:1",31L,fact)));
    }
    ProjectStageApprovalCommandService.Command command(ProjectStageExecutionContext context) {
        return new ProjectStageApprovalCommandService.Command(9L,"PREP",context,submission);
    }
    Fact start() { return service.start(command(execution),1L,"intent"); }

    @Test void locksProjectAndUsesServerResolvedDefinitionWithOriginalFormAndApprovers() {
        assertEquals(running,start());
        var ordered = inOrder(projects,contexts,approvals);
        ordered.verify(projects).selectProjectForCommandForUpdate(any());
        ordered.verify(contexts).getContext(eq(9L),eq("PREP"),any());
        ordered.verify(approvals).start(7L,execution,new ApprovalWorkBindingSchema.Definition("review","review:1"),1L,"intent",submission);
        assertFalse(recorded.detailSnapshot().contains("private-value"));
        assertTrue(recorded.detailSnapshot().contains("pi")); assertTrue(recorded.businessEvents().isEmpty());
    }
    @Test void permissionAndScopeAreRequiredBeforeLedgerAndAgainUnderLock() {
        when(permissions.hasAnyPermissions(1L,"pms:project:update")).thenReturn(false);
        assertThrows(RuntimeException.class,this::start); verifyNoInteractions(commands,projects,approvals);
        when(permissions.hasAnyPermissions(1L,"pms:project:update")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,this::start); verifyNoInteractions(commands,projects,approvals);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()),new ProjectScopeResult(9L,2L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,this::start); verifyNoInteractions(contexts,approvals);
    }
    @Test void staleRoundAndClosedOrForeignProjectCannotStart() {
        var stale = new ProjectStageExecutionContext(9L,1,11L,1,41L,1,21L,30L,2,2,true);
        assertThrows(RuntimeException.class,() -> service.start(command(stale),1L,"old"));
        project.setLifecycleStatus("CLOSED"); assertThrows(RuntimeException.class,this::start);
        project.setLifecycleStatus("ACTIVE"); project.setTenantId(8L); assertThrows(RuntimeException.class,this::start);
        verifyNoInteractions(approvals);
    }
    @Test void runningApprovedUnknownOrReadonlyResultsCannotStartAnotherProcess() {
        for (String status : List.of("RUNNING","APPROVED","UNKNOWN")) {
            context(status,false); assertThrows(RuntimeException.class,this::start);
        }
        context("NOT_STARTED",true); assertThrows(RuntimeException.class,this::start);
        verifyNoInteractions(approvals);
    }
    @Test void rejectedAndCancelledResultsCanUseANewIntent() {
        for (String status : List.of("REJECTED","CANCELLED")) {
            context(status,false); assertEquals(running,start());
        }
        verify(approvals,times(2)).start(eq(7L),eq(execution),any(),eq(1L),eq("intent"),eq(submission));
    }
    @Test void completedIdempotencyReplayDoesNotRepeatWritesAndConflictDoesNotStart() {
        when(commands.execute(any(),anyString(),eq(Fact.class),any(),any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,running));
        assertEquals(running,start()); verifyNoInteractions(projects,contexts,approvals);
        for (var decision : List.of(PlatformCommandExecutionApi.Decision.CONFLICT,PlatformCommandExecutionApi.Decision.IN_PROGRESS)) {
            when(commands.execute(any(),anyString(),eq(Fact.class),any(),any())).thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(decision,null));
            assertThrows(RuntimeException.class,this::start);
        }
        verifyNoInteractions(projects,contexts,approvals);
    }
}
