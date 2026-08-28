package cn.iocoder.yudao.module.pms.project.dal.mysql.project.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

public class CustomerProjectSummaryPageQuery extends PageParam {

    private final Long tenantId;
    private final Long customerId;

    public CustomerProjectSummaryPageQuery(Long tenantId, Long customerId, Integer pageNo, Integer pageSize) {
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
