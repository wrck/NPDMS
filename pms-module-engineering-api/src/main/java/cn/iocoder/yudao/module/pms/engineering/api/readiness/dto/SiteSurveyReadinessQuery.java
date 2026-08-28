package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

public record SiteSurveyReadinessQuery(Long projectId, Long preparationId) {
    public SiteSurveyReadinessQuery {
        if (projectId == null || projectId <= 0 || preparationId != null && preparationId <= 0) {
            throw new IllegalArgumentException("invalid site survey readiness query");
        }
    }
}
