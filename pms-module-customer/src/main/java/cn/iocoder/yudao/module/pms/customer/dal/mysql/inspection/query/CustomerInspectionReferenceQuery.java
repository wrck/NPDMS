package cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.query;

import java.util.Set;

public record CustomerInspectionReferenceQuery(Long tenantId, Set<Long> historyIds) { }
