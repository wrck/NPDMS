package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P15StageSelectionTest {
    final P15DeliverySyncTask.TargetMapper mapper=mock(P15DeliverySyncTask.TargetMapper.class);
    final ManagedOrganizationApi organizations=mock(ManagedOrganizationApi.class);
    final SyncSource source=mock(SyncSource.class);
    final List<String> executed=new ArrayList<>();
    final P15DeliverySyncTask task=new P15DeliverySyncTask(mapper,organizations){
        @Override protected List<Source> syncData(SyncContext<Parameters> context){executed.add(context.dataName());return List.of();}
    };
    SyncContext<Void> context(boolean preview,List<String> stages){
        when(organizations.list()).thenReturn(List.of());
        when(mapper.selectCarriers(any())).thenReturn(List.of());
        return new SyncContext<Void>(1,2,3,preview,2000,5000000,LocalDateTime.now(),source,
                mock(SyncIdentityStore.class),mock(SyncReferences.class),"BASE_P15",mock(SqlSession.class),false,true).forStages(stages);
    }
    @Test void relationshipOnlyDoesNotReadShipmentEventsOrRunEarlierStages(){
        var context=context(true,List.of("ShipmentOrderLine","OrderContractRelation"));
        task.execute(context);
        assertEquals(List.of("OrderContractRelation","ShipmentOrderLine"),executed);
        verifyNoInteractions(source);
        verify(mapper,never()).packageSourceColumnReady();
        assertTrue(context.runState().warnings().contains("STAGES:ShipmentOrderLine,OrderContractRelation"));
    }
    @Test void localRelationCanRunAloneAndSelectionDoesNotLeakToNextCall(){
        task.execute(context(true,List.of("OrderContractRelation")));
        task.execute(context(true,List.of("ContractOrganization")));
        assertEquals(List.of("OrderContractRelation","ContractOrganization"),executed);
        verifyNoInteractions(source);
    }
    @Test void unspecifiedStagesKeepFullWorkflowAndShipmentCapacityGuard(){
        var context=context(true,List.of());
        var session=mock(SyncSource.Session.class);
        var sourceMapper=mock(P15DeliverySyncTask.SourceMapper.class);
        when(source.open(3)).thenReturn(session);when(session.mapper(P15DeliverySyncTask.SourceMapper.class)).thenReturn(sourceMapper);
        when(mapper.packageSourceColumnReady()).thenReturn(1);
        task.execute(context);
        assertEquals(task.supportedStages(),executed);
        verify(sourceMapper).countShipmentEvents(any());
    }
    @Test void invalidOrRepeatedStageIsRejectedBeforeAnyReads(){
        for(var stages:List.of(List.of("selectAnyTable"),List.of("OrderContractRelation","OrderContractRelation"))){
            assertThrows(IllegalArgumentException.class,()->task.execute(context(true,stages)));
        }
        verify(mapper,never()).selectCarriers(any());verifyNoInteractions(source);
        assertTrue(executed.isEmpty());
    }
    @Test void selectedExecutionRetainsFormalPrerequisite(){
        assertThrows(IllegalArgumentException.class,()->task.execute(context(false,List.of("OrderContractRelation"))));
        assertTrue(executed.isEmpty());
        when(mapper.latestP24Execution(any())).thenReturn("COMPLETED");
        task.execute(context(false,List.of("OrderContractRelation")));
        assertEquals(List.of("OrderContractRelation"),executed);
    }
}
