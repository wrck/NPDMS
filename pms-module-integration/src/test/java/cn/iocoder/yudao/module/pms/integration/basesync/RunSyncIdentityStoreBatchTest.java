package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore.Save;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RunSyncIdentityStoreBatchTest {
 final BaseSyncMetadataMapper mapper=mock(BaseSyncMetadataMapper.class);
 RunSyncIdentityStore store(boolean preview){return new RunSyncIdentityStore(mapper,new BaseSyncMetadataMapper.RunRow(9,1,2,"request",new BaseSyncConfiguration("BASE_P31",3,500,10000),"actor",preview));}
 RunSyncIdentityStore storeWithBatch(int size){return new RunSyncIdentityStore(mapper,new BaseSyncMetadataMapper.RunRow(9,1,2,"request",new BaseSyncConfiguration("BASE_P31",3,size,10000),"actor",false));}
 Save fresh(int id){return new Save("MEMBER_HISTORY","source"+id,id,"a".repeat(64),null);}
 @Test void thousandAndOneNewBindingsUseThreeBatchesAndRetainAllFields(){
  when(mapper.insertBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());
  store(false).saveAll(IntStream.rangeClosed(1,1001).mapToObj(this::fresh).toList());
  var batches=org.mockito.ArgumentCaptor.forClass(BaseSyncMetadataMapper.BindingBatch.class);verify(mapper,times(3)).insertBindings(batches.capture());
  assertEquals(List.of(500,500,1),batches.getAllValues().stream().map(b->b.rows().size()).toList());
  var rows=batches.getAllValues().stream().flatMap(b->b.rows().stream()).toList();
  for(int i=0;i<rows.size();i++){var row=rows.get(i);assertEquals("source"+(i+1),row.sourceKey());assertEquals(i+1,row.targetId());assertEquals(1,row.tenantId());assertEquals(2,row.taskId());assertEquals(9,row.runId());assertEquals("actor",row.actor());assertEquals("MEMBER_HISTORY",row.object());assertEquals("a".repeat(64),row.fingerprint());assertNull(row.previousFingerprint());assertTrue(row.id()>0);}
  verifyNoMoreInteractions(mapper);
 }
 @Test void emptyDoesNothingAndPreviewRejectsBeforeAnyMapperWrite(){
  store(true).saveAll(List.of());verifyNoInteractions(mapper);
  assertThrows(IllegalStateException.class,()->store(true).saveAll(List.of(fresh(1))));verifyNoInteractions(mapper);
 }
 @Test void mixedRowsFlushBeforeProtectedUpdateAndPreserveInputOrder(){
  when(mapper.insertBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());when(mapper.updateBindings(any())).thenReturn(1);
  var update=new Save("MEMBER_HISTORY","changed",8,"b".repeat(64),"a".repeat(64));
  store(false).saveAll(List.of(fresh(1),update,fresh(2)));
  var order=inOrder(mapper);order.verify(mapper).insertBindings(argThat(b->b.rows().size()==1&&b.rows().getFirst().sourceKey().equals("source1")));
  order.verify(mapper).updateBindings(argThat(b->b.rows().size()==1&&b.rows().getFirst().sourceKey().equals("changed")&&b.rows().getFirst().previousFingerprint().equals("a".repeat(64))&&b.rows().getFirst().targetId()==8));
  order.verify(mapper).insertBindings(argThat(b->b.rows().size()==1&&b.rows().getFirst().sourceKey().equals("source2")));order.verifyNoMoreInteractions();
 }
 @Test void invalidIdentitiesAndAffectedRowMismatchFailClosed(){
  for(var invalid:List.of(new Save("bad","key",1,"a".repeat(64),null),new Save("MEMBER_HISTORY"," ",1,"a".repeat(64),null),new Save("MEMBER_HISTORY","x".repeat(129),1,"a".repeat(64),null),new Save("MEMBER_HISTORY","key",0,"a".repeat(64),null),new Save("MEMBER_HISTORY","key",1,"invalid",null))){assertThrows(IllegalArgumentException.class,()->store(false).saveAll(List.of(invalid)));}
  verifyNoInteractions(mapper);
  when(mapper.insertBindings(any())).thenReturn(0);assertThrows(IllegalStateException.class,()->store(false).saveAll(List.of(fresh(1))));
  when(mapper.updateBindings(any())).thenReturn(0);assertThrows(IllegalStateException.class,()->store(false).saveAll(List.of(new Save("MEMBER_HISTORY","key",1,"a".repeat(64),"b".repeat(64)))));
 }
 @Test void thousandAndOneExistingBindingsUseThreeConditionalStatements(){
  when(mapper.updateBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());
  var rows=IntStream.rangeClosed(1,1001).mapToObj(id->new Save("MEMBER_HISTORY","source"+id,id,"b".repeat(64),"a".repeat(64))).toList();
  store(false).saveAll(rows);
  var batches=org.mockito.ArgumentCaptor.forClass(BaseSyncMetadataMapper.BindingBatch.class);verify(mapper,times(3)).updateBindings(batches.capture());
  assertEquals(List.of(500,500,1),batches.getAllValues().stream().map(b->b.rows().size()).toList());
  assertTrue(batches.getAllValues().stream().flatMap(b->b.rows().stream()).allMatch(r->r.previousFingerprint().equals("a".repeat(64))&&r.tenantId()==1&&r.taskId()==2));
  verifyNoMoreInteractions(mapper);
 }
 @Test void repeatedKeysKeepSequentialFingerprintChangesAndRawSourceText(){
  when(mapper.updateBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());
  store(false).saveAll(List.of(new Save("MEMBER_HISTORY","source",1,"b".repeat(64),"a".repeat(64)),new Save("MEMBER_HISTORY","source ",1,"c".repeat(64),"b".repeat(64))));
  var order=inOrder(mapper);
  order.verify(mapper).updateBindings(argThat(b->b.rows().size()==1&&b.rows().getFirst().previousFingerprint().equals("a".repeat(64))));
  order.verify(mapper).updateBindings(argThat(b->b.rows().size()==1&&b.rows().getFirst().sourceKey().equals("source ")&&b.rows().getFirst().previousFingerprint().equals("b".repeat(64))));order.verifyNoMoreInteractions();
 }
 @Test void runtimeBatchInputOverridesDefaultForBothNewAndExistingBindings(){
  when(mapper.insertBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());
  when(mapper.updateBindings(any())).thenAnswer(call->call.<BaseSyncMetadataMapper.BindingBatch>getArgument(0).rows().size());
  storeWithBatch(128).saveAll(IntStream.rangeClosed(1,300).mapToObj(this::fresh).toList());
  storeWithBatch(128).saveAll(IntStream.rangeClosed(1,300).mapToObj(id->new Save("MEMBER_HISTORY","source"+id,id,"b".repeat(64),"a".repeat(64))).toList());
  var inserts=org.mockito.ArgumentCaptor.forClass(BaseSyncMetadataMapper.BindingBatch.class);var updates=org.mockito.ArgumentCaptor.forClass(BaseSyncMetadataMapper.BindingBatch.class);
  verify(mapper,times(3)).insertBindings(inserts.capture());verify(mapper,times(3)).updateBindings(updates.capture());
  assertEquals(List.of(128,128,44),inserts.getAllValues().stream().map(b->b.rows().size()).toList());
  assertEquals(List.of(128,128,44),updates.getAllValues().stream().map(b->b.rows().size()).toList());verifyNoMoreInteractions(mapper);
 }
}
