package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.*;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SharedConditionBackendTest {
    @Test void switchingExecutionBackendsPreservesNumericAndUnknownVerdicts() {
        var ref=new EntityRef(1L,"IT","condition",20L);
        var snapshot=new ProcessDefinitionSnapshot("DEFAULT_CONDITION",1,"n","IT","condition","IT_CONDITION",1,
                "save",1,"FIELD_CONDITION","1",List.of(new BusinessFieldFilter("amount",BusinessFieldFilter.Operator.EQ,List.of(0))),"IT_READY");
        var observations=List.of(
                new FactObservation("amount",FactObservation.Availability.VALUE,new BigDecimal("0.000"),"v1",Completeness.COMPLETE),
                new FactObservation("amount",FactObservation.Availability.VALUE,1L,"v1",Completeness.COMPLETE),
                new FactObservation("amount",FactObservation.Availability.UNKNOWN,null,"v1",Completeness.UNAVAILABLE),
                new FactObservation("amount",FactObservation.Availability.VALUE,0L,"v1",Completeness.UNAVAILABLE));
        var expected=List.of("SATISFIED","UNSATISFIED","UNKNOWN","UNKNOWN");
        for(int i=0;i<observations.size();i++){
            var facts=mock(BusinessFactPort.class);when(facts.observeField(any(),eq("amount"),anyString())).thenReturn(observations.get(i));
            var results=mock(BusinessResultFormationPort.class);
            when(results.form(anyString(),any(),any(),anyString(),anyString())).thenAnswer(call->new BusinessResultRecord("IT_READY",ref,"result",ResultSemantics.NEW_RESULT,call.getArgument(3),null,true,null,1));
            var inlineRows=mock(InlineInstanceMapper.class);var eventRows=mock(EventedInstanceMapper.class);
            var inline=new InlineSyncBackend(inlineRows,facts,results);
            assertEquals(expected.get(i),inline.run(snapshot,1L,20L,"inline-"+i).verdict());
            var event=new BusinessEventRecord("event-"+i,ref,BusinessEventKind.CHANGED,"v1",null,Map.of(),i+1);
            var events=mock(BusinessEventPollPort.class);when(events.pollPending(eq(EventDrivenBackend.CONSUMED_EVENT_TYPE),eq(50),eq(0L))).thenReturn(List.of(event));
            var definitions=mock(ProcessDefinitionPort.class);when(definitions.listPublished("IT","condition")).thenReturn(List.of(snapshot));
            new EventDrivenBackend(events,definitions,mock(EventCheckpointMapper.class),eventRows,facts,results,mock(ResultSubscriptionPort.class)).pollAndProcess();
            String verdict=expected.get(i);
            verify(eventRows).insert(argThat((EventedInstanceDO row)->verdict.equals(row.getVerdict())));
            verify(results,times(i==0?2:0)).form(anyString(),any(),any(),anyString(),anyString());
        }
    }
}
