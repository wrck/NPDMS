package cn.iocoder.yudao.module.pms.platform.api.delivery;
/** Template identity presence only: no material, submission or requirement mutation dependencies. */
public interface PlatformDeliveryRequirementIdentityApi {
    boolean containsTemplateIdentity(Long projectId, String deliverableCode);
    boolean lockTemplateIdentity(Long projectId, String deliverableCode);
}
