package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 单连接临时表验证，不写或清理共享业务表。 */
@EnabledIfSystemProperty(named="npdms.member.mysql",matches="true")
class ProjectMemberHistoryMySqlTest {
    Connection db; SingleConnectionDataSource data; ProjectMemberAssignmentMapper mapper;
    cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper history;
    final LocalDateTime now=LocalDateTime.of(2026,10,11,12,0);
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        String originalDdl;
        try(var sql=db.createStatement();var result=sql.executeQuery("SHOW CREATE TABLE proj_project_member_assignment")) {
            assertTrue(result.next());originalDdl=result.getString(2);
        }
        try(var sql=db.createStatement()) {
            sql.execute(originalDdl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
            if(!originalDdl.contains("`authorization_confirmed`"))
                sql.execute(Files.readString(Path.of(System.getProperty("npdms.member.sourceRoot"),"SQL/migrations/V434__approved_project_member_history_fields.sql")));
        }
        data=new SingleConnectionDataSource(db,true);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(data);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));factory.setPlugins(plugins);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        factory.setMapperLocations(new ClassPathResource("mapper/projectmanual/ProjectMemberAssignmentMapper.xml"),new ClassPathResource("mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(ProjectMemberAssignmentMapper.class);
        history=session.getMapper(cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.class);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() throws Exception {TenantContextHolder.clear();if(data!=null)data.destroy();else if(db!=null)db.close();}
    void row(long id,long tenant,Long user,boolean confirmed) throws Exception {
        try(var sql=db.prepareStatement("INSERT INTO proj_project_member_assignment (id,tenant_id,project_id,user_id,member_role,status,version,effective_from,member_name,member_code,phone_number,email,registration_source,authorization_confirmed,creator,create_time,updater,update_time,deleted) VALUES (?,?,100,?,'PROJECT_MANAGER','ACTIVE',0,'2020-01-01','原姓名','z01010','原座机','', '1',?,NULL,?,NULL,?,b'0')")) {
            sql.setLong(1,id);sql.setLong(2,tenant);sql.setObject(3,user);sql.setBoolean(4,confirmed);
            sql.setTimestamp(5,confirmed?Timestamp.valueOf("2020-01-01 00:00:00"):null);sql.setTimestamp(6,confirmed?Timestamp.valueOf("2020-01-01 00:00:00"):null);sql.executeUpdate();
        }
    }
    ProjectMemberPageQuery page(String state) {
        var p=new PageParam();p.setPageNo(1);p.setPageSize(20);
        return ProjectMemberPageQuery.builder().tenantId(1L).projectId(100L).page(p).state(state).effectiveAt(now).build();
    }
    @Test void unconfirmedNullIdentityAndAuditAreAllowedButNeverGrantAccess() throws Exception {
        row(1,1,null,false);
        assertTrue(mapper.selectActiveByUser(new ActiveProjectMemberQuery(1L,40L,now)).isEmpty());
        assertTrue(mapper.selectActiveForAssignmentState(new ProjectAssignmentStateQuery(100L,now)).isEmpty());
        assertEquals(0,mapper.selectMemberPage(page("CURRENT")).getTotal());
        var history=mapper.selectMemberPage(page("HISTORY"));assertEquals(1,history.getTotal());
        var record=history.getList().getFirst();assertNull(record.getUserId());assertNull(record.getCreateTime());assertNull(record.getUpdateTime());
        assertNull(record.getCreator());assertNull(record.getUpdater());assertEquals("",record.getEmail());assertEquals("原座机",record.getPhoneNumber());
    }
    @Test void explicitUnconfirmedUserIsExcludedFromBothLambdaAndLockQueries() throws Exception {
        row(1,1,40L,false);
        assertTrue(mapper.selectActiveByUser(new ActiveProjectMemberQuery(1L,40L,now)).isEmpty());
        assertTrue(mapper.selectActiveMemberIdentityForUpdate(new ProjectMemberIdentityQuery(1L,100L,40L,"PROJECT_MANAGER",now)).isEmpty());
    }
    @Test void confirmationCannotBypassTheRequiredUserAndAudit() throws Exception {
        assertThrows(SQLException.class,()->row(1,1,null,true));
        row(2,1,40L,true);assertEquals(1,mapper.selectMemberPage(page("CURRENT")).getTotal());
    }
    @Test void normalInsertDefaultsToConfirmedAndRetainsNormalAuditFilling() {
        var row=new ProjectMemberAssignmentDO();row.setProjectId(100L);row.setTenantId(1L);row.setUserId(40L);
        row.setMemberRole("PROJECT_MANAGER");row.setStatus("ACTIVE");row.setVersion(0);row.setEffectiveFrom(now.minusDays(1));
        assertEquals(1,mapper.insert(row));var stored=mapper.selectById(row.getId());
        assertTrue(stored.getAuthorizationConfirmed());assertNotNull(stored.getCreateTime());assertNotNull(stored.getUpdateTime());
    }
    @Test void otherTenantCannotEnterHistoryOrCurrentAuthorizationQueries() throws Exception {
        row(1,2,40L,true);row(2,2,null,false);
        assertEquals(0,mapper.selectMemberPage(page("CURRENT")).getTotal());assertEquals(0,mapper.selectMemberPage(page("HISTORY")).getTotal());
        assertTrue(mapper.selectActiveByUser(new ActiveProjectMemberQuery(1L,40L,now)).isEmpty());
    }
    @Test void existingIdentityUniquenessRemainsInForce() throws Exception {
        row(1,1,40L,true);assertThrows(SQLException.class,()->row(2,1,40L,true));
        try(var sql=db.createStatement();var rs=sql.executeQuery("SHOW COLUMNS FROM proj_project_member_assignment")){int n=0;while(rs.next())n++;assertEquals(34,n);}
    }
    ProjectMemberAssignmentDO historical() {
        var row=new ProjectMemberAssignmentDO();row.setId(10L);row.setTenantId(1L);row.setProjectId(100L);
        row.setMemberCode("no-account");row.setMemberName("原区间姓名");row.setPhoneNumber("原座机");row.setEmail("");
        row.setMemberRole("PROJECT_MANAGER");row.setRegistrationSource("2");row.setStatus("ACTIVE");row.setVersion(0);
        row.setEffectiveFrom(now.minusDays(5));row.setAuthorizationConfirmed(false);row.setDeleted(false);return row;
    }
    @Test void historicalSqlPreservesNullActorsAndDatesDespiteNormalAutoFillHandler() {
        var original=historical();assertEquals(1,history.insertHistorical(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.HistoricalInsert(original)));
        var actual=history.selectProjectForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.ProjectQuery(1L,100L)).getFirst();
        assertEquals(original,actual);assertNull(actual.getCreator());assertNull(actual.getUpdater());
        assertNull(actual.getCreateTime());assertNull(actual.getUpdateTime());assertNull(actual.getUserId());
        assertNull(actual.getDeletedTime());assertEquals("",actual.getEmail());assertFalse(actual.getAuthorizationConfirmed());
    }
    @Test void intervalCloseIsTenantAndVersionGuardedAndKeepsTheHistoricalPersonAndCreation() throws Exception {
        row(1,1,1L,true);var before=mapper.selectById(1L);
        var q=new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.CloseInterval(2L,100L,1L,0,now,"7","已裁决变更");
        assertEquals(0,history.closeApprovedInterval(q));
        assertEquals(0,history.closeApprovedInterval(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.CloseInterval(1L,100L,1L,3,now,"7","已裁决变更")));
        assertEquals(1,history.closeApprovedInterval(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.CloseInterval(1L,100L,1L,0,now,"7","已裁决变更")));
        var after=mapper.selectById(1L);assertEquals(before.getCreateTime(),after.getCreateTime());assertEquals(before.getCreator(),after.getCreator());
        assertEquals(before.getUserId(),after.getUserId());assertEquals(now,after.getEffectiveTo());assertEquals(1,after.getVersion());assertEquals("7",after.getUpdater());
    }
    @Test void failedMigrationRollsBackNewHistoryAndAdministratorIntervalTogether() throws Exception {
        row(1,1,1L,true);var before=mapper.selectById(1L);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(data));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            assertEquals(1,history.insertHistorical(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.HistoricalInsert(historical())));
            assertEquals(1,history.closeApprovedInterval(new cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.CloseInterval(1L,100L,1L,0,now,"7","已裁决变更")));
            throw new IllegalStateException("SYNTHETIC_EVIDENCE_FAILURE");
        }));
        assertNull(mapper.selectById(10L));assertEquals(before,mapper.selectById(1L));
    }
}
