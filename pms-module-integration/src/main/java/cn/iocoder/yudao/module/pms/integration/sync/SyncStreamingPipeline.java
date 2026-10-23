package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One bounded prefetch slot overlaps source I/O with ordered, transactional target commits. */
final class SyncStreamingPipeline {
    @FunctionalInterface interface Read {
        void stream(Consumer<SpringJdbcStreamingReader.StreamChunk> consumer)throws Exception;
    }
    static void consume(Read source,Consumer<SpringJdbcStreamingReader.StreamChunk> target)throws Exception {
        long tenant=TenantContextHolder.getRequiredTenantId();
        var pending=new ArrayBlockingQueue<SpringJdbcStreamingReader.StreamChunk>(1);
        var read=new FutureTask<Void>(()->{
            TenantContextHolder.setTenantId(tenant);
            try {
                source.stream(chunk->{
                    try {pending.put(chunk);}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();throw new CancellationException("流式读取已取消");}
                });
                return null;
            }finally{TenantContextHolder.clear();}
        });
        var producer=Thread.ofVirtual().name("data-sync-source").start(read);
        try {
            while(true) {
                var chunk=pending.poll(100,TimeUnit.MILLISECONDS);
                if(chunk!=null){target.accept(chunk);continue;}
                if(read.isDone()&&pending.isEmpty()){read.get();return;}
            }
        }catch(ExecutionException ex){
            if(ex.getCause() instanceof Exception cause)throw cause;
            if(ex.getCause() instanceof Error cause)throw cause;
            throw ex;
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw ex;}
        finally {read.cancel(true);producer.interrupt();}
    }
}
