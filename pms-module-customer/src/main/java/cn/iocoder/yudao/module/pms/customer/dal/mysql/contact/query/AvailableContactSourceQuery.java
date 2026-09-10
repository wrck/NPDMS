package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
public record AvailableContactSourceQuery(Long tenantId, Long projectId, Long customerId, String name, PageParam page) {
    public int offset() { return (page.getPageNo()-1)*page.getPageSize(); }
    public int limit() { return page.getPageSize(); }
}
