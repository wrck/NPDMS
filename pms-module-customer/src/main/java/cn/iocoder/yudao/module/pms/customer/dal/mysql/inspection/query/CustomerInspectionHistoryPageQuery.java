package cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import java.util.Set;

public record CustomerInspectionHistoryPageQuery(Long tenantId, Long customerId, Set<Long> visibleProjectIds, PageParam page) { }
