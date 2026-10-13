package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderLineFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderLineFieldCarryQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Owner/XML on empty private clones; the full physical quantity/customer/audit columns are protected. */
@EnabledIfSystemProperty(named="npdms.line-field-carry.mysql",matches="true")
class DppmsOrderLineFieldCarryMapperMySqlTest {
    final long tenant=8_000_000_323L;
    Connection db;SqlSession session;DppmsOrderLineFieldCarryMapper mapper;DppmsOrderLineFieldCarryOwner owner;
    @BeforeEach void fixture() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&serverTimezone=Asia/Shanghai",System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        try(var sql=db.createStatement()) {
            for(String table:List.of("com_sales_order","com_sales_order_line")) {
                String ddl;try(var schema=sql.executeQuery("SHOW CREATE TABLE "+table)) {assertTrue(schema.next());ddl=schema.getString(2);}
                assertTrue(ddl.startsWith("CREATE TABLE `"+table+"`"));
                ddl=ddl.replaceFirst("CREATE TABLE `"+table+"`","CREATE TEMPORARY TABLE `"+table+"`");
                ddl=ddl.replaceAll("(?m)^\\s*CONSTRAINT[^\\n]*FOREIGN KEY[^\\n]*\\n","").replaceAll(",\\n\\)","\n)");
                sql.execute(ddl);try(var count=sql.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(count.next());assertEquals(0,count.getInt(1));}
            }
        }
        db.setAutoCommit(false);var config=new Configuration(new Environment("private-line-carry",new JdbcTransactionFactory(),new SingleConnectionDataSource(db,true)));
        config.setMapUnderscoreToCamelCase(true);config.addMapper(DppmsOrderLineFieldCarryMapper.class);config.addMapper(ErpOrderSyncMapper.class);
        for(String path:List.of("mapper/migration/DppmsOrderLineFieldCarryMapper.xml","mapper/order/ErpOrderSyncMapper.xml"))
            try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,config,path,config.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(config).openSession(db);assertSame(db,session.getConnection());mapper=session.getMapper(DppmsOrderLineFieldCarryMapper.class);
        owner=new DppmsOrderLineFieldCarryOwner(mapper,session.getMapper(ErpOrderSyncMapper.class));TenantContextHolder.setTenantId(tenant);
        try(var sql=db.createStatement()) {
            sql.executeUpdate("INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_id,company_code,company_name,order_type,order_no,source_lifecycle_status,source_updated_at,status,creator,updater) VALUES(10,"+tenant+",'D365','DPPMS|D365|001|1|ORDER','opaque-parent',1,'001','公司一','1','ORDER','RETURNED','2026-08-02 23:51:24','DISABLED','fixture','fixture')");
            sql.executeUpdate("INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,company_code,order_type,order_no,line_no,line_type,product_code,product_model,product_desc,product_id,customer_id,customer_code,customer_name,order_qty,open_qty,delivered_qty,unit_scale,quantity_status,source_lifecycle_status,source_updated_at,status,version,creator,create_time,updater,update_time) VALUES(20,"+tenant+",10,'ERP','DPPMS|D365|01|1|ORDER|1','opaque-line','01','1','ORDER','1','1','PRODUCT','MODEL',' 原描述 ',88,99,'CUSTOMER','原买方',-2,-0.25,-1.75,3,'PENDING_AUTHORITY','RETURNED','2026-08-02 23:51:27','DISABLED',0,'old-creator','2020-01-01','old-updater','2020-02-01')");
        }
    }
    @AfterEach void close() throws Exception {TenantContextHolder.clear();if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    DppmsOrderLineFieldCarryMapper.Current current() {return mapper.selectCurrent(new OrderLineFieldCarryQuery(tenant,List.of(20L))).getFirst();}
    Row row(String id,DppmsOrderLineFieldCarryMapper.Current before) {
        var helper=new DppmsOrderLineFieldCarryOwnerTest();var source=helper.row(id);var fields=new LinkedHashMap<>(source.fields());
        var original=JsonUtils.parseMap(fields.get("originalSourceFields").toString());original.put("customInfo",Map.of("inventTransId","RAW-TX"));
        original.replaceAll((name,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);fields.put("originalSourceFields",JsonUtils.toJsonString(original));
        fields.put("lineFieldCarryProof",new DppmsOrderLineFieldCarryOwner.Proof(20L,0,"a".repeat(64),before.getFieldsFingerprint(),before.getProtectedFingerprint()));
        TenantContextHolder.setTenantId(tenant);return new Row("LINE",id,fields,20L);
    }
    Batch batch(Row... rows) {return new Batch("integration:"+tenant+":100",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void actualFieldsCarryAndReplayKeepAllQuantityCustomerAuditAndParentColumns() {
        var before=current();var input=batch(row("1",before));assertEquals("UPDATED",owner.preview(input).getFirst().action());assertEquals(before.getFieldsFingerprint(),current().getFieldsFingerprint());
        assertEquals("UPDATED",owner.apply(input).getFirst().action());var after=current();
        assertEquals("",after.getBundleCode());assertEquals("D365",after.getSourceSystem());assertEquals("001",after.getCompanyCode());assertEquals(1L,after.getCompanyId());
        assertEquals("RAW-TX",JsonUtils.parseTree(after.getCustomInfo()).path("inventTransId").asText());assertEquals(36,after.getWarrantyMonth());assertEquals(1,after.getVersion());
        assertEquals(before.getProtectedFingerprint(),after.getProtectedFingerprint());assertNotEquals(before.getFieldsFingerprint(),after.getFieldsFingerprint());
        assertEquals("UNCHANGED",owner.apply(input).getFirst().action());assertEquals(after.getFieldsFingerprint(),current().getFieldsFingerprint());
    }
    @Test void emptyAndForeignTenantScopeReturnNothingAndConcurrentVersionBlocks() {
        assertTrue(mapper.selectCurrent(new OrderLineFieldCarryQuery(tenant,List.of())).isEmpty());assertTrue(mapper.selectCurrent(new OrderLineFieldCarryQuery(tenant+1,List.of(20L))).isEmpty());
        var before=current();assertEquals(0,mapper.updateFields(new DppmsOrderLineFieldCarryMapper.FieldUpdate(tenant,1,before)));assertEquals(before.getFieldsFingerprint(),current().getFieldsFingerprint());
    }
    @Test void twoRawOriginalsWriteOnceAndBothReplay() {
        var before=current();var input=batch(row("1",before),row("2",before));var changes=owner.apply(input);assertEquals(List.of("1","2"),changes.stream().map(Change::sourceKey).toList());
        assertEquals(1,current().getVersion());assertEquals(before.getProtectedFingerprint(),current().getProtectedFingerprint());assertTrue(owner.apply(input).stream().allMatch(c->"UNCHANGED".equals(c.action())));
    }
    @Test void nativeQuantityAndForeignKeyEditsAfterReviewCannotBeOverwritten() throws Exception {
        var input=batch(row("1",current()));try(var sql=db.createStatement()){sql.executeUpdate("UPDATE com_sales_order_line SET order_qty=-3,version=version+1 WHERE id=20 AND tenant_id="+tenant);}
        session.clearCache();var changed=current();assertThrows(IllegalArgumentException.class,()->owner.apply(input));assertEquals(changed.getFieldsFingerprint(),current().getFieldsFingerprint());
    }
}
