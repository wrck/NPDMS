package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.LocationCodeSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LocationCodeService {
    private final LocationCodeSequenceMapper sequenceMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String prefix) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        sequenceMapper.initialize(tenantId, prefix);
        Long value = sequenceMapper.lockValue(tenantId, prefix);
        sequenceMapper.advance(tenantId, prefix);
        return prefix + String.format(Locale.ROOT, "%04d", value);
    }
}
