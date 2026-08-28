package cn.iocoder.yudao.module.pms.project.api.scope.dto;

/** 无锚点解析主体在租户内可完整访问的全部项目。 */
public record ProjectAllScopeQuery(
        Long tenantId,
        Long subjectUserId,
        String actionCode) {
}
