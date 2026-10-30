package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceProjectFilterQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceProjectModelFilterSqlTest {
    private SqlSession session;
    private DeviceMapper mapper;

    @BeforeEach void setup() throws Exception {
        var source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:project_model_filter_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=source.getConnection();var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,sn VARCHAR(64),product_code VARCHAR(64),product_model VARCHAR(64),warranty_status VARCHAR(32),deleted INT)");
            sql.execute("CREATE TABLE ast_product_official_info(tenant_id BIGINT,product_code VARCHAR(64),product_model VARCHAR(64),deleted INT,UNIQUE(tenant_id,product_code))");
            sql.execute("INSERT INTO ast_device VALUES(1,1,10,'SERIAL-A','A',NULL,'ACTIVE',0),(2,1,10,'SERIAL-B','A','','ACTIVE',0),(3,1,11,'SERIAL-LOCAL','B','LOCAL','ACTIVE',0),(4,1,12,'SERIAL-FOREIGN','FOREIGN',NULL,'ACTIVE',0),(5,1,13,'SERIAL-DELETED-MASTER','DELETED',NULL,'ACTIVE',0),(6,1,NULL,'UNASSIGNED','A',NULL,'ACTIVE',0),(7,2,97,'FOREIGN-TENANT','A',NULL,'ACTIVE',0),(8,1,98,'DELETED-DEVICE','A',NULL,'ACTIVE',1)");
            sql.execute("INSERT INTO ast_product_official_info VALUES(1,'A','MASTER-MODEL',0),(1,'B','MASTER-MODEL',0),(2,'FOREIGN','MASTER-MODEL',0),(1,'DELETED','MASTER-MODEL',1)");
        }
        var config=new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("project-model-filter",new JdbcTransactionFactory(),source));
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config,""),DeviceDO.class);
        for(var name:List.of("DeviceCurrentModelMapper","DeviceQueryMapper")) {
            try(var xml=getClass().getResourceAsStream("/mapper/device/"+name+".xml")) {
                new XMLMapperBuilder(xml,config,name,config.getSqlFragments()).parse();
            }
        }
        TenantContextHolder.setTenantId(1L);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        mapper=session.getMapper(DeviceMapper.class);
    }

    @AfterEach void close() {if(session!=null)session.close();TenantContextHolder.clear();}

    @Test void projectModelSearchFindsTheSameCurrentModelAndDeduplicatesProjects() {
        assertEquals(List.of(10L),mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(1L,null,"MASTER",null)));
        assertEquals(List.of(11L),mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(1L,null,"LOCAL",null)));
    }

    @Test void serialAndWarrantyConditionsRemainAnIntersectionWithCurrentModel() {
        assertEquals(List.of(10L),mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(1L,"SERIAL-A","MASTER","ACTIVE")));
        assertTrue(mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(1L,"SERIAL-A","MASTER","EXPIRED")).isEmpty());
        assertTrue(mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(1L,"SERIAL-LOCAL","MASTER",null)).isEmpty());
    }

    @Test void missingTenantCannotExpandTheProjectCandidates() {
        assertTrue(mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(null,null,"MASTER",null)).isEmpty());
        assertTrue(mapper.selectListProjectIdsByMatch(new DeviceProjectFilterQuery(3L,null,"MASTER",null)).isEmpty());
    }
}
