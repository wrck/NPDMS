package cn.iocoder.yudao.module.pms.project.dal;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectPartyQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real current-schema reads, isolated in a connection-local clone; no persistent rows are changed. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class ProjectPartyApprovedSchemaMySqlTest {
    Connection db;
    org.apache.ibatis.session.SqlSession session;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
            @Override public void insertFill(org.apache.ibatis.reflection.MetaObject object) {
                if(object.hasSetter("creator") && object.getValue("creator")==null)setFieldValByName("creator","1",object);
                updateFill(object);
            }
            @Override public void updateFill(org.apache.ibatis.reflection.MetaObject object) {
                if(object.hasSetter("updater") && object.getValue("updater")==null)setFieldValByName("updater","1",object);
            }
        });
        factory.setGlobalConfig(global);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/projectmanual/ProjectPartyMapper.xml"));
        session=factory.getObject().openSession(db);
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE approved_party_test LIKE proj_project_party");
            sql.execute("ALTER TABLE approved_party_test RENAME TO proj_project_party");
            sql.execute("INSERT INTO proj_project_party(id,tenant_id,project_id,party_role,party_code,party_name,source_system,source_table,source_record_key,status,version,effective_from,effective_to,deleted,creator,create_time,updater,update_time) VALUES(1,1,100,'FINAL_CUSTOMER','C','End user','DPPMS','pm_project_header','101','ACTIVE',0,'2023-01-01',NULL,0,'7','2023-01-02','8','2023-01-03'),(2,2,100,'FINAL_CUSTOMER','C2','Other tenant','DPPMS','pm_project_header','102','ACTIVE',0,'2023-01-01',NULL,0,'7','2023-01-02','8','2023-01-03'),(3,1,100,'FINAL_CUSTOMER','C3','Expired','DPPMS','pm_project_header','103','ACTIVE',0,'2023-01-01','2023-01-04',0,'7','2023-01-02','8','2023-01-03'),(4,1,100,'FINAL_CUSTOMER','C4','Deleted','DPPMS','pm_project_header','104','ACTIVE',0,'2023-01-01',NULL,1,'7','2023-01-02','8','2023-01-03')");
        }
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void currentPartyReadUsesExistingAuditColumnsWithoutSourceMirrors() {
        var rows=session.getMapper(ProjectPartyMapper.class).selectCurrent(new ProjectPartyQuery(1L,100L,LocalDateTime.of(2026,10,9,0,0)));
        assertEquals(List.of(1L),rows.stream().map(x->x.getId()).toList());
        var row=rows.getFirst();assertEquals("End user",row.getPartyName());
        assertEquals("7",row.getCreator());assertEquals("8",row.getUpdater());
        assertEquals(LocalDateTime.of(2023,1,2,0,0),row.getCreateTime());
        assertEquals(LocalDateTime.of(2023,1,3,0,0),row.getUpdateTime());
    }
    @Test void sourceIdentityLockKeepsTenantAndOriginalFormalIdentity() {
        var row=session.getMapper(ProjectPartyMapper.class).selectSourceForUpdate(new ProjectPartyMapper.PartySourceQuery(1L,"DPPMS","pm_project_header","101","FINAL_CUSTOMER"));
        assertNotNull(row);assertEquals(1L,row.getId());
        assertNull(session.getMapper(ProjectPartyMapper.class).selectSourceForUpdate(new ProjectPartyMapper.PartySourceQuery(2L,"DPPMS","pm_project_header","101","FINAL_CUSTOMER")));
    }
    @Test void explicitMigrationAuditSurvivesFrameworkInsertAndUpdateAutoFill() {
        var mapper=session.getMapper(ProjectPartyMapper.class);
        var row=new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO();
        row.setId(11L);row.setTenantId(1L);row.setProjectId(100L);row.setPartyRole("10");row.setPartyName("渠道");
        row.setSourceSystem("DPPMS");row.setSourceTable("pm_project_related_party");row.setSourceRecordKey("11");
        row.setStatus("ACTIVE");row.setVersion(0);row.setDeleted(false);row.setCreateTime(LocalDateTime.of(2023,1,2,3,4,5));
        row.setUpdateTime(row.getCreateTime());
        assertEquals(1,mapper.insertMigrationParty(new ProjectPartyMapper.MigrationWrite(row,null,null)));
        var identity=new ProjectPartyMapper.MigrationSourceQuery(1L,"pm_project_related_party","11");
        var stored=mapper.selectMigrationSourceForUpdate(identity).getFirst();
        assertNull(stored.getCreator());assertNull(stored.getUpdater());
        row.setPartyName("修正渠道");row.setVersion(1);
        assertEquals(1,mapper.updateMigrationParty(new ProjectPartyMapper.MigrationWrite(row,null,null)));
        stored=mapper.selectMigrationSourceForUpdate(identity).getFirst();
        assertNull(stored.getCreator());assertNull(stored.getUpdater());assertEquals(row.getCreateTime(),stored.getUpdateTime());
    }
}
