package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheSourceNullabilityTest {
    SyncContext<Void> runtime(){return new SyncContext<Void>(1,2,3,true,500,1000,LocalDateTime.now(),
            mock(SyncSource.class),mock(SyncIdentityStore.class),mock(SyncReferences.class),"test",mock(org.apache.ibatis.session.SqlSession.class),false,true);}

    @Test void planRetainsRealTimesWhenTargetColumnsAreNotNullableAndRejectsActualNulls(){
        var mapper=mock(P23CollectionPlanSyncTask.TargetMapper.class);
        when(mapper.nullableColumns()).thenReturn(List.of());
        var task=new P23CollectionPlanSyncTask(mapper);
        var context=runtime().forParameters(task.createParameters(runtime()));
        var source=new P23CollectionPlanSyncTask.CollectionPlanCacheSource().setCreateTime("2020-01-01 00:00:00").setUpdateTime("2021-01-01 00:00:00");
        var batch=new SyncBatch<>(context,List.of(source));
        assertDoesNotThrow(()->task.syncDataValidate(batch));
        assertEquals(LocalDateTime.of(2020,1,1,0,0),source.getTarget().getCreateTime());
        source.setUpdateTime(null);
        assertThrows(IllegalStateException.class,()->task.syncDataValidate(batch));
        when(mapper.nullableColumns()).thenReturn(List.of("update_time"));
        var allowed=new SyncBatch<>(runtime().forParameters(task.createParameters(runtime())),List.of(source));
        task.syncDataValidate(allowed);assertNull(source.getTarget().getUpdateTime());
    }

    @Test void factoryAllowsPresentSerialButNeverInventsOrSkipsAnActualNull(){
        var mapper=mock(P34FactoryHistorySyncTask.TargetMapper.class);
        var task=new P34FactoryHistorySyncTask(mapper);
        var runtime=runtime();when(runtime.identities().findByKeys(any())).thenReturn(Map.of());
        when(mapper.selectExisting(any())).thenReturn(List.of());
        var source=new P34FactoryHistorySyncTask.FactorySyncSource().setSourceKey("digest:1").setDeviceSn("SN");
        task.syncDataInsertBefore(new SyncBatch<>(runtime.forParameters(task.createParameters(runtime)),List.of(source)));
        assertEquals("SN",source.getTarget().getDeviceSn());
        source.setDeviceSn(null);
        assertThrows(IllegalStateException.class,()->task.syncDataInsertBefore(new SyncBatch<>(runtime.forParameters(task.createParameters(runtime)),List.of(source))));
        when(mapper.nullableSerialColumn()).thenReturn(1);
        task.syncDataInsertBefore(new SyncBatch<>(runtime.forParameters(task.createParameters(runtime)),List.of(source)));
        assertNull(source.getTarget().getDeviceSn());assertEquals(SyncWriteAction.INSERT,source.getSyncAction());
    }
}
