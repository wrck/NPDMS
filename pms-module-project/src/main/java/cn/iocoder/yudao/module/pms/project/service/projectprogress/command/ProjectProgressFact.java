package cn.iocoder.yudao.module.pms.project.service.projectprogress.command;

import java.math.BigDecimal;

public record ProjectProgressFact(Long projectId, Long factVersion, BigDecimal progress,
                                  String sourceWatermark) {}
