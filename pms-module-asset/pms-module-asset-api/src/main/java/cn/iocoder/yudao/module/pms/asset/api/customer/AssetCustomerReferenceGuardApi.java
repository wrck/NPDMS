package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;

public interface AssetCustomerReferenceGuardApi {

    CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query);
}
