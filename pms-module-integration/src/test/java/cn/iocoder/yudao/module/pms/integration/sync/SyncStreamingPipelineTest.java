package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SyncStreamingPipelineTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    SpringJdbcStreamingReader.StreamChunk chunk(int sequence){
        return new SpringJdbcStreamingReader.StreamChunk(LocalDateTime.now(),0,"ROW","source",
                List.of(Map.of("id",sequence)),sequence,16,false,sequence);
    }
    @Test void prefetchIsBoundedAndCommitsStayOrderedWithTenantContext()throws Exception {
        TenantContextHolder.setTenantId(7L);
        var produced=new AtomicInteger();var third=new CountDownLatch(1);List<Integer> commits=new ArrayList<>();
        SyncStreamingPipeline.consume(sink->{
            assertEquals(7L,TenantContextHolder.getRequiredTenantId());
            for(int i=1;i<=20;i++){
                produced.incrementAndGet();if(i==3)third.countDown();sink.accept(chunk(i));
            }
        },value->{
            if(value.sequence()==1){
                try {assertTrue(third.await(2,TimeUnit.SECONDS));}
                catch(InterruptedException ex){throw new AssertionError(ex);}
                assertEquals(3,produced.get());
            }
            assertEquals(7L,TenantContextHolder.getRequiredTenantId());commits.add(value.sequence());
        });
        assertEquals(java.util.stream.IntStream.rangeClosed(1,20).boxed().toList(),commits);
    }
    @Test void targetFailureStopsProducerAndNeverCommitsQueuedChunks()throws Exception {
        TenantContextHolder.setTenantId(1L);var closed=new CountDownLatch(1);List<Integer> commits=new ArrayList<>();
        var failure=assertThrows(IllegalStateException.class,()->SyncStreamingPipeline.consume(sink->{
            try {for(int i=1;i<=100;i++)sink.accept(chunk(i));}finally{closed.countDown();}
        },value->{
            if(value.sequence()==2)throw new IllegalStateException("target rollback");
            commits.add(value.sequence());
        }));
        assertEquals("target rollback",failure.getMessage());assertEquals(List.of(1),commits);
        assertTrue(closed.await(2,TimeUnit.SECONDS));
    }
    @Test void sourceFailurePropagatesAfterPreviouslyEmittedChunks() {
        TenantContextHolder.setTenantId(1L);List<Integer> commits=new ArrayList<>();
        var failure=assertThrows(SQLException.class,()->SyncStreamingPipeline.consume(sink->{
            sink.accept(chunk(1));sink.accept(chunk(2));throw new SQLException("source disconnected");
        },value->commits.add(value.sequence())));
        assertEquals("source disconnected",failure.getMessage());assertEquals(List.of(1,2),commits);
    }
}
