package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserPageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** PM-01：客户端不能改变项目公司、指定角色或受信任租户。 */
@Service
@RequiredArgsConstructor
public class ProjectManagerCandidateService {
    private final ProjectCreationAuthorizationService authorization;
    private final ProjectManualCreationService projects;
    private final OrganizationScopeApi organization;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectManagerMemberResult current(Long projectId, ProjectManualCreationService.ProjectAccessActor actor) {
        var project = projects.getProject(projectId, actor);
        var now = LocalDateTime.now();
        var members = projects.getMemberAssignments(projectId, actor).stream()
                .filter(row -> "PROJECT_MANAGER".equals(row.getMemberRole()) && "ACTIVE".equals(row.getStatus()))
                .filter(row -> row.getEffectiveFrom() == null || !row.getEffectiveFrom().isAfter(now))
                .filter(row -> row.getEffectiveTo() == null || row.getEffectiveTo().isAfter(now))
                .map(row -> new ProjectManagerMemberResult.Member(row.getId(), row.getUserId(), row.getMemberName(), row.getEffectiveFrom()))
                .toList();
        return new ProjectManagerMemberResult(projectId, project.getVersion(), project.getManagerId(), project.getAssignmentStatus(), false, members);
    }

    public PageResult<CompanyRoleUserRespDTO> page(Long projectId, String keyword, int pageNo, int pageSize,
                                                ProjectManualCreationService.ProjectAccessActor actor) {
        authorization.assertCanAssign(actor.actorId());
        var project = projects.getProjectForManage(projectId, actor);
        var query = new CompanyRoleUserPageReqDTO().setCompanyId(project.getCompanyId())
                .setRoleCode("PROJECT_MANAGER").setKeyword(keyword);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return organization.pageCompanyRoleUsers(query);
    }
}
