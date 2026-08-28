package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

public record SiteSurveyReadinessRevalidationQuery(
        Long projectId,
        Long preparationId,
        Integer expectedBusinessVersion,
        Integer expectedInputVersion,
        Integer expectedPreparationVersion,
        Integer expectedReadinessVersion,
        Long expectedSnapshotId,
        Long expectedProjectScopeVersion,
        ReadinessFactVector expectedFactVector) {

    public SiteSurveyReadinessRevalidationQuery {
        if (projectId == null || projectId <= 0 || preparationId == null || preparationId <= 0
                || invalid(expectedBusinessVersion) || invalid(expectedInputVersion)
                || invalid(expectedPreparationVersion) || invalid(expectedReadinessVersion)
                || expectedSnapshotId == null || expectedSnapshotId <= 0
                || expectedProjectScopeVersion == null || expectedProjectScopeVersion < 0
                || expectedFactVector == null) {
            throw new IllegalArgumentException("invalid site survey readiness revalidation query");
        }
    }

    private static boolean invalid(Integer value) {
        return value == null || value < 0;
    }
}
