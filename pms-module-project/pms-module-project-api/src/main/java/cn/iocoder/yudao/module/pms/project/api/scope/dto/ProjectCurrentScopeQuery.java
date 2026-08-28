package cn.iocoder.yudao.module.pms.project.api.scope.dto;

/** 按当前生效项目树解析访问范围，不接受调用方自报树版本。 */
public record ProjectCurrentScopeQuery(
        Long tenantId,
        Long subjectUserId,
        Long anchorProjectId,
        String actionCode) {
}
