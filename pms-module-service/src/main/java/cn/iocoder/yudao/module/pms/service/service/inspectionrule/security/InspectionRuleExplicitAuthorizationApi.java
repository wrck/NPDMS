package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

public interface InspectionRuleExplicitAuthorizationApi {

    ExplicitAuthorization findExplicitAuthorization(
            Long tenantId,
            Long actorId,
            String permissionCode);

    record ExplicitAuthorization(
            Long tenantId,
            Long actorId,
            String permissionCode,
            String authorizationType,
            String authorizationSourceId) {
    }
}
