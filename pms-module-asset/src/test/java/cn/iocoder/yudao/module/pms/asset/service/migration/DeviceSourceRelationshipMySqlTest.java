package cn.iocoder.yudao.module.pms.asset.service.migration;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceSourceRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceSourceRelationshipMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.ibatis.session.SqlSession;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.device-relation.mysql",matches="true")
class DeviceSourceRelationshipMySqlTest {
    Connection db;SqlSession session;DeviceSourceRelationshipMapper mapper;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/assembly/DeviceSourceRelationshipMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(DeviceSourceRelationshipMapper.class);
        String relationshipDdl;
        try(var s=db.createStatement();var result=s.executeQuery("SHOW CREATE TABLE ast_device_relationship")) {
            assertTrue(result.next());relationshipDdl=result.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE");
        }
        try(var s=db.createStatement()) {
            // Connection-local shadows; the actual business tables are never changed.
            s.execute(relationshipDdl);
            s.execute("CREATE TEMPORARY TABLE ast_device(id BIGINT,tenant_id BIGINT,sn VARCHAR(100),project_id BIGINT,deleted BIT DEFAULT 0)");
            s.execute("CREATE TEMPORARY TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(100),project_id BIGINT,effective_from DATETIME(3),effective_to DATETIME(3),deleted BIT DEFAULT 0)");
            s.execute("INSERT INTO ast_device VALUES(1,1,'MAIN',100,0),(2,1,'UNRELATED',200,0),(3,2,'MAIN',100,0)");
            s.execute("INSERT INTO ast_device_project_relationship VALUES(1,'MAIN',300,'2020-01-01',NULL,0),(1,'UNRELATED',100,'2020-01-01','2021-01-01',0)");
        }
        db.setAutoCommit(false);
        insert(1,1,"MAIN","EXTRA","C1");insert(2,1,"MAIN","SECOND","C2");
        insert(3,2,"MAIN","FOREIGN","C1");insert(4,1,"UNRELATED","OTHER","C1");
    }
    void insert(long id,long tenant,String main,String extra,String contract) {
        var row=new DeviceSourceRelationshipDO();row.setId(id);row.setTenantId(tenant);
        row.setSourceDeviceSn(main);row.setTargetDeviceSn(extra);row.setSourceItem(" P1 ");row.setTargetItem("");
        row.setRelationshipType("EXTRA_SN");row.setContractNo(contract);row.setSourceSystem("DPPMS");row.setSourceKey(""+id);
        row.setSourceVersion(null);row.setVersion(0);row.setDeleted(false);
        row.setEffectiveFrom(LocalDateTime.of(2023,1,2,3,4,5,123000000));
        row.setCreateTime(row.getEffectiveFrom());row.setUpdateTime(row.getEffectiveFrom());row.setCreator("7");row.setUpdater("7");
        assertEquals(1,mapper.insertHistorical(new DeviceSourceRelationshipMapper.HistoricalInsert(row)));
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void sourceIdentityRetainsFullAuditAndEmptyProductValues() {
        var row=mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","1"));
        assertEquals(1L,row.getId());assertEquals(" P1 ",row.getSourceItem());assertEquals("",row.getTargetItem());
        assertNull(row.getSourceVersion());assertEquals("7",row.getUpdater());
        assertEquals(LocalDateTime.of(2023,1,2,3,4,5,123000000),row.getCreateTime());
        assertEquals(row.getCreateTime(),row.getUpdateTime());
        assertNull(mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(2L,"DPPMS","1")));
    }
    @Test void projectQueryRequiresExactContractAndCurrentDeviceAssociation() {
        var query=new DeviceSourceRelationshipPageQuery(1L,100L,Set.of("C1"),null,0,20);
        assertEquals(1,mapper.countRelationships(query));assertEquals(1L,mapper.selectRelationshipPage(query).getFirst().getId());
        assertEquals(0,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,100L,Set.of("c1"),null,0,20)));
        assertEquals(0,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,200L,Set.of("C2"),null,0,20)));
    }
    @Test void activeOfficialProjectHistorySupportsProjectQueryButExpiredHistoryDoesNot() {
        assertEquals(1,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,300L,Set.of("C1"),null,0,20)));
        assertEquals(1,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,100L,Set.of("C1"),null,0,20)));
    }
    @Test void emptyContractAndUnscopedQueryAreClosedAtSqlBoundary() {
        assertEquals(0,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,100L,Set.of(),null,0,20)));
        assertEquals(0,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,null,Set.of(),null,0,20)));
    }
    @Test void deviceQueryShowsContractHistoryWithExactSnAndTenantPaging() {
        var query=new DeviceSourceRelationshipPageQuery(1L,null,Set.of(),"MAIN",0,1);
        assertEquals(2,mapper.countRelationships(query));assertEquals(2L,mapper.selectRelationshipPage(query).getFirst().getId());
        assertEquals(1L,mapper.selectRelationshipPage(new DeviceSourceRelationshipPageQuery(1L,null,Set.of(),"MAIN",1,1)).getFirst().getId());
        assertEquals(0,mapper.countRelationships(new DeviceSourceRelationshipPageQuery(1L,null,Set.of(),"main",0,20)));
    }
    @Test void originalUniqueIdentityAndSelfRelationConstraintsStillBlock() {
        assertThrows(RuntimeException.class,()->insert(10,1,"MAIN","MAIN","C1"));
        assertThrows(RuntimeException.class,()->insert(1,1,"NEW","EXTRA","C1"));
    }
    @Test void rollbackDoesNotLeavePartialBusinessRows() throws Exception {
        db.commit();insert(10,1,"TEN","ELEVEN","C1");db.rollback();session.clearCache();
        assertNull(mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","10")));
    }
    @Test void historicalCorrectionPreservesEveryOtherColumnAndChecksVersion() {
        var before=mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","1"));
        var time=LocalDateTime.of(2018,1,2,3,4,5,690000000);
        assertEquals(1,mapper.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(1L,1L,0,time,time)));
        session.clearCache();var after=mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","1"));
        before.setCreateTime(time);before.setUpdateTime(time);before.setVersion(1);assertEquals(before,after);
        assertEquals(0,mapper.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(1L,1L,0,time,time)));
        assertEquals(0,mapper.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(2L,1L,1,time,time)));
    }
    @Test void failedCorrectionChunkRollsBackEarlierDateAndVersion() throws Exception {
        db.commit();var before=mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","1"));
        var time=LocalDateTime.of(2018,1,2,3,4,5,690000000);
        assertEquals(1,mapper.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(1L,1L,0,time,time)));
        assertEquals(0,mapper.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(1L,2L,99,time,time)));
        db.rollback();session.clearCache();assertEquals(before,mapper.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(1L,"DPPMS","1")));
    }
}
