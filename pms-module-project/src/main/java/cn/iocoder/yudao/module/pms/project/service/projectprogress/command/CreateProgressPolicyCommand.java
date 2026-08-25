package cn.iocoder.yudao.module.pms.project.service.projectprogress.command;

import java.math.BigDecimal;
import java.util.List;

public record CreateProgressPolicyCommand(Long parentProjectId, String policyType, List<Item> items) {
    public record Item(Long childProjectId, BigDecimal weight, List<String> includeStatuses) {}
}
