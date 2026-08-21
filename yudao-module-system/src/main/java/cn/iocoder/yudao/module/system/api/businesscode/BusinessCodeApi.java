package cn.iocoder.yudao.module.system.api.businesscode;

import cn.iocoder.yudao.module.system.api.businesscode.dto.BusinessCodeAllocation;

/** PM-01 服务端业务编码分配边界。 */
public interface BusinessCodeApi {

    BusinessCodeAllocation allocate(long tenantId, String ruleCode);
}
