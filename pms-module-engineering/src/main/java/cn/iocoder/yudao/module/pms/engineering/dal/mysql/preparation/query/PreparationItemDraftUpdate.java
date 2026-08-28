package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;
import java.util.Set;

public record PreparationItemDraftUpdate(Long tenantId, Long preparationId, Long itemId,
                                         Integer expectedVersion, Set<String> submittedFields,
                                         String applicabilityCode, Boolean outsourced,
                                         Long assigneeUserId, LocalDateTime assigneeEffectiveFrom,
                                         String siteResultCode, String siteResultDetail,
                                         String evidenceReferenceSnapshot, String notApplicableReason,
                                         String updater) {}
