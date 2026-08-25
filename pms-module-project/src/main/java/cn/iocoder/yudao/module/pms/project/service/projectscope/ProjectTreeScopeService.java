package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CreatedProjectScopeQuery;
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
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_EDIT;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectTreeScopeService {

    private static final String SCOPE_CURRENT = "CURRENT_PROJECT";
    private static final String SCOPE_DESCENDANTS = "PROJECT_AND_DESCENDANTS";
    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROJECT_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");
    private static final int GRANT_DISCOVERY_PAGE_SIZE = 100;

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
        if (ACTION_VIEW.equals(query.actionCode())) {
            projectMapper.selectListCreatedBy(new CreatedProjectScopeQuery(
                            query.tenantId(), String.valueOf(query.subjectUserId()), rootNodes)).stream()
                    .map(ProjectMasterDO::getId)
                    .forEach(full::add);
        }

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

    /**
     * 解析主体在租户内可完整访问的全部项目，用于无锚点的项目分页入口。
     * 成员关系和有效授权只用于发现候选树，最终范围仍逐树复用统一 resolve 算法。
     */
    public Set<Long> resolveAllFullProjectIds(Long tenantId, Long subjectUserId, String actionCode) {
        validateActorAction(tenantId, subjectUserId, actionCode);
        LocalDateTime effectiveAt = LocalDateTime.now();
        Set<Long> anchors = memberMapper.selectActiveByUser(
                        new ActiveProjectMemberQuery(tenantId, subjectUserId, effectiveAt)).stream()
                .filter(item -> allows(item.getMemberRole(), actionCode))
                .map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));
        if (ACTION_VIEW.equals(actionCode)) {
            projectMapper.selectListCreatedBy(new CreatedProjectScopeQuery(
                            tenantId, String.valueOf(subjectUserId), null)).stream()
                    .map(ProjectMasterDO::getId)
                    .forEach(anchors::add);
        }
        discoverGrantAnchors(tenantId, subjectUserId, actionCode, effectiveAt, anchors);
        if (ACTION_VIEW.equals(actionCode) || ACTION_EDIT.equals(actionCode)) {
            discoverGrantAnchors(tenantId, subjectUserId, ACTION_MANAGE, effectiveAt, anchors);
        }
        if (anchors.isEmpty()) {
            return Set.of();
        }
        Set<Long> rootIds = projectMapper.selectBatchIds(anchors).stream()
                .filter(project -> Objects.equals(project.getTenantId(), tenantId))
                .map(project -> project.getRootId() == null ? project.getId() : project.getRootId())
                .collect(Collectors.toSet());
        Set<Long> visible = new HashSet<>();
        for (Long rootId : rootIds) {
            ProjectTreeVersionDO version = versionMapper.selectLatestActive(rootId);
            if (version == null) {
                continue;
            }
            visible.addAll(resolve(new ProjectScopeQuery(
                    tenantId, subjectUserId, rootId, actionCode, version.getTreeVersion())).fullProjectIds());
        }
        return Set.copyOf(visible);
    }

    public void assertFullAccess(ProjectScopeQuery query) {
        ProjectTreeScope scope = resolve(query);
        if (scope.visibility(query.anchorProjectId()) != Visibility.FULL) {
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
        } else if (ACTION_EDIT.equals(query.actionCode())) {
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

    private void discoverGrantAnchors(Long tenantId, Long subjectUserId, String actionCode,
                                      LocalDateTime effectiveAt, Set<Long> anchors) {
        int pageNo = 1;
        long consumed;
        long total;
        do {
            var page = authorizationGrantApi.page(new AuthorizationGrantPageQuery(
                    tenantId, "USER", subjectUserId, "PROJ", "PROJECT", null,
                    actionCode, null, "ACTIVE", effectiveAt, pageNo, GRANT_DISCOVERY_PAGE_SIZE));
            page.list().stream().map(AuthorizationGrantDTO::resourceId).forEach(anchors::add);
            consumed = (long) pageNo * GRANT_DISCOVERY_PAGE_SIZE;
            total = page.total();
            pageNo++;
            if (page.list().isEmpty()) {
                break;
            }
        } while (consumed < total);
    }

    private boolean allows(String memberRole, String actionCode) {
        return ACTION_VIEW.equals(actionCode) || ACTION_EDIT.equals(actionCode)
                || MANAGER_ROLES.contains(memberRole);
    }

    private void validate(ProjectScopeQuery query) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (query == null || query.tenantId() == null || query.tenantId() < 0
                || contextTenantId != null && !contextTenantId.equals(query.tenantId())
                || query.subjectUserId() == null || query.subjectUserId() <= 0
                || query.anchorProjectId() == null || query.anchorProjectId() <= 0
                || query.expectedTreeVersion() == null || query.expectedTreeVersion() <= 0
                || !Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_MANAGE).contains(query.actionCode())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void validateActorAction(Long tenantId, Long subjectUserId, String actionCode) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0
                || contextTenantId != null && !contextTenantId.equals(tenantId)
                || subjectUserId == null || subjectUserId <= 0
                || !Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_MANAGE).contains(actionCode)) {
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
