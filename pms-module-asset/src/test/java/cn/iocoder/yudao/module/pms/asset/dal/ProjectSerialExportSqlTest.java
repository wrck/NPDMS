package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectSerialExportMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectSerialExportQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectSerialExportSqlTest {
    private JdbcDataSource source;
    private SqlSession session;
    private ProjectSerialExportMapper mapper;
    @BeforeEach void setup() throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:serial_export_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("CREATE TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,sn VARCHAR(64),name VARCHAR(64),product_code VARCHAR(64),product_name VARCHAR(64),location_snapshot VARCHAR(255),conp_type VARCHAR(64),conp_version VARCHAR(64),warranty_start_date DATE,warranty_end_date DATE,deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE ast_device_network_version(tenant_id BIGINT,device_sn VARCHAR(64),boot_version VARCHAR(64),cpld_version VARCHAR(64),source_system VARCHAR(20),sync_status VARCHAR(20),deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE ast_device_warranty_record(tenant_id BIGINT,device_sn VARCHAR(64),extended INT,deleted INT DEFAULT 0)");
            s.execute("CREATE TABLE ast_product_official_info(tenant_id BIGINT,product_code VARCHAR(64),product_name VARCHAR(64),deleted INT,UNIQUE(tenant_id,product_code))");
            for(int id=1;id<=131;id++) s.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn,name,product_code) VALUES("+id+",1,10,'001SN"+id+"','核心交换机','02010005')");
            s.execute("INSERT INTO ast_device(id,tenant_id,project_id,sn) VALUES(1001,2,10,'FOREIGN'),(1002,1,20,'OUTSIDE')");
            s.execute("UPDATE ast_device SET deleted=1 WHERE id=131");
            s.execute("INSERT INTO ast_device_network_version VALUES(1,'001SN130','BOOT','CPLD','DEVICE_LOG','FAILED',0),(2,'001SN130','FOREIGN','FOREIGN','DEVICE_LOG','SUCCESS',0),(1,'001SN129','OLD','OLD','ITR','SUCCESS',0)");
            s.execute("INSERT INTO ast_device_warranty_record VALUES(1,'001SN130',1,0),(1,'001SN130',1,0),(1,'001SN130',1,1),(2,'001SN130',1,0)");
        }
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));config.setMapUnderscoreToCamelCase(true);
        for(var entity:List.of(
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO.class,
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO.class,
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(config,""),entity);
        }
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var interceptor=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        config.addInterceptor(interceptor);
        for(var name:List.of("DeviceCurrentModelMapper","ProjectSerialExportMapper")) {
            try(var xml=getClass().getResourceAsStream("/mapper/device/"+name+".xml")) {new XMLMapperBuilder(xml,config,name,config.getSqlFragments()).parse();}
        }
        session=new SqlSessionFactoryBuilder().build(config).openSession();mapper=session.getMapper(ProjectSerialExportMapper.class);
    }
    @AfterEach void close(){if(session!=null)session.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    private ProjectSerialExportQuery query(){return ProjectSerialExportQuery.builder().tenantId(1L).projectId(10L).limit(50).build();}
    @Test void cursorBatchesContainEveryMatchingDeviceOnceAndKeepKnownVersions() {
        var q=query();List<Long> ids=new ArrayList<>();
        while(true){var rows=mapper.selectBatch(q);rows.forEach(row->ids.add(row.getDeviceId()));if(rows.size()<50)break;q=q.toBuilder().beforeId(rows.getLast().getDeviceId()).build();}
        assertEquals(130,ids.size());assertEquals(130,new HashSet<>(ids).size());assertEquals(130L,ids.getFirst());assertEquals(1L,ids.getLast());
        var rows=mapper.selectBatch(query());assertEquals("BOOT",rows.getFirst().getBootVersion());assertEquals(2L,rows.getFirst().getExtendedCount());assertNull(rows.get(1).getBootVersion());
    }
    @Test void combinedFiltersAndMissingScopeCannotExpandTheExport() {
        assertEquals(1,mapper.selectBatch(query().toBuilder().sn("001SN130").name("交换机").productCode("02010005").build()).size());
        assertTrue(mapper.selectBatch(query().toBuilder().sn("001SN13").productCode("different").build()).isEmpty());
        assertTrue(mapper.selectBatch(query().toBuilder().projectId(null).build()).isEmpty());
        assertTrue(mapper.selectBatch(query().toBuilder().tenantId(2L).build()).isEmpty());
    }
    @Test void exportedCurrentProductNamesUseTheSameMasterFallbackAndKeepRawDeviceValues() throws Exception {
        try(var c=source.getConnection();var sql=c.createStatement()) {
            sql.execute("UPDATE ast_device SET product_code='A',product_name=NULL WHERE id=130");
            sql.execute("UPDATE ast_device SET product_code='B',product_name=' DEVICE-NAME ' WHERE id=129");
            sql.execute("UPDATE ast_device SET product_code='C',product_name='' WHERE id=128");
            sql.execute("UPDATE ast_device SET product_code='D' WHERE id=127");
            sql.execute("UPDATE ast_device SET product_code='E' WHERE id=126");
            sql.execute("INSERT INTO ast_product_official_info VALUES(1,'A',' MASTER-NAME ',0),(1,'B','IGNORED',0),(1,'C','NAME-C',0),(2,'D','FOREIGN',0),(1,'E','DELETED',1)");
        }
        var byId=mapper.selectBatch(query()).stream().collect(java.util.stream.Collectors.toMap(row->row.getDeviceId(),row->row));
        assertEquals(" MASTER-NAME ",byId.get(130L).getProductName());
        assertEquals(" DEVICE-NAME ",byId.get(129L).getProductName());
        assertEquals("NAME-C",byId.get(128L).getProductName());
        assertNull(byId.get(127L).getProductName());assertNull(byId.get(126L).getProductName());
        assertEquals(50,byId.size());
        try(var c=source.getConnection();var sql=c.createStatement();var raw=sql.executeQuery("SELECT product_name FROM ast_device WHERE id=130")) {
            assertTrue(raw.next());assertNull(raw.getString(1));
        }
    }
}
