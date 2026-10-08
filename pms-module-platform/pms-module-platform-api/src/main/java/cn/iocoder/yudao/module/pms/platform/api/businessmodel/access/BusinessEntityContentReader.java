package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;

/** Shared bridge for native Owner content, preserving current/revision identity and authorization. */
public interface BusinessEntityContentReader {
    boolean supports(String ownerModule, String entityType);
    BusinessEntityData read(EntityDataRef target, EntityActor actor);

    /** Opt in only when the native reader owns both list scope and field projection. */
    default boolean supportsQueries() { return false; }

    default BusinessEntitySlice query(BusinessEntityPageQuery query, EntityActor actor) {
        throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                "NATIVE_QUERY_UNSUPPORTED", "Native content reader does not provide list queries");
    }
}
