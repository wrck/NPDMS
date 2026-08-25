package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ProjectGovernanceGuardResult(
        Long projectId,
        Integer projectVersion,
        Long treeRootProjectId,
        Long treeVersion,
        String action,
        boolean allowed,
        String guardToken,
        List<ProviderVersion> providerFacts,
        List<ProjectGovernanceBlocker> blockers,
        LocalDateTime checkedAt) {

    public ProjectGovernanceGuardResult {
        providerFacts = providerFacts == null ? List.of() : providerFacts.stream()
                .sorted(Comparator.comparing(ProviderVersion::provider)).toList();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record ProviderVersion(String provider, String factVersion,
                                  String watermark, String factDigest) {
    }
}
