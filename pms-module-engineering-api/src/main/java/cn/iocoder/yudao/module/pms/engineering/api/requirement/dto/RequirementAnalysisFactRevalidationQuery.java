package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

public record RequirementAnalysisFactRevalidationQuery(
        Long projectId,
        Long preparationId,
        Integer expectedBusinessVersion,
        Integer expectedContentVersion,
        Integer expectedProjectVersion,
        Long expectedTemplateRevision,
        RequirementAnalysisFactVector expectedFactVector) {

    public RequirementAnalysisFactRevalidationQuery {
        if (projectId == null || projectId <= 0 || preparationId == null || preparationId <= 0
                || expectedBusinessVersion == null || expectedBusinessVersion <= 0
                || expectedContentVersion == null || expectedContentVersion < 0
                || expectedProjectVersion == null || expectedProjectVersion < 0
                || expectedTemplateRevision == null || expectedTemplateRevision <= 0
                || expectedFactVector == null) {
            throw new IllegalArgumentException("invalid requirement analysis revalidation query");
        }
    }
}
