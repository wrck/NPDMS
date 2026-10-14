package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in bounded-memory CPU benchmark. The sink is synthetic: this is NOT database throughput acceptance. */
@EnabledIfSystemProperty(named="pms.sync.benchmark",matches="true")
class GenericSyncPlanningBenchmarkTest {
    static final class CountingStore extends GenericSyncJdbcStore {
        long inserted, lookups, singles, schemas;
        CountingStore(){super(new JdbcDataSource());}
        @Override public Map<String,Column> columns(GenericTargetCatalog.Table table){schemas++;return Map.of();}
        @Override public void validateValues(Map<String,Column> schema,Map<String,Object> values,boolean insert){}
        @Override public List<Map<String,Object>> findByKeys(GenericTargetCatalog.Table table,List<Map<String,Object>> keys,boolean lock){lookups++;return List.of();}
        @Override public List<Map<String,Object>> find(Match match){singles++;return List.of();}
        @Override public void insertBatch(GenericTargetCatalog.Table table,List<Map<String,Object>> rows,int chunkSize){inserted+=rows.size();}
        @Override public void updateBatch(GenericTargetCatalog.Table table,List<BatchUpdate> updates){fail("Unexpected update in empty-target benchmark");}
    }
    @Test void fourMillionDeviceShipmentPlansUseBoundedChunks() {
        int total=Integer.getInteger("pms.sync.benchmark.rows",4_000_000);
        int chunk=Integer.getInteger("pms.sync.benchmark.chunk",1000);
        assertTrue(total>0 && chunk>0 && chunk<=5000);
        TenantContextHolder.setTenantId(1L);
        try {
            var definition=new GenericSyncTemplates().list(1L).stream().filter(t->t.key().equals("device-sn")).findFirst().orElseThrow().definition();
            var transactionSource=new JdbcDataSource();
            transactionSource.setURL("jdbc:h2:mem:planning_benchmark;DB_CLOSE_DELAY=-1");transactionSource.setUser("sa");
            var tx=new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(transactionSource));
            var store=new CountingStore();
            var adapter=new GenericSyncEngine(new GenericTargetCatalog(),store,event->{}).bind(definition);
            long started=System.nanoTime();long peakUsed=0;
            for(int offset=0;offset<total;offset+=chunk) {
                List<DataSyncAdapter.Row> rows=new ArrayList<>();
                for(int n=offset;n<Math.min(offset+chunk,total);n++) {
                    Map<String,Object> fields=new LinkedHashMap<>();
                    for(var step:definition.sources().getFirst().targets())for(var mapping:step.mappings())
                        if(mapping.source()!=null&&!mapping.source().startsWith("$"))fields.put(mapping.source(),null);
                    fields.put("barcode","BENCH-"+n);fields.put("pack_id","PACK-"+(n/20));
                    fields.put("shipment_time","2026-10-01T10:00:00");fields.put("contract_no","C-"+(n/100));
                    fields.put("event_type","SHIPMENT");fields.put("rma_marked",0);
                    fields.put("item","PRODUCT-1");fields.put("orderNumber","ORDER-"+(n/50));
                    rows.add(new DataSyncAdapter.Row("DEVICE_SN",Integer.toString(n+1),fields,null));
                }
                var result=tx.execute(status->adapter.apply(new DataSyncAdapter.Batch("benchmark",rows,List.of(),true,"RETAIN",false,"UPSERT",false)));
                assertEquals(rows.size(),result.size());
                assertTrue(result.stream().allMatch(c->"CREATED".equals(c.action())&&c.targetId()!=null));
                peakUsed=Math.max(peakUsed,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                if((offset+rows.size())%100_000==0)System.out.println("SYNC_PLAN_PROGRESS rows="+(offset+rows.size()));
            }
            double seconds=(System.nanoTime()-started)/1_000_000_000.0;
            assertEquals(total,store.inserted);assertEquals(0,store.singles);
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("kind","CPU_ONLY_SYNTHETIC_SINK");report.put("rows",total);report.put("chunkSize",chunk);
            report.put("seconds",seconds);report.put("rowsPerSecond",total/seconds);
            report.put("peakSampledHeapBytes",peakUsed);report.put("batchLookupCalls",store.lookups);
            report.put("singleLookupCalls",store.singles);report.put("schemaReads",store.schemas);
            System.out.println("SYNC_PLANNING_BENCHMARK="+JsonUtils.toJsonString(report));
        } finally {TenantContextHolder.clear();}
    }
}
