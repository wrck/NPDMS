package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
public record PaymentAcceptanceUpdate(Long tenantId, Long projectId, Long nodeId, String nodeType,
                                      Integer expectedVersion, LocalDateTime acceptanceTime) {}
