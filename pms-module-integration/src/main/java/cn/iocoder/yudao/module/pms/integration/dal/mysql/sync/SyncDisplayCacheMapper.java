package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Fixed capabilities of the approved INT importer; no caller-supplied SQL identifiers. */
@Mapper
public interface SyncDisplayCacheMapper {
    enum Cache { PROJECT_LEASE, PROJECT_CONFIGURATION, CONTRACT_PLAN }
    record Scope(Long tenantId,Cache cache) {
        public Scope {
            if(tenantId==null || cache==null)
                throw new IllegalArgumentException("缓存替换范围无效");
        }
    }
    int clearCurrent(@Param("query") Scope query);
    int retireBindings(@Param("query") SyncQueries.Task query);
}
