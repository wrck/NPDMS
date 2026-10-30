package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisAccessTest {
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectManagementQualificationApi qualifications = mock(ProjectManagementQualificationApi.class);
    private final RequirementAnalysisAccess access = new RequirementAnalysisAccess(null,scopes,participants,permissions,null,null,qualifications);
    private final EntityActor actor = new EntityActor(1L,9L,null);
    private final ProjectManagementQualificationApi.Query subject = new ProjectManagementQualificationApi.Query(1L,20L,9L);
    private void allowed() {
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
    }
    private ProjectManagementQualificationApi.Fact member(String role) {
        return new ProjectManagementQualificationApi.Fact(subject,ProjectManagementQualificationApi.Source.PROJECT_MEMBER,
                new ProjectParticipantFact(20L,9L,Set.of(role),"PRIMARY","ACTIVE","S1",3L,3L),null);
    }
    @Test void missingManagerDoesNotPreventReadingButStillDeniesDraftAccess() {
        allowed();
        assertDoesNotThrow(()->access.requireRead(20L,actor,false));
        assertFalse(access.isManager(20L,actor));
        assertThrows(ServiceException.class,()->access.requireRead(20L,actor,true));
    }
    @Test void viewerWithoutManageScopeCannotSeeDraftOrQueryQualification() {
        allowed();
        when(scopes.resolveCurrent(argThat(q->ProjectScopeApi.ACTION_MANAGE.equals(q.actionCode())))).thenReturn(new ProjectScopeResult(20L,1L,Set.of(),Set.of()));
        assertFalse(access.isManager(20L,actor));
        assertThrows(ServiceException.class,()->access.requireRead(20L,actor,true));
        verifyNoInteractions(qualifications);
    }
    @ParameterizedTest @ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void currentManagementQualificationCanReadDraftButRevokedQualificationCannot(String role) {
        allowed();when(qualifications.inspect(subject)).thenReturn(member(role));
        assertTrue(access.isManager(20L,actor));
        assertDoesNotThrow(()->access.requireRead(20L,actor,true));
        when(qualifications.inspect(subject)).thenReturn(null);
        assertFalse(access.isManager(20L,actor));
        verifyNoInteractions(participants);
    }
    @Test void unavailableOwnerDisablesEditingWithoutConsultingAnotherQualificationSource() {
        allowed();when(qualifications.inspect(subject)).thenThrow(new IllegalStateException("connection failed"));
        assertFalse(access.isManager(20L,actor));verifyNoInteractions(participants);
    }
    @Test void regionalManagerUsesSameQualificationOwnerUnderFunctionalAndManageScope() {
        allowed();when(qualifications.inspect(subject)).thenReturn(new ProjectManagementQualificationApi.Fact(subject,
                ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,new ProjectRegionalManagerFactApi.Fact(20L,9L,3L)));
        assertTrue(access.isManager(20L,actor));assertDoesNotThrow(()->access.requireRead(20L,actor,true));
        when(permissions.hasAnyPermissions(9L,"pms:requirement-analysis:manage")).thenReturn(false);
        assertFalse(access.isManager(20L,actor));
    }
    @ParameterizedTest @ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2","REGIONAL"})
    void writePassesExactInspectedSourceAndStopsOnRevocation(String role) {
        allowed();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
        var observed = "REGIONAL".equals(role) ? new ProjectManagementQualificationApi.Fact(subject,
                ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,new ProjectRegionalManagerFactApi.Fact(20L,9L,3L)) : member(role);
        when(qualifications.inspect(subject)).thenReturn(observed);
        assertDoesNotThrow(()->access.lockScope(20L,actor));
        var request = new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE",null);
        verify(qualifications).lockAndRevalidate(request);
        when(qualifications.lockAndRevalidate(request)).thenThrow(new ServiceException(1014024033,"revoked"));
        assertThrows(ServiceException.class,()->access.lockScope(20L,actor));
        verify(qualifications,times(2)).inspect(subject);verifyNoInteractions(participants);
    }
    @Test void administratorWithoutRawPmCanUseCurrentUnifiedQualification() {
        allowed();when(permissions.hasAnyRoles(eq(9L),any(String[].class))).thenReturn(true);
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
        when(participants.inspect(any())).thenThrow(new ServiceException(ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE,"no raw PM"));
        var observed = new ProjectManagementQualificationApi.Fact(subject,ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,
                new ProjectRegionalManagerFactApi.Fact(20L,9L,3L));
        when(qualifications.inspect(subject)).thenReturn(observed);
        assertTrue(access.isManager(20L,actor));access.lockScope(20L,actor);
        verify(qualifications).lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE",null));
        doThrow(new IllegalStateException("provider unavailable")).when(participants).inspect(any());
        assertFalse(access.isManager(20L,actor));
        assertThrows(IllegalStateException.class,()->access.lockScope(20L,actor));
        verify(qualifications,times(2)).inspect(subject);
    }
    @Test void administratorKeepsExistingRealPmFactAndRawLock() {
        allowed();when(permissions.hasAnyRoles(eq(9L),any(String[].class))).thenReturn(true);
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
        when(participants.inspect(any())).thenReturn(new ProjectParticipantFact(20L,10L,Set.of("PROJECT_MANAGER"),"PRIMARY","ACTIVE","S1",3L,3L));
        assertTrue(access.isManager(20L,actor));access.lockScope(20L,actor);
        verify(participants).lockAndRevalidate(argThat(q->q.userId().equals(10L)));
        verify(participants,never()).inspectAuthorization(any());verifyNoInteractions(qualifications);
    }
}
