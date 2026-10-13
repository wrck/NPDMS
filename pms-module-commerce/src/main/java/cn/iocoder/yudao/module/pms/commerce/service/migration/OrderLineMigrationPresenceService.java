package cn.iocoder.yudao.module.pms.commerce.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.OrderLineMigrationPresenceApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class OrderLineMigrationPresenceService implements OrderLineMigrationPresenceApi {
    private final SalesOrderLineMapper mapper;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    @Override public Set<Long> existingTargets(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId()) || query.actorId()==null
                || query.targetIds()==null || query.targetIds().stream().anyMatch(id->id==null || id<=0))
            throw new IllegalArgumentException("ORDER_LINE_MIGRATION_SCOPE_INVALID");
        users.validateUser(query.actorId());
        if(!permissions.hasAnyPermissions(query.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        return query.targetIds().isEmpty()?Set.of():Set.copyOf(mapper.selectMigrationTargetIdsForUpdate(query));
    }
}
