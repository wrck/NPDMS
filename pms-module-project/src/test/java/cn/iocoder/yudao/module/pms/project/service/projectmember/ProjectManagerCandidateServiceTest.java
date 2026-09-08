package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** PM-01：候选范围只能来自授权项目，当前主责不从加入快照推断。 */
class ProjectManagerCandidateServiceTest {
    final ProjectCreationAuthorizationService authorization = mock(ProjectCreationAuthorizationService.class);
    final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    final OrganizationScopeApi organization = mock(OrganizationScopeApi.class);
    final ProjectManagerCandidateService service = new ProjectManagerCandidateService(authorization, projects, organization);
    final ProjectManualCreationService.ProjectAccessActor actor = new ProjectManualCreationService.ProjectAccessActor(1L, 7L);

    @Test void candidatesUseProjectCompanyAndFixedRoleAfterAuthorization() {
        var project = new ProjectMasterDO(); project.setCompanyId(21L);
        when(projects.getProjectForManage(9L, actor)).thenReturn(project);
        when(organization.pageCompanyRoleUsers(any())).thenReturn(PageResult.empty());
        service.page(9L, "张", 2, 20, actor);
        verify(authorization).assertCanAssign(7L);
        verify(organization).pageCompanyRoleUsers(argThat(query -> query.getCompanyId().equals(21L)
                && query.getRoleCode().equals("PROJECT_MANAGER") && query.getPageNo() == 2 && query.getPageSize() == 20));
        doThrow(new IllegalArgumentException("forbidden")).when(projects).getProjectForManage(10L, actor);
        assertThrows(RuntimeException.class, () -> service.page(10L, "", 1, 20, actor));
        verifyNoMoreInteractions(organization);
    }

    @Test void currentReturnsOnlyEffectiveManagersAndKeepsProjectPrimaryReference() {
        var project = new ProjectMasterDO(); project.setManagerId(8L); project.setVersion(3);
        when(projects.getProject(9L, actor)).thenReturn(project);
        var active = member(8L); active.setAssignmentType("COLLABORATOR");
        var ended = member(7L); ended.setEffectiveTo(LocalDateTime.now().minusMinutes(1));
        var future = member(6L); future.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        var serviceManager = member(5L); serviceManager.setMemberRole("SERVICE_MANAGER_L1");
        when(projects.getMemberAssignments(9L, actor)).thenReturn(List.of(active, ended, future, serviceManager));
        var result = service.current(9L, actor);
        assertEquals(8L, result.primaryUserId()); assertEquals(3, result.version());
        assertEquals(List.of(8L), result.members().stream().map(ProjectManagerMemberResult.Member::userId).toList());
    }
    private ProjectMemberAssignmentDO member(Long id) {
        var member = new ProjectMemberAssignmentDO(); member.setId(id); member.setUserId(id);
        member.setMemberRole("PROJECT_MANAGER"); member.setStatus("ACTIVE"); return member;
    }
}
