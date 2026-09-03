package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverPlanInvalidationUpdate(Long tenantId, Long planRevisionId, Integer expectedVersion,
                                             Integer newVersion, Integer expectedApprovalVersion,
                                             Integer newApprovalVersion, Long invalidatedBy,
                                             LocalDateTime invalidatedAt, String reasonCode) {
}
