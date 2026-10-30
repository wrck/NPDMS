package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectOverdueMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectOverdueNodeFact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStatusPresentationServiceTest {
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    private final ProjectOverdueMapper overdue = mock(ProjectOverdueMapper.class);
    private final ProjectStatusPresentationService service = new ProjectStatusPresentationService(members, stages, overdue);

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void distinguishesPrimaryManagersFromCollaboratorsAndKeepsParallelStages() {
        TenantContextHolder.setTenantId(7L);
        var first = project(11L); var second = project(12L); var third = project(13L);
        first.setManagerId(101L); third.setManagerId(101L);
        when(members.selectStatusAssignments(any())).thenReturn(List.of(
                member(11L, "SERVICE_MANAGER_L1", "COLLABORATOR"), member(11L, "PROJECT_MANAGER", "PRIMARY"),
                member(12L, "SERVICE_MANAGER", "PRIMARY"), member(12L, "PROJECT_MANAGER", "PRIMARY"),
                member(13L, "SERVICE_MANAGER", "PRIMARY"), member(13L, "PROJECT_MANAGER", "COLLABORATOR")));
        when(stages.selectActiveForStatus(any())).thenReturn(List.of(stage(13L, "工前准备"), stage(13L, "施工计划")));
        service.populate(List.of(first, second, third));
        assertFalse(first.getServiceManagerAssigned()); assertTrue(first.getProjectManagerAssigned());
        assertTrue(second.getServiceManagerAssigned()); assertFalse(second.getProjectManagerAssigned());
        assertTrue(third.getServiceManagerAssigned()); assertTrue(third.getProjectManagerAssigned());
        assertEquals(List.of("工前准备", "施工计划"), third.getActiveStageNames());
        assertEquals(List.of(), first.getActiveStageNames());
        verify(members).selectStatusAssignments(argThat(q -> q.tenantId().equals(7L)
                && q.projectIds().equals(Set.of(11L, 12L, 13L)) && q.effectiveAt() != null));
        verify(stages).selectActiveForStatus(any());
        verify(overdue).selectStatusFacts(argThat(q -> q.tenantId().equals(7L)
                && q.projectIds().equals(Set.of(11L, 12L, 13L))));
        verifyNoMoreInteractions(members, stages);
    }

    @Test void emptyAuthorizedPageDoesNotQueryAnything() {
        service.populate(List.of());
        verifyNoInteractions(members, stages, overdue);
    }

    @Test void managerPointerMustReferToAnEffectiveProjectManager() {
        TenantContextHolder.setTenantId(7L);
        var project = project(11L); project.setManagerId(102L);
        when(members.selectStatusAssignments(any())).thenReturn(List.of(
                member(11L, "SERVICE_MANAGER", "PRIMARY"), member(11L, "PROJECT_MANAGER", "PRIMARY")));
        when(stages.selectActiveForStatus(any())).thenReturn(List.of());
        service.populate(List.of(project));
        assertTrue(project.getServiceManagerAssigned());
        assertFalse(project.getProjectManagerAssigned());
    }

    private static ProjectRespVO project(Long id) {
        var value = new ProjectRespVO(); value.setId(id); return value;
    }

    @Test void stageInstanceViewExposesExistingPlanDatesAndStableNodeIdentity() {
        var source = new ProjectStageInstanceDO();
        source.setId(99123456789012345L);
        source.setPlanEndTime(java.time.LocalDateTime.of(2026, 10, 9, 18, 0));
        source.setAcceptanceTime(java.time.LocalDateTime.of(2026, 10, 10, 18, 0));
        var view = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(source,
                cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectInstancesRespVO.StageItem.class);
        assertEquals(source.getId(), view.getId());
        assertEquals(source.getPlanEndTime(), view.getPlanEndTime());
        assertEquals(source.getAcceptanceTime(), view.getAcceptanceTime());
    }

    @Test void currentAndCompletedOverdueFactsRemainSeparateAndClosedProjectsLeaveCurrentList() {
        TenantContextHolder.setTenantId(7L);
        var active = project(11L); active.setLifecycleStatus("ACTIVE");
        var closed = project(12L); closed.setLifecycleStatus("NORMAL_CLOSED");
        var current = new ProjectOverdueNodeFact(); current.setProjectId(11L); current.setNodeId(21L);
        current.setNodeKind("STAGE"); current.setWorkOverdue(true); current.setAcceptanceOverdue(true);
        var completed = new ProjectOverdueNodeFact(); completed.setProjectId(11L); completed.setNodeId(22L);
        completed.setCompletedAt(java.time.LocalDateTime.now()); completed.setWorkOverdue(true);
        var closedCurrent = new ProjectOverdueNodeFact(); closedCurrent.setProjectId(12L);
        when(overdue.selectStatusFacts(any())).thenReturn(List.of(current, completed, closedCurrent));
        service.populate(List.of(active, closed));
        assertEquals(1, active.getOverdueNodes().size());
        assertTrue(active.getOverdueNodes().getFirst().workOverdue());
        assertTrue(active.getOverdueNodes().getFirst().acceptanceOverdue());
        assertEquals(22L, active.getCompletedOverdueNodes().getFirst().nodeId());
        assertTrue(closed.getOverdueNodes().isEmpty());
    }
    private static ProjectMemberAssignmentDO member(Long id, String role, String type) {
        var value = new ProjectMemberAssignmentDO(); value.setProjectId(id);
        value.setUserId(101L);
        value.setMemberRole(role); value.setAssignmentType(type); return value;
    }
    private static ProjectStageInstanceDO stage(Long id, String name) {
        var value = new ProjectStageInstanceDO(); value.setProjectId(id); value.setName(name); return value;
    }
}
