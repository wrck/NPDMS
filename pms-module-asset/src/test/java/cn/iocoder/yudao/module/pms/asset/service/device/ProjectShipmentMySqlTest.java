package cn.iocoder.yudao.module.pms.asset.service.device;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.ProjectShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ProjectShipmentPageQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class ProjectShipmentMySqlTest {
    java.sql.Connection db;
    java.net.URLClassLoader loader;
    ProjectShipmentMapper mapper;
    @BeforeEach void setup() throws Exception {
        loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
        db=driver.connect(System.getenv("BASE_PRODUCT_DB_URL"),properties);
        var data=new SingleConnectionDataSource(db,true);var jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TEMPORARY TABLE shipment_structure LIKE npdms_reverify_20261014.ast_device_shipment");
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_shipment LIKE shipment_structure");
        jdbc.update("INSERT INTO ast_device_shipment(id,tenant_id,device_sn,contract_no,source_system,source_key,event_type,sync_status,creator,updater,deleted) VALUES(1,1,'SN','C-1','TEST','1','SHIPMENT','SUCCESS','fixture','fixture',FALSE),(2,1,'SN','C-1','TEST','2','SHIPMENT','SUCCESS','fixture','fixture',FALSE),(3,2,'OTHER','C-1','TEST','3','SHIPMENT','SUCCESS','fixture','fixture',FALSE),(4,1,'OTHER','C-2','TEST','4','SHIPMENT','SUCCESS','fixture','fixture',FALSE),(5,1,'OTHER','C-1','TEST','5','SHIPMENT','SUCCESS','fixture','fixture',TRUE),(6,1,'OTHER','c-1','TEST','6','SHIPMENT','SUCCESS','fixture','fixture',FALSE)");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("isolated",new SpringManagedTransactionFactory(),data));
        String resource="mapper/shipment/ProjectShipmentMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(ProjectShipmentMapper.class);
    }
    @AfterEach void close() throws Exception {try{if(db!=null)db.close();}finally{if(loader!=null)loader.close();}}
    @Test void exactContractTenantDeletionAndRepeatedSnArePreserved() {
        var query=new ProjectShipmentPageQuery(1L,Set.of("C-1"),null,0,20);
        assertEquals(2,mapper.count(query));assertEquals(List.of(2L,1L),mapper.selectPage(query).stream().map(row->row.getId()).toList());
        assertEquals(1,mapper.count(new ProjectShipmentPageQuery(2L,Set.of("C-1"),null,0,20)));
        assertEquals(0,mapper.count(new ProjectShipmentPageQuery(1L,Set.of(),null,0,20)));
        assertEquals(0,mapper.count(new ProjectShipmentPageQuery(1L,Set.of("C-1"),"missing",0,20)));
        assertEquals(1,mapper.selectPage(new ProjectShipmentPageQuery(1L,Set.of("C-1"),"SN",1,1)).size());
    }
}
