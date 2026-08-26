package cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

public class CustomerDeviceSummaryPageQuery extends PageParam {

    private final Long tenantId;
    private final Long customerId;

    public CustomerDeviceSummaryPageQuery(Long tenantId, Long customerId, Integer pageNo, Integer pageSize) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        setPageNo(pageNo);
        setPageSize(pageSize);
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
