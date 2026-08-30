package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.time.LocalDateTime;

public record AuthorityCandidateDecisionUpdate(Long tenantId, Long candidateId, Integer expectedVersion,
                                               String candidateStatus, String matchedOwnerTable,
                                               Long matchedOwnerId, String matchedOwnerSourceVersion,
                                               Long decidedBy, LocalDateTime decidedAt) {
}
