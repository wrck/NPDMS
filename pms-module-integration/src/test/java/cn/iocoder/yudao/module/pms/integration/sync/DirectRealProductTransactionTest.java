package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectRealProductMigrationApi.*;
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

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P20_TEMP_MYSQL_TEST",matches="true")
class DirectRealProductTransactionTest {
    java.sql.Connection temporaryConnection;
    JdbcTemplate jdbc;TransactionTemplate tx;DirectRealProductPersistence writer;DirectRealProductMapper mapper;
    final ProjectMigrationIdentityContextApi identity=mock(ProjectMigrationIdentityContextApi.class);
    final DirectProjectAccess access=mock(DirectProjectAccess.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));
        temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE proj_project_real_product(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,contract_no VARCHAR(100),project_code VARCHAR(100),execution_no VARCHAR(100),product_category_name VARCHAR(100),product_name VARCHAR(100),product_sub_code VARCHAR(100),product_sub_model VARCHAR(100),product_sub_name VARCHAR(100),quantity DECIMAL(18,0),memo TEXT,data_source VARCHAR(25),creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectRealProductMapper.xml";
        try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));mapper=session.getMapper(DirectRealProductMapper.class);
        writer=new DirectRealProductPersistence(identity,new DirectRealProductPreparation(source,access,mapper));
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Source entry(String id,String code,Long target,String baseline) {
        var raw=new LinkedHashMap<String,String>();ProjectRealProductMigrationApi.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id",id,"projectId","101","contractNo","","projectCode","COMMERCIAL","orderExecNumber","X1","productSubCode",code,"productSubModel","MODEL","productSubName","原名称","num","-2"));
        var upstream=new LinkedHashMap<>(raw);upstream.keySet().retainAll(DirectRealProductPreparation.SHARED_COLUMNS);upstream.put("dataSource","SMS");
        return new Source(id,raw,upstream,1,target,baseline);
    }
    Command command(Source... entries){return new Command(new ProjectMigrationIdentityContextApi.Query(1L,7L,100L,3L,"101",9L),List.of(entries));}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_real_product",Integer.class);}
    @Test void previewCreateReplayAndChangedAuditAreProtected() {
        var input=command(entry("1","A",null,null));var preview=tx.execute(status->writer.preview(input)).getFirst();
        assertNull(preview.targetId());assertEquals(19,preview.fields().size());assertEquals(0,count());
        assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input)).getFirst();assertNotEquals(1L,saved.targetId());assertEquals(1,count());
        assertEquals(new BigDecimal("-2"),saved.fields().get("quantity"));assertEquals("",saved.fields().get("contractNo"));assertNull(saved.fields().get("memo"));
        var replay=command(entry("1","A",saved.targetId(),saved.baseline()));
        assertEquals("UNCHANGED",tx.execute(status->writer.apply(replay)).getFirst().action());assertEquals(1,count());
        jdbc.update("UPDATE proj_project_real_product SET updater='manual'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(replay)));
    }
    @Test void secondSqlFailureRollsBackFirstProduct() {
        jdbc.execute("ALTER TABLE proj_project_real_product ADD CONSTRAINT second_product_rejected CHECK(product_sub_code<>'B')");
        var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.apply(command(entry("1","A",null,null),entry("2","B",null,null)))));
        assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_product_rejected"));assertEquals(0,count());
    }
    @Test void exactUpstreamCoverageIntegerQuantityAndDuplicatesRejectBeforeWrite() {
        var missing=entry("1","A",null,null);missing.originalFields().remove("memo");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(missing))));
        var different=entry("1","A",null,null);different.upstreamFields().put("productSubName","different");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(different))));
        var fractional=entry("1","A",null,null);fractional.originalFields().put("num","1.5");fractional.upstreamFields().put("num","1.5");assertThrows(ArithmeticException.class,()->tx.execute(status->writer.preview(command(fractional))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(entry("1","A",null,null),entry("2","A",null,null)))));assertEquals(0,count());
    }
    @Test void existingUnboundListDeletedHistoryTenantAndPermissionDoNotGetAdopted() {
        var saved=tx.execute(status->writer.apply(command(entry("1","A",null,null)))).getFirst();
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("2","B",null,null)))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(entry("1","A",null,null)))));
        jdbc.update("UPDATE proj_project_real_product SET deleted=TRUE");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1","A",saved.targetId(),saved.baseline())))));
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(entry("1","A",null,null)))));TenantContextHolder.setTenantId(1L);
        doThrow(new IllegalArgumentException("forbidden")).when(access).getProjectForManage(any(),any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(entry("1","A",null,null)))));assertEquals(1,count());
    }
    @Test void independentConfigurationKeepsApprovedSnapshotAndNativeCursor() {
        var original=DppmsProjectRealProductSyncTemplate.create(9L);var direct=DppmsDirectRealProductTemplate.create(9L);
        assertEquals("DPPMS_PROJECT_REAL_PRODUCT",original.adapter());assertEquals("DPPMS_PROJECT_REAL_PRODUCT_DIRECT",direct.adapter());
        assertEquals("id",direct.sources().getFirst().sourceKey());assertFalse(direct.autoPaging());assertEquals("SNAPSHOT",direct.readStrategy());assertEquals("ONCE",direct.mode());
    }
}
