package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;

import java.util.Set;
public record InspectionProjectRows(Long tenantId, Set<Long> projectIds) { }
