package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerCandidatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerCandidateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerResponsibilityPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ServiceManagerResponsibilityRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentServiceManagerAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ServiceManagerResponsibilityPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

/** 服务经理候选与项目树责任分布查询。 */
@Service
@RequiredArgsConstructor
public class ProjectServiceManagerQueryService {

    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreeScopeService treeScopeService;
    private final ProjectSiteApplicationService projectSiteService;
    private final DeptApi deptApi;
    private final OrganizationScopeApi organizationScopeApi;

    public PageResult<ServiceManagerCandidateRespVO> getCandidates(
            Long projectId, ServiceManagerCandidatePageReqVO request, Actor actor) {
        validateCandidateRequest(projectId, request, actor);
        ProjectMasterDO project = requireProject(projectId, actor.tenantId());
        var candidateScope = resolveManageScope(project, actor);
        if (!candidateScope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        if (project.getCompanyId() == null) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "项目公司范围不存在");
        }
        if (request.getSiteId() != null && projectSiteService.getActiveSites(projectId).stream()
                .noneMatch(site -> Objects.equals(site.getSiteId(), request.getSiteId()))) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "站点不在项目当前实施范围内");
        }
        var department = deptApi.getDeptByCode(request.getDepartmentCode());
        if (department == null || !Objects.equals(department.getId(), request.getDepartmentId())) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "办事处部门不存在或ID编码不一致");
        }
        OrganizationUserCandidatePageReqDTO systemRequest = new OrganizationUserCandidatePageReqDTO();
        systemRequest.setCompanyId(project.getCompanyId());
        systemRequest.setDepartmentId(request.getDepartmentId());
        systemRequest.setDepartmentCode(request.getDepartmentCode());
        systemRequest.setKeyword(request.getKeyword());
        systemRequest.setPageNo(request.getPageNo());
        systemRequest.setPageSize(request.getPageSize());
        var page = organizationScopeApi.pageActiveUsers(systemRequest);
        return new PageResult<>(BeanUtils.toBean(page.getList(), ServiceManagerCandidateRespVO.class),
                page.getTotal());
    }

    public PageResult<ServiceManagerResponsibilityRespVO> getResponsibilities(
            Long rootProjectId, ServiceManagerResponsibilityPageReqVO request, Actor actor) {
        validateResponsibilityRequest(rootProjectId, request, actor);
        ProjectMasterDO root = requireProject(rootProjectId, actor.tenantId());
        if (!Objects.equals(rootProjectId, root.getRootId() == null ? root.getId() : root.getRootId())) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "责任分布入口必须是根项目");
        }
        Set<Long> visibleProjectIds = resolveManageScope(root, actor).fullProjectIds();
        if (visibleProjectIds.isEmpty()
                || (request.getProjectId() != null && !visibleProjectIds.contains(request.getProjectId()))) {
            return PageResult.empty();
        }
        ServiceManagerResponsibilityPageQuery pageQuery = new ServiceManagerResponsibilityPageQuery(
                actor.tenantId(), visibleProjectIds, request.getProjectId(),
                (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize());
        long total = memberMapper.selectResponsibilityNodeCount(pageQuery);
        if (total == 0) {
            return PageResult.empty();
        }
        List<ProjectMasterDO> nodes = memberMapper.selectResponsibilityNodePage(pageQuery);
        Set<Long> pageProjectIds = nodes.stream().map(ProjectMasterDO::getId).collect(java.util.stream.Collectors.toSet());
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectCurrentServiceManagerAssignments(
                new CurrentServiceManagerAssignmentsQuery(actor.tenantId(), pageProjectIds, LocalDateTime.now()));
        return new PageResult<>(assembleResponsibilities(nodes, assignments), total);
    }

    private List<ServiceManagerResponsibilityRespVO> assembleResponsibilities(
            List<ProjectMasterDO> nodes, List<ProjectMemberAssignmentDO> assignments) {
        Map<Long, ServiceManagerResponsibilityRespVO> nodeViews = new LinkedHashMap<>();
        Map<Long, Map<ResponsibilityKey, ServiceManagerResponsibilityRespVO.ResponsibilityScope>> scopes =
                new LinkedHashMap<>();
        for (ProjectMasterDO node : nodes) {
            ServiceManagerResponsibilityRespVO view = BeanUtils.toBean(node,
                    ServiceManagerResponsibilityRespVO.class);
            if (view.getAssignmentStatus() == null) {
                view.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED);
            }
            nodeViews.put(node.getId(), view);
            scopes.put(node.getId(), new LinkedHashMap<>());
        }
        for (ProjectMemberAssignmentDO assignment : assignments) {
            ServiceManagerResponsibilityRespVO nodeView = nodeViews.get(assignment.getProjectId());
            if (nodeView == null) {
                continue;
            }
            ResponsibilityKey key = new ResponsibilityKey(assignment.getMemberRole(), assignment.getSiteId(),
                    assignment.getDepartmentId(), assignment.getDepartmentCode());
            ServiceManagerResponsibilityRespVO.ResponsibilityScope scope = scopes.get(assignment.getProjectId())
                    .computeIfAbsent(key, ignored -> newScope(assignment));
            ServiceManagerResponsibilityRespVO.Manager manager = BeanUtils.toBean(assignment,
                    ServiceManagerResponsibilityRespVO.Manager.class);
            manager.setAssignmentId(assignment.getId());
            if (assignment.getAssignmentType() == null
                    || ProjectRules.ASSIGNMENT_TYPE_PRIMARY.equals(assignment.getAssignmentType())) {
                if (scope.getPrimaryManager() == null) {
                    scope.setPrimaryManager(manager);
                }
            } else {
                scope.getCollaborators().add(manager);
            }
        }
        for (Map.Entry<Long, ServiceManagerResponsibilityRespVO> entry : nodeViews.entrySet()) {
            entry.getValue().setResponsibilities(new ArrayList<>(scopes.get(entry.getKey()).values()));
        }
        return new ArrayList<>(nodeViews.values());
    }

    private ServiceManagerResponsibilityRespVO.ResponsibilityScope newScope(
            ProjectMemberAssignmentDO assignment) {
        ServiceManagerResponsibilityRespVO.ResponsibilityScope scope =
                new ServiceManagerResponsibilityRespVO.ResponsibilityScope();
        scope.setLevelCode(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1.equals(assignment.getMemberRole())
                ? "L1" : "L2");
        scope.setSiteId(assignment.getSiteId());
        scope.setDepartmentId(assignment.getDepartmentId());
        scope.setDepartmentCode(assignment.getDepartmentCode());
        scope.setDepartmentName(assignment.getDepartmentName());
        return scope;
    }

    private ProjectTreeScopeService.ProjectTreeScope resolveManageScope(ProjectMasterDO project, Actor actor) {
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        var version = treeVersionMapper.selectLatestActive(rootId);
        if (version == null) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return treeScopeService.resolve(new ProjectScopeQuery(actor.tenantId(), actor.actorId(),
                project.getId(), ProjectScopeApi.ACTION_MANAGE, version.getTreeVersion()));
    }

    private ProjectMasterDO requireProject(Long projectId, Long tenantId) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return project;
    }

    private void validateCandidateRequest(Long projectId, ServiceManagerCandidatePageReqVO request, Actor actor) {
        if (projectId == null || projectId <= 0 || request == null || request.getDepartmentId() == null
                || request.getDepartmentId() <= 0
                || (request.getSiteId() != null && request.getSiteId() <= 0)
                || request.getDepartmentCode() == null || request.getDepartmentCode().isBlank()
                || request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageSize() == null || request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new IllegalArgumentException("候选查询参数不完整");
        }
        validateActor(actor);
    }

    private void validateResponsibilityRequest(
            Long rootProjectId, ServiceManagerResponsibilityPageReqVO request, Actor actor) {
        if (rootProjectId == null || rootProjectId <= 0 || request == null
                || (request.getProjectId() != null && request.getProjectId() <= 0)
                || request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageSize() == null || request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new IllegalArgumentException("责任分布查询参数不完整");
        }
        validateActor(actor);
    }

    private void validateActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private record ResponsibilityKey(String memberRole, Long siteId, Long departmentId, String departmentCode) {
    }

    public record Actor(Long tenantId, Long actorId) {
    }
}
