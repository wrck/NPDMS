package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Acquire shared target scopes in a stable order and release partial acquisitions on every failure. */
final class SyncTargetLocks implements AutoCloseable {
    private final List<RLock> locks;
    private final List<RLock> held=new ArrayList<>();
    SyncTargetLocks(RedissonClient redisson,long tenant,SyncDefinition definition,DataSyncAdapter adapter) {
        locks=names(tenant,definition,adapter).stream().map(redisson::getLock).toList();
    }
    static List<String> names(long tenant,SyncDefinition definition,DataSyncAdapter adapter) {
        Set<String> names=new TreeSet<>();
        if(!"TABLE_MAPPING".equals(definition.adapter()))names.add("int:data-sync:"+tenant+":"+definition.adapter());
        for(String table:adapter.writeTables()) {
            cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetCatalog.identifier(table);
            names.add("int:data-sync:"+tenant+":table:"+table);
        }
        if(names.isEmpty())throw new IllegalArgumentException("同步适配器未声明目标范围");
        return List.copyOf(names);
    }
    boolean tryLock(long wait,TimeUnit unit)throws InterruptedException {
        try {
            for(var lock:locks) {
                if(!lock.tryLock(wait,unit)){close();return false;}
                held.add(lock);
            }
            return true;
        }catch(InterruptedException|RuntimeException ex){close();throw ex;}
    }
    boolean tryLock() {
        try {
            for(var lock:locks) {
                if(!lock.tryLock()){close();return false;}
                held.add(lock);
            }
            return true;
        }catch(RuntimeException ex){close();throw ex;}
    }
    @Override public void close() {
        for(int i=held.size()-1;i>=0;i--) {
            var lock=held.get(i);
            if(lock.isHeldByCurrentThread())lock.unlock();
        }
        held.clear();
    }
}
