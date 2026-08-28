package cn.iocoder.yudao.module.pms.project.service.projectgovernance.provider;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectTreeGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "PROJECT_TREE";

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreePathMapper treePathMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query) {
        validateTenant(query);
        if (query.projectIds().isEmpty()) {
            return buildFact(List.of(), List.of(), List.of("EMPTY"));
        }

        List<ProjectMasterDO> loadedProjects = projectMapper.selectBatchIds(query.projectIds());
        if (loadedProjects.stream().anyMatch(project -> !Objects.equals(project.getTenantId(), query.tenantId())
                || !query.projectIds().contains(project.getId()))) {
            throw new IllegalStateException("project tree guard query returned out-of-scope fact");
        }
        Map<Long, ProjectMasterDO> projects = loadedProjects.stream()
                .collect(Collectors.toMap(ProjectMasterDO::getId, Function.identity()));
        List<ProjectGovernanceBlocker> blockers = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        query.projectIds().stream().filter(id -> !projects.containsKey(id)).forEach(id -> {
            facts.add("MISSING|" + id);
            blockers.add(blocker("PROJECT", id, "UNKNOWN", "PROJECT_NOT_FOUND", "项目节点不可用"));
        });

        Map<Long, List<ProjectMasterDO>> projectsByRoot = projects.values().stream()
                .collect(Collectors.groupingBy(ProjectTreeGovernanceGuardProvider::effectiveRoot,
                        TreeMap::new, Collectors.toList()));
        if (projectsByRoot.size() > 1) {
            String roots = projectsByRoot.keySet().stream().map(String::valueOf)
                    .collect(Collectors.joining(","));
            facts.add("ROOT_MISMATCH|" + roots);
            projects.values().forEach(project -> blockers.add(blocker("PROJECT", project.getId(),
                    lifecycle(project), "PROJECT_TREE_ROOT_MISMATCH", summary(project))));
        }
        List<String> versions = new ArrayList<>();
        for (Map.Entry<Long, List<ProjectMasterDO>> entry : projectsByRoot.entrySet()) {
            Set<Long> requestedIds = entry.getValue().stream().map(ProjectMasterDO::getId)
                    .collect(Collectors.toSet());
            inspectTree(query.tenantId(), entry.getKey(), requestedIds, entry.getValue(),
                    facts, blockers, versions);
        }
        return buildFact(facts, blockers, versions);
    }

    private void inspectTree(Long tenantId, Long rootId, Set<Long> requestedIds,
                             List<ProjectMasterDO> projects, List<String> facts,
                             List<ProjectGovernanceBlocker> blockers, List<String> versions) {
        projects.sort(Comparator.comparing(ProjectMasterDO::getId));
        ProjectMasterDO anchor = findUniqueAnchor(projects);
        if (anchor == null) {
            versions.add(rootId + ":ANCHOR_UNAVAILABLE");
            projects.forEach(project -> blockers.add(blocker("PROJECT", project.getId(), lifecycle(project),
                    "PROJECT_TREE_SCOPE_INCOMPLETE", "项目树范围不完整")));
            return;
        }
        ProjectTreeVersionDO active = treeVersionMapper.selectLatestActive(rootId);
        if (active == null) {
            versions.add(rootId + ":UNAVAILABLE");
            projects.forEach(project -> {
                facts.add(projectFact(project, null, false));
                blockers.add(blocker("PROJECT", project.getId(), lifecycle(project),
                        "PROJECT_TREE_VERSION_UNAVAILABLE", summary(project)));
            });
            return;
        }

        versions.add(rootId + ":" + active.getTreeVersion());
        List<ProjectTreePathDO> rootPaths = treePathMapper.selectByAncestor(
                rootId, active.getTreeVersion(), rootId, null);
        Set<Long> treeProjectIds = rootPaths.stream()
                .filter(path -> isSubtreePath(path, rootId, active.getTreeVersion(), rootId))
                .map(ProjectTreePathDO::getDescendantProjectId).collect(Collectors.toCollection(HashSet::new));
        List<ProjectTreePathDO> completeTreePaths = treePathMapper.selectByDescendants(
                rootId, active.getTreeVersion(), treeProjectIds);
        if (rootPaths.stream().anyMatch(path -> !Objects.equals(path.getTenantId(), tenantId))
                || completeTreePaths.stream().anyMatch(path -> !Objects.equals(path.getTenantId(), tenantId))) {
            throw new IllegalStateException("project tree guard query returned cross-tenant path fact");
        }
        Set<Long> treeSelfPaths = completeTreePaths.stream()
                .filter(path -> isSelfPath(path, rootId, active.getTreeVersion()))
                .map(ProjectTreePathDO::getDescendantProjectId).collect(Collectors.toCollection(HashSet::new));
        boolean treeComplete = Objects.equals(active.getNodeCount(), treeProjectIds.size())
                && Objects.equals(active.getPathCount(), completeTreePaths.size())
                && treeSelfPaths.equals(treeProjectIds);
        facts.add("TREE_COMPLETENESS|" + treeProjectIds.size() + "|" + completeTreePaths.size()
                + "|" + treeComplete);
        if (!treeComplete) {
            blockers.add(blocker("PROJECT", anchor.getId(), lifecycle(anchor),
                    "PROJECT_TREE_INCOMPLETE", "项目树投影不完整"));
        }

        Set<Long> completeProjectIds = completeTreePaths.stream()
                .filter(path -> isSubtreePath(path, rootId, active.getTreeVersion(), anchor.getId()))
                .map(ProjectTreePathDO::getDescendantProjectId).collect(Collectors.toCollection(HashSet::new));
        boolean scopeMatches = completeProjectIds.equals(requestedIds);
        facts.add("SCOPE|" + anchor.getId() + "|requested=" + requestedIds.stream().sorted()
                .map(String::valueOf).collect(Collectors.joining(",")) + "|complete="
                + completeProjectIds.stream().sorted().map(String::valueOf)
                .collect(Collectors.joining(",")) + "|matches=" + scopeMatches);
        if (!scopeMatches) {
            blockers.add(blocker("PROJECT", anchor.getId(), lifecycle(anchor),
                    "PROJECT_TREE_SCOPE_INCOMPLETE", "项目树范围不完整"));
        }
        if (completeProjectIds.isEmpty()) {
            facts.add(versionFact(active));
            return;
        }

        List<ProjectMasterDO> completeProjects = projectMapper.selectBatchIds(completeProjectIds);
        if (completeProjects.stream().anyMatch(project -> !Objects.equals(project.getTenantId(), tenantId)
                || !completeProjectIds.contains(project.getId()))) {
            throw new IllegalStateException("project tree guard query returned out-of-scope fact");
        }
        Map<Long, ProjectMasterDO> completeProjectMap = completeProjects.stream()
                .collect(Collectors.toMap(ProjectMasterDO::getId, Function.identity()));
        completeProjectIds.stream().filter(id -> !completeProjectMap.containsKey(id)).forEach(id -> {
            facts.add("MISSING|" + id);
            blockers.add(blocker("PROJECT", id, "UNKNOWN", "PROJECT_NOT_FOUND", "项目节点不可用"));
        });
        facts.add(versionFact(active));
        for (ProjectMasterDO project : completeProjects.stream()
                .sorted(Comparator.comparing(ProjectMasterDO::getId)).toList()) {
            boolean complete = treeSelfPaths.contains(project.getId());
            facts.add(projectFact(project, active.getTreeVersion(), complete));
            if (!complete) {
                blockers.add(blocker("PROJECT", project.getId(), lifecycle(project),
                        "PROJECT_TREE_INCOMPLETE", summary(project)));
            } else if (!Objects.equals(project.getId(), anchor.getId())
                    && "ACTIVE".equals(project.getLifecycleStatus())) {
                blockers.add(blocker("PROJECT", project.getId(), "ACTIVE",
                        "ACTIVE_DESCENDANT", summary(project)));
            } else if (!Objects.equals(project.getId(), anchor.getId())
                    && !isClosed(project.getLifecycleStatus())) {
                blockers.add(blocker("PROJECT", project.getId(), lifecycle(project),
                        "PROJECT_LIFECYCLE_UNKNOWN", summary(project)));
            }
        }
    }

    private static ProjectMasterDO findUniqueAnchor(List<ProjectMasterDO> projects) {
        Integer minimumDepth = projects.stream().map(ProjectMasterDO::getTreeDepth)
                .filter(Objects::nonNull).min(Integer::compareTo).orElse(null);
        if (minimumDepth == null) {
            return null;
        }
        List<ProjectMasterDO> candidates = projects.stream()
                .filter(project -> Objects.equals(project.getTreeDepth(), minimumDepth)).toList();
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static boolean isSubtreePath(ProjectTreePathDO path, Long rootId, Long treeVersion,
                                         Long ancestorId) {
        return Objects.equals(path.getRootProjectId(), rootId)
                && Objects.equals(path.getTreeVersion(), treeVersion)
                && Objects.equals(path.getAncestorProjectId(), ancestorId)
                && path.getDistance() != null && path.getDistance() >= 0;
    }

    private static boolean isSelfPath(ProjectTreePathDO path, Long rootId, Long treeVersion) {
        return Objects.equals(path.getRootProjectId(), rootId)
                && Objects.equals(path.getTreeVersion(), treeVersion)
                && Objects.equals(path.getAncestorProjectId(), path.getDescendantProjectId())
                && Objects.equals(path.getDistance(), 0);
    }

    private static boolean isClosed(String lifecycleStatus) {
        return "NORMAL_CLOSED".equals(lifecycleStatus) || "EXCEPTION_CLOSED".equals(lifecycleStatus);
    }

    private static Long effectiveRoot(ProjectMasterDO project) {
        return project.getRootId() == null ? project.getId() : project.getRootId();
    }

    private static String projectFact(ProjectMasterDO project, Long treeVersion, boolean complete) {
        return "PROJECT|" + value(project.getId()) + "|" + value(effectiveRoot(project)) + "|"
                + value(treeVersion) + "|" + lifecycle(project) + "|" + value(project.getVersion()) + "|"
                + value(project.getUpdateTime()) + "|" + complete;
    }

    private static String versionFact(ProjectTreeVersionDO version) {
        return "TREE|" + value(version.getRootProjectId()) + "|" + value(version.getTreeVersion()) + "|"
                + value(version.getNodeCount()) + "|" + value(version.getPathCount()) + "|"
                + value(version.getVersion()) + "|" + value(version.getActivatedAt()) + "|"
                + value(version.getUpdateTime());
    }

    private static ProjectGovernanceProviderFact buildFact(List<String> facts,
                                                            List<ProjectGovernanceBlocker> blockers,
                                                            List<String> versions) {
        List<String> orderedFacts = facts.stream().sorted().toList();
        List<ProjectGovernanceBlocker> orderedBlockers = blockers.stream()
                .sorted(Comparator.comparing(ProjectGovernanceBlocker::objectId)
                        .thenComparing(ProjectGovernanceBlocker::code)).toList();
        String factVersion = String.join(",", versions.stream().sorted().toList());
        String watermark = orderedFacts.isEmpty() ? "EMPTY" : ProjectGovernanceFactDigest.digest(orderedFacts);
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, factVersion, watermark,
                ProjectGovernanceFactDigest.digest(orderedFacts), orderedBlockers);
    }

    private static ProjectGovernanceBlocker blocker(String objectType, Long objectId, String status,
                                                     String code, String summary) {
        return new ProjectGovernanceBlocker(objectType, String.valueOf(objectId), status, code, summary);
    }

    private static String lifecycle(ProjectMasterDO project) {
        return project.getLifecycleStatus() == null || project.getLifecycleStatus().isBlank()
                ? "UNKNOWN" : project.getLifecycleStatus();
    }

    private static String summary(ProjectMasterDO project) {
        return "项目节点阻断";
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
