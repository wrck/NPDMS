package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.time.LocalDateTime;

public record AuthorityCandidateDecisionUpdate(Long tenantId, Long candidateId, Integer expectedVersion,
                                               String candidateStatus, String matchedOwnerType,
                                               Long matchedOwnerId, String matchedOwnerSourceVersion,
                                               String decisionReason, Long decidedBy, LocalDateTime decidedAt) {
}
