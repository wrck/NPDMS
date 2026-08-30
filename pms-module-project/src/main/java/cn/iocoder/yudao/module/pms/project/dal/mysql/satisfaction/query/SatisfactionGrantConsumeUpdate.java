package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

import java.time.LocalDateTime;

public record SatisfactionGrantConsumeUpdate(Long tenantId, Long grantId, Integer expectedVersion,
                                             LocalDateTime consumedAt, String updater) {}
