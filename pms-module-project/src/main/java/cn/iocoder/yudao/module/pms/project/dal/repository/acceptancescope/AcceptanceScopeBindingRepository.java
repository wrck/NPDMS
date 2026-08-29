package cn.iocoder.yudao.module.pms.project.dal.repository.acceptancescope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancescope.AcceptanceScopeBindingDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.AcceptanceScopeBindingMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeBindingIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeCurrentQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class AcceptanceScopeBindingRepository {

    private final AcceptanceScopeBindingMapper mapper;

    public AcceptanceScopeBindingDO selectByIdentityForUpdate(AcceptanceScopeBindingIdentityQuery query) {
        requireTrustedTenant(query.tenantId());
        return mapper.selectByIdentityForUpdate(query);
    }

    public List<AcceptanceScopeBindingDO> selectCurrentByScopeForUpdate(AcceptanceScopeCurrentQuery query) {
        requireTrustedTenant(query.tenantId());
        return mapper.selectCurrentByScopeForUpdate(query);
    }

    public int append(AcceptanceScopeBindingDO binding) {
        requireTrustedTenant(binding.getTenantId());
        return mapper.insert(binding);
    }

    private void requireTrustedTenant(Long tenantId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("acceptance scope tenant must match trusted tenant context");
        }
    }
}
