package cn.iocoder.yudao.module.pms.platform.support.persistence;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Set;

/** Controlled persistence for a declared patch: CAS and explicit nulls survive Mapper field strategies. */
public final class DeclaredBusinessEntityWriter {
    private DeclaredBusinessEntityWriter() {
    }

    public static void update(BaseMapper<BaseBusinessEntity> mapper, BaseBusinessEntity entity,
                              Long tenantId, Long expectedVersion, Set<String> clearedFields) {
        if (expectedVersion == null || !expectedVersion.equals(entity.getVersion()))
            throw new BusinessContractException("CONCURRENCY_BASIS_REQUIRED", "保存必须携带已核对的并发依据");
        long nextVersion = Math.incrementExact(expectedVersion);
        UpdateWrapper<BaseBusinessEntity> condition = new UpdateWrapper<>();
        condition.eq("id", entity.getId()).eq("tenant_id", tenantId).eq("version", expectedVersion);
        condition.set("version", nextVersion);
        for (var field : BusinessModelIntrospector.businessFields(entity.getClass())) {
            if (clearedFields.contains(field.code())) condition.set(field.column(), null);
        }
        // The wrapper owns the version assignment/predicate. Suppress the entity version while
        // constructing the Mapper statement so the optional interceptor cannot duplicate either.
        entity.setVersion(null);
        boolean updated = false;
        try {
            if (mapper.update(entity, condition) != 1)
                throw new BusinessContractException("CONCURRENCY_CONFLICT", "并发依据过期: " + entity.getId());
            updated = true;
        } finally {
            entity.setVersion(updated ? nextVersion : expectedVersion);
        }
    }
}
