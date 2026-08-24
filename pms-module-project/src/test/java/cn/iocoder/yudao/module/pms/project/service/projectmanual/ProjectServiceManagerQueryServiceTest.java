package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerCandidatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerResponsibilityPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceManagerQueryServiceTest {

    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectMemberAssignmentMapper memberMapper;
    @Mock private ProjectTreeVersionMapper treeVersionMapper;
    @Mock private ProjectTreeScopeService treeScopeService;
    @Mock private ProjectSiteApplicationService projectSiteService;
    @Mock private DeptApi deptApi;
    @Mock private OrganizationScopeApi organizationScopeApi;

    @InjectMocks
    private ProjectServiceManagerQueryService service;

    @Test
    void candidateQueryUsesExactProjectCompanyAndDepartmentScope() {
        allowScope(Set.of(1L));
        DeptRespDTO department = new DeptRespDTO();
        department.setId(20L);
        department.setCode("DEP-01");
        when(deptApi.getDeptByCode("DEP-01")).thenReturn(department);
        OrganizationUserCandidateRespDTO candidate = new OrganizationUserCandidateRespDTO();
        candidate.setUserId(66L);
        candidate.setCompanyId(10L);
        candidate.setDepartmentId(20L);
        candidate.setDepartmentCode("DEP-01");
        when(organizationScopeApi.pageActiveUsers(any())).thenReturn(new PageResult<>(List.of(candidate), 1L));

        var result = service.getCandidates(1L, candidateRequest(), actor());

        assertEquals(1L, result.getTotal());
        assertEquals(66L, result.getList().getFirst().getUserId());
        verify(organizationScopeApi).pageActiveUsers(any());
    }

    @Test
    void candidateQueryRejectsSiteOutsideCurrentProjectScope() {
        allowScope(Set.of(1L));
        ServiceManagerCandidatePageReqVO request = candidateRequest();
        request.setSiteId(99L);
        when(projectSiteService.getActiveSites(1L)).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> service.getCandidates(1L, request, actor()));

        verify(organizationScopeApi, never()).pageActiveUsers(any());
    }

    @Test
    void responsibilityQueryGroupsPrimaryAndCollaboratorsPerActualNode() {
        allowScope(Set.of(1L, 2L));
        ProjectMasterDO child = project(2L, 1L);
        child.setParentId(1L);
        child.setProjectCode("P-01-01");
        child.setProjectName("子项目");
        child.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_ASSIGNED);
        when(memberMapper.selectResponsibilityNodeCount(any())).thenReturn(1L);
        when(memberMapper.selectResponsibilityNodePage(any())).thenReturn(List.of(child));
        when(memberMapper.selectCurrentServiceManagerAssignments(any())).thenReturn(List.of(
                assignment(8L, 2L, 66L, ProjectRules.ASSIGNMENT_TYPE_PRIMARY),
                assignment(9L, 2L, 67L, ProjectRules.ASSIGNMENT_TYPE_COLLABORATOR)));

        var result = service.getResponsibilities(1L, responsibilityRequest(), actor());

        assertEquals(1L, result.getTotal());
        var scope = result.getList().getFirst().getResponsibilities().getFirst();
        assertEquals(66L, scope.getPrimaryManager().getUserId());
        assertEquals(67L, scope.getCollaborators().getFirst().getUserId());
        assertEquals("L2", scope.getLevelCode());
    }

    @Test
    void responsibilityQueryReturnsEmptyPageForEmptyManageScope() {
        allowScope(Set.of());

        var result = service.getResponsibilities(1L, responsibilityRequest(), actor());

        assertEquals(0L, result.getTotal());
        verify(memberMapper, never()).selectResponsibilityNodeCount(any());
    }

    @Test
    void crossTenantProjectIsRejectedBeforeScopeResolution() {
        ProjectMasterDO project = project(1L, 1L);
        project.setTenantId(2L);
        when(projectMapper.selectById(1L)).thenReturn(project);

        assertThrows(ServiceException.class,
                () -> service.getCandidates(1L, candidateRequest(), actor()));

        verify(treeScopeService, never()).resolve(any());
    }

    private void allowScope(Set<Long> projectIds) {
        when(projectMapper.selectById(1L)).thenReturn(project(1L, 1L));
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(3L);
        when(treeVersionMapper.selectLatestActive(1L)).thenReturn(version);
        when(treeScopeService.resolve(any())).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(1L, 3L, projectIds, Set.of(), Set.of()));
    }

    private ProjectMasterDO project(Long id, Long rootId) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setRootId(rootId);
        project.setTenantId(1L);
        project.setCompanyId(10L);
        return project;
    }

    private ServiceManagerCandidatePageReqVO candidateRequest() {
        ServiceManagerCandidatePageReqVO request = new ServiceManagerCandidatePageReqVO();
        request.setDepartmentId(20L);
        request.setDepartmentCode("DEP-01");
        return request;
    }

    private ServiceManagerResponsibilityPageReqVO responsibilityRequest() {
        return new ServiceManagerResponsibilityPageReqVO();
    }

    private ProjectMemberAssignmentDO assignment(Long id, Long projectId, Long userId, String assignmentType) {
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setId(id);
        assignment.setProjectId(projectId);
        assignment.setUserId(userId);
        assignment.setMemberRole(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L2);
        assignment.setAssignmentType(assignmentType);
        assignment.setSiteId(30L);
        assignment.setDepartmentId(20L);
        assignment.setDepartmentCode("DEP-01");
        assignment.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        return assignment;
    }

    private ProjectServiceManagerQueryService.Actor actor() {
        return new ProjectServiceManagerQueryService.Actor(1L, 7L);
    }
}
