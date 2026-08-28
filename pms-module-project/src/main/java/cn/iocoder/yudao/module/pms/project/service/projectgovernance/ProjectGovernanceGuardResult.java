package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ProjectGovernanceGuardResult(
        Long projectId,
        Integer projectVersion,
        String lifecycleStatus,
        String currentStage,
        String assignmentStatus,
        Long treeRootProjectId,
        Long treeVersion,
        String action,
        boolean allowed,
        String guardToken,
        List<ProviderVersion> providerFacts,
        List<Blocker> blockers,
        LocalDateTime checkedAt) {

    public ProjectGovernanceGuardResult {
        providerFacts = providerFacts == null ? List.of() : providerFacts.stream()
                .sorted(Comparator.comparing(ProviderVersion::provider)).toList();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record ProviderVersion(String provider, String factVersion,
                                  String watermark, String factDigest) {
    }

    /** 带提供方归属的最小阻断引用，避免HTTP响应丢失物理契约中的provider。 */
    public record Blocker(String provider, String objectType, String objectId,
                          String status, String code, String summary) {
    }
}
