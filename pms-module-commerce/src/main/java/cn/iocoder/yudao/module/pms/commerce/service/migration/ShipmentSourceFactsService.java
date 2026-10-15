package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentSourceFactsMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ShipmentSourceFactsService implements ShipmentSourceFactsApi {
    private final ShipmentSourceFactsMapper mapper;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    @Override @Transactional(readOnly=true)
    public List<Fact> read(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.packageSourceKeys()==null
                || query.packageSourceKeys().size()>500 || query.packageSourceKeys().stream().anyMatch(s->s==null||s.isBlank()))
            throw new IllegalArgumentException("SHIPMENT_SOURCE_FACTS_INVALID");
        users.validateUser(query.actorId());
        if(!permissions.hasAnyPermissions(query.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        if(query.packageSourceKeys().isEmpty())return List.of();
        var facts=mapper.selectFacts(new ShipmentSourceFactsMapper.Query(query.tenantId(),query.packageSourceKeys()));
        var seen=new HashSet<String>();
        for(var fact:facts)if(!query.packageSourceKeys().contains(fact.packageSourceKey())||!seen.add(fact.packageSourceKey()))
            throw new IllegalArgumentException("SHIPMENT_SOURCE_FACTS_AMBIGUOUS");
        return List.copyOf(facts);
    }
}
