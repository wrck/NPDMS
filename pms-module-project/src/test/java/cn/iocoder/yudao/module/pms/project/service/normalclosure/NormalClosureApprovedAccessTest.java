package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.ClosureExitCommand;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalClosureApprovedAccessTest {
    private final NormalClosureAccess access = mock(NormalClosureAccess.class);
    private final NormalClosureApprovalFactApi approvals = mock(NormalClosureApprovalFactApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final OrganizationScopeApi organizations = mock(OrganizationScopeApi.class);
    private final NormalClosureApprovedAccess service = new NormalClosureApprovedAccess(access, approvals, scopes, participants, organizations);
    private final ClosureExitCommand command = new ClosureExitCommand(7L,9L,3L,1L,21L,
            "BPM_NORMAL_CLOSURE:process-1",101L,102L,11L,"S6",31L,"digest-1","process-1",0L);
    private final ProjectMasterDO project = new ProjectMasterDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L); login(11L);
        project.setId(9L); project.setTenantId(7L); project.setVersion(3L); project.setLifecycleStatus("ACTIVE");
        project.setCompanyId(41L); project.setDepartmentId(51L);
        when(access.lockProject(9L,7L)).thenReturn(project);
        when(approvals.requireApprovedApplicant(any())).thenReturn(new NormalClosureApprovalFactApi.Fact(102L,0L,"S6",31L,"digest-1"));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void reviewerRemainsAuthenticatedWhileOriginalApplicantQualificationIsLocked(String role) {
        var member = member(role);
        when(participants.inspectAuthorization(any())).thenReturn(member);
        when(participants.lockAndRevalidateAuthorization(any())).thenReturn(member);
        assertSame(project, service.lock(command).project());
        assertEquals(11L, SecurityFrameworkUtils.getLoginUserId());
        var ordered = inOrder(access, approvals, scopes, participants);
        ordered.verify(access).lockProject(9L,7L);
        ordered.verify(approvals).requireApprovedApplicant(new NormalClosureApprovalFactApi.Query(7L,9L,101L,"process-1",21L,11L,3L,1L));
        ordered.verify(scopes).lockAndRevalidate(argThat(q -> q.subjectUserId().equals(21L)));
        ordered.verify(participants).inspectAuthorization(any());
        ordered.verify(participants).lockAndRevalidateAuthorization(argThat(q -> q.userId().equals(21L) && q.requiredRoleCodes().equals(Set.of(role))));
        verifyNoInteractions(organizations);
    }

    @Test void regionalApplicantUsesSameCompanyDepartmentAndServiceRolesWithoutBecomingAMember() {
        when(participants.inspectAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        when(organizations.lockSubjectRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
        service.lock(command);
        verify(organizations).lockSubjectRoleScope(argThat(q -> q.tenantId().equals(7L) && q.userId().equals(21L)
                && q.companyId().equals(41L) && q.departmentId().equals(51L) && q.requiredRoleCodes().equals(ProjectMemberRoles.SERVICE_CODES)));
        verify(participants, never()).lockAndRevalidateAuthorization(any());
        assertEquals(11L,SecurityFrameworkUtils.getLoginUserId());
    }

    @Test void applicantCannotSubstituteForOriginalReviewerAndForeignTenantIsDeniedBeforeLocks() {
        login(21L); assertThrows(RuntimeException.class, () -> service.lock(command));
        login(99L); assertThrows(RuntimeException.class, () -> service.lock(command));
        login(11L); TenantContextHolder.setTenantId(8L); assertThrows(RuntimeException.class, () -> service.lock(command));
        verifyNoInteractions(access, approvals, scopes, participants, organizations);
    }

    @Test void staleApprovalStopsBeforeApplicantAccessAndDoesNotSelectReplacement() {
        when(approvals.requireApprovedApplicant(any())).thenReturn(new NormalClosureApprovalFactApi.Fact(102L,1L,"S6",31L,"digest-1"));
        assertThrows(RuntimeException.class, () -> service.lock(command));
        verifyNoInteractions(scopes,participants,organizations);
    }

    @Test void revokedApplicantMemberDoesNotFallBackToOtherwiseAvailableRegionalGrant() {
        when(participants.inspectAuthorization(any())).thenReturn(member("SERVICE_MANAGER"));
        when(participants.lockAndRevalidateAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        assertThrows(RuntimeException.class, () -> service.lock(command));
        verifyNoInteractions(organizations);
    }

    @Test void missingRegionalScopeAndOwnerFailureAreDenied() {
        assertThrows(RuntimeException.class, () -> service.lock(command));
        reset(organizations);
        when(participants.inspectAuthorization(any())).thenThrow(new IllegalStateException("owner unavailable"));
        assertThrows(IllegalStateException.class, () -> service.lock(command));
        verifyNoInteractions(organizations);
    }

    @Test void revokedProjectScopeStopsBeforeQualification() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> service.lock(command));
        verifyNoInteractions(participants,organizations);
    }

    private ProjectParticipantFact member(String role) { return new ProjectParticipantFact(9L,21L,Set.of(role),"PRIMARY","ACTIVE","S6",3L,3L); }

    @Test void realAcceptanceProofAndProjectQualificationAgreeOnBothIdentities() {
        var mapper = mock(cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper.class);
        var bpm = mock(cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.class);
        var guard = new cn.iocoder.yudao.module.pms.acceptance.service.normalclosure.NormalClosureApprovalRevalidation(mapper,bpm);
        var callback = new NormalClosureApprovedAccess(access,guard,scopes,participants,organizations);
        var application = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.NormalClosureApplicationDO();
        application.setId(101L); application.setTenantId(7L); application.setProjectId(9L);
        application.setApplicantUserId(21L); application.setReviewerUserId(11L); application.setServiceManagerUserId(31L);
        application.setStatus("IN_REVIEW"); application.setClosureType("NORMAL"); application.setSnapshotId(102L);
        application.setProjectVersion(3L); application.setTreeVersion(1L); application.setVersion(0L); application.setFromStage("S6");
        application.setProcessInstanceId("process-1"); application.setProcessDefinitionId("definition-1");
        application.setBusinessKey("PROJECT_NORMAL_CLOSURE:101");
        application.setProcessEvidence(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.Started("process-1","definition-1",java.util.List.of(
                        new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.AssignedNode("serviceManagerReview",31L),
                        new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.AssignedNode("materialReview",11L)))));
        var snapshot = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.NormalClosureSnapshotDO();
        snapshot.setId(102L); snapshot.setTenantId(7L); snapshot.setProjectId(9L); snapshot.setProjectVersion(3L);
        snapshot.setTreeVersion(1L); snapshot.setFromStage("S6"); snapshot.setPassed(true); snapshot.setSourceDigest("digest-1");
        when(mapper.selectApplicationForUpdate(any())).thenReturn(application);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        var now = java.time.LocalDateTime.now();
        when(bpm.inspectResult(7L,"process-1")).thenReturn(new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.Result(
                "process-1","definition-1","PROJECT_NORMAL_CLOSURE:101","APPROVE",java.util.List.of(
                        new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.Review("sm","serviceManagerReview",31L,"APPROVE","TEST_ONLY",now),
                        new cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.Review("material","materialReview",11L,"APPROVE","TEST_ONLY",now.plusSeconds(1)))));
        when(participants.inspectAuthorization(any())).thenReturn(member("SERVICE_MANAGER"));
        when(participants.lockAndRevalidateAuthorization(any())).thenReturn(member("SERVICE_MANAGER"));
        assertSame(project,callback.lock(command).project());
        assertEquals(11L,SecurityFrameworkUtils.getLoginUserId());
        clearInvocations(participants);
        application.setApplicantUserId(99L);
        assertThrows(RuntimeException.class,()->callback.lock(command));
        verifyNoInteractions(participants);
        verify(mapper,never()).decideApplicationIfMatch(any());
    }
    private void login(Long id) { SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(id).setTenantId(7L), new MockHttpServletRequest()); }
}
