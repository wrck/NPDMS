package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDetailDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectprogress.ProjectProgressRules;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PROJECTION_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class ProjectProgressSnapshotService {
    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectProgressPolicyService policyService;
    private final ProjectProgressPolicyItemMapper policyItemMapper;
    private final ProjectProgressFactMapper factMapper;
    private final ProjectProgressSnapshotMapper snapshotMapper;
    private final ProjectProgressSnapshotDetailMapper detailMapper;
    private final ProjectTreeScopeService scopeService;
    private final ProjectProgressMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public ProjectProgressResult calculate(Long projectId, ProjectProgressPolicyService.Actor actor) {
        long started = System.nanoTime();
        ProjectMasterDO parent = projectMapper.selectByIdForUpdate(projectId);
        if (parent == null || !Objects.equals(parent.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
        ProjectTreeVersionDO treeVersion = treeVersionMapper.selectLatestActive(rootId);
        if (treeVersion == null) throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        scopeService.assertFullAccess(actor.actorId(), projectId, treeVersion.getTreeVersion());
        List<ProjectMasterDO> children = projectMapper.selectChildren(projectId);
        if (children.isEmpty()) {
            throw exception(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_POLICY_INVALID,
                    "叶子项目进度应由合法进度事实提供，不生成汇总快照");
        }
        ProjectProgressPolicyRevisionDO policy = policyService.requireActiveOrCreateDefault(projectId, actor);
        List<ProjectProgressPolicyItemDO> policyItems = policyItemMapper.selectByRevisionId(policy.getId());
        List<WorkingItem> working = resolveWorkingItems(parent, treeVersion, children, policyItems);
        String watermark = sourceWatermark(policy.getId(), treeVersion.getTreeVersion(), working);
        ProjectProgressSnapshotDO existing = snapshotMapper.selectByIdentity(
                projectId, policy.getId(), treeVersion.getTreeVersion(), watermark);
        if (existing != null) return toResult(existing, detailMapper.selectBySnapshotId(existing.getId()));

        int missingCount = (int) working.stream().filter(item -> item.missingReason() != null
                && !"EXCLUDED_BY_POLICY".equals(item.missingReason())).count();
        String status = missingCount == 0 ? "READY" : "PENDING";
        BigDecimal progress = "READY".equals(status) ? aggregate(working) : null;
        ProjectProgressSnapshotDO snapshot = new ProjectProgressSnapshotDO();
        snapshot.setProjectId(projectId);
        snapshot.setPolicyRevisionId(policy.getId());
        snapshot.setTreeVersion(treeVersion.getTreeVersion());
        snapshot.setSourceWatermark(watermark);
        snapshot.setSnapshotStatus(status);
        snapshot.setProgress(progress);
        snapshot.setMissingItemCount(missingCount);
        snapshot.setCalculatedAt(LocalDateTime.now());
        snapshot.setVersion(0);
        if (snapshotMapper.insert(snapshot) != 1) {
            throw new IllegalStateException("项目进度快照写入失败");
        }
        List<ProjectProgressSnapshotDetailDO> details = working.stream().map(item -> toDetail(snapshot.getId(), item)).toList();
        if (!Boolean.TRUE.equals(detailMapper.insertBatch(details))) {
            throw new IllegalStateException("项目进度快照明细写入失败");
        }
        metrics.snapshot(status, missingCount, System.nanoTime() - started);
        return toResult(snapshot, details);
    }

    private List<WorkingItem> resolveWorkingItems(ProjectMasterDO parent, ProjectTreeVersionDO treeVersion,
                                                   List<ProjectMasterDO> children,
                                                   List<ProjectProgressPolicyItemDO> policyItems) {
        Map<Long, ProjectMasterDO> childById = new HashMap<>();
        children.forEach(child -> childById.put(child.getId(), child));
        Map<Long, ProjectProgressPolicyItemDO> policyByChild = new HashMap<>();
        policyItems.forEach(item -> policyByChild.put(item.getChildProjectId(), item));
        Set<Long> allIds = new HashSet<>(childById.keySet());
        allIds.addAll(policyByChild.keySet());
        Set<Long> nonLeafIds = pathMapper.selectParentsWithChildren(
                parent.getRootId() == null ? parent.getId() : parent.getRootId(),
                treeVersion.getTreeVersion(), childById.keySet());
        Set<Long> leafIds = new HashSet<>(childById.keySet());
        leafIds.removeAll(nonLeafIds);
        Map<Long, ProjectProgressFactDO> facts = new HashMap<>();
        if (!leafIds.isEmpty()) factMapper.selectLatestByProjects(parent.getTenantId(), leafIds)
                .forEach(fact -> facts.put(fact.getProjectId(), fact));
        Map<Long, ProjectProgressSnapshotDO> snapshots = new HashMap<>();
        if (!nonLeafIds.isEmpty()) snapshotMapper.selectLatestByProjects(parent.getTenantId(), nonLeafIds)
                .forEach(snapshot -> snapshots.put(snapshot.getProjectId(), snapshot));

        List<WorkingItem> result = new ArrayList<>();
        for (Long childId : allIds.stream().sorted().toList()) {
            ProjectMasterDO child = childById.get(childId);
            ProjectProgressPolicyItemDO policy = policyByChild.get(childId);
            if (child == null) {
                result.add(new WorkingItem(childId, null, null, BigDecimal.ZERO, null, "CHILD_NOT_IN_TREE", null));
                continue;
            }
            if (policy == null) {
                result.add(new WorkingItem(childId, null, null, BigDecimal.ZERO, null, "POLICY_ITEM_MISSING", null));
                continue;
            }
            List<String> includeStatuses = JsonUtils.parseArray(policy.getIncludeStatusSnapshot(), String.class);
            if (!includeStatuses.isEmpty() && !includeStatuses.contains(child.getLifecycleStatus())) {
                result.add(new WorkingItem(childId, null, null, BigDecimal.ZERO, null,
                        "EXCLUDED_BY_POLICY", null));
                continue;
            }
            if (nonLeafIds.contains(childId)) {
                ProjectProgressSnapshotDO source = snapshots.get(childId);
                if (source == null || !"READY".equals(source.getSnapshotStatus())) {
                    result.add(new WorkingItem(childId, null, null, policy.getWeight(), null,
                            source == null ? "CHILD_SNAPSHOT_MISSING" : "CHILD_PROGRESS_PENDING",
                            source == null ? null : source.getSourceWatermark()));
                } else {
                    result.add(new WorkingItem(childId, source.getId(), source.getProgress(), policy.getWeight(),
                            null, null, source.getSourceWatermark()));
                }
            } else {
                ProjectProgressFactDO source = facts.get(childId);
                if (source == null) {
                    result.add(new WorkingItem(childId, null, null, policy.getWeight(), null,
                            "PROGRESS_FACT_MISSING", null));
                } else {
                    result.add(new WorkingItem(childId, source.getFactVersion(), source.getProgress(),
                            policy.getWeight(), null, null, source.getSourceWatermark()));
                }
            }
        }
        return normalizeIncludedWeights(result);
    }

    private List<WorkingItem> normalizeIncludedWeights(List<WorkingItem> items) {
        BigDecimal includedWeight = items.stream()
                .filter(item -> !"EXCLUDED_BY_POLICY".equals(item.missingReason()))
                .map(WorkingItem::configuredWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (includedWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return items.stream().map(item -> item.withMissing("POLICY_DENOMINATOR_EMPTY")).toList();
        }
        int includedCount = (int) items.stream()
                .filter(item -> !"EXCLUDED_BY_POLICY".equals(item.missingReason())).count();
        int includedIndex = 0;
        BigDecimal allocated = BigDecimal.ZERO;
        List<WorkingItem> result = new ArrayList<>(items.size());
        for (WorkingItem item : items) {
            if ("EXCLUDED_BY_POLICY".equals(item.missingReason())) {
                result.add(item);
                continue;
            }
            includedIndex++;
            BigDecimal normalized = includedIndex == includedCount
                    ? new BigDecimal("100.0000").subtract(allocated)
                    : item.configuredWeight().multiply(new BigDecimal("100"))
                    .divide(includedWeight, 4, RoundingMode.DOWN);
            allocated = allocated.add(normalized);
            result.add(item.withNormalizedWeight(normalized));
        }
        return List.copyOf(result);
    }

    private BigDecimal aggregate(List<WorkingItem> items) {
        List<WorkingItem> included = items.stream()
                .filter(item -> !"EXCLUDED_BY_POLICY".equals(item.missingReason())).toList();
        return ProjectProgressRules.aggregate(included.stream().map(WorkingItem::progress).toList(),
                included.stream().map(WorkingItem::normalizedWeight).toList());
    }

    private ProjectProgressSnapshotDetailDO toDetail(Long snapshotId, WorkingItem item) {
        ProjectProgressSnapshotDetailDO detail = new ProjectProgressSnapshotDetailDO();
        detail.setSnapshotId(snapshotId);
        detail.setChildProjectId(item.childProjectId());
        detail.setFactVersion(item.factVersion());
        detail.setChildProgress(item.progress());
        detail.setNormalizedWeight(item.normalizedWeight());
        detail.setContribution(item.progress() == null || item.normalizedWeight() == null ? null
                : item.progress().multiply(item.normalizedWeight()).divide(
                        new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        detail.setMissingReason(item.missingReason());
        detail.setVersion(0);
        return detail;
    }

    private ProjectProgressResult toResult(ProjectProgressSnapshotDO snapshot,
                                           List<ProjectProgressSnapshotDetailDO> details) {
        return new ProjectProgressResult(snapshot.getProjectId(), snapshot.getPolicyRevisionId(),
                snapshot.getTreeVersion(), snapshot.getSourceWatermark(), snapshot.getSnapshotStatus(),
                snapshot.getProgress(), details.stream().map(detail -> new ProjectProgressResult.Item(
                        detail.getChildProjectId(), detail.getFactVersion(), detail.getChildProgress(),
                        detail.getNormalizedWeight(), detail.getContribution(), detail.getMissingReason())).toList());
    }

    private String sourceWatermark(Long policyId, Long treeVersion, List<WorkingItem> items) {
        String material = policyId + ":" + treeVersion + ":" + items.stream()
                .sorted(Comparator.comparing(WorkingItem::childProjectId))
                .map(item -> item.childProjectId() + "," + item.factVersion() + "," + item.progress()
                        + "," + item.configuredWeight() + "," + item.missingReason() + "," + item.sourceWatermark())
                .collect(Collectors.joining(";"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record WorkingItem(Long childProjectId, Long factVersion, BigDecimal progress,
                               BigDecimal configuredWeight, BigDecimal normalizedWeight,
                               String missingReason, String sourceWatermark) {
        WorkingItem withNormalizedWeight(BigDecimal value) {
            return new WorkingItem(childProjectId, factVersion, progress, configuredWeight,
                    value, missingReason, sourceWatermark);
        }

        WorkingItem withMissing(String reason) {
            return new WorkingItem(childProjectId, factVersion, progress, configuredWeight,
                    BigDecimal.ZERO, reason, sourceWatermark);
        }
    }
}
