package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record PreparationWaiverStatusUpdate(Long tenantId, Long preparationId, Long itemId,
                                            Long waiverId, Integer expectedVersion,
                                            String expectedStatusCode, String statusCode,
                                            LocalDateTime submittedAt, Long decidedBy,
                                            LocalDateTime decidedAt, String decisionOpinion,
                                            LocalDateTime withdrawnAt, String updater) {}
