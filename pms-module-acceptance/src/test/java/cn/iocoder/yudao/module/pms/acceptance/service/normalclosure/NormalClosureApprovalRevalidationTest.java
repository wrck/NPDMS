package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.ClosureExitCommand;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalClosureApprovalRevalidationTest {
    private final NormalClosureMapper mapper=mock(NormalClosureMapper.class);
    private final BpmNormalClosureApi bpm=mock(BpmNormalClosureApi.class);
    private final NormalClosureApprovalRevalidation guard=new NormalClosureApprovalRevalidation(mapper,bpm);
    private final NormalClosureApplicationDO application=new NormalClosureApplicationDO();
    private final NormalClosureSnapshotDO snapshot=new NormalClosureSnapshotDO();
    private final Context context=new Context(7L,11L,9L,6L,"BPM_NORMAL_CLOSURE:process-1");
    private final ClosureExitCommand command=new ClosureExitCommand(7L,9L,3L,1L,21L,
            "BPM_NORMAL_CLOSURE:process-1",101L,102L,11L,"S6",31L,"digest-1","process-1",0L);
    private final ClosureRevalidation query=new ClosureRevalidation(context,command,"10","owner-v1");
    private final LocalDateTime now=LocalDateTime.of(2026,10,25,8,0);
    private BpmNormalClosureApi.Result approved;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L); login(11L);
        application.setId(101L);application.setTenantId(7L);application.setProjectId(9L);
        application.setStatus("IN_REVIEW");application.setClosureType("NORMAL");application.setSnapshotId(102L);
        application.setProcessInstanceId("process-1");application.setProcessDefinitionId("definition-3");
        application.setBusinessKey("PROJECT_NORMAL_CLOSURE:101");application.setApplicantUserId(21L);
        application.setReviewerUserId(11L);application.setServiceManagerUserId(31L);
        application.setProjectVersion(3L);application.setTreeVersion(1L);application.setFromStage("S6");application.setVersion(0L);
        application.setProcessEvidence(JsonUtils.toJsonString(new BpmNormalClosureApi.Started("process-1","definition-3",
                List.of(new BpmNormalClosureApi.AssignedNode("serviceManagerReview",31L),new BpmNormalClosureApi.AssignedNode("materialReview",11L)))));
        snapshot.setId(102L);snapshot.setTenantId(7L);snapshot.setProjectId(9L);snapshot.setProjectVersion(3L);
        snapshot.setTreeVersion(1L);snapshot.setFromStage("S6");snapshot.setPassed(true);snapshot.setSourceDigest("digest-1");
        when(mapper.selectApplicationForUpdate(any())).thenReturn(application);when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        approved=new BpmNormalClosureApi.Result("process-1","definition-3","PROJECT_NORMAL_CLOSURE:101","APPROVE",
                List.of(new BpmNormalClosureApi.Review("sm","serviceManagerReview",31L,"APPROVE","synthetic",now),
                        new BpmNormalClosureApi.Review("material","materialReview",11L,"APPROVE","synthetic",now.plusMinutes(1))));
        when(bpm.inspectResult(7L,"process-1")).thenReturn(approved);
    }
    @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void acceptsOnlyTheExactLockedApplicationSnapshotAndTwoManualApprovals(){
        guard.require(query);
        verify(mapper).selectApplicationForUpdate(new NormalClosureMapper.ApplicationQuery(7L,9L,101L));
        verify(mapper).selectSnapshot(new NormalClosureMapper.SnapshotQuery(7L,9L,102L));
        verify(mapper,never()).decideApplicationIfMatch(any());
    }
    @Test void rejectsWrongAuthenticatedReviewerAndTenantBeforeAccess(){
        login(99L);assertThrows(RuntimeException.class,()->guard.require(query));
        login(11L);TenantContextHolder.setTenantId(8L);assertThrows(RuntimeException.class,()->guard.require(query));
        verifyNoInteractions(mapper,bpm);
    }
    @Test void rejectsForeignProjectAndUnboundCorrelationBeforeAccess(){
        assertThrows(RuntimeException.class,()->guard.require(new ClosureRevalidation(new Context(7L,11L,99L,6L,context.correlationId()),command,"10","owner-v1")));
        assertThrows(RuntimeException.class,()->guard.require(new ClosureRevalidation(new Context(7L,11L,9L,6L,"ordinary-command"),command,"10","owner-v1")));
        verifyNoInteractions(mapper,bpm);
    }
    @Test void rejectsReplayedOrChangedApplicationAndSnapshot(){
        application.setStatus("APPROVED");assertThrows(RuntimeException.class,()->guard.require(query));
        application.setStatus("IN_REVIEW");application.setVersion(1L);assertThrows(RuntimeException.class,()->guard.require(query));
        application.setVersion(0L);application.setProcessInstanceId("other-instance");assertThrows(RuntimeException.class,()->guard.require(query));
        application.setProcessInstanceId("process-1");snapshot.setSourceDigest("changed");assertThrows(RuntimeException.class,()->guard.require(query));
        snapshot.setSourceDigest("digest-1");snapshot.setPassed(false);assertThrows(RuntimeException.class,()->guard.require(query));
        verifyNoInteractions(bpm);
    }
    @Test void rejectsRunningProcessMissingApprovalOrForgedFrozenCandidate(){
        when(bpm.inspectResult(7L,"process-1")).thenReturn(new BpmNormalClosureApi.Result("process-1","definition-3",application.getBusinessKey(),"RUNNING",List.of()));
        assertThrows(RuntimeException.class,()->guard.require(query));
        when(bpm.inspectResult(7L,"process-1")).thenReturn(new BpmNormalClosureApi.Result("process-1","definition-3",application.getBusinessKey(),"APPROVE",List.of(approved.reviews().getFirst())));
        assertThrows(RuntimeException.class,()->guard.require(query));
        when(bpm.inspectResult(7L,"process-1")).thenReturn(approved);
        application.setProcessEvidence(JsonUtils.toJsonString(new BpmNormalClosureApi.Started("process-1","definition-3",List.of())));
        assertThrows(RuntimeException.class,()->guard.require(query));
    }
    private void login(Long id){var login=new LoginUser();login.setId(id);login.setTenantId(7L);SecurityFrameworkUtils.setLoginUser(login,new MockHttpServletRequest());}

    @Test void crossModuleApplicantProofRetainsOriginalReviewerAndRejectsApplicantSubstitution() {
        var proof = new cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi.Query(
                7L,9L,101L,"process-1",21L,11L,3L,1L);
        assertEquals("digest-1", guard.requireApprovedApplicant(proof).sourceDigest());
        assertEquals(11L, SecurityFrameworkUtils.getLoginUserId());
        login(21L);
        assertThrows(RuntimeException.class, () -> guard.requireApprovedApplicant(proof));
        login(11L); application.setApplicantUserId(99L);
        assertThrows(RuntimeException.class, () -> guard.requireApprovedApplicant(proof));
    }

    @Test void crossModuleProofRejectsOtherProcessAndInvalidSnapshotOrBpmResult() {
        var proof = new cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi.Query(
                7L,9L,101L,"process-1",21L,11L,3L,1L);
        application.setProcessInstanceId("other");
        assertThrows(RuntimeException.class, () -> guard.requireApprovedApplicant(proof));
        application.setProcessInstanceId("process-1"); snapshot.setPassed(false);
        assertThrows(RuntimeException.class, () -> guard.requireApprovedApplicant(proof));
        snapshot.setPassed(true);
        when(bpm.inspectResult(7L,"process-1")).thenReturn(new BpmNormalClosureApi.Result("process-1","definition-3",application.getBusinessKey(),"RUNNING",List.of()));
        assertThrows(RuntimeException.class, () -> guard.requireApprovedApplicant(proof));
    }

    @Test void callbackUsesApprovedApplicantPathAndKeepsBothOriginalIdentitiesThroughExit() {
        var contexts = mock(cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi.class);
        var exits = mock(cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.class);
        var commands = mock(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.class);
        var service = new NormalClosureApplicationService(contexts,
                mock(cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureCheckApi.class), exits,mapper,commands,bpm,
                mock(cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi.class),
                mock(cn.iocoder.yudao.module.pms.project.api.projectgovernance.ProjectReopenScopeApi.class));
        when(mapper.selectApplicationByProcess(any())).thenReturn(application);
        when(contexts.lockApprovedApplicant(command)).thenAnswer(call -> {
            guard.requireApprovedApplicant(new cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi.Query(
                    7L,9L,101L,"process-1",21L,11L,3L,1L));
            return new cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi.ClosureContext(
                    9L,7L,3L,"ACTIVE","S6",21L,9L,1L,"{}",1L,1L,1L,1);
        });
        when(commands.execute(any(),any(),any(),any(),any())).thenAnswer(call ->
                new cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.ExecutionResult<>(
                        cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.Decision.NEW,
                        ((java.util.function.Supplier<?>)call.getArgument(3)).get()));
        when(mapper.insertReview(any())).thenReturn(1);
        when(mapper.decideApplicationIfMatch(any())).thenReturn(1);
        service.onBpmResult(7L,"process-1");
        verify(contexts).lockApprovedApplicant(command);
        verify(contexts,never()).lock(any(),any(),any(),any(),any());
        verify(exits).executeApprovedExit(command);
        assertEquals(11L,SecurityFrameworkUtils.getLoginUserId());
        clearInvocations(mapper,exits,commands);
        login(21L);
        assertThrows(RuntimeException.class,()->service.onBpmResult(7L,"process-1"));
        verifyNoInteractions(exits,commands);
        verify(mapper,never()).insertReview(any());
        verify(mapper,never()).decideApplicationIfMatch(any());
    }
}
