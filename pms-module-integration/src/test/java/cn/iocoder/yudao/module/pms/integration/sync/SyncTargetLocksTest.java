package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SyncTargetLocksTest {
    RedissonClient client(){
        var locks=new ConcurrentHashMap<String,RLock>();var client=mock(RedissonClient.class);
        when(client.getLock(anyString())).thenAnswer(call->locks.computeIfAbsent(call.getArgument(0),name->{
            var actual=new ReentrantLock();var lock=mock(RLock.class);
            try {
                when(lock.tryLock(anyLong(),any(TimeUnit.class))).thenAnswer(c->actual.tryLock(c.getArgument(0),c.getArgument(1)));
                when(lock.tryLock()).thenAnswer(c->actual.tryLock());
                when(lock.isHeldByCurrentThread()).thenAnswer(c->actual.isHeldByCurrentThread());
                doAnswer(c->{actual.unlock();return null;}).when(lock).unlock();
            }catch(InterruptedException ex){throw new AssertionError(ex);}
            return lock;
        }));return client;
    }
    SyncTargetLocks scope(RedissonClient client,long tenant,String...tables){
        var adapter=mock(DataSyncAdapter.class);when(adapter.writeTables()).thenReturn(List.of(tables));
        return new SyncTargetLocks(client,tenant,SyncDefinition.builder().adapter("TABLE_MAPPING").build(),adapter);
    }
    @Test void disjointTargetsAndOtherTenantsRunWhileSharedSecondaryTargetIsProtected()throws Exception {
        var client=client();
        try(var first=scope(client,1,"ast_device_shipment","ast_device")){
            assertTrue(first.tryLock());
            try(var executor=Executors.newSingleThreadExecutor()){
                assertTrue(executor.submit(()->{try(var lock=scope(client,1,"com_contract")){return lock.tryLock();}}).get());
                assertTrue(executor.submit(()->{try(var lock=scope(client,2,"ast_device")){return lock.tryLock();}}).get());
                assertFalse(executor.submit(()->{try(var lock=scope(client,1,"ast_device")){return lock.tryLock();}}).get());
            }
        }
        try(var next=scope(client,1,"ast_device")){assertTrue(next.tryLock());}
    }
    @Test void contentionReleasesPreviouslyAcquiredScopes()throws Exception {
        var client=client();
        try(var busy=scope(client,1,"table_b")){
            assertTrue(busy.tryLock());
            try(var executor=Executors.newSingleThreadExecutor()){
                assertFalse(executor.submit(()->{try(var lock=scope(client,1,"table_a","table_b")){return lock.tryLock();}}).get());
                try(var released=scope(client,1,"table_a")){assertTrue(released.tryLock());}
            }
        }
    }
}
