package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskApprovalServiceTest {
    final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    final ProjectNodeApprovalApi owner = mock(ProjectNodeApprovalApi.class);
    final ProjectTaskApprovalService service = new ProjectTaskApprovalService(executions,owner);
    final LocalDateTime started = LocalDateTime.of(2026,9,15,9,0);
    ProjectTaskExecutionContractDO binding() {
        var binding = new ProjectTaskExecutionContractDO(); binding.setId(91L); binding.setWorkBindingTypeCode("APPROVAL");
        binding.setBindingParameterSnapshot("{\"approvalDefinitionKey\":\"review\",\"processDefinitionId\":\"review:1\"}");
        return binding;
    }
    ProjectTaskExecutionContext context(LocalDateTime at) {
        return new ProjectTaskExecutionContext(9L,1,21L,2,91L,1,51L,61L,1,2,62L,1,at!=null,at);
    }
    @Test void startsFromFrozenBindingAndCurrentExecutionInsteadOfLegacyApprovalId() {
        var binding = binding(); binding.setApprovalInstanceId(123L);
        when(executions.inspect(any())).thenReturn(context(started));
        var submission = new ProjectNodeApprovalApi.Submission(java.util.Map.of("comment","private form"), java.util.Map.of("review",java.util.List.of(2L)));
        service.start(7L,9L,21L,binding,1L,"START:intent",submission);
        verify(owner).start(argThat(command -> command.scope().executionId().equals(61L)
                && command.scope().contractId().equals(91L) && command.scope().definitionId().equals("review:1")
                && command.scope().definitionKey().equals("review") && command.scope().startedAt().equals(started)
                && command.execution().roundNo()==2 && command.actorId().equals(1L)
                && command.operationId().equals("START:intent") && command.variables().equals(submission.variables())
                && command.selectedApprovers().equals(submission.selectedApprovers())));
    }
    @Test void missingOrFailedOwnerResultIsUnknownAndDoesNotAllowTaskClosure() {
        when(executions.inspect(any())).thenReturn(context(started));
        assertEquals(ProjectNodeApprovalApi.Outcome.UNKNOWN,service.inspect(7L,9L,21L,binding(),61L,started).outcome());
        assertThrows(IllegalStateException.class,() -> service.requireMayCancel(7L,9L,21L,binding()));
        when(owner.inspect(any())).thenThrow(new IllegalStateException("private owner diagnostic"));
        var fact = service.inspect(7L,9L,21L,binding(),61L,started);
        assertEquals(ProjectNodeApprovalApi.Outcome.UNKNOWN,fact.outcome());
        assertFalse(fact.toString().contains("private owner diagnostic"));
    }
    @Test void taskClosureRequiresBpmToFinishOrExplicitlyCancelActiveApproval() {
        when(executions.inspect(any())).thenReturn(context(started));
        when(owner.inspect(any())).thenReturn(new ProjectNodeApprovalApi.Fact(ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,"RUNNING","p1","review:1",null));
        assertThrows(IllegalStateException.class,() -> service.requireMayCancel(7L,9L,21L,binding()));
        when(owner.inspect(any())).thenReturn(new ProjectNodeApprovalApi.Fact(ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,"CANCELLED","p1","review:1",null));
        assertDoesNotThrow(() -> service.requireMayCancel(7L,9L,21L,binding()));
    }
    @Test void anUnstartedTaskCanStillBeExplicitlyClosed() {
        when(executions.inspect(any())).thenReturn(context(null));
        assertDoesNotThrow(() -> service.requireMayCancel(7L,9L,21L,binding()));
        verifyNoInteractions(owner);
    }
    @Test void unstartedWorkbenchExposesFrozenRoutingWithoutStartingOrReadingAnApproval() {
        when(executions.inspect(any())).thenReturn(context(null));
        var view = service.view(7L,9L,21L,binding());
        assertEquals("review:1",view.definitionId()); assertEquals(61L,view.executionId());
        assertEquals("NOT_STARTED",view.current().status());
        verifyNoInteractions(owner);
    }
    @Test void workbenchUsesCurrentOwnerInstanceAndDoesNotReturnFormValues() {
        when(executions.inspect(any())).thenReturn(context(started));
        var fact = new ProjectNodeApprovalApi.Fact(ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,"RUNNING","current-2","review:1",null);
        when(owner.inspect(any())).thenReturn(fact);
        assertEquals(fact,service.view(7L,9L,21L,binding()).current());
        verify(owner,never()).start(any());
    }
}
