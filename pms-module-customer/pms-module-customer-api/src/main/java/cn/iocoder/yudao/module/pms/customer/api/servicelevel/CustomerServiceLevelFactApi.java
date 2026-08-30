package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactQuery;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactResult;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactRevalidationQuery;

/** CUS客户当前服务等级只读权威事实。 */
public interface CustomerServiceLevelFactApi {

    CustomerServiceLevelFactResult inspectCurrent(CustomerServiceLevelFactQuery query);

    CustomerServiceLevelFactResult lockAndRevalidate(CustomerServiceLevelFactRevalidationQuery query);
}
