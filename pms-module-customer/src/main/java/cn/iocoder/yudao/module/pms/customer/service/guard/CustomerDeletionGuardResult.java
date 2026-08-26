package cn.iocoder.yudao.module.pms.customer.service.guard;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;

import java.util.List;

public record CustomerDeletionGuardResult(
        boolean allowed,
        CustomerReferenceGuardStatus status,
        long referenceCount,
        List<CustomerReferenceGuardResult> providerResults) {
}
