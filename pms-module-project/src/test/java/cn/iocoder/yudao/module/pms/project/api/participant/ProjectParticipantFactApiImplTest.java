package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.ROLE_PROJECT_MANAGER;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectParticipantFactApiImplTest {

    @Mock
    private ProjectMasterMapper projectMapper;
    @Mock
    private ProjectMemberAssignmentMapper memberMapper;
    private ProjectParticipantFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new ProjectParticipantFactApiImpl(projectMapper, memberMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void inspectReturnsCurrentProjectManagerWithoutMemberRow() {
        when(projectMapper.selectById(100L)).thenReturn(project(100L, 200L, "ACTIVE", "S1", 7, 0L));
        when(memberMapper.selectParticipantFacts(any(ProjectParticipantFactLookupQuery.class)))
                .thenReturn(List.of());

        var fact = api.inspect(new ProjectParticipantFactQuery(
                100L, 200L, Set.of(ROLE_PROJECT_MANAGER), LocalDateTime.now()));

        assertEquals(200L, fact.userId());
        assertEquals(Set.of(ROLE_PROJECT_MANAGER), fact.effectiveRoleCodes());
        assertEquals("PRIMARY", fact.assignmentType());
        assertEquals(7, fact.projectVersion());
        assertEquals(7L, fact.factVersion());
        ArgumentCaptor<ProjectParticipantFactLookupQuery> captor =
                ArgumentCaptor.forClass(ProjectParticipantFactLookupQuery.class);
        verify(memberMapper).selectParticipantFacts(captor.capture());
        assertEquals(Set.of(), captor.getValue().requiredRoleCodes());
    }

    @Test
    void inspectNormalizesLegacyNullPrimaryServiceManager() {
        when(projectMapper.selectById(100L)).thenReturn(project(100L, 200L, "ACTIVE", "S2", 8, 0L));
        when(memberMapper.selectParticipantFacts(any(ProjectParticipantFactLookupQuery.class)))
                .thenReturn(List.of(assignment(100L, 300L, ROLE_SERVICE_MANAGER_L1, null, 0L)));

        var fact = api.inspect(new ProjectParticipantFactQuery(
                100L, 300L, Set.of(ROLE_SERVICE_MANAGER_L1, ROLE_SERVICE_MANAGER_L2), LocalDateTime.now()));

        assertEquals(300L, fact.userId());
        assertEquals(Set.of(ROLE_SERVICE_MANAGER_L1), fact.effectiveRoleCodes());
        assertEquals("PRIMARY", fact.assignmentType());
        assertEquals("S2", fact.currentStage());
    }

    @Test
    void inspectCombinesManagerAndServiceManagerForSameUser() {
        when(projectMapper.selectById(100L)).thenReturn(project(100L, 200L, "ACTIVE", "S1", 9, 0L));
        when(memberMapper.selectParticipantFacts(any(ProjectParticipantFactLookupQuery.class)))
                .thenReturn(List.of(assignment(100L, 200L, ROLE_SERVICE_MANAGER_L2, "PRIMARY", 0L)));

        var fact = api.inspect(new ProjectParticipantFactQuery(100L, 200L,
                Set.of(ROLE_PROJECT_MANAGER, ROLE_SERVICE_MANAGER_L2), LocalDateTime.now()));

        assertEquals(Set.of(ROLE_PROJECT_MANAGER, ROLE_SERVICE_MANAGER_L2), fact.effectiveRoleCodes());
    }

    @Test
    void inspectRejectsAmbiguousPrimaryParticipants() {
        when(projectMapper.selectById(100L)).thenReturn(project(100L, null, "ACTIVE", "S1", 1, 0L));
        when(memberMapper.selectParticipantFacts(any(ProjectParticipantFactLookupQuery.class)))
                .thenReturn(List.of(
                        assignment(100L, 300L, ROLE_SERVICE_MANAGER_L1, "PRIMARY", 0L),
                        assignment(100L, 301L, ROLE_SERVICE_MANAGER_L2, "PRIMARY", 0L)));

        assertThrows(ServiceException.class, () -> api.inspect(new ProjectParticipantFactQuery(
                100L, null, Set.of(ROLE_SERVICE_MANAGER_L1, ROLE_SERVICE_MANAGER_L2), LocalDateTime.now())));
    }

    @Test
    void inspectRejectsInconsistentMapperFact() {
        when(projectMapper.selectById(100L)).thenReturn(project(100L, null, "ACTIVE", "S1", 1, 0L));
        when(memberMapper.selectParticipantFacts(any(ProjectParticipantFactLookupQuery.class)))
                .thenReturn(List.of(assignment(100L, 300L, ROLE_SERVICE_MANAGER_L1, "COLLABORATOR", 0L)));

        assertThrows(ServiceException.class, () -> api.inspect(new ProjectParticipantFactQuery(
                100L, 300L, Set.of(ROLE_SERVICE_MANAGER_L1), LocalDateTime.now())));
    }

    @Test
    void lockAndRevalidateUsesProjectThenCurrentAssignment() {
        when(projectMapper.selectByIdForUpdate(100L))
                .thenReturn(project(100L, 200L, "ACTIVE", "S2", 11, 0L));
        when(memberMapper.selectParticipantFactsForUpdate(any(ProjectParticipantFactLockQuery.class)))
                .thenReturn(List.of(assignment(100L, 300L, ROLE_SERVICE_MANAGER_L1, "PRIMARY", 0L)));

        var fact = api.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                100L, 300L, 11, "ACTIVE", null, Set.of(ROLE_SERVICE_MANAGER_L1)));

        assertEquals(300L, fact.userId());
        assertEquals(11L, fact.factVersion());
        verify(projectMapper).selectByIdForUpdate(100L);
        verify(memberMapper).selectParticipantFactsForUpdate(any(ProjectParticipantFactLockQuery.class));
    }

    @Test
    void lockAndRevalidateRejectsVersionOrRequiredStageChangeBeforeMemberRead() {
        when(projectMapper.selectByIdForUpdate(100L))
                .thenReturn(project(100L, 200L, "ACTIVE", "S2", 12, 0L));

        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(
                        100L, 200L, 11, "ACTIVE", null, Set.of(ROLE_PROJECT_MANAGER))));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(
                        100L, 200L, 12, "ACTIVE", "S1", Set.of(ROLE_PROJECT_MANAGER))));
        verify(memberMapper, never()).selectParticipantFactsForUpdate(any(ProjectParticipantFactLockQuery.class));
    }

    @Test
    void rejectsEmptyRolesCrossTenantAndMissingTenant() {
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectParticipantFactQuery(
                100L, 200L, Set.of(), LocalDateTime.now())));
        when(projectMapper.selectById(100L)).thenReturn(project(100L, 200L, "ACTIVE", "S1", 1, 1L));
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectParticipantFactQuery(
                100L, 200L, Set.of(ROLE_PROJECT_MANAGER), LocalDateTime.now())));

        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectParticipantFactQuery(
                100L, 200L, Set.of(ROLE_PROJECT_MANAGER), LocalDateTime.now())));
    }

    private static ProjectMasterDO project(long id, Long managerId, String lifecycleStatus,
                                           String currentStage, int version, long tenantId) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setManagerId(managerId);
        project.setLifecycleStatus(lifecycleStatus);
        project.setCurrentStage(currentStage);
        project.setVersion(version);
        project.setTenantId(tenantId);
        return project;
    }

    private static ProjectMemberAssignmentDO assignment(long projectId, long userId, String role,
                                                         String assignmentType, long tenantId) {
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setProjectId(projectId);
        assignment.setUserId(userId);
        assignment.setMemberRole(role);
        assignment.setAssignmentType(assignmentType);
        assignment.setTenantId(tenantId);
        return assignment;
    }

}
