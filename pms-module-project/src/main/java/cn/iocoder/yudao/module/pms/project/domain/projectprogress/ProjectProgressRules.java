package cn.iocoder.yudao.module.pms.project.domain.projectprogress;

import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.CreateProgressPolicyCommand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProjectProgressRules {
    public static final String POLICY_SYSTEM_EQUAL = "SYSTEM_EQUAL";
    public static final String POLICY_MANUAL = "MANUAL";
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private ProjectProgressRules() {}

    public static List<CreateProgressPolicyCommand.Item> normalize(
            String policyType, List<Long> childProjectIds, List<CreateProgressPolicyCommand.Item> requestedItems) {
        if (childProjectIds == null || childProjectIds.isEmpty()) {
            throw new IllegalArgumentException("父项目必须存在直接子项目");
        }
        if (POLICY_SYSTEM_EQUAL.equals(policyType)) {
            return equalItems(childProjectIds);
        }
        if (!POLICY_MANUAL.equals(policyType) || requestedItems == null
                || requestedItems.size() != childProjectIds.size()) {
            throw new IllegalArgumentException("人工策略必须完整覆盖全部直接子项目");
        }
        Set<Long> expected = Set.copyOf(childProjectIds);
        Set<Long> actual = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateProgressPolicyCommand.Item item : requestedItems) {
            if (item == null || item.childProjectId() == null || item.weight() == null
                    || item.weight().compareTo(BigDecimal.ZERO) < 0 || item.weight().compareTo(HUNDRED) > 0
                    || item.includeStatuses() != null && item.includeStatuses().stream()
                    .anyMatch(status -> status == null || status.isBlank())
                    || !actual.add(item.childProjectId())) {
                throw new IllegalArgumentException("权重项重复、缺失或超出0到100范围");
            }
            total = total.add(item.weight());
        }
        if (!actual.equals(expected) || total.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("人工策略必须完整覆盖且权重合计100%");
        }
        return requestedItems.stream().map(item -> new CreateProgressPolicyCommand.Item(
                item.childProjectId(), item.weight().setScale(4, RoundingMode.UNNECESSARY),
                item.includeStatuses() == null ? List.of() : List.copyOf(item.includeStatuses()))).toList();
    }

    public static BigDecimal aggregate(List<BigDecimal> progresses, List<BigDecimal> weights) {
        if (progresses == null || weights == null || progresses.size() != weights.size()
                || progresses.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("汇总进度事实不完整");
        }
        BigDecimal result = BigDecimal.ZERO;
        for (int i = 0; i < progresses.size(); i++) {
            result = result.add(progresses.get(i).multiply(weights.get(i))
                    .divide(HUNDRED, 8, RoundingMode.HALF_UP));
        }
        return result.setScale(4, RoundingMode.HALF_UP);
    }

    private static List<CreateProgressPolicyCommand.Item> equalItems(List<Long> childProjectIds) {
        List<CreateProgressPolicyCommand.Item> result = new ArrayList<>(childProjectIds.size());
        BigDecimal base = HUNDRED.divide(BigDecimal.valueOf(childProjectIds.size()), 4, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < childProjectIds.size(); index++) {
            BigDecimal weight = index == childProjectIds.size() - 1 ? HUNDRED.subtract(allocated) : base;
            result.add(new CreateProgressPolicyCommand.Item(childProjectIds.get(index), weight, List.of()));
            allocated = allocated.add(weight);
        }
        return List.copyOf(result);
    }
}
