package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectManagementQualificationApiImplTest {
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final ProjectRegionalManagerFactApi regions = mock(ProjectRegionalManagerFactApi.class);
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectCurrentStageService stages = mock(ProjectCurrentStageService.class);
    private final ProjectManagementQualificationApi api = new ProjectManagementQualificationApiImpl(participants,regions,projects,stages);
    private final ProjectManagementQualificationApi.Query query = new ProjectManagementQualificationApi.Query(1L,10L,20L);

    @BeforeEach void identity() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(20L).setTenantId(1L),new MockHttpServletRequest());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void managementMemberSelectsOneSourceAndLocksTheOriginalFact(String role) {
        var member = member(role,7L);
        when(participants.inspectAuthorization(any())).thenReturn(member);
        var observed = api.inspect(query);
        assertEquals(ProjectManagementQualificationApi.Source.PROJECT_MEMBER, observed.source());
        when(participants.lockAndRevalidateAuthorization(any())).thenReturn(member);
        assertEquals(observed,api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE","S3")));
        verify(participants).lockAndRevalidateAuthorization(argThat(lock -> lock.requiredRoleCodes().equals(Set.of(role))
                && lock.expectedProjectVersion().equals(3L) && "S3".equals(lock.requiredCurrentStage())));
        verifyNoInteractions(regions);
    }

    @Test void onlyExplicitQualificationDenialMaySelectRegionalSource() {
        when(participants.inspectAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        var region = new ProjectRegionalManagerFactApi.Fact(10L,20L,3L);
        when(regions.inspect(any())).thenReturn(region);
        var observed = api.inspect(query);
        assertEquals(ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,observed.source());
        assertNull(observed.member()); assertEquals(region,observed.regional());
        when(regions.inspect(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        assertNull(api.inspect(query));
    }

    @Test void sourceFailureAndMalformedMemberNeverBecomeRegionalAuthority() {
        when(participants.inspectAuthorization(any())).thenThrow(new IllegalStateException("owner unavailable"));
        assertThrows(IllegalStateException.class,()->api.inspect(query));
        verifyNoInteractions(regions);
        doReturn(new ProjectParticipantFact(10L,99L,Set.of("PROJECT_MANAGER"),"PRIMARY","ACTIVE","S3",3L,7L)).when(participants).inspectAuthorization(any());
        assertThrows(ServiceException.class,()->api.inspect(query));
        verifyNoInteractions(regions);
    }

    @Test void closedMemberIsNotActiveManagementAndDoesNotTriggerRegionalFallback() {
        when(participants.inspectAuthorization(any())).thenReturn(new ProjectParticipantFact(10L,20L,
                Set.of("PROJECT_MANAGER"),"PRIMARY","NORMAL_CLOSED","S6",3L,7L));
        assertNull(api.inspect(query));
        verifyNoInteractions(regions);
    }

    @Test void memberRevocationDoesNotSelectAnOtherwiseAvailableRegion() {
        when(participants.inspectAuthorization(any())).thenReturn(member("PROJECT_MANAGER",7L));
        var observed = api.inspect(query);
        when(participants.lockAndRevalidateAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE",null)));
        verifyNoInteractions(regions);
    }

    @Test void changedMemberVersionAndClientProjectVersionAreRejected() {
        when(participants.inspectAuthorization(any())).thenReturn(member("PROJECT_MANAGER",7L));
        var observed = api.inspect(query);
        when(participants.lockAndRevalidateAuthorization(any())).thenReturn(member("PROJECT_MANAGER",8L));
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE",null)));
        var stale = assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,2L,"ACTIVE",null)));
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(),stale.getCode());
        verifyNoInteractions(regions);
    }

    @Test void regionalSourceRevalidatesPhysicalProjectAndRequestedParallelStage() {
        when(participants.inspectAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        var region = new ProjectRegionalManagerFactApi.Fact(10L,20L,3L);
        when(regions.inspect(any())).thenReturn(region);
        var observed = api.inspect(query);
        when(regions.lockAndRevalidate(any())).thenReturn(region);
        var project = new ProjectMasterDO(); project.setId(10L);project.setTenantId(1L);project.setVersion(3L);
        project.setLifecycleStatus("ACTIVE");project.setCurrentStage("S1");project.setActivePlanVersionId(100L);
        when(projects.selectByIdForUpdate(10L)).thenReturn(project);
        when(stages.isActive(project,"S3")).thenReturn(true);
        assertEquals(observed,api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE","S3")));
        when(stages.isActive(project,"S3")).thenReturn(false);
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE","S3")));
        verify(participants,never()).lockAndRevalidateAuthorization(any());
    }

    @Test void regionalRevocationNeverFallsBackToMemberOrReinspects() {
        when(participants.inspectAuthorization(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        var region = new ProjectRegionalManagerFactApi.Fact(10L,20L,3L);
        when(regions.inspect(any())).thenReturn(region);
        var observed = api.inspect(query);
        when(regions.lockAndRevalidate(any())).thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectManagementQualificationApi.Revalidation(observed,3L,"ACTIVE",null)));
        verify(participants,times(1)).inspectAuthorization(any());verify(regions,times(1)).inspect(any());
        verify(participants,never()).lockAndRevalidateAuthorization(any());
    }

    @Test void subjectMustBeTheCurrentTenantActorAndFactCannotMixSources() {
        assertThrows(ServiceException.class,()->api.inspect(new ProjectManagementQualificationApi.Query(2L,10L,20L)));
        assertThrows(ServiceException.class,()->api.inspect(new ProjectManagementQualificationApi.Query(1L,10L,21L)));
        assertThrows(IllegalArgumentException.class,()->new ProjectManagementQualificationApi.Fact(query,
                ProjectManagementQualificationApi.Source.PROJECT_MEMBER,member("PROJECT_MANAGER",7L),new ProjectRegionalManagerFactApi.Fact(10L,20L,3L)));
        verifyNoInteractions(participants,regions);
    }

    @Test void currentLockUsesCallerVersionAndExactlyOneSourceSelection() throws Exception {
        var member = member("SERVICE_MANAGER",7L);
        when(participants.inspectAuthorization(any())).thenReturn(member);
        when(participants.lockAndRevalidateAuthorization(any())).thenReturn(member);
        assertNotNull(api.lockCurrent(new ProjectManagementQualificationApi.CurrentRevalidation(query,3L,"ACTIVE","S3")));
        verify(participants,times(1)).inspectAuthorization(any());
        verify(participants,times(1)).lockAndRevalidateAuthorization(any());
        var transaction = ProjectManagementQualificationApiImpl.class.getMethod("lockCurrent",ProjectManagementQualificationApi.CurrentRevalidation.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,transaction.propagation());
        verifyNoInteractions(regions);
    }

    private ProjectParticipantFact member(String role,Long factVersion) {
        return new ProjectParticipantFact(10L,20L,Set.of(role),"COLLABORATOR","ACTIVE","S3",3L,factVersion);
    }
}
