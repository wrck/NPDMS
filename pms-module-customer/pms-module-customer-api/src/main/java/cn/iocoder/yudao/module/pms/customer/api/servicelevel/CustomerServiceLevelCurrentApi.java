package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

import lombok.Builder;
import java.time.LocalDate;

/** 读取既有等级维护记录；来源记录不是不可变等级修订。 */
public interface CustomerServiceLevelCurrentApi {
    Fact inspect(Query query);

    record Query(Long tenantId, Long customerId) { }
    enum Status { AVAILABLE, NOT_CONFIGURED }

    @Builder
    record Fact(Status status, Long tenantId, Long customerId, Long sourceRecordId,
                Long sourceRecordVersion, String serviceLevelCode, LocalDate validFrom, LocalDate validTo) { }
}
