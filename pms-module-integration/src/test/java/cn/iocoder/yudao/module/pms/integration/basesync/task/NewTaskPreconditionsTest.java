package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewTaskPreconditionsTest {
 SyncContext<Void> context(){return new SyncContext<>(1,2,3,true,500,1000000,LocalDateTime.now(),mock(SyncSource.class),mock(SyncIdentityStore.class),mock(SyncReferences.class),"Task",mock(SqlSession.class),false,true);}
 @Test void independentScopeRequiresItsOwnCompletedReceiptAndNeverReadsOldPublicationTables(){
  var mapper=mock(BaseSyncPublishedProjects.ReferenceMapper.class);
  org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
  org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
  try{assertTrue(assertThrows(IllegalStateException.class,()->new BaseSyncPublishedProjects(mapper).load(new BaseSyncPublishedProjects.Query(1,3,1000000))).getMessage().contains("COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED"));verify(mapper,never()).selectPage(any());}
  finally{org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
 }
 @Test void projectMasterCannotUseCurrentOneSourcePerTargetSchemaAndNeverOpensExternalSource(){
  var mapper=mock(P16ProjectSyncTask.TargetMapper.class);when(mapper.oneSourcePerTargetIndex()).thenReturn(1);var context=context().forParameters((Void)null);context=new SyncContext<Void>(1,2,3,false,500,1000000,LocalDateTime.now(),context.source(),context.identities(),context.references(),"Task",context.target(),false,true);var execution=context;var task=new P16ProjectSyncTask(mapper,mock(cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi.class),mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class),mock(cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi.class));
  assertTrue(assertThrows(IllegalArgumentException.class,()->task.execute(execution)).getMessage().contains("METADATA_MULTI_SOURCE_INDEX_REQUIRED"));verifyNoInteractions(context.source(),context.target(),context.identities());
 }
 @Test void p15WholeTableCapacityIsBoundedWithoutChangingAnExistingTaskFuse(){
  assertDoesNotThrow(()->new BaseSyncConfiguration("BASE_P15",3,500,5000000));assertThrows(IllegalArgumentException.class,()->new BaseSyncConfiguration("BASE_P15",3,500,5000001));
  var mapper=mock(P15DeliverySyncTask.TargetMapper.class);when(mapper.packageSourceColumnReady()).thenReturn(1);var context=context();var session=mock(SyncSource.Session.class);var source=mock(P15DeliverySyncTask.SourceMapper.class);when(context.source().open(3)).thenReturn(session);when(session.mapper(P15DeliverySyncTask.SourceMapper.class)).thenReturn(source);when(source.countShipmentEvents(any())).thenReturn(4283326L);
  assertTrue(assertThrows(IllegalArgumentException.class,()->new P15DeliverySyncTask(mapper).execute(context)).getMessage().contains("4283326:1000000"));verify(mapper,never()).latestP24Execution(any());verifyNoInteractions(context.target(),context.identities());
 }
}
