package cn.iocoder.yudao.module.pms.project.domain.projectprogress;

import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.CreateProgressPolicyCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectProgressRulesTest {

    @Test
    void systemEqualAllocatesExactlyOneHundred() {
        List<CreateProgressPolicyCommand.Item> items = ProjectProgressRules.normalize(
                ProjectProgressRules.POLICY_SYSTEM_EQUAL, List.of(11L, 12L, 13L), List.of());

        assertEquals(List.of(new BigDecimal("33.3333"), new BigDecimal("33.3333"),
                new BigDecimal("33.3334")), items.stream().map(CreateProgressPolicyCommand.Item::weight).toList());
    }

    @Test
    void manualPolicyMustCoverEveryDirectChildExactlyOnce() {
        List<Long> children = List.of(11L, 12L);

        assertThrows(IllegalArgumentException.class, () -> ProjectProgressRules.normalize(
                ProjectProgressRules.POLICY_MANUAL, children,
                List.of(new CreateProgressPolicyCommand.Item(11L, new BigDecimal("100"), List.of()))));
        assertThrows(IllegalArgumentException.class, () -> ProjectProgressRules.normalize(
                ProjectProgressRules.POLICY_MANUAL, children, List.of(
                        new CreateProgressPolicyCommand.Item(11L, new BigDecimal("50"), List.of()),
                        new CreateProgressPolicyCommand.Item(11L, new BigDecimal("50"), List.of()))));
    }

    @Test
    void aggregateUsesOnlyExplicitWeightsAndRejectsMissingFact() {
        assertEquals(new BigDecimal("55.0000"), ProjectProgressRules.aggregate(
                List.of(new BigDecimal("25"), new BigDecimal("75")),
                List.of(new BigDecimal("40"), new BigDecimal("60"))));
        assertThrows(IllegalArgumentException.class, () -> ProjectProgressRules.aggregate(
                java.util.Arrays.asList(new BigDecimal("25"), null),
                List.of(new BigDecimal("40"), new BigDecimal("60"))));
    }
}
