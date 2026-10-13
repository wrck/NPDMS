package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only connection-local temporary clones are mutated; real tables provide the approved pilot fixture. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class DppmsProductScopeMapperMySqlTest {
    Connection db;Configuration configuration;Long lineId;
    String prefix=DppmsProductScopeMapper.class.getName()+".";
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));db.setAutoCommit(false);
        configuration=new Configuration();String resource="mapper/migration/DppmsProductScopeMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        String scopeResource="mapper/scope/DeliveryScopeMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(scopeResource)) {
            assertNotNull(input);new XMLMapperBuilder(input,configuration,scopeResource,configuration.getSqlFragments()).parse();
        }
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE com_sales_order AS SELECT * FROM com_sales_order WHERE tenant_id=1 AND order_no='SO064298' AND deleted=0");
            sql.execute("CREATE TEMPORARY TABLE com_sales_order_line AS SELECT * FROM com_sales_order_line WHERE tenant_id=1 AND order_no='SO064298' AND deleted=0");
            sql.execute("CREATE TEMPORARY TABLE com_project_contract_relation AS SELECT * FROM com_project_contract_relation WHERE tenant_id=1 AND project_id=992203060198 AND deleted=0");
            sql.execute("CREATE TEMPORARY TABLE com_contract AS SELECT * FROM com_contract WHERE tenant_id=1 AND contract_no='31220230224A04' AND deleted=0");
            sql.execute("CREATE TEMPORARY TABLE com_order_contract_relation AS SELECT * FROM com_order_contract_relation WHERE tenant_id=1 AND order_id IN (SELECT id FROM com_sales_order)");
            String ddl;try(var data=sql.executeQuery("SHOW CREATE TABLE com_delivery_scope")){assertTrue(data.next());ddl=data.getString(2);}
            // MySQL forbids foreign keys on temporary tables. Keep every column, CHECK and unique
            // constraint; the exact-order query below verifies the referenced business identity.
            ddl=ddl.replaceAll("(?m)^  CONSTRAINT `[^`]+` FOREIGN KEY .*\\r?\\n", "");
            sql.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
            try(var data=sql.executeQuery("SHOW CREATE TABLE com_delivery_scope_detail")){assertTrue(data.next());ddl=data.getString(2);}
            ddl=ddl.replaceAll("(?m)^  CONSTRAINT `[^`]+` FOREIGN KEY .*\\r?\\n", "");
            sql.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
            sql.execute("ALTER TABLE com_delivery_scope DROP INDEX uk_scope_version, ADD UNIQUE KEY uk_scope_version "
                    + "(tenant_id,(CASE WHEN allocation_confirmed=1 THEN order_line_id ELSE NULL END),project_id,allocation_version)");
        }
        var ids=ids("selectAssociatedLines",new DppmsProductScopeMapper.OrderLineQuery(1L,992203060198L,"31220230224A04","SO064298","1","01100518"));
        assertEquals(1,ids.size());lineId=ids.getFirst();
    }
    @AfterEach void cleanup() throws Exception {if(db!=null)try{db.rollback();}finally{db.close();}}
    @Test void exactContractOrderProductAndTenantAreRequiredEvenForLockedRead() throws Exception {
        var valid=new DppmsProductScopeMapper.OrderLineQuery(1L,992203060198L,"31220230224A04","SO064298","1","01100518");
        assertEquals(List.of(lineId),ids("lockAssociatedLines",valid));
        assertTrue(ids("selectAssociatedLines",new DppmsProductScopeMapper.OrderLineQuery(2L,992203060198L,"31220230224A04","SO064298","1","01100518")).isEmpty());
        assertTrue(ids("selectAssociatedLines",new DppmsProductScopeMapper.OrderLineQuery(1L,992203060198L,"OTHER","SO064298","1","01100518")).isEmpty());
        assertTrue(ids("selectAssociatedLines",new DppmsProductScopeMapper.OrderLineQuery(1L,992203060198L,"31220230224A04","SO064298","1","OTHER")).isEmpty());
    }
    DeliveryScopeDO row(long id,String evidence) {
        var row=new DeliveryScopeDO();row.setId(id);row.setTenantId(1L);row.setProjectId(992203060198L);row.setProjectCode("P");
        row.setOrderLineId(lineId);row.setOrderSourceSystem("D365");row.setOrderCompanyCode("001");row.setOrderType("0");row.setOrderNo("SO064298");row.setLineNo("1");
        row.setProductCode("01100518");row.setProductDesc("Original name");row.setAllocatedQty(new BigDecimal("4"));row.setDeliveredQuantity(new BigDecimal("4"));row.setOpenQuantity(BigDecimal.ZERO);
        row.setAllocationVersion(1L);row.setDepartmentId(178L);row.setDepartmentCode("162007");row.setDepartmentName("Office");row.setDepartmentVersion(7);
        row.setSourceEvidence(evidence);row.setEffectiveFrom(LocalDateTime.of(2026,10,10,1,2,3,123000000));
        row.setCreator("original creator");row.setUpdater("original updater");row.setCreateTime(LocalDateTime.of(2020,1,2,3,4,5,321000000));row.setUpdateTime(row.getCreateTime());return row;
    }
    @Test void historicalInsertPreservesAuditAndDoesNotOccupyCurrentSlotOrRelaxPositiveQuantity() throws Exception {
        var row=row(9991,"proof-A");assertEquals(1,insert(row));
        var second=row(9992,"proof-B");assertEquals(1,insert(second));
        try(var sql=db.createStatement();var data=sql.executeQuery("SELECT * FROM com_delivery_scope ORDER BY id")) {
            int count=0;while(data.next()) {
                count++;assertFalse(data.getBoolean("allocation_confirmed"));assertNull(data.getObject("current_order_line_id"));
                assertEquals("ACTIVE",data.getString("scope_status"));assertEquals(1,data.getLong("allocation_version"));
                assertEquals("original creator",data.getString("creator"));assertEquals(row.getCreateTime(),data.getTimestamp("create_time").toLocalDateTime());
                assertEquals(row.getUpdateTime(),data.getTimestamp("update_time").toLocalDateTime());assertEquals("DPPMS",data.getString("allocation_source"));
            }assertEquals(2,count);
        }
        assertEquals(List.of(9991L),ids("lockIdentity",new DppmsProductScopeMapper.IdentityQuery(1L,"proof-A")));
        assertTrue(ids("selectIdentity",new DppmsProductScopeMapper.IdentityQuery(2L,"proof-A")).isEmpty());
        var zero=row(9993,"proof-zero");zero.setAllocatedQty(BigDecimal.ZERO);assertThrows(SQLException.class,()->insert(zero));
        try(var sql=db.createStatement()) {
            assertEquals(1,sql.executeUpdate("UPDATE com_delivery_scope SET allocation_confirmed=1 WHERE id=9991"));
            var duplicate=assertThrows(SQLException.class,()->sql.executeUpdate("UPDATE com_delivery_scope SET allocation_confirmed=1,effective_to=effective_from WHERE id=9992"));
            assertEquals(1062,duplicate.getErrorCode());assertTrue(duplicate.getMessage().contains("uk_scope_version"));
            var current=assertThrows(SQLException.class,()->sql.executeUpdate("UPDATE com_delivery_scope SET allocation_confirmed=1,allocation_version=2 WHERE id=9992"));
            assertEquals(1062,current.getErrorCode());assertTrue(current.getMessage().contains("uk_scope_current"));
            assertEquals(1,sql.executeUpdate("UPDATE com_delivery_scope SET effective_to=effective_from WHERE id=9991"));
            assertEquals(1,sql.executeUpdate("UPDATE com_delivery_scope SET allocation_confirmed=1,allocation_version=2 WHERE id=9992"));
        }
        db.rollback();try(var sql=db.createStatement();var count=sql.executeQuery("SELECT COUNT(*) FROM com_delivery_scope")) {assertTrue(count.next());assertEquals(0,count.getInt(1));}
    }
    int insert(DeliveryScopeDO row) throws Exception {
        var mapped=configuration.getMappedStatement(prefix+"insertHistoricalScope");var parameter=Map.of("row",row);var bound=mapped.getBoundSql(parameter);
        try(var sql=db.prepareStatement(bound.getSql())) {new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);return sql.executeUpdate();}
    }
    @Test void jdbcAndDatetime3RoundReceiptsToTheStoredMillisecond() throws Exception {
        var second=LocalDateTime.of(2026,10,13,23,11,33);
        int[] nanos={123499000,123499500,123500000,266651500,999999500};
        int[] millis={123,124,124,267,1000};
        for(int index=0;index<nanos.length;index++) {
            var input=row(9900+index,"precision-"+index);
            var time=second.withNano(nanos[index]);
            input.setCreateTime(time);input.setUpdateTime(time);input.setEffectiveFrom(time);
            assertEquals(1,insert(input));
            try(var sql=db.prepareStatement("SELECT create_time,update_time,effective_from FROM com_delivery_scope WHERE id=?")) {
                sql.setLong(1,input.getId());
                try(var data=sql.executeQuery()) {
                    assertTrue(data.next());
                    for(String field:List.of("create_time","update_time","effective_from"))
                        assertEquals(second.plusNanos(millis[index]*1_000_000L),data.getTimestamp(field).toLocalDateTime(),field+":"+nanos[index]);
                }
            }
        }
    }
    @Test void formalOrderFieldsAreJoinedOnlyForAuthorizedScopeIdsAndTenant() throws Exception {
        assertEquals(1,insert(row(9991,"proof-order-fields")));
        String name=cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.class.getName()+".selectOrderFields";
        var mapped=configuration.getMappedStatement(name);
        for(var query:List.of(
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsQuery(1L,List.of(9991L)),
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsQuery(2L,List.of(9991L)),
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsQuery(1L,List.of()))) {
            var parameter=Map.of("query",query);var bound=mapped.getBoundSql(parameter);
            try(var sql=db.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);
                try(var data=sql.executeQuery()) {
                    if(query.tenantId()==1L && !query.scopeIds().isEmpty()) {
                        assertTrue(data.next());assertEquals(9991L,data.getLong("scope_id"));
                        assertEquals("31220230224A04",data.getString("contract_no"));
                        assertEquals(0,new BigDecimal("4").compareTo(data.getBigDecimal("order_quantity")));
                        assertNull(data.getString("unit_code"));assertFalse(data.next());
                    } else assertFalse(data.next());
                }
            }
        }
    }
    @Test void productDetailKeepsFullQuantityNoSerialAndIsLockedByTenantWithRollback() throws Exception {
        assertEquals(1,insert(row(9991,"proof-product")));
        var detail=new DeliveryScopeDetailDO();detail.setId(19991L);detail.setTenantId(1L);detail.setDeliveryScopeId(9991L);
        detail.setProductCode("01100518");detail.setProductName("Original product");detail.setAllocatedQty(new BigDecimal("4"));
        detail.setSourceRecordKey("proof-product:PRODUCT");detail.setCreator("1");detail.setUpdater("1");
        detail.setCreateTime(LocalDateTime.of(2026,10,10,2,3,4,123000000));detail.setUpdateTime(detail.getCreateTime());
        var mapped=configuration.getMappedStatement(prefix+"insertHistoricalProductDetail");var parameter=Map.of("row",detail);var bound=mapped.getBoundSql(parameter);
        try(var sql=db.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);assertEquals(1,sql.executeUpdate());
        }
        assertEquals(List.of(19991L),ids("selectProductDetailsForUpdate",new DppmsProductScopeMapper.ProductDetailQuery(1L,9991L)));
        assertTrue(ids("selectProductDetails",new DppmsProductScopeMapper.ProductDetailQuery(2L,9991L)).isEmpty());
        try(var sql=db.createStatement();var data=sql.executeQuery("SELECT * FROM com_delivery_scope_detail")) {
            assertTrue(data.next());assertNull(data.getString("serial_no"));assertEquals("01100518",data.getString("product_code"));
            assertEquals("Original product",data.getString("product_name"));assertEquals(0,new BigDecimal("4").compareTo(data.getBigDecimal("allocated_qty")));
            assertEquals("ACTIVE",data.getString("detail_status"));assertNull(data.getString("source_snapshot"));
            assertEquals(detail.getCreateTime(),data.getTimestamp("create_time").toLocalDateTime());assertFalse(data.next());
        }
        try(var sql=db.createStatement()) {
            assertEquals(1,sql.executeUpdate("UPDATE com_delivery_scope_detail SET deleted=1 WHERE id=19991"));
        }
        // Deleted/changed rows must be visible to the migration guard, never silently replaced.
        assertEquals(List.of(19991L),ids("selectProductDetails",new DppmsProductScopeMapper.ProductDetailQuery(1L,9991L)));
        db.rollback();
        try(var sql=db.createStatement();var data=sql.executeQuery("SELECT COUNT(*) FROM com_delivery_scope_detail")) {
            assertTrue(data.next());assertEquals(0,data.getInt(1));
        }
    }
    List<Long> ids(String name,Object query) throws Exception {
        var mapped=configuration.getMappedStatement(prefix+name);var parameter=Map.of("query",query);var bound=mapped.getBoundSql(parameter);
        try(var sql=db.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);var result=new ArrayList<Long>();
            try(var data=sql.executeQuery()){while(data.next())result.add(data.getLong("id"));}return result;
        }
    }
}
