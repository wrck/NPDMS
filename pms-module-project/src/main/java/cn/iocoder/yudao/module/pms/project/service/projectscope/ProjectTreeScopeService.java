package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectTreeScopeService {

    private static final String SCOPE_CURRENT = "CURRENT_PROJECT";
    private static final String SCOPE_DESCENDANTS = "PROJECT_AND_DESCENDANTS";
    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROJECT_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectTreeVersionMapper versionMapper;
    private final AuthorizationGrantApi authorizationGrantApi;

    public ProjectTreeScope resolve(ProjectScopeQuery query) {
        validate(query);
        ProjectMasterDO anchor = projectMapper.selectById(query.anchorProjectId());
        if (anchor == null || !Objects.equals(anchor.getTenantId(), query.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        long rootId = anchor.getRootId() == null ? anchor.getId() : anchor.getRootId();
        ProjectTreeVersionDO currentVersion = versionMapper.selectLatestActive(rootId);
        if (currentVersion == null
                || !Objects.equals(currentVersion.getTreeVersion(), query.expectedTreeVersion())) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        Set<Long> rootNodes = pathMapper.selectByAncestor(
                        rootId, query.expectedTreeVersion(), rootId, null).stream()
                .map(ProjectTreePathDO::getDescendantProjectId)
                .collect(Collectors.toSet());
        if (rootNodes.isEmpty()) {
            return emptyScope(rootId, query.expectedTreeVersion());
        }

        LocalDateTime effectiveAt = LocalDateTime.now();
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectActiveByUser(
                        new ActiveProjectMemberQuery(query.tenantId(), query.subjectUserId(), effectiveAt)).stream()
                .filter(item -> rootNodes.contains(item.getProjectId()))
                .filter(item -> allows(item.getMemberRole(), query.actionCode()))
                .toList();
        Set<Long> full = assignments.stream().map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));

        List<AuthorizationGrantDTO> grants = listEffectiveGrants(query, rootNodes, effectiveAt);
        Set<Long> descendantAnchors = new HashSet<>();
        for (AuthorizationGrantDTO grant : grants) {
            if (SCOPE_CURRENT.equals(grant.scopeCode())) {
                full.add(grant.resourceId());
            } else if (SCOPE_DESCENDANTS.equals(grant.scopeCode())) {
                descendantAnchors.add(grant.resourceId());
            }
        }
        pathMapper.selectByAncestors(rootId, query.expectedTreeVersion(), descendantAnchors).stream()
                .map(ProjectTreePathDO::getDescendantProjectId)
                .forEach(full::add);
        full.retainAll(rootNodes);

        Set<Long> placeholders = pathMapper.selectByDescendants(
                        rootId, query.expectedTreeVersion(), full).stream()
                .map(ProjectTreePathDO::getAncestorProjectId)
                .collect(Collectors.toCollection(HashSet::new));
        placeholders.removeAll(full);
        return new ProjectTreeScope(rootId, query.expectedTreeVersion(), Set.copyOf(full),
                Set.copyOf(placeholders), Set.of());
    }

    /** F-PROJ-002内部兼容入口；Task 5将当前业务入口全部改为显式动作。 */
    public ProjectTreeScope resolve(Long actorId, Long anchorProjectId, long treeVersion) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return resolve(new ProjectScopeQuery(
                tenantId, actorId, anchorProjectId, ACTION_VIEW, treeVersion));
    }

    public void assertFullAccess(Long actorId, Long projectId, long treeVersion) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectTreeScope scope = resolve(new ProjectScopeQuery(
                tenantId, actorId, projectId, ACTION_MANAGE, treeVersion));
        if (scope.visibility(projectId) != Visibility.FULL) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private List<AuthorizationGrantDTO> listEffectiveGrants(
            ProjectScopeQuery query, Set<Long> rootNodes, LocalDateTime effectiveAt) {
        List<AuthorizationGrantDTO> grants = new ArrayList<>(authorizationGrantApi.listEffective(
                authorizationQuery(query, rootNodes, query.actionCode(), effectiveAt)));
        if (ACTION_VIEW.equals(query.actionCode())) {
            grants.addAll(authorizationGrantApi.listEffective(
                    authorizationQuery(query, rootNodes, ACTION_MANAGE, effectiveAt)));
        }
        return grants;
    }

    private AuthorizationGrantQuery authorizationQuery(
            ProjectScopeQuery query, Set<Long> rootNodes, String actionCode, LocalDateTime effectiveAt) {
        return new AuthorizationGrantQuery(query.tenantId(), "USER", query.subjectUserId(),
                "PROJ", "PROJECT", rootNodes, actionCode, effectiveAt);
    }

    private boolean allows(String memberRole, String actionCode) {
        return ACTION_VIEW.equals(actionCode) || MANAGER_ROLES.contains(memberRole);
    }

    private void validate(ProjectScopeQuery query) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (query == null || query.tenantId() == null || query.tenantId() < 0
                || contextTenantId != null && !contextTenantId.equals(query.tenantId())
                || query.subjectUserId() == null || query.subjectUserId() <= 0
                || query.anchorProjectId() == null || query.anchorProjectId() <= 0
                || query.expectedTreeVersion() == null || query.expectedTreeVersion() <= 0
                || !Set.of(ACTION_VIEW, ACTION_MANAGE).contains(query.actionCode())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private ProjectTreeScope emptyScope(long rootId, long treeVersion) {
        return new ProjectTreeScope(rootId, treeVersion, Set.of(), Set.of(), Set.of());
    }

    public enum Visibility { FULL, ROOT_SUMMARY, PATH_PLACEHOLDER, NONE }

    public record ProjectTreeScope(long rootProjectId, long treeVersion, Set<Long> fullProjectIds,
                                   Set<Long> placeholderProjectIds, Set<Long> summaryProjectIds) {
        public Visibility visibility(Long projectId) {
            if (fullProjectIds.contains(projectId)) return Visibility.FULL;
            if (summaryProjectIds.contains(projectId)) return Visibility.ROOT_SUMMARY;
            if (placeholderProjectIds.contains(projectId)) return Visibility.PATH_PLACEHOLDER;
            return Visibility.NONE;
        }

        public Set<Long> visibleProjectIds() {
            Set<Long> visible = new HashSet<>(fullProjectIds);
            visible.addAll(placeholderProjectIds);
            visible.addAll(summaryProjectIds);
            return Set.copyOf(visible);
        }
    }
}
