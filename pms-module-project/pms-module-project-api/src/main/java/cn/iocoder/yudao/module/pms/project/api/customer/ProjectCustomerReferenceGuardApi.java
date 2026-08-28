package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;

public interface ProjectCustomerReferenceGuardApi {

    CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query);
}
