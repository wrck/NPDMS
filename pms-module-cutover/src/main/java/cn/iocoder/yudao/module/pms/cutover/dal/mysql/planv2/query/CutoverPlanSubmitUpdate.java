package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverPlanSubmitUpdate(Long tenantId, Long planRevisionId, Integer expectedVersion,
                                      Integer newVersion, Long submittedBy, LocalDateTime submittedAt,
                                      Long approvalInstanceId, Integer approvalVersion) {
}
