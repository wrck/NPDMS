package cn.iocoder.yudao.module.pms.platform.service.businessmodel;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.BusinessDeletionProtectionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.BusinessDeletionProtectionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class PlatformBusinessDeletionGuard implements BusinessDeletionGuard {
    private final BusinessDeletionProtectionMapper protection;
    public void requireDeletable(EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);
        if (protection.hasProtectedReferences(new BusinessDeletionProtectionQuery(actor.tenantId(), entity.ownerModule(), entity.entityType(), entity.entityId())))
            throw new BusinessContractException("DELETE_REFERENCED_ENTITY", "业务存在交付、归档或审批历史引用，不能删除");
    }
}
