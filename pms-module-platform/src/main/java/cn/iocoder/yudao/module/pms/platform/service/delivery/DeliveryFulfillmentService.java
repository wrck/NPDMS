package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryFulfillmentDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryFulfillmentMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementMaterialQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryFulfillmentIdentityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.Set;

/** 调用方先锁定要求并验证来源证据；本服务不推导同项目材料的自动适用性。 */
@Service
@RequiredArgsConstructor
public class DeliveryFulfillmentService {
    private final DeliveryFulfillmentMapper mapper;
    private final DeliveryMaterialMapper materials;

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void associate(DeliveryRequirementDO requirement, DeliveryMaterialDO material) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (requirement == null || material == null) throw new BusinessContractException(
                "DELIVERY_MATERIAL_OWNER_MISMATCH", "要求或材料不存在于当前租户");
        if (!tenantId.equals(requirement.getTenantId()) || !tenantId.equals(material.getTenantId())
                || requirement.getProjectId() == null || !requirement.getProjectId().equals(material.getProjectId())
                || !DeliveryMaterialDO.STATUS_ACTIVE.equals(material.getStatus())) {
            throw new BusinessContractException("DELIVERY_MATERIAL_OWNER_MISMATCH", "只能关联本项目有效材料");
        }
        // A caller may hold an older ACTIVE object while public withdrawal has already committed.
        // Serialize both commands on the material row and validate its current tenant/project/status.
        var locked = materials.selectMaterialsForUpdate(new DeliveryMaterialIdLockQuery(tenantId, java.util.List.of(material.getId())));
        if (locked.size() != 1 || !tenantId.equals(locked.getFirst().getTenantId())
                || !requirement.getProjectId().equals(locked.getFirst().getProjectId())
                || !DeliveryMaterialDO.STATUS_ACTIVE.equals(locked.getFirst().getStatus())) {
            throw new BusinessContractException("DELIVERY_MATERIAL_OWNER_MISMATCH", "当前材料已失效或归属已变化");
        }
        DeliveryFulfillmentDO existing = mapper.selectIdentity(new DeliveryFulfillmentIdentityQuery(TenantContextHolder.getRequiredTenantId(), requirement.getId(), material.getId()));
        if (existing != null) {
            if (!DeliveryFulfillmentDO.ACTIVE.equals(existing.getStatus())) {
                existing.setStatus(DeliveryFulfillmentDO.ACTIVE);
                mapper.updateById(existing);
            }
            return;
        }
        DeliveryFulfillmentDO row = new DeliveryFulfillmentDO();
        row.setTenantId(tenantId);
        row.setRequirementId(requirement.getId());
        row.setMaterialId(material.getId());
        row.setStatus(DeliveryFulfillmentDO.ACTIVE);
        try { mapper.insert(row); }
        catch (DuplicateKeyException conflict) {
            // 要求锁已串行化正常调用；唯一键仍拒绝未遵循锁序的竞争写入。
            throw new BusinessContractException("DELIVERY_FULFILLMENT_CONFLICT", "材料使用关系并发变化，请重试");
        }
    }

    public void retainOnly(Long requirementId, Set<Long> selected) {
        for (DeliveryFulfillmentDO row : mapper.selectListForRequirement(
                new DeliveryRequirementMaterialQuery(TenantContextHolder.getRequiredTenantId(), requirementId))) {
            if (DeliveryFulfillmentDO.ACTIVE.equals(row.getStatus()) && !selected.contains(row.getMaterialId())) {
                row.setStatus(DeliveryFulfillmentDO.WITHDRAWN);
                mapper.updateById(row);
            }
        }
    }

    public void withdraw(Long requirementId, Long materialId) {
        DeliveryFulfillmentDO row = mapper.selectIdentity(new DeliveryFulfillmentIdentityQuery(TenantContextHolder.getRequiredTenantId(), requirementId, materialId));
        if (row != null && DeliveryFulfillmentDO.ACTIVE.equals(row.getStatus())) {
            row.setStatus(DeliveryFulfillmentDO.WITHDRAWN);
            mapper.updateById(row);
        }
    }
}
