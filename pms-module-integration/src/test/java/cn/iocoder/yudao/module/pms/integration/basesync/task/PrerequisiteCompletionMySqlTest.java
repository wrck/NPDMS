package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrerequisiteCompletionMySqlTest extends RemainingTaskMySqlSupport {
 @BeforeEach void metadataFixture(){
  jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_run(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,connection_id BIGINT,task_code VARCHAR(16),status VARCHAR(32),preview BOOLEAN,deleted BOOLEAN)");
  jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows) VALUES(1,1,'account','BASE_P04','DPPMS',3,1,100),(2,1,'receivable','BASE_P24','DPPMS',3,1,100)");
  jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,password,status) VALUES(8,1,'w123','fixture','private-fixture-hash',0)");
  jdbc.update("INSERT INTO int_base_sync_run VALUES(10,1,1,3,'BASE_P04','COMPLETED',FALSE,FALSE),(11,1,2,3,'BASE_P24','COMPLETED',FALSE,FALSE)");
  jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(1,1,1,'USER_FND','100',8,?,10)","a".repeat(64));
 }
 @Test void accountDependenciesAcceptOnlyTheNewCompletedNonPreviewReceipt(){
  var mapper=session.getMapper(P16ProjectSyncTask.TargetMapper.class);var q=new P16ProjectSyncTask.AccountsQuery(1,3);assertEquals(1,mapper.selectAccountBindings(q).size());jdbc.update("UPDATE int_base_sync_run SET status='SUCCESS' WHERE id=10");session.clearCache();assertTrue(mapper.selectAccountBindings(q).isEmpty());jdbc.update("UPDATE int_base_sync_run SET status='COMPLETED',preview=TRUE WHERE id=10");session.clearCache();assertTrue(mapper.selectAccountBindings(q).isEmpty());
 }
 @Test void deliveryRecognizesCompletedP24AndStillRejectsPreviewOrFailedInput(){
  var nativeMapper=session.getMapper(P15DeliverySyncTask.TargetMapper.class);assertEquals("COMPLETED",nativeMapper.latestP24Execution(new P15DeliverySyncTask.PrerequisiteQuery(1,3)));
  var mapper=mock(P15DeliverySyncTask.TargetMapper.class);when(mapper.packageSourceColumnReady()).thenReturn(1);when(mapper.latestP24Execution(any())).thenAnswer(call->nativeMapper.latestP24Execution(call.getArgument(0)));
  var source=mock(SyncSource.class);var opened=mock(SyncSource.Session.class);var sourceMapper=mock(P15DeliverySyncTask.SourceMapper.class);when(source.open(3)).thenReturn(opened);when(opened.mapper(P15DeliverySyncTask.SourceMapper.class)).thenReturn(sourceMapper);when(sourceMapper.countShipmentEvents(any())).thenReturn(1L);
  var task=new DeliveryProbe(mapper);var c=new SyncContext<Void>(1,2,3,false,1,100,at,source,identities,mock(SyncReferences.class),"Task",session,false,true);assertDoesNotThrow(()->task.execute(c));jdbc.update("UPDATE int_base_sync_run SET status='FAILED' WHERE id=11");session.clearCache();assertThrows(IllegalArgumentException.class,()->task.execute(c));
 }
 @Test void deliveryPreviewUsesAllFiveSourcePhasesWithoutACompletedP24Receipt(){
  var mapper=mock(P15DeliverySyncTask.TargetMapper.class);when(mapper.packageSourceColumnReady()).thenReturn(1);
  var source=mock(SyncSource.class);var opened=mock(SyncSource.Session.class);var sourceMapper=mock(P15DeliverySyncTask.SourceMapper.class);when(source.open(3)).thenReturn(opened);when(opened.mapper(P15DeliverySyncTask.SourceMapper.class)).thenReturn(sourceMapper);
  var task=new DeliveryProbe(mapper);var c=new SyncContext<Void>(1,2,3,true,1,100,at,source,identities,mock(SyncReferences.class),"Task",session,false,true);
  assertDoesNotThrow(()->task.execute(c));assertEquals(List.of("ShipmentContract","Package","ShipmentEvent","DeviceRelationship","Contract"),task.steps);
  assertEquals(List.of(false,false,false,false,false),task.localSources);verify(mapper,never()).latestP24Execution(any());verifyNoInteractions(identities);
 }
 static final class DeliveryProbe extends P15DeliverySyncTask {
  final List<String> steps=new ArrayList<>();final List<Boolean> localSources=new ArrayList<>();
  DeliveryProbe(TargetMapper mapper){super(mapper,org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class),org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.class));}
  @Override protected List<Source> syncData(SyncContext<Parameters> c){steps.add(c.dataName());localSources.add(c.source().local());return List.of();}
 }
}
