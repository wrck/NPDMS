package cn.iocoder.yudao.module.pms.customer.api.masterdata;

import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataResult;

public interface CustomerMasterDataApi {

    CustomerMasterDataResult apply(CustomerMasterDataCommand command);
}
