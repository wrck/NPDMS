package cn.iocoder.yudao.module.system.api.user;

import java.util.Set;

/** Project owns current FULL membership eligibility; SYSTEM never accepts caller-supplied authorization IDs. */
public interface ProjectMemberIdentityScopeProvider {
    Set<Long> resolveCurrent(Query query);
    enum Filter { MANAGER, SERVICE_MANAGER, SALES }
    record Query(Long tenantId, Long actorId, Filter filter) { }
}
