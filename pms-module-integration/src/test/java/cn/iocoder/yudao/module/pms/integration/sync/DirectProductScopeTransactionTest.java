package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P22_TEMP_MYSQL_TEST",matches="true")
class DirectProductScopeTransactionTest {
    java.sql.Connection temporaryConnection;
    JdbcTemplate jdbc;TransactionTemplate tx;DirectProductScopePersistence writer;DirectProductScopeMapper mapper;DirectScopeLookupMapper lookup;
    final ProjectScopeMigrationContextApi projects=mock(ProjectScopeMigrationContextApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        // Every name accessed by the XML is shadowed on this one connection before fixture inserts.
        var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));
        temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var source=new SingleConnectionDataSource(temporaryConnection,true);
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE com_sales_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,contract_no VARCHAR(64),source_system VARCHAR(20),company_code VARCHAR(20),order_type VARCHAR(20),status VARCHAR(20),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE com_sales_order_line(id BIGINT PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,order_no VARCHAR(20),line_no VARCHAR(20),product_code VARCHAR(50),source_system VARCHAR(20),company_code VARCHAR(20),company_name VARCHAR(100),order_type VARCHAR(20),status VARCHAR(20),order_qty DECIMAL(18,6),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE com_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,contract_no VARCHAR(64),company_code VARCHAR(20),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE com_project_contract_relation(id BIGINT PRIMARY KEY,tenant_id BIGINT,contract_id BIGINT,project_id BIGINT,status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE com_order_contract_relation(id BIGINT PRIMARY KEY,tenant_id BIGINT,order_id BIGINT,contract_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
        jdbc.execute("""
CREATE TEMPORARY TABLE com_delivery_scope(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,project_code VARCHAR(64),project_name VARCHAR(200),project_company_code VARCHAR(64),project_company_name VARCHAR(100),project_department_code VARCHAR(64),project_department_name VARCHAR(100),project_customer_code VARCHAR(64),project_customer_name VARCHAR(100),project_manager_employee_no VARCHAR(64),project_manager_name VARCHAR(100),order_line_id BIGINT,order_source_system VARCHAR(20),order_company_code VARCHAR(64),order_company_name VARCHAR(100),order_type VARCHAR(20),order_no VARCHAR(64),line_no VARCHAR(30),product_code VARCHAR(100),product_desc VARCHAR(255),allocated_qty DECIMAL(18,6),allocation_confirmed BOOLEAN,delivered_quantity DECIMAL(18,6),open_quantity DECIMAL(18,6),scope_status VARCHAR(20),allocation_version BIGINT,allocation_source VARCHAR(30),change_reason VARCHAR(200),department_id BIGINT,department_code VARCHAR(64),department_name VARCHAR(100),department_version INTEGER,source_evidence VARCHAR(200),effective_from TIMESTAMP(3),effective_to TIMESTAMP(3),status VARCHAR(20),version BIGINT,creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)
""");
        jdbc.execute("""
CREATE TEMPORARY TABLE com_delivery_scope_detail(id BIGINT PRIMARY KEY,tenant_id BIGINT,delivery_scope_id BIGINT,detail_sequence INTEGER,serial_no VARCHAR(100),product_code VARCHAR(100),product_name VARCHAR(255),device_type_code VARCHAR(50),device_type_name VARCHAR(100),delivery_batch_no VARCHAR(64),source_record_key VARCHAR(200),allocated_qty DECIMAL(18,6),detail_status VARCHAR(20),source_snapshot VARCHAR(1000),remark VARCHAR(1000),version INTEGER,creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)
""");
        jdbc.execute("CREATE TEMPORARY TABLE com_delivery_scope_project_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,scope_version BIGINT,deleted BOOLEAN)");
        jdbc.update("INSERT INTO com_sales_order VALUES(30,1,'C','D365','001','0','ENABLED',FALSE)");
        jdbc.update("INSERT INTO com_sales_order_line VALUES(40,1,30,'O','1','I','D365','001','Company','0','ENABLED',4,FALSE)");
        jdbc.update("INSERT INTO com_contract VALUES(11,1,'C','001',FALSE)");
        jdbc.update("INSERT INTO com_project_contract_relation VALUES(20,1,11,100,'ACTIVE',NULL,NULL,FALSE)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("DirectProductScopeMapper","DirectScopeLookupMapper")) {
            String path="mapper/sync/"+name+".xml";
            try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));mapper=session.getMapper(DirectProductScopeMapper.class);lookup=session.getMapper(DirectScopeLookupMapper.class);
        var context=new ProjectScopeMigrationContextApi.Context(100L,3L,"P","Project","001","Company","C","Customer","E","Manager",20L,"162007","Office",7);
        when(projects.resolve(any())).thenReturn(context);when(projects.lockAndRevalidate(any())).thenReturn(context);
        writer=new DirectProductScopePersistence(source,projects,mapper,lookup,new DirectProductScopeVersions(lookup));
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Entry entry(String id) {
        var raw=new LinkedHashMap<String,String>();List.of("id","projectId","contractNo","itemCode","itemName","projectQuantity","orderQuantity","deliverQuantity","openQuantity","orderNumber","lineNum").forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id",id,"projectId","101","contractNo","C","itemCode","I","itemName","原产品","orderQuantity","4","deliverQuantity","3","openQuantity","1","orderNumber","O","lineNum","1"));
        return new Entry(id,raw,null);
    }
    Command command(Entry... entries){return new Command(1L,7L,100L,3L,"101",List.of(entries),9L);}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void fullXmlJoinPreviewAndNewRootDetailPreserveUnconfirmedHistoricalQuantities() {
        var input=command(entry("1"));assertEquals("CREATED",tx.execute(status->writer.preview(input)).getFirst().action());assertEquals(0,count("com_delivery_scope"));assertEquals(0,count("com_delivery_scope_detail"));
        assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input)).getFirst();assertEquals("CREATED",saved.action());assertNotEquals(1L,saved.targetId());
        assertFalse(jdbc.queryForObject("SELECT allocation_confirmed FROM com_delivery_scope",Boolean.class));
        assertEquals(new BigDecimal("4.000000"),jdbc.queryForObject("SELECT allocated_qty FROM com_delivery_scope",BigDecimal.class));
        assertEquals(new BigDecimal("3.000000"),jdbc.queryForObject("SELECT delivered_quantity FROM com_delivery_scope",BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT serial_no FROM com_delivery_scope_detail",String.class));assertEquals(0,count("com_delivery_scope_project_version"));
        assertEquals(new BigDecimal("4.000000"),jdbc.queryForObject("SELECT order_qty FROM com_sales_order_line",BigDecimal.class));
        var fields=saved.fields();var baseline=new Baseline(saved.targetId(),(String)fields.get("creator"),(java.time.LocalDateTime)fields.get("createTime"),(String)fields.get("updater"),(java.time.LocalDateTime)fields.get("updateTime"));
        assertEquals("UNCHANGED",tx.execute(status->writer.apply(command(new Entry("1",entry("1").originalFields(),baseline)))).getFirst().action());
        assertEquals(1,count("com_delivery_scope_detail"));
    }
    @Test void secondDetailSqlFailureRollsBackAllScopeRootsAndEarlierDetail() {
        jdbc.execute("ALTER TABLE com_delivery_scope_detail ADD CONSTRAINT second_source_rejected CHECK(source_record_key<>'DPPMS:pm_project_product_line:101:2:PRODUCT')");
        var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.apply(command(entry("1"),entry("2")))));
        assertEquals(3819,failure.getSQLException().getErrorCode());
        assertTrue(failure.getSQLException().getMessage().contains("second_source_rejected"));
        assertEquals(0,count("com_delivery_scope"));assertEquals(0,count("com_delivery_scope_detail"));
    }
    @Test void missingContractRelationAndChangedOrderCannotBeGuessed() {
        jdbc.update("UPDATE com_project_contract_relation SET status='HISTORICAL'");assertEquals("ISSUE",tx.execute(status->writer.preview(command(entry("1")))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1")))));assertEquals(0,count("com_delivery_scope"));
        jdbc.update("UPDATE com_project_contract_relation SET status='ACTIVE'");jdbc.update("UPDATE com_sales_order_line SET order_qty=5");
        assertEquals("CONFLICT",tx.execute(status->writer.preview(command(entry("1")))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1")))));
    }
    @Test void sourceTenantAndProjectScopeRemainRequiredAndTemplateIsIndependent() {
        doThrow(new IllegalArgumentException("forbidden")).when(projects).lockAndRevalidate(any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1")))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(new Command(2L,7L,100L,3L,"101",List.of(entry("1")),9L))));
        var adapter=new DppmsDirectProductScopeTask(mapper,lookup,new DriverManagerDataSource(),projects,mock(cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.class),mock(ProjectMasterMigrationApi.class),mock(SyncTaskService.class),mock(SyncRunMapper.class));
        var definition=DppmsDirectProductScopeTemplate.create(9L);assertEquals("DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT",definition.adapter());assertEquals("id",definition.sources().getFirst().sourceKey());assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
}
