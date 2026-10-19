package cn.iocoder.yudao.module.pms.cutover.service.task.port;

import java.time.LocalDateTime;

/** CUT 对 CUS 客户服务等级事实的消费端口。 */
public interface CutoverCustomerLevelPort {

    CustomerLevelFact inspect(Long customerId);

    @lombok.Builder(toBuilder = true)
    record CustomerLevelFact(String status, Long customerId, String customerCode, String customerName,
                             Long serviceLevelRevisionId, String serviceLevelCode, long factVersion,
                             LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Long sourceRecordId) {
    }
}
