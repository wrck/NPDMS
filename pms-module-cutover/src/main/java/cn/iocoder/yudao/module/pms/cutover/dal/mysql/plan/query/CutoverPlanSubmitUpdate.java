package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

import java.time.LocalDateTime;

public record CutoverPlanSubmitUpdate(Long tenantId, Long planRevisionId, Long expectedVersion,
                                      Long newVersion, Long submittedBy, LocalDateTime submittedAt,
                                      Long approvalInstanceId, Long approvalVersion) {
}
