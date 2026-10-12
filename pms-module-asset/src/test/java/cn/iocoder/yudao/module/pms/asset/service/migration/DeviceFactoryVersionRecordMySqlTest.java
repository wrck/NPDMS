package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceFactoryVersionRecordIdentityQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.ibatis.session.SqlSession;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the approved schema and real Owner mapper with connection-local tables only. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class DeviceFactoryVersionRecordMySqlTest {
    Connection db;SqlSession session;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/version/DeviceFactoryVersionRecordMapper.xml"));
        session=factory.getObject().openSession(db);
        String sql=Files.readString(Path.of("../sql/migrations/V414__dppms_native_source_business_records.sql"),StandardCharsets.UTF_8);
        int begin=sql.indexOf("CREATE TABLE ast_device_factory_version_record (");int end=sql.indexOf(';',begin);
        try(var statement=db.createStatement()) {
            statement.execute(sql.substring(begin,end).replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            statement.execute("INSERT INTO ast_device_factory_version_record(id,tenant_id,project_id,device_sn,conp_version,boot_version,cpld_version,pcb_version,creator,create_time,updater,update_time) VALUES (1,1,10,'SN',' V ','',NULL,'P','historic','2024-01-02 03:04:05.123','','2024-02-03 04:05:06.456'),(2,1,10,'SN','OTHER',NULL,'','P','historic','2024-01-02 03:04:05.123','','2024-02-03 04:05:06.456'),(3,2,10,'SN','FOREIGN',NULL,NULL,NULL,'historic','2024-01-02 03:04:05.123','','2024-02-03 04:05:06.456'),(4,1,NULL,'UNASSOCIATED',NULL,NULL,NULL,NULL,'historic','2024-01-02 03:04:05.123','','2024-02-03 04:05:06.456')");
        }
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void exactBindingReadsDistinctVersionsWithOriginalAuditAndNullValues() {
        var mapper=session.getMapper(DeviceFactoryVersionRecordMapper.class);
        var first=mapper.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(1L,10L,1L));
        var second=mapper.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(1L,10L,2L));
        assertEquals("SN",first.getDeviceSn());assertEquals(first.getDeviceSn(),second.getDeviceSn());
        assertEquals(" V ",first.getConpVersion());assertEquals("OTHER",second.getConpVersion());
        assertEquals("",first.getBootVersion());assertNull(first.getCpldVersion());assertNull(second.getBootVersion());assertEquals("",second.getCpldVersion());
        assertEquals("historic",first.getCreator());assertEquals("",first.getUpdater());assertEquals(123000000,first.getCreateTime().getNano());assertEquals(456000000,first.getUpdateTime().getNano());
    }
    @Test void tenantProjectAndNullableAssociationCannotBeBypassedByTargetId() throws Exception {
        var mapper=session.getMapper(DeviceFactoryVersionRecordMapper.class);
        assertNull(mapper.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(1L,10L,3L)));
        assertNull(mapper.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(1L,11L,1L)));
        assertNull(mapper.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(1L,10L,4L)));
        try(var statement=db.createStatement();var result=statement.executeQuery("SELECT project_id FROM ast_device_factory_version_record WHERE id=4")) {assertTrue(result.next());assertNull(result.getObject(1));}
    }
}
