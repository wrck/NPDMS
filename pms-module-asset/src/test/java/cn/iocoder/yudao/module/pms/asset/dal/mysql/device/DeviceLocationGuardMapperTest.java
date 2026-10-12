package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the lock query, so protection cannot depend on fields only present in mocks. */
class DeviceLocationGuardMapperTest {
    private SqlSession session;
    @BeforeEach void setup() throws Exception {
        var ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("""
            CREATE TABLE ast_device(id BIGINT PRIMARY KEY,sn VARCHAR(64),version BIGINT,tenant_id BIGINT,
              site_id BIGINT,site_location_id BIGINT,location_resolution_status VARCHAR(16),
              location_snapshot VARCHAR(1000),location_effective_from TIMESTAMP,location_record_id BIGINT,deleted SMALLINT)
            """);
        jdbc.update("INSERT INTO ast_device VALUES(8,'SN-8',3,1,90,91,'RESOLVED','location',?,92,0)",LocalDateTime.of(2020,1,2,3,4,5));
        jdbc.update("INSERT INTO ast_device(id,sn,version,tenant_id,deleted) VALUES(9,'SN-9',0,1,1)");
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(true);
        String path="mapper/device/DeviceCommandMapper.xml";
        try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,config,path,config.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(config).openSession();
    }
    @AfterEach void cleanup(){if(session!=null)session.close();}
    private DeviceDO lock(long tenant,long id){return session.selectOne(DeviceMapper.class.getName()+".selectByTenantAndIdForUpdate",Map.of("tenantId",tenant,"id",id));}
    @Test void lockedRowContainsEveryProjectionNeededForFillAndReplayProtection() {
        var row=lock(1,8);assertEquals(3L,row.getVersion());assertEquals(1L,row.getTenantId());
        assertEquals(90L,row.getSiteId());assertEquals(91L,row.getSiteLocationId());assertEquals(92L,row.getLocationRecordId());
        assertEquals("RESOLVED",row.getLocationResolutionStatus());assertEquals("location",row.getLocationSnapshot());
        assertEquals(LocalDateTime.of(2020,1,2,3,4,5),row.getLocationEffectiveFrom());
    }
    @Test void lockDoesNotExposeOtherTenantOrDeletedProjection() {
        assertNull(lock(2,8));assertNull(lock(1,9));
    }
}
