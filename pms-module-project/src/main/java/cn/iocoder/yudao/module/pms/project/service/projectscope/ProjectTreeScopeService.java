package cn.iocoder.yudao.module.pms.project.service.projectscope;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.TenantProjectScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
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
import java.util.Map;
import java.util.HashMap;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ActiveProjectTreeVersionsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectScopeRootPathsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectScopeDescendantsQuery;
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
    private static final Set<String> MANAGER_ROLES = ProjectMemberRoles.MANAGEMENT_CODES;
    private static final int GRANT_DISCOVERY_PAGE_SIZE = 100;

    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectTreeVersionMapper versionMapper;
    private final AuthorizationGrantApi authorizationGrantApi;
    private final PermissionApi permissionApi;
    private final ProjectOrganizationScopeService organizationScope;

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
        return resolveAtVersion(query, rootId);
    }

    private ProjectTreeScope resolveAtVersion(ProjectScopeQuery query, long rootId) {
        Set<Long> rootNodes = pathMapper.selectByAncestor(
                        rootId, query.expectedTreeVersion(), rootId, null).stream()
                .map(ProjectTreePathDO::getDescendantProjectId)
                .collect(Collectors.toSet());
        if (rootNodes.isEmpty()) {
            return emptyScope(rootId, query.expectedTreeVersion());
        }
        var office = officeScope(query.tenantId(), query.subjectUserId(), query.actionCode());
        if (OfficeProjectReadContext.current() != null) {
            return new ProjectTreeScope(rootId, query.expectedTreeVersion(),
                    office != null && rootNodes.contains(office) ? Set.of(office) : Set.of(), Set.of(), Set.of());
        }
        // 已先校验租户、项目和树版本；超管只扩展授权范围，不改变业务状态。
        if (hasTenantProjectManagementScope(query.tenantId(), query.subjectUserId())) {
            return new ProjectTreeScope(rootId, query.expectedTreeVersion(), Set.copyOf(rootNodes),
                    Set.of(), Set.of());
        }

        LocalDateTime effectiveAt = LocalDateTime.now();
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectAuthorizedByUser(
                        new ActiveProjectMemberQuery(query.tenantId(), query.subjectUserId(), effectiveAt, rootNodes)).stream()
                .filter(item -> rootNodes.contains(item.getProjectId()))
                .filter(item -> allows(item.getMemberRole(), query.actionCode()))
                .toList();
        Set<Long> full = assignments.stream().map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));
        full.addAll(organizationScope.projectIds(new ProjectOrganizationScopeService.Query(
                query.tenantId(), query.subjectUserId(), query.actionCode(), false, rootNodes)));
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

    public ProjectTreeScope resolveCurrent(ProjectCurrentScopeQuery query) {
        validateCurrent(query);
        ProjectMasterDO anchor = projectMapper.selectById(query.anchorProjectId());
        if (anchor == null || !Objects.equals(anchor.getTenantId(), query.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        long rootId = anchor.getRootId() == null ? anchor.getId() : anchor.getRootId();
        ProjectTreeVersionDO currentVersion = versionMapper.selectLatestActive(rootId);
        if (currentVersion == null) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        var versionedQuery = new ProjectScopeQuery(query.tenantId(), query.subjectUserId(),
                query.anchorProjectId(), query.actionCode(), currentVersion.getTreeVersion());
        validate(versionedQuery);
        return resolveAtVersion(versionedQuery, rootId);
    }

    public ProjectTreeScope lockAndRevalidate(ProjectScopeRevalidationQuery query) {
        validateRevalidation(query);
        ProjectMasterDO initialAnchor = projectMapper.selectById(query.anchorProjectId());
        if (initialAnchor == null || !Objects.equals(initialAnchor.getTenantId(), query.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        long rootId = initialAnchor.getRootId() == null ? initialAnchor.getId() : initialAnchor.getRootId();
        ProjectMasterDO lockedRoot = projectMapper.selectByIdForUpdate(rootId);
        if (lockedRoot == null || !Objects.equals(lockedRoot.getTenantId(), query.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        ProjectMasterDO lockedAnchor = Objects.equals(rootId, query.anchorProjectId())
                ? lockedRoot : projectMapper.selectByIdForUpdate(query.anchorProjectId());
        long lockedRootId = lockedAnchor == null || lockedAnchor.getRootId() == null
                ? query.anchorProjectId() : lockedAnchor.getRootId();
        if (lockedAnchor == null || !Objects.equals(lockedAnchor.getTenantId(), query.tenantId())
                || !Objects.equals(rootId, lockedRootId)) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        ProjectTreeVersionDO lockedVersion = versionMapper.selectLatestActiveForUpdate(rootId);
        if (lockedVersion == null) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        return resolveAtVersion(new ProjectScopeQuery(query.tenantId(), query.subjectUserId(),
                query.anchorProjectId(), query.actionCode(), lockedVersion.getTreeVersion()), rootId);
    }

    /**
     * 解析主体在租户内可完整访问的全部项目，用于无锚点的项目分页入口。
     * 成员关系和有效授权发现候选树；批量读取当前树事实，按原规则合并完整权限。
     */
    public Set<Long> resolveAllFullProjectIds(Long tenantId, Long subjectUserId, String actionCode) {
        validateActorAction(tenantId, subjectUserId, actionCode);
        if (OfficeProjectReadContext.current() != null) {
            var office = officeScope(tenantId, subjectUserId, actionCode);
            if (office == null) return Set.of();
            return resolveCurrent(new ProjectCurrentScopeQuery(tenantId, subjectUserId, office, actionCode)).fullProjectIds();
        }
        if (hasTenantProjectManagementScope(tenantId, subjectUserId)) {
            return Set.copyOf(projectMapper.selectTenantProjectIds(new TenantProjectScopeQuery(tenantId)));
        }
        List<ProjectScopeRootPathsQuery.TreeVersion> trees = discoverCurrentScopeTrees(
                tenantId, subjectUserId, actionCode);
        if (trees.isEmpty()) return Set.of();
        return resolveDiscoveredTrees(tenantId, subjectUserId, actionCode, trees);
    }

    /** 保留现有候选发现、根校验和版本复核；最终成员/授权在关联SQL中重新读取。 */
    public ProjectViewReadScopeApi.ReadScope prepareViewReadScope(
            ProjectAllScopeQuery query) {
        validateActorAction(query.tenantId(), query.subjectUserId(), query.actionCode());
        if (!ACTION_VIEW.equals(query.actionCode())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        if (OfficeProjectReadContext.current() != null) {
            var project = officeScope(query.tenantId(), query.subjectUserId(), ACTION_VIEW);
            var scope = project == null ? null : resolveCurrent(new ProjectCurrentScopeQuery(
                    query.tenantId(), query.subjectUserId(), project, ACTION_VIEW));
            var trees = scope == null || !scope.fullProjectIds().contains(project) ? List.<ProjectViewReadScopeApi.Tree>of()
                    : List.of(new ProjectViewReadScopeApi.Tree(scope.rootProjectId(), scope.treeVersion()));
            return new ProjectViewReadScopeApi.ReadScope(query.tenantId(), query.subjectUserId(),
                    ProjectViewReadScopeApi.Mode.SCOPED, LocalDateTime.now(), trees, List.of(), project);
        }
        var mode = ProjectViewReadScopeApi.Mode.TENANT_ADMIN;
        if (hasTenantProjectManagementScope(query.tenantId(), query.subjectUserId())) {
            return new ProjectViewReadScopeApi.ReadScope(
                    query.tenantId(), query.subjectUserId(), mode, LocalDateTime.now(), List.of());
        }
        var trees = discoverCurrentScopeTrees(query.tenantId(), query.subjectUserId(), ACTION_VIEW).stream()
                .map(tree -> new ProjectViewReadScopeApi.Tree(
                        tree.rootProjectId(), tree.treeVersion())).toList();
        mode = isTenantSuperAdmin(query.tenantId(), query.subjectUserId())
                ? ProjectViewReadScopeApi.Mode.TREE_ADMIN
                : ProjectViewReadScopeApi.Mode.SCOPED;
        return new ProjectViewReadScopeApi.ReadScope(
                query.tenantId(), query.subjectUserId(), mode, LocalDateTime.now(), trees,
                organizationScope.rows(new ProjectOrganizationScopeService.Query(
                        query.tenantId(), query.subjectUserId(), ACTION_VIEW, false, null)));
    }

    private List<ProjectScopeRootPathsQuery.TreeVersion> discoverCurrentScopeTrees(
            Long tenantId, Long subjectUserId, String actionCode) {
        LocalDateTime effectiveAt = LocalDateTime.now();
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectAuthorizedByUser(
                        new ActiveProjectMemberQuery(tenantId, subjectUserId, effectiveAt)).stream()
                .filter(item -> allows(item.getMemberRole(), actionCode)).toList();
        Set<Long> anchors = assignments.stream().map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));
        anchors.addAll(organizationScope.projectIds(new ProjectOrganizationScopeService.Query(
                tenantId, subjectUserId, actionCode, false, null)));
        List<ProjectMasterDO> created = ACTION_VIEW.equals(actionCode)
                ? projectMapper.selectListCreatedBy(new CreatedProjectScopeQuery(
                        tenantId, String.valueOf(subjectUserId), null)) : List.of();
        created.stream().map(ProjectMasterDO::getId).forEach(anchors::add);
        String primaryGrantAction = ACTION_EDIT.equals(actionCode) ? ACTION_MANAGE : actionCode;
        discoverGrantAnchors(tenantId, subjectUserId, primaryGrantAction, effectiveAt, anchors);
        if (ACTION_VIEW.equals(actionCode)) {
            discoverGrantAnchors(tenantId, subjectUserId, ACTION_MANAGE, effectiveAt, anchors);
        }
        if (anchors.isEmpty()) return List.of();
        Set<Long> rootIds = projectMapper.selectBatchIds(anchors).stream()
                .filter(project -> Objects.equals(project.getTenantId(), tenantId))
                .map(project -> project.getRootId() == null ? project.getId() : project.getRootId())
                .collect(Collectors.toSet());
        if (rootIds.isEmpty()) return List.of();
        List<ProjectTreeVersionDO> discoveredVersions = versionMapper.selectLatestActiveBatch(
                new ActiveProjectTreeVersionsQuery(tenantId, rootIds));
        if (discoveredVersions.isEmpty()) return List.of();
        Set<Long> versionedRoots = discoveredVersions.stream().map(ProjectTreeVersionDO::getRootProjectId)
                .collect(Collectors.toSet());
        Map<Long, ProjectMasterDO> roots = projectMapper.selectBatchIds(versionedRoots).stream()
                .collect(Collectors.toMap(ProjectMasterDO::getId, java.util.function.Function.identity()));
        Map<Long, Long> expectedVersions = new HashMap<>();
        for (ProjectTreeVersionDO version : discoveredVersions) {
            validate(new ProjectScopeQuery(tenantId, subjectUserId, version.getRootProjectId(),
                    actionCode, version.getTreeVersion()));
            ProjectMasterDO root = roots.get(version.getRootProjectId());
            if (root == null || !Objects.equals(root.getTenantId(), tenantId)) {
                throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
            }
            Long actualRoot = root.getRootId() == null ? root.getId() : root.getRootId();
            Long previous = expectedVersions.put(actualRoot, version.getTreeVersion());
            if (previous != null && !Objects.equals(previous, version.getTreeVersion())) {
                throw exception(PROJECT_TREE_VERSION_CONFLICT);
            }
        }
        // Preserve the discovery -> root validation -> version revalidation boundary, in two batch reads.
        Map<Long, Long> currentVersions = versionMapper.selectLatestActiveBatch(
                        new ActiveProjectTreeVersionsQuery(tenantId, expectedVersions.keySet())).stream()
                .collect(Collectors.toMap(ProjectTreeVersionDO::getRootProjectId, ProjectTreeVersionDO::getTreeVersion));
        if (!expectedVersions.equals(currentVersions)) throw exception(PROJECT_TREE_VERSION_CONFLICT);
        return expectedVersions.entrySet().stream()
                .map(entry -> new ProjectScopeRootPathsQuery.TreeVersion(entry.getKey(), entry.getValue())).toList();
    }

    private Set<Long> resolveDiscoveredTrees(Long tenantId, Long subjectUserId, String actionCode,
                                            List<ProjectScopeRootPathsQuery.TreeVersion> trees) {
        Map<Long, Set<Long>> nodesByRoot = pathMapper.selectScopeRootPaths(
                        new ProjectScopeRootPathsQuery(tenantId, trees)).stream()
                .collect(Collectors.groupingBy(ProjectTreePathDO::getRootProjectId,
                        Collectors.mapping(ProjectTreePathDO::getDescendantProjectId, Collectors.toSet())));
        Set<Long> allNodes = nodesByRoot.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (allNodes.isEmpty()) return Set.of();
        if (hasTenantProjectManagementScope(tenantId, subjectUserId)) return Set.copyOf(allNodes);
        // Discovery is not authorization: re-read current membership / creator facts for all trees together.
        LocalDateTime resolutionAt = LocalDateTime.now();
        String primaryGrantAction = ACTION_EDIT.equals(actionCode) ? ACTION_MANAGE : actionCode;
        Set<Long> full = memberMapper.selectAuthorizedByUser(
                        new ActiveProjectMemberQuery(tenantId, subjectUserId, resolutionAt)).stream()
                .filter(item -> allows(item.getMemberRole(), actionCode))
                .map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));
        full.addAll(organizationScope.projectIds(new ProjectOrganizationScopeService.Query(
                tenantId, subjectUserId, actionCode, false, allNodes)));
        if (ACTION_VIEW.equals(actionCode)) {
            projectMapper.selectListCreatedBy(new CreatedProjectScopeQuery(
                            tenantId, String.valueOf(subjectUserId), allNodes)).stream()
                    .map(ProjectMasterDO::getId).forEach(full::add);
        }
        List<AuthorizationGrantDTO> grants = new ArrayList<>(authorizationGrantApi.listEffective(
                new AuthorizationGrantQuery(tenantId, "USER", subjectUserId, "PROJ", "PROJECT",
                        allNodes, primaryGrantAction, resolutionAt)));
        if (ACTION_VIEW.equals(actionCode)) {
            grants.addAll(authorizationGrantApi.listEffective(new AuthorizationGrantQuery(
                    tenantId, "USER", subjectUserId, "PROJ", "PROJECT", allNodes, ACTION_MANAGE, resolutionAt)));
        }
        Set<Long> descendantAnchors = new HashSet<>();
        for (AuthorizationGrantDTO grant : grants) {
            if (SCOPE_CURRENT.equals(grant.scopeCode())) full.add(grant.resourceId());
            else if (SCOPE_DESCENDANTS.equals(grant.scopeCode())) descendantAnchors.add(grant.resourceId());
        }
        List<ProjectScopeDescendantsQuery.Subtree> subtrees = new ArrayList<>();
        for (ProjectScopeRootPathsQuery.TreeVersion tree : trees) {
            Set<Long> treeAnchors = new HashSet<>(descendantAnchors);
            treeAnchors.retainAll(nodesByRoot.getOrDefault(tree.rootProjectId(), Set.of()));
            if (!treeAnchors.isEmpty()) subtrees.add(new ProjectScopeDescendantsQuery.Subtree(
                    tree.rootProjectId(), tree.treeVersion(), Set.copyOf(treeAnchors)));
        }
        if (!subtrees.isEmpty()) {
            pathMapper.selectScopeDescendants(new ProjectScopeDescendantsQuery(tenantId, subtrees)).stream()
                    .filter(path -> nodesByRoot.getOrDefault(path.getRootProjectId(), Set.of())
                            .contains(path.getDescendantProjectId()))
                    .map(ProjectTreePathDO::getDescendantProjectId).forEach(full::add);
        }
        full.retainAll(allNodes);
        return Set.copyOf(full);
    }

    public void assertFullAccess(ProjectScopeQuery query) {
        ProjectTreeScope scope = resolve(query);
        if (scope.visibility(query.anchorProjectId()) != Visibility.FULL) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private List<AuthorizationGrantDTO> listEffectiveGrants(
            ProjectScopeQuery query, Set<Long> rootNodes, LocalDateTime effectiveAt) {
        String primaryGrantAction = ACTION_EDIT.equals(query.actionCode())
                ? ACTION_MANAGE : query.actionCode();
        List<AuthorizationGrantDTO> grants = new ArrayList<>(authorizationGrantApi.listEffective(
                authorizationQuery(query, rootNodes, primaryGrantAction, effectiveAt)));
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

    private Long officeScope(Long tenantId, Long userId, String actionCode) {
        var scope = OfficeProjectReadContext.current();
        return scope != null && ACTION_VIEW.equals(actionCode) && Objects.equals(scope.tenantId(), tenantId)
                && Objects.equals(scope.userId(), userId) ? scope.projectId() : null;
    }

    private boolean allows(String memberRole, String actionCode) {
        return ACTION_VIEW.equals(actionCode) || ACTION_EDIT.equals(actionCode)
                || MANAGER_ROLES.contains(memberRole);
    }

    /** 项目范围仅扩展到当前租户；功能权限和对象状态由调用方继续校验。 */
    private boolean hasTenantProjectManagementScope(Long tenantId, Long userId) {
        return isTenantSuperAdmin(tenantId, userId)
                || tenantId != null && userId != null && userId > 0
                && Objects.equals(tenantId, TenantContextHolder.getTenantId())
                && permissionApi.hasAnyRoles(userId, "engineeManager", "engineeManagerLeader", "projectAdmin");
    }

    /** 当前租户的角色身份；消费者仍须独立校验对象、范围及业务状态。 */
    public boolean isTenantSuperAdmin(Long tenantId, Long userId) {
        return tenantId != null && userId != null && userId > 0
                && Objects.equals(tenantId, TenantContextHolder.getTenantId())
                && permissionApi.hasAnyRoles(userId, cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode());
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

    private void validateCurrent(ProjectCurrentScopeQuery query) {
        if (query == null) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        validateActorAction(query.tenantId(), query.subjectUserId(), query.actionCode());
        if (query.anchorProjectId() == null || query.anchorProjectId() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void validateRevalidation(ProjectScopeRevalidationQuery query) {
        if (query == null || query.expectedScopeVersion() == null
                || query.expectedScopeVersion() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        validateActorAction(query.tenantId(), query.subjectUserId(), query.actionCode());
        if (query.anchorProjectId() == null || query.anchorProjectId() <= 0) {
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
