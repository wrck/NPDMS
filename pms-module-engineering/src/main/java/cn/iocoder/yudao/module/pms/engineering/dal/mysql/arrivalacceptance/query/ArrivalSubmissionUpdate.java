package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

/** DRAFT提交为领域计算候选态的聚合CAS更新。 */
public record ArrivalSubmissionUpdate(
        Long tenantId,
        Long arrivalAcceptanceId,
        Integer expectedVersion,
        String submittedStatus,
        Long evidenceId,
        Integer evidenceRevision,
        Long submittedBy,
        LocalDateTime submittedAt) {
}
