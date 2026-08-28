package cn.iocoder.yudao.module.pms.project.service.projectprogress.command;

import java.math.BigDecimal;
import java.util.List;

public record ProjectProgressResult(Long projectId, Long policyRevisionId, Long treeVersion,
                                    String sourceWatermark, String status, BigDecimal progress,
                                    List<Item> items) {
    public record Item(Long childProjectId, Long factVersion, BigDecimal childProgress,
                       BigDecimal normalizedWeight, BigDecimal contribution, String missingReason) {}
}
