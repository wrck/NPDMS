package cn.iocoder.yudao.module.pms.customer.api.guard;

import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;

public interface CustomerReferenceGuardApi {

    CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query);
}
