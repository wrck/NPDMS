package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

public record RequirementAnalysisFactQuery(Long projectId, Long preparationId) {
    public RequirementAnalysisFactQuery {
        if (projectId == null || projectId <= 0 || (preparationId != null && preparationId <= 0)) {
            throw new IllegalArgumentException("invalid requirement analysis fact query");
        }
    }
}
