package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import java.time.LocalDate;

/** Trusted internal read. The consumer authorizes project visibility; IMP enforces tenant/project/source identity. */
public interface SignedArrivalDateApi {
    LocalDate latest(Query query);
    record Query(Long tenantId, Long projectId) { }
}
