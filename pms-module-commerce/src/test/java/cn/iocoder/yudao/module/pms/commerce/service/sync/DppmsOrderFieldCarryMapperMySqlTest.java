package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsOrderFieldCarryMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderFieldCarryQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.service.migration.DppmsExecutionOrganizationReferences;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual production XML and Owner, restricted to empty connection-local clones on the designated target. */
@EnabledIfSystemProperty(named="npdms.order-field-carry.mysql",matches="true")
class DppmsOrderFieldCarryMapperMySqlTest {
    private final long tenant=8_000_000_321L;
    private final LocalDateTime clock=LocalDateTime.of(2026,7,1,10,20,30);
    private Connection db;private SqlSession session;private DppmsOrderFieldCarryMapper mapper;private DppmsOrderFieldCarryOwner owner;
    @BeforeEach void fixture() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        try(var sql=db.createStatement()) {
            String ddl;
            try(var schema=sql.executeQuery("SHOW CREATE TABLE com_sales_order")) {assertTrue(schema.next());ddl=schema.getString(2);}
            assertTrue(ddl.startsWith("CREATE TABLE `com_sales_order`"));
            sql.execute(ddl.replaceFirst("CREATE TABLE `com_sales_order`","CREATE TEMPORARY TABLE `com_sales_order`"));
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM com_sales_order")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1),"Must use the empty temporary clone");}
        }
        db.setAutoCommit(false);
        var configuration=new Configuration(new Environment("temporary-order-field-carry",new JdbcTransactionFactory(),new SingleConnectionDataSource(db,true)));
        configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(DppmsOrderFieldCarryMapper.class);configuration.addMapper(ErpOrderSyncMapper.class);
        for(String path:List.of("mapper/migration/DppmsOrderFieldCarryMapper.xml","mapper/order/ErpOrderSyncMapper.xml"))
            try(var xml=getClass().getClassLoader().getResourceAsStream(path)) {assertNotNull(xml);new XMLMapperBuilder(xml,configuration,path,configuration.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(configuration).openSession(db);assertSame(db,session.getConnection());
        mapper=session.getMapper(DppmsOrderFieldCarryMapper.class);
        var organizations=mock(DppmsExecutionOrganizationReferences.class);var company=mock(ManagedOrganizationApi.Node.class);
        when(company.id()).thenReturn(1L);when(company.name()).thenReturn("公司一");
        when(organizations.load()).thenReturn(new DppmsExecutionOrganizationReferences.Directory(Map.of("001",company),Map.of()));
        owner=new DppmsOrderFieldCarryOwner(mapper,session.getMapper(ErpOrderSyncMapper.class),organizations);TenantContextHolder.setTenantId(tenant);
        try(var insert=db.prepareStatement("INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,order_type,order_no,customer_code,order_comment,order_amount,currency_code,source_lifecycle_status,source_updated_at,status,version,creator,create_time,updater,update_time,deleted) VALUES(20,?,'ERP','DPPMS|D365|01|0|ORDER',?,'01','0','ORDER','CUSTOMER','原备注',123.00,'CNY','ACTIVE',?,'DISABLED',0,'old-creator','2020-01-01','old-updater','2020-02-01',0)")) {
            insert.setLong(1,tenant);insert.setString(2,clock.toString());insert.setObject(3,clock);assertEquals(1,insert.executeUpdate());
        }
    }
    @AfterEach void close() throws Exception {
        TenantContextHolder.clear();if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();
    }
    private DppmsOrderFieldCarryOwner.Entry entry(String key,DppmsOrderFieldCarryMapper.Current before) {
        var originals=new LinkedHashMap<String,Object>();for(String name:List.of("id","orderNumber","contractNo","orderExecNumber","orderCreateTime","customerRequireTime","customerCode","customerName","projectName","orderComment","orderType","compCode","salesType","source","customInfo","syncTime"))originals.put(name,null);
        originals.put("id",key);originals.put("source","D365");
        originals.put("orderNumber","ORDER");originals.put("orderType",0);originals.put("compCode","01");originals.put("customerCode","CUSTOMER");
        originals.put("contractNo","CONTRACT");originals.put("orderExecNumber","EXEC");originals.put("orderComment"," 原备注 ");
        var proof=new DppmsOrderFieldCarryOwner.Proof(20L,0L,"a".repeat(64),before.getFieldsFingerprint(),before.getProtectedFingerprint());
        var fact=new CommerceSalesOrderFact(before.getSourceKey(),before.getSourceVersion(),before.getSourceVersion(),"001","ORDER","0","CUSTOMER",null,null,null,
                CommerceSourceLifecycleStatus.ACTIVE,clock,null,null," 原备注 ",null,null,null,"CONTRACT","EXEC");
        return new DppmsOrderFieldCarryOwner.Entry(new Row("ORDER",key,Map.of(),20L),fact,proof,"a".repeat(64),originals);
    }
    private Batch batch(DppmsOrderFieldCarryOwner.Entry... entries) {return new Batch("integration:"+tenant+":100",Arrays.stream(entries).map(DppmsOrderFieldCarryOwner.Entry::source).toList(),List.of(),true,"RETAIN",false,"UPSERT",false);}
    private DppmsOrderFieldCarryMapper.Current current() {return mapper.selectCurrent(new OrderFieldCarryQuery(tenant,List.of(20L))).getFirst();}

    @Test void actualXmlCarriesFieldsAndReplaysWithoutChangingProtectedColumns() {
        var before=current();var e=entry("1",before);assertEquals("UPDATED",owner.preview(batch(e),List.of(e)).getFirst().action());
        assertEquals(before.getFieldsFingerprint(),current().getFieldsFingerprint());
        assertEquals("UPDATED",owner.apply(batch(e),List.of(e)).getFirst().action());var after=current();
        assertEquals("D365",after.getSourceSystem());assertEquals("001",after.getCompanyCode());assertEquals(1L,after.getCompanyId());assertEquals("公司一",after.getCompanyName());
        assertEquals("CONTRACT",after.getContractNo());assertEquals("EXEC",after.getExecutionNo());assertEquals(" 原备注 ",after.getOrderComment());assertEquals(1L,after.getVersion());
        assertEquals(before.getProtectedFingerprint(),after.getProtectedFingerprint());assertNotEquals(before.getFieldsFingerprint(),after.getFieldsFingerprint());
        assertEquals("UNCHANGED",owner.apply(batch(e),List.of(e)).getFirst().action());assertEquals(after.getFieldsFingerprint(),current().getFieldsFingerprint());
    }
    @Test void emptyOrForeignTenantIdsReturnNothingAndStaleVersionDoesNotWrite() {
        assertTrue(mapper.selectCurrent(new OrderFieldCarryQuery(tenant,List.of())).isEmpty());assertTrue(mapper.selectCurrent(new OrderFieldCarryQuery(tenant+1,List.of(20L))).isEmpty());
        var before=current();assertEquals(0,mapper.updateFields(new DppmsOrderFieldCarryMapper.FieldUpdate(tenant,1L,before)));
        assertEquals(before.getFieldsFingerprint(),current().getFieldsFingerprint());
    }
    @Test void bothOriginalAliasesUpdateTheActualRowOnlyOnce() {
        var before=current();var a=entry("1",before);var b=entry("2",before);
        var changes=owner.apply(batch(a,b),List.of(a,b));assertEquals(List.of("1","2"),changes.stream().map(Change::sourceKey).toList());assertEquals(1L,current().getVersion());
        assertEquals(before.getProtectedFingerprint(),current().getProtectedFingerprint());assertTrue(owner.apply(batch(a,b),List.of(a,b)).stream().allMatch(c->"UNCHANGED".equals(c.action())));
    }
    @Test void aNativeEditAfterReviewBlocksWithoutOverwritingItsContent() throws Exception {
        var e=entry("1",current());try(var sql=db.createStatement()){sql.executeUpdate("UPDATE com_sales_order SET order_comment='changed-by-native',version=version+1 WHERE id=20 AND tenant_id="+tenant);}
        session.clearCache();var changed=current();assertThrows(IllegalArgumentException.class,()->owner.apply(batch(e),List.of(e)));
        assertEquals(changed.getFieldsFingerprint(),current().getFieldsFingerprint());assertEquals("changed-by-native",current().getOrderComment());
    }
}
