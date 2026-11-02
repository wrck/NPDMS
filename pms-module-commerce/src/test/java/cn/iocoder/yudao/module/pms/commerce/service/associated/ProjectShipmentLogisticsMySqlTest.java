package cn.iocoder.yudao.module.pms.commerce.service.associated;

import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.associated.ProjectShipmentLogisticsMapper;
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
class ProjectShipmentLogisticsMySqlTest {
    java.sql.Connection db;
    java.net.URLClassLoader loader;
    ProjectShipmentLogisticsMapper mapper;
    JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
        db=driver.connect(System.getenv("BASE_PRODUCT_DB_URL"),properties);
        var data=new SingleConnectionDataSource(db,true);jdbc=new JdbcTemplate(data);
        // Session-local shadows: persistent tables and rows are never modified.
        for (var table : List.of("com_shipment_package", "com_shipment_contract_reference", "com_contract", "com_project_contract_relation")) {
            jdbc.execute("CREATE TEMPORARY TABLE fixture_"+table+" LIKE npdms_reverify_20261014."+table);
            jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE fixture_"+table);
        }
        jdbc.update("INSERT INTO com_contract(id,tenant_id,contract_no,company_code,master_source_system,creator,updater,deleted) VALUES(1,1,'C-1','TEST','DPPMS','fixture','fixture',0)");
        jdbc.update("INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,status,source_system,creator,updater,deleted) VALUES(1,1,86648,1,'ACTIVE','DPPMS','fixture','fixture',0)");
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,contract_id,contract_no,source_system,source_record_key,creator,updater,deleted) VALUES(1,1,1,'C-1','DPPMS','REF-1','fixture','fixture',0)");
        jdbc.update("INSERT INTO com_shipment_package(id,tenant_id,shipment_contract_ref_id,package_no,source_system,source_record_key,carrier_name,express_no,receiver_name,creator,updater,deleted) VALUES(1,1,1,'P-1','DPPMS','PK-1','Carrier','EXP-1','Receiver','fixture','fixture',0),(2,1,1,'P-2','DPPMS','PK-2','Wrong','Wrong','Wrong','fixture','fixture',0),(3,2,1,'P-1','DPPMS','PK-3','Wrong','Wrong','Wrong','fixture','fixture',0),(4,1,1,'P-4','DPPMS','PK-4','Wrong','Wrong','Wrong','fixture','fixture',1)");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("isolated",new SpringManagedTransactionFactory(),data));
        String resource="mapper/associated/ProjectShipmentLogisticsMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(ProjectShipmentLogisticsMapper.class);
    }
    @AfterEach void close() throws Exception {try{if(db!=null)db.close();}finally{if(loader!=null)loader.close();}}
    Query query() { return new Query(1L,7L,86648L,List.of(new Reference("DPPMS","P-1","C-1"))); }
    @Test void exactReferencesTenantDeletionAndEmptyScope() {
        assertEquals(List.of(new Fact("DPPMS","P-1","C-1","Carrier","EXP-1","Receiver")),mapper.selectFacts(query()));
        assertEquals(List.of(),mapper.selectFacts(new Query(1L,7L,86648L,List.of())));
        assertEquals(List.of(),mapper.selectFacts(new Query(1L,7L,86649L,query().references())));
        assertEquals(List.of(),mapper.selectFacts(new Query(2L,7L,86648L,query().references())));
        assertEquals(List.of(),mapper.selectFacts(new Query(1L,7L,86648L,List.of(new Reference("DPPMS","P-1","c-1")))));
    }
    @Test void expiredInactiveAndDeletedRelationsCannotSupplyLogistics() {
        jdbc.update("UPDATE com_project_contract_relation SET effective_to='2000-01-01'");
        assertTrue(mapper.selectFacts(query()).isEmpty());
        jdbc.update("UPDATE com_project_contract_relation SET effective_to=NULL,effective_from='2099-01-01'");
        assertTrue(mapper.selectFacts(query()).isEmpty());
        jdbc.update("UPDATE com_project_contract_relation SET effective_from=NULL,deleted=1");
        assertTrue(mapper.selectFacts(query()).isEmpty());
        jdbc.update("UPDATE com_project_contract_relation SET deleted=0,status='INACTIVE'");
        assertTrue(mapper.selectFacts(query()).isEmpty());
    }
    @Test void deletedParentCannotSupplyLogistics() {
        jdbc.update("UPDATE com_shipment_contract_reference SET deleted=1");
        assertTrue(mapper.selectFacts(query()).isEmpty());
    }
}
