package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverPlanInvalidationUpdate(Long tenantId, Long planRevisionId, Long expectedVersion,
                                             Long newVersion, Long expectedApprovalVersion,
                                             Long newApprovalVersion, Long invalidatedBy,
                                             LocalDateTime invalidatedAt, String reasonCode) {
}
