package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

import java.util.List;

public record SiteSurveyReadinessFact(
        Long projectId,
        Long preparationId,
        Integer businessVersion,
        String status,
        String readinessStatus,
        Long latestSnapshotId,
        Integer snapshotNo,
        Integer inputVersion,
        Integer preparationVersion,
        Integer readinessVersion,
        Long projectScopeVersion,
        Boolean snapshotCurrent,
        List<String> blockerCodes,
        ReadinessFactVector factVector) {

    public SiteSurveyReadinessFact {
        blockerCodes = List.copyOf(blockerCodes == null ? List.of() : blockerCodes);
    }
}
