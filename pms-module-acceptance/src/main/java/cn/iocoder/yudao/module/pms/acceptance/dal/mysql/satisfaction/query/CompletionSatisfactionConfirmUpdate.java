package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

import java.time.LocalDateTime;
public record CompletionSatisfactionConfirmUpdate(Long tenantId, Long certificateId, Long expectedVersion,
                                                  LocalDateTime confirmedAt, String actorRef) { }
