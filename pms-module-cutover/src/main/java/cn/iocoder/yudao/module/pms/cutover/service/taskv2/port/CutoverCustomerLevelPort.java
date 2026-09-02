package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import java.time.LocalDateTime;

/** CUT 对 CUS 客户服务等级事实的消费端口。 */
public interface CutoverCustomerLevelPort {

    CustomerLevelFact inspect(Long customerId);

    CustomerLevelFact lockAndRevalidate(CustomerLevelFact expected);

    record CustomerLevelFact(String status, Long customerId, String customerCode, String customerName,
                             Long serviceLevelRevisionId, String serviceLevelCode, long factVersion,
                             LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
    }
}
