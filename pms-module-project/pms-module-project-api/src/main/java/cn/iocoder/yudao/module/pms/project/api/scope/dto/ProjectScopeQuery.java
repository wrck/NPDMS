package cn.iocoder.yudao.module.pms.project.api.scope.dto;

public record ProjectScopeQuery(
        Long tenantId,
        Long subjectUserId,
        Long anchorProjectId,
        String actionCode,
        Long expectedTreeVersion) {
}
