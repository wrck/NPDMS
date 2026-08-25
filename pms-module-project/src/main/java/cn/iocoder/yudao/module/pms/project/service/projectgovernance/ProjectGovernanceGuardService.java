package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_GUARD_TOKEN_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class ProjectGovernanceGuardService {

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectGovernanceProviderRegistry providerRegistry;
    private final ProjectGovernanceGuardTokenService tokenService;

    public ProjectGovernanceGuardResult evaluate(Long projectId, GovernanceAction action, Actor actor) {
        validate(projectId, action, actor);
        GuardContext context = loadContext(projectId, actor.tenantId());
        LocalDateTime checkedAt = LocalDateTime.now();
        ProjectGovernanceGuardQuery query = new ProjectGovernanceGuardQuery(
                actor.tenantId(), context.projectIds(), action.name(), checkedAt);
        List<ProjectGovernanceProviderFact> facts = providerRegistry.inspectAll(query);
        List<ProjectGovernanceGuardResult.ProviderVersion> versions = facts.stream()
                .map(ProjectGovernanceGuardService::version).toList();
        List<ProjectGovernanceBlocker> blockers = facts.stream()
                .flatMap(fact -> fact.blockers().stream())
                .sorted(Comparator.comparing(ProjectGovernanceBlocker::objectType)
                        .thenComparing(ProjectGovernanceBlocker::objectId)
                        .thenComparing(ProjectGovernanceBlocker::code)).toList();
        boolean allowed = blockers.isEmpty();
        ProjectGovernanceGuardTokenService.GuardClaims claims = new ProjectGovernanceGuardTokenService.GuardClaims(
                actor.tenantId(), projectId, action.name(), context.project().getVersion(),
                context.rootProjectId(), context.treeVersion(), versions, checkedAt);
        String token = allowed ? tokenService.issue(claims) : null;
        return new ProjectGovernanceGuardResult(projectId, context.project().getVersion(),
                context.rootProjectId(), context.treeVersion(), action.name(), allowed,
                token, versions, blockers, checkedAt);
    }

    public VerifiedGuard verifyAndRevalidate(String guardToken, Long projectId,
                                             GovernanceAction action, Integer expectedProjectVersion,
                                             Actor actor) {
        validate(projectId, action, actor);
        if (expectedProjectVersion == null || expectedProjectVersion < 0) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        ProjectGovernanceGuardTokenService.GuardClaims frozen;
        try {
            frozen = tokenService.verify(guardToken);
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_GOVERNANCE_GUARD_TOKEN_INVALID);
        }
        if (!Objects.equals(frozen.tenantId(), actor.tenantId())
                || !Objects.equals(frozen.projectId(), projectId)
                || !Objects.equals(frozen.action(), action.name())
                || !Objects.equals(frozen.projectVersion(), expectedProjectVersion)) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        ProjectGovernanceGuardResult latest = evaluate(projectId, action, actor);
        if (!latest.allowed()
                || !Objects.equals(latest.projectVersion(), frozen.projectVersion())
                || !Objects.equals(latest.treeRootProjectId(), frozen.treeRootProjectId())
                || !Objects.equals(latest.treeVersion(), frozen.treeVersion())
                || !Objects.equals(latest.providerFacts(), frozen.providerFacts())) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        return new VerifiedGuard(frozen, latest);
    }

    private GuardContext loadContext(Long projectId, Long tenantId) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO tree = treeVersionMapper.selectLatestActive(rootId);
        if (tree == null || tree.getTreeVersion() == null || tree.getTreeVersion() <= 0
                || !Objects.equals(tree.getTenantId(), tenantId)) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        List<ProjectTreePathDO> paths = pathMapper.selectByAncestor(
                rootId, tree.getTreeVersion(), projectId, null);
        if (paths.stream().anyMatch(path -> !validPath(path, tenantId, rootId,
                tree.getTreeVersion(), projectId))) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        Set<Long> projectIds = new LinkedHashSet<>();
        paths.stream().sorted(Comparator.comparing(ProjectTreePathDO::getDistance)
                        .thenComparing(ProjectTreePathDO::getDescendantProjectId))
                .map(ProjectTreePathDO::getDescendantProjectId).forEach(projectIds::add);
        if (!projectIds.contains(projectId)) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        return new GuardContext(project, rootId, tree.getTreeVersion(), Set.copyOf(projectIds));
    }

    private static boolean validPath(ProjectTreePathDO path, Long tenantId, Long rootId,
                                     Long treeVersion, Long projectId) {
        return Objects.equals(path.getTenantId(), tenantId)
                && Objects.equals(path.getRootProjectId(), rootId)
                && Objects.equals(path.getTreeVersion(), treeVersion)
                && Objects.equals(path.getAncestorProjectId(), projectId)
                && path.getDescendantProjectId() != null
                && path.getDistance() != null && path.getDistance() >= 0;
    }

    private static ProjectGovernanceGuardResult.ProviderVersion version(ProjectGovernanceProviderFact fact) {
        return new ProjectGovernanceGuardResult.ProviderVersion(
                fact.provider(), fact.factVersion(), fact.watermark(), fact.factDigest());
    }

    private static void validate(Long projectId, GovernanceAction action, Actor actor) {
        Long trustedTenantId = TenantContextHolder.getRequiredTenantId();
        if (projectId == null || projectId <= 0 || action == null || actor == null
                || actor.tenantId() == null || actor.actorId() == null
                || !Objects.equals(actor.tenantId(), trustedTenantId)) {
            throw new IllegalArgumentException("invalid project governance guard request");
        }
    }

    public enum GovernanceAction {
        ROLLBACK, EXCEPTION_CLOSE, REOPEN
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }

    public record VerifiedGuard(ProjectGovernanceGuardTokenService.GuardClaims claims,
                                ProjectGovernanceGuardResult latest) {
    }

    private record GuardContext(ProjectMasterDO project, Long rootProjectId,
                                Long treeVersion, Set<Long> projectIds) {
    }
}
