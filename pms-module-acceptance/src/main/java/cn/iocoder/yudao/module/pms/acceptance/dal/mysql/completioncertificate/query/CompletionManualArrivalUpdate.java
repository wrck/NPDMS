package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query;

import java.time.LocalDate;

public record CompletionManualArrivalUpdate(Long tenantId, Long id, Long version, LocalDate date) {}
