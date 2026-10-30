package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemberConditionEvaluatorTest {

    private static final EntityRef REF = new EntityRef(1L, "demo", "ticket", 5L);

    private static BusinessFieldFilter countCondition(String relation, BusinessFieldFilter.Operator op,
                                                      long threshold) {
        return new BusinessFieldFilter(relation, op, List.of(threshold));
    }

    private static ProcessDefinitionSnapshot snapshot(List<BusinessFieldFilter> conditions) {
        return new ProcessDefinitionSnapshot("DEF", 1, "n", "demo", "ticket", "DEMO_TICKET", 1,
                "save", 1, MemberConditionEvaluator.SEMANTIC_MEMBER_CONDITION, "1", conditions,
                "demo.ticket.members.ready");
    }

    private static BusinessFactPort factPort(FactObservation members) {
        return new BusinessFactPort() {
            @Override
            public FactObservation observeField(EntityDataRef ref, String fieldCode, String sceneCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FactObservation observeMembers(String ownerModule, String entityType, Long entityId,
                                                  String relationCode, List<BusinessFieldFilter> scopeFilters,
                                                  String sceneCode) {
                return members;
            }

            @Override
            public FactObservation observeDelivery(EntityRef entity, String sceneCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FactObservation observeApproval(EntityRef entity, String sceneCode) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static FactObservation count(long value) {
        return new FactObservation("test", FactObservation.Availability.VALUE, value, "计数",
                Completeness.COMPLETE);
    }

    @Test
    void emptyMemberSetIsAKnownZeroNotAnUnknown() {
        // 已知空集合计数 0：GTE 1 明确不满足，不得伪装未知。
        var verdict = MemberConditionEvaluator.evaluate(snapshot(List.of(
                countCondition("members", BusinessFieldFilter.Operator.GTE, 1))),
                REF, factPort(new FactObservation("test", FactObservation.Availability.EMPTY, 0L, "空",
                        Completeness.COMPLETE)), "test");
        assertEquals(Boolean.FALSE, verdict.satisfied());
        assertTrue(verdict.collectionComplete());
    }

    @Test
    void countConditionsCompareAgainstKnownCounts() {
        var snapshot = snapshot(List.of(
                countCondition("members", BusinessFieldFilter.Operator.GTE, 2),
                countCondition("members", BusinessFieldFilter.Operator.LTE, 5)));
        assertEquals(Boolean.TRUE, MemberConditionEvaluator.evaluate(snapshot, REF,
                factPort(count(3L)), "test").satisfied());
        assertEquals(Boolean.FALSE, MemberConditionEvaluator.evaluate(snapshot, REF,
                factPort(count(6L)), "test").satisfied());
    }

    @Test
    void unreadableMembersStayUnknownAndNegationDoesNotPass() {
        for (FactObservation.Availability availability : List.of(FactObservation.Availability.UNKNOWN,
                FactObservation.Availability.UNAVAILABLE)) {
            var verdict = MemberConditionEvaluator.evaluate(snapshot(List.of(
                    countCondition("members", BusinessFieldFilter.Operator.LTE, 0))), REF,
                    factPort(new FactObservation("test", availability, null, "不可读",
                            Completeness.UNAVAILABLE)), "test");
            assertNull(verdict.satisfied(), "成员不可读不得给出确定结论");
            assertFalse(verdict.collectionComplete());
        }
    }

    @Test
    void malformedThresholdIsUnknownNotASilentPass() {
        var snapshot = snapshot(List.of(new BusinessFieldFilter("members",
                BusinessFieldFilter.Operator.GTE, List.of("many"))));
        var verdict = MemberConditionEvaluator.evaluate(snapshot, REF, factPort(count(3L)), "test");
        assertNull(verdict.satisfied());
        assertFalse(verdict.collectionComplete());
    }

    @Test
    void unsupportedRuleCodeIsRejectedNotApproximated() {
        var snapshot = new ProcessDefinitionSnapshot("DEF", 1, "n", "demo", "ticket", "DEMO_TICKET", 1,
                "save", 1, "MYSTERY_RULE", "1", List.of(), "demo.x");
        assertThrows(IllegalArgumentException.class,
                () -> RuleSemanticEvaluator.evaluate(snapshot, REF, factPort(count(1L)), "test"));
    }
}
