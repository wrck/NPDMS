package cn.iocoder.yudao.module.pms.platform.service.delivery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementIdentityApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class DeliveryRequirementIdentityApiImpl implements PlatformDeliveryRequirementIdentityApi {
    private final DeliveryRequirementMapper requirements;
    @Override public boolean containsTemplateIdentity(Long projectId,String code) {
        return requirements.selectIdentity(new DeliveryRequirementIdentityQuery(
                TenantContextHolder.getRequiredTenantId(),PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE,
                PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE,projectId,code)) != null;
    }
    /** Join the caller transaction and retain the original SELECT FOR UPDATE lock and lock order. */
    @Override @Transactional public boolean lockTemplateIdentity(Long projectId,String code) {
        return requirements.selectIdentityForUpdate(new DeliveryRequirementIdentityLockQuery(
                TenantContextHolder.getRequiredTenantId(),PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE,
                PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE,projectId,code)) != null;
    }
}
