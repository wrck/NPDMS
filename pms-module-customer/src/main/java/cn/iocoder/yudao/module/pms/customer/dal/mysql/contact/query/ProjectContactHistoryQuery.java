package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
public record ProjectContactHistoryQuery(Long tenantId, Long projectId, PageParam page) {}
