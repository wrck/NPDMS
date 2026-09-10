package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;

public record VisibleContactPageQuery(Long tenantId, Long customerId, String name, Boolean primaryFlag,
                                      Integer status, CustomerVisibleScope scope, PageParam page) {
    public int offset() { return (page.getPageNo()-1)*page.getPageSize(); }
    public int limit() { return page.getPageSize(); }
}
