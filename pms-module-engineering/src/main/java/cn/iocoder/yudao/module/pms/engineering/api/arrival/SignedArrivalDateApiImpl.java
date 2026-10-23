package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.LatestSignedArrivalQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SignedArrivalDateApiImpl implements SignedArrivalDateApi {
    private final ArrivalMapper arrivals;
    @Override public LocalDate latest(Query query) {
        if (query == null || query.tenantId() == null || query.projectId() == null || query.projectId() <= 0
                || TenantContextHolder.isIgnore() || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("SIGNED_ARRIVAL_PROJECT_SCOPE_INVALID");
        var time = arrivals.selectLatestSignedArrivalTime(new LatestSignedArrivalQuery(query.tenantId(),query.projectId()));
        return time == null ? null : time.toLocalDate();
    }
}
