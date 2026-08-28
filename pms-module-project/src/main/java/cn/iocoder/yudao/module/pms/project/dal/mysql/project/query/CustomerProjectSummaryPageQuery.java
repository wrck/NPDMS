package cn.iocoder.yudao.module.pms.project.dal.mysql.project.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import java.util.Set;

public class CustomerProjectSummaryPageQuery extends PageParam {

    private final Long tenantId;
    private final Long customerId;
    private final Set<Long> visibleProjectIds;

    public CustomerProjectSummaryPageQuery(Long tenantId, Long customerId, Set<Long> visibleProjectIds,
                                           Integer pageNo, Integer pageSize) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.visibleProjectIds = visibleProjectIds;
        setPageNo(pageNo);
        setPageSize(pageSize);
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Set<Long> getVisibleProjectIds() {
        return visibleProjectIds;
    }
}
