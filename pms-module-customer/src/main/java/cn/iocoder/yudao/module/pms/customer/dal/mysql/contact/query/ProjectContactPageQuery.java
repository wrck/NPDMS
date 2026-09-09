package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
public record ProjectContactPageQuery(Long tenantId, Long projectId, Integer status, String name, PageParam page) {}
