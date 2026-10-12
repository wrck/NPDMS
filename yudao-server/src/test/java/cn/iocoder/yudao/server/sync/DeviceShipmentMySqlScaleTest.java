package cn.iocoder.yudao.server.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.generic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real 4m source cursor + target read/write benchmark on complete production
 * device/shipment DDL. This isolates business DML cost: PLT/binding/scheduler and
 * organization projection cost are NOT included. The companion acceptance test
 * exercises runner + real PLT evidence separately. Generated data, not DPPMS export.
 * Existing tables/tenants are never dropped or cleared; tenant 3 must be empty. */
@EnabledIfSystemProperty(named="pms.sync.mysql.scale",matches="true")
class DeviceShipmentMySqlScaleTest {
    static final long TENANT=3L, BASE=10_000_000L;
    @Test void fourMillionActualDevicesAndShipments() throws Exception {
        int total=Integer.getInteger("pms.sync.mysql.scale.rows",4_000_000);
        assertTrue(total>0 && total<=4_000_000);
        var ds=new DriverManagerDataSource(DeviceShipmentMySqlAcceptanceTest.URL,"root","");
        var jdbc=new JdbcTemplate(ds);
        assertEquals(DeviceShipmentMySqlAcceptanceTest.DB,jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=3",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=3",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM fb_shipment_barcode WHERE id>=?",Long.class,BASE));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order WHERE id=30 AND tenant_id=3 AND order_no='ORDER-A'",Integer.class)==0)jdbc.update("""
          INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,
            order_type,order_no,status,creator,updater) VALUES(30,3,'ERP','ORDER-A','1','COMPANY-A','SALE','ORDER-A','ACTIVE','test','test')
          """);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order_line WHERE id=31 AND tenant_id=3 AND order_id=30 AND order_no='ORDER-A' AND line_no='1' AND product_code='PRODUCT-A'",Integer.class)==0)jdbc.update("""
          INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,
            company_code,order_type,order_no,line_no,product_code,unit_code,unit_scale,quantity_status,status,creator,updater)
          VALUES(31,3,30,'ERP','LINE-A','1','COMPANY-A','SALE','ORDER-A','1','PRODUCT-A','EA',0,'PENDING','ACTIVE','test','test')
          """);
        jdbc.execute("CREATE TABLE IF NOT EXISTS sync_test_digits(n INT PRIMARY KEY) ENGINE=InnoDB");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM sync_test_digits",Integer.class)==0)
            for(int i=0;i<10;i++)jdbc.update("INSERT INTO sync_test_digits VALUES(?)",i);
        assertEquals(java.util.stream.IntStream.range(0,10).boxed().toList(),jdbc.queryForList("SELECT n FROM sync_test_digits ORDER BY n",Integer.class));
        long seedStart=System.nanoTime();
        String series="SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n AS n FROM sync_test_digits a CROSS JOIN sync_test_digits b CROSS JOIN sync_test_digits c CROSS JOIN sync_test_digits d CROSS JOIN sync_test_digits e CROSS JOIN sync_test_digits f";
        for(int offset=0;offset<total;offset+=1_000_000){
            int take=Math.min(1_000_000,total-offset);
            jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,item,com_barcode,pack_id,warrantyMonth,orderNumber,lineNum,uuid,source_only_note) "+
                "SELECT ?+n,CONCAT('SCALE-',?+n),'PRODUCT-A',CONCAT('INTERNAL-',?+n),1,24,'ORDER-A','1',CONCAT('SCALE-UUID-',?+n),'generated-scale-fixture' FROM ("+series+") series_rows WHERE n<?",BASE+offset,offset,offset,offset,take);
            System.out.println("REAL_MYSQL_SEED_PROGRESS rows="+(offset+take));
        }
        System.out.println("REAL_MYSQL_SOURCE_SEED="+JsonUtils.toJsonString(Map.of("rows",total,"seconds",(System.nanoTime()-seedStart)/1e9)));
        TenantContextHolder.setTenantId(TENANT);
        try(var context=new AnnotationConfigApplicationContext(DeviceShipmentMySqlAcceptanceTest.Config.class)) {
            var definition=new GenericSyncTemplates().list(1L).stream().filter(t->t.key().equals("shipment-barcode")).findFirst().orElseThrow().definition();
            // The only SQL difference is a test namespace predicate, retaining all joins and mappings.
            definition.sources().getFirst().sql(definition.sources().getFirst().sql()+" WHERE b.id >= "+BASE);
            definition=definition.toBuilder().chunkSize(1000).fetchSize(1000).build();
            var reader=context.getBean(SpringJdbcStreamingReader.class);
            var mapper=context.getBean(SyncFieldMapper.class);
            var engine=context.getBean(GenericSyncEngine.class);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            for(String pass:List.of("INITIAL_IMPORT","UNCHANGED_REPLAY")) {
                var bound=engine.bind(definition);var d=definition;
                long[] observed={0},peak={0};var actions=new TreeMap<String,Long>();long begin=System.nanoTime();
                var read=reader.stream(definition,null,true,null,chunk->{
                    if(chunk.rows().isEmpty())return;
                    var snapshot=new MysqlSyncReader.Snapshot(chunk.upper(),List.of(new MysqlSyncReader.SourceRows(chunk.object(),chunk.sourceObject(),chunk.rows())),chunk.bytes());
                    var rows=mapper.transform(d,snapshot,List.of());
                    var changes=tx.execute(s->bound.apply(new DataSyncAdapter.Batch("actual-mysql-scale",rows,List.of(),true,"RETAIN",false,"UPSERT",false)));
                    assertNotNull(changes);assertEquals(rows.size(),changes.size());
                    for(var change:changes){
                        assertEquals(pass.equals("INITIAL_IMPORT")?"CREATED":"UNCHANGED",change.action(),change::message);
                        assertNotNull(change.targetId());actions.merge(change.action(),1L,Long::sum);
                    }
                    observed[0]+=rows.size();peak[0]=Math.max(peak[0],ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                    if(observed[0]%100_000==0)System.out.println("REAL_MYSQL_BUSINESS_PROGRESS pass="+pass+" rows="+observed[0]+" seconds="+(System.nanoTime()-begin)/1e9);
                });
                double seconds=(System.nanoTime()-begin)/1e9;
                assertEquals(total,read.rows());assertEquals(total,observed[0]);
                System.out.println("REAL_MYSQL_BUSINESS_SCALE="+JsonUtils.toJsonString(Map.of("kind","REAL_SOURCE_CURSOR_AND_BUSINESS_TARGET_DML","pass",pass,"rows",total,"seconds",seconds,"rowsPerSecond",total/seconds,"peakSampledHeapBytes",peak[0],"actions",actions,"evidenceAndOrganizationProjectionIncluded",false)));
            }
            assertEquals(total,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=3",Long.class));
            assertEquals(total,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=3",Long.class));
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment s LEFT JOIN ast_device d ON d.tenant_id=s.tenant_id AND d.sn=s.device_sn WHERE s.tenant_id=3 AND (d.id IS NULL OR d.shipment_record_id IS NULL OR d.shipment_record_id<>s.id OR s.order_line_id IS NULL OR s.order_line_id<>31)",Long.class));
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=4",Long.class));
            System.out.println("REAL_MYSQL_SCALE_RELATIONS=PASS");
        } finally {TenantContextHolder.clear();}
    }
}
