package cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;

import java.time.LocalDateTime;

/** CUT此前冻结、提交给CUS重验的客户服务等级期望事实。 */
public record ExpectedCustomerServiceLevelFact(
        CustomerServiceLevelFact.Status status,
        Long tenantId,
        Long customerId,
        Long serviceLevelRevisionId,
        String serviceLevelCode,
        Long factVersion,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {

    public ExpectedCustomerServiceLevelFact {
        require(status != null, "status is required");
        require(tenantId != null && tenantId > 0, "tenantId must be positive");
        require(customerId != null && customerId > 0, "customerId must be positive");
        require(factVersion != null && factVersion >= 0, "factVersion must be non-negative");
        if (status == CustomerServiceLevelFact.Status.AVAILABLE) {
            require(serviceLevelRevisionId != null && serviceLevelRevisionId > 0,
                    "AVAILABLE revisionId must be positive");
            require(serviceLevelCode != null && serviceLevelCode.equals(serviceLevelCode.trim())
                            && !serviceLevelCode.isEmpty() && serviceLevelCode.length() <= 64,
                    "AVAILABLE serviceLevelCode must be normalized and 1..64 characters");
            require(effectiveFrom != null, "AVAILABLE effectiveFrom is required");
            require(effectiveTo == null || effectiveTo.isAfter(effectiveFrom),
                    "AVAILABLE effectiveTo must be after effectiveFrom");
        } else {
            require(serviceLevelRevisionId == null && serviceLevelCode == null
                            && effectiveFrom == null && effectiveTo == null,
                    "NOT_CONFIGURED must not contain revision or effective interval");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new CustomerServiceLevelFactException(
                    CustomerServiceLevelFactException.Code.INVALID_REQUEST, message);
        }
    }
}
