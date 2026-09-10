package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

/** The customer scope is authorized before this single-customer query is constructed. */
public record ContactMasterPageQuery(Long tenantId, Long customerId, String name,
                                     Integer status, PageParam page) {
}
