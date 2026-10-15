package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationHistoricalCarryUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentRecordQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
/** Executes the formal Owner XML against isolated rows; no application database is connected. */
class InstallationSourceHistoryMapperTest {
    SqlSession session;JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("""
          CREATE TABLE imp_eng_installation(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,code VARCHAR(64),equipment_id BIGINT,
          install_location VARCHAR(255),address_id BIGINT,address_version BIGINT,address_snapshot VARCHAR(1000),location_snapshot VARCHAR(1000),
          install_time TIMESTAMP,effective_from TIMESTAMP,effective_to TIMESTAMP,status INT,remark VARCHAR(500),version BIGINT,
          creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64) DEFAULT '1',update_time TIMESTAMP,deleted SMALLINT,
          site_id BIGINT,site_version BIGINT,site_location_id BIGINT,site_location_version BIGINT,
          location_resolution_status VARCHAR(16),installer_user_id BIGINT,environment_check VARCHAR(1000),
          spec_check VARCHAR(1000),photo_url VARCHAR(500),result VARCHAR(1000),UNIQUE(tenant_id,project_id,code))
          """);
        jdbc.update("INSERT INTO imp_eng_installation(id,tenant_id,project_id,code,equipment_id,status,version,creator,updater,deleted) VALUES(700,1,100,'DPPMS-301',500,0,0,'1','1',0)");
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(true);
        String path="mapper/installation/InstallationMapper.xml";try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,config,path,config.getSqlFragments()).parse();}
        path="mapper/installation/InstallationHistoryInsertMapper.xml";try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,config,path,config.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(config).openSession();
    }
    @AfterEach void cleanup(){if(session!=null)session.close();}
    InstallationDO corrected(Long tenant) {
        var r=new InstallationDO();r.setId(700L);r.setTenantId(tenant);r.setAddressId(900L);r.setAddressVersion(0L);r.setAddressSnapshot("address");r.setLocationSnapshot("location");r.setInstallLocation("旧地址");
        r.setStatus(2);r.setVersion(1L);r.setCreator("31");r.setUpdater(null);r.setCreateTime(LocalDateTime.of(2020,1,2,3,4,5));r.setUpdateTime(r.getCreateTime());r.setInstallTime(r.getCreateTime());r.setEffectiveFrom(r.getCreateTime());return r;
    }
    int carry(InstallationDO r,Long expected){return session.update(InstallationMapper.class.getName()+".carryHistoricalRecord",Map.of("query",new InstallationHistoricalCarryUpdate(r,expected)));}
    @Test void explicitNullAuditAndHistoricalDatesSurviveActualOwnerSql() {
        var r=corrected(1L);assertEquals(1,carry(r,0L));session.commit();
        var values=jdbc.queryForMap("SELECT * FROM imp_eng_installation WHERE id=700");assertNull(values.get("UPDATER"));assertEquals("31",values.get("CREATOR"));assertEquals(2,values.get("STATUS"));
        assertEquals(r.getCreateTime(),((java.sql.Timestamp)values.get("CREATE_TIME")).toLocalDateTime());assertEquals(r.getCreateTime(),((java.sql.Timestamp)values.get("UPDATE_TIME")).toLocalDateTime());
    }
    @Test void tenantOrVersionMismatchCannotModifyAnyColumn() {
        assertEquals(0,carry(corrected(2L),0L));assertEquals(0,carry(corrected(1L),1L));session.commit();
        assertEquals("1",jdbc.queryForObject("SELECT updater FROM imp_eng_installation WHERE id=700",String.class));assertEquals(0,jdbc.queryForObject("SELECT status FROM imp_eng_installation WHERE id=700",Integer.class));
    }
    @Test void deletedRecordCannotBeReactivatedByHistoricalCarry() {
        jdbc.update("UPDATE imp_eng_installation SET deleted=1 WHERE id=700");
        assertEquals(0,carry(corrected(1L),0L));session.commit();
        assertEquals(0,jdbc.queryForObject("SELECT status FROM imp_eng_installation WHERE id=700",Integer.class));
    }
    @Test void currentRecordLookupUsesExistingCompletedEffectiveAndTenantFacts() {
        carry(corrected(1L),0L);
        InstallationDO row=session.selectOne(InstallationMapper.class.getName()+".selectCurrentRecordForUpdate",Map.of("query",new InstallationCurrentRecordQuery(1L,500L)));assertEquals(700L,row.getId());
        assertNull(session.selectOne(InstallationMapper.class.getName()+".selectCurrentRecordForUpdate",Map.of("query",new InstallationCurrentRecordQuery(2L,500L))));
        session.rollback();assertEquals(0,jdbc.queryForObject("SELECT status FROM imp_eng_installation WHERE id=700",Integer.class));
    }
    InstallationDO newHistory() {
        var row=corrected(1L);row.setId(701L);row.setProjectId(100L);row.setEquipmentId(501L);
        row.setCode("PROJECT-AZ-001");row.setLocationResolutionStatus("UNRESOLVED");row.setDeleted(false);return row;
    }
    @Test void firstInsertPreservesMappedAuditAndDoesNotTouchExistingHistory() {
        var row=newHistory();assertEquals(1,session.getMapper(InstallationHistoryInsertMapper.class).insertHistorical(row));session.commit();
        var values=jdbc.queryForMap("SELECT * FROM imp_eng_installation WHERE id=701");
        assertNull(values.get("UPDATER"));assertNull(values.get("INSTALLER_USER_ID"));assertNull(values.get("SITE_ID"));
        assertEquals("31",values.get("CREATOR"));assertEquals("UNRESOLVED",values.get("LOCATION_RESOLUTION_STATUS"));
        assertEquals(row.getCreateTime(),((java.sql.Timestamp)values.get("CREATE_TIME")).toLocalDateTime());
        assertEquals(row.getCreateTime(),((java.sql.Timestamp)values.get("UPDATE_TIME")).toLocalDateTime());
        assertEquals(0,jdbc.queryForObject("SELECT status FROM imp_eng_installation WHERE id=700",Integer.class));
    }
    @Test void duplicateCodeFailsAndTransactionRollbackRemovesTheWholeInsert() {
        var mapper=session.getMapper(InstallationHistoryInsertMapper.class);var row=newHistory();mapper.insertHistorical(row);
        row.setId(702L);assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,()->mapper.insertHistorical(row));session.rollback();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
    }
    @Test void boundHistoryLookupRequiresExactTenantProjectAndIdAndExposesDeletionForReplayRejection() {
        var mapper=session.getMapper(InstallationHistoryInsertMapper.class);
        var row=mapper.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(1L,100L,700L));
        assertEquals(700L,row.getId());assertEquals(500L,row.getEquipmentId());assertFalse(row.getDeleted());
        assertNull(mapper.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(2L,100L,700L)));
        assertNull(mapper.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(1L,101L,700L)));
        assertNull(mapper.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(1L,100L,701L)));
        session.commit(true);
        jdbc.update("UPDATE imp_eng_installation SET deleted=1 WHERE id=700");
        assertTrue(mapper.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(1L,100L,700L)).getDeleted());
    }
}
