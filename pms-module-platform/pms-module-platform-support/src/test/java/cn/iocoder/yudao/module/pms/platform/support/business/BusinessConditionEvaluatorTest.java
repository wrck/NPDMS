package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter.Operator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessConditionEvaluatorTest {
    private FactObservation fact(Object value){return new FactObservation("test",value==null?FactObservation.Availability.EMPTY:FactObservation.Availability.VALUE,value,"v1",Completeness.COMPLETE);}
    private BusinessCondition group(BusinessCondition.Kind kind,BusinessCondition... children){return new BusinessCondition(kind,null,null,List.of(),List.of(children));}
    @Test void bodyAndDeliveryMustBothMatchAndNumericJsonTypesCompareExactly(){
        var condition=group(BusinessCondition.Kind.ALL,BusinessCondition.field("confirmed",Operator.EQ,true),
                new BusinessCondition(BusinessCondition.Kind.DELIVERY,"REPORT",Operator.EQ,List.of(true),List.of()));
        var facts=new HashMap<String,FactObservation>();facts.put("confirmed",fact(false));facts.put("$delivery:REPORT",fact(true));
        assertFalse(BusinessConditionEvaluator.evaluate(condition,facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(condition,facts,"r1").diagnostics().stream().anyMatch(message->message.contains("confirmed")));
        facts.put("confirmed",fact(true));assertTrue(BusinessConditionEvaluator.evaluate(condition,facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(BusinessCondition.field("amount",Operator.EQ,0),Map.of("amount",fact(new java.math.BigDecimal("0.000"))),"r1").satisfied());
        assertNull(BusinessConditionEvaluator.evaluate(BusinessCondition.field("amount",Operator.GTE,"one"),Map.of("amount",fact(1)),"r1").satisfied());
    }
    @Test void unknownAndIncompleteEvidenceNeverPassEvenAfterNegationOrAny(){
        var unknown=BusinessCondition.field("hidden",Operator.NE,true);
        assertNull(BusinessConditionEvaluator.evaluate(group(BusinessCondition.Kind.NOT,unknown),Map.of(),"r1").satisfied());
        var any=group(BusinessCondition.Kind.ANY,unknown,BusinessCondition.field("visible",Operator.EQ,true));
        var result=BusinessConditionEvaluator.evaluate(any,Map.of("visible",fact(true)),"r1");assertNull(result.satisfied());assertFalse(result.collectionComplete());
        var incomplete=new FactObservation("rows",FactObservation.Availability.VALUE,List.of(),"v1",Completeness.UNAVAILABLE);
        assertNull(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.COUNT,"rows",Operator.EQ,List.of(0),List.of()),Map.of("rows",incomplete),"r1").satisfied());
    }
    @Test void allAnyCountUseTheSameMemberPredicatesAndPreserveEmptyCollectionSemantics(){
        var predicate=BusinessCondition.field("enabled",Operator.EQ,true);
        var facts=Map.of("rows",fact(List.of(Map.of("enabled",true),Map.of("enabled",false))));
        assertFalse(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.MEMBER_ALL,"rows",null,List.of(),List.of(predicate)),facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.MEMBER_ANY,"rows",null,List.of(),List.of(predicate)),facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.COUNT,"rows",Operator.EQ,List.of(1),List.of(predicate)),facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.COUNT,"rows",Operator.EQ,List.of(0),List.of()),Map.of("rows",fact(List.of())),"r1").satisfied());
        assertFalse(BusinessConditionEvaluator.evaluate(new BusinessCondition(BusinessCondition.Kind.MEMBER_ANY,"rows",null,List.of(),List.of(predicate)),Map.of("rows",fact(List.of())),"r1").satisfied());
    }
    @Test void diagnosticsOnlyDescribeTheBranchesThatPreventCompletion(){
        var facts=Map.of("a",fact(false),"b",fact(true),"c",fact(false));
        var either=group(BusinessCondition.Kind.ANY,BusinessCondition.field("a",Operator.EQ,true),BusinessCondition.field("b",Operator.EQ,true));
        assertTrue(BusinessConditionEvaluator.evaluate(either,facts,"r1").diagnostics().isEmpty());
        var result=BusinessConditionEvaluator.evaluate(group(BusinessCondition.Kind.ALL,either,BusinessCondition.field("c",Operator.EQ,true)),facts,"r1");
        assertEquals(List.of("条件未满足: c EQ"),result.diagnostics());
        assertEquals(List.of("否定条件未满足"),BusinessConditionEvaluator.evaluate(group(BusinessCondition.Kind.NOT,BusinessCondition.field("b",Operator.EQ,true)),facts,"r1").diagnostics());
    }
    @Test void persistedDatesCompareToTheirJsonOperandsAndInvalidDatesStayUnknown(){
        var facts=Map.of("day",fact(java.time.LocalDate.of(2026,10,9)),"time",fact(java.time.LocalDateTime.of(2026,10,9,12,30)));
        assertTrue(BusinessConditionEvaluator.evaluate(BusinessCondition.field("day",Operator.EQ,"2026-10-09"),facts,"r1").satisfied());
        assertTrue(BusinessConditionEvaluator.evaluate(BusinessCondition.field("time",Operator.GT,"2026-10-09 12:00:00"),facts,"r1").satisfied());
        assertNull(BusinessConditionEvaluator.evaluate(BusinessCondition.field("day",Operator.NE,"not-a-date"),facts,"r1").satisfied());
    }
}
