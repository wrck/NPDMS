package cn.iocoder.yudao.module.pms.project.api.scope.dto;

/** 锁住项目所属根树的当前版本，并返回锁定后的范围事实供调用方比较。 */
public record ProjectScopeRevalidationQuery(
        Long tenantId,
        Long subjectUserId,
        Long anchorProjectId,
        String actionCode,
        Long expectedScopeVersion) {
}
