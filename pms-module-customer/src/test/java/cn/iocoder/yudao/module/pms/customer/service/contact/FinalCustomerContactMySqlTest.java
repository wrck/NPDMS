package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** 原有业务表仅用于读取DDL；测试全部落在连接级临时表。 */
@EnabledIfSystemProperty(named="npdms.contact.mysql",matches="true")
class FinalCustomerContactMySqlTest {
    Connection db;SingleConnectionDataSource data;FinalCustomerContactMigrationMapper history;ProjectCustomerContactMapper ordinary;
    final LocalDateTime original=LocalDateTime.of(2023,4,14,14,55,2);
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        String ddl;try(var sql=db.createStatement();var rs=sql.executeQuery("SHOW CREATE TABLE cus_project_customer_contact_relation")){assertTrue(rs.next());ddl=rs.getString(2);}
        try(var sql=db.createStatement()){
            sql.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
            if(!ddl.contains("`project_party_id`"))sql.execute(Files.readString(Path.of(System.getProperty("npdms.contact.sourceRoot"),"SQL/migrations/V436__approved_final_customer_project_contacts.sql")));
        }
        data=new SingleConnectionDataSource(db,true);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(data);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));factory.setPlugins(plugins);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
        factory.setMapperLocations(new ClassPathResource("mapper/contact/ProjectCustomerContactMapper.xml"),new ClassPathResource("mapper/contact/FinalCustomerContactMigrationMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());history=session.getMapper(FinalCustomerContactMigrationMapper.class);ordinary=session.getMapper(ProjectCustomerContactMapper.class);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() throws Exception {TenantContextHolder.clear();if(data!=null)data.destroy();else if(db!=null)db.close();}
    ProjectCustomerContactDO row(long id){
        var row=new ProjectCustomerContactDO();row.setId(id);row.setTenantId(1L);row.setProjectId(100L);row.setProjectPartyId(99L);
        row.setContactCode("");row.setName("董颖");row.setPhone("13500802833");row.setEmail("");row.setRegistrationSource("2");row.setEffectiveFrom(original);
        row.setCreator("40");row.setCreateTime(original);row.setUpdater(null);row.setUpdateTime(original);row.setPrimaryFlag(false);row.setStatus(0);row.setVersion(0);row.setDeleted(false);return row;
    }
    int insert(ProjectCustomerContactDO row){return history.insertHistorical(new FinalCustomerContactMigrationMapper.HistoricalInsert(row));}
    @Test void historicalSqlPreservesEveryNamedFieldAndNullUpdaterDespiteAutomaticAuditFilling(){
        var row=row(1);assertEquals(1,insert(row));var actual=history.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(1L,100L)).getFirst();
        assertEquals(row,actual);assertNull(actual.getUpdater());assertNull(actual.getCustomerId());assertNull(actual.getCustomerContactId());assertEquals("",actual.getEmail());
    }
    @Test void ordinaryContactsCannotLoseBuyerOrMasterAndFinalContactsCannotBecomePrimary(){
        var invalid=row(1);invalid.setProjectPartyId(null);assertThrows(RuntimeException.class,()->insert(invalid));
        var half=row(2);half.setCustomerId(8L);assertThrows(RuntimeException.class,()->insert(half));
        var primary=row(3);primary.setPrimaryFlag(true);assertThrows(RuntimeException.class,()->insert(primary));
        var valid=row(4);valid.setProjectPartyId(null);valid.setCustomerId(8L);valid.setCustomerContactId(9L);assertEquals(1,insert(valid));
    }
    @Test void normalPrimaryUniquenessStillAllowsNonPrimaryFinalHistory(){
        var first=row(1);first.setProjectPartyId(null);first.setCustomerId(8L);first.setCustomerContactId(9L);first.setPrimaryFlag(true);assertEquals(1,insert(first));
        var second=row(2);second.setProjectPartyId(null);second.setCustomerId(8L);second.setCustomerContactId(10L);second.setPrimaryFlag(true);
        assertThrows(RuntimeException.class,()->insert(second));assertEquals(1,insert(row(3)));assertEquals(2,history.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(1L,100L)).size());
    }
    @Test void otherTenantCannotEnterProjectListOrLockedHistory(){
        var row=row(1);row.setTenantId(2L);TenantContextHolder.setTenantId(2L);assertEquals(1,insert(row));TenantContextHolder.setTenantId(1L);
        assertTrue(history.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(1L,100L)).isEmpty());
        assertEquals(0,ordinary.selectPage(new ProjectContactPageQuery(1L,100L,null,null,new PageParam())).getTotal());
    }
    @Test void failedEvidenceTransactionRollsBackContactWithoutTouchingExistingContact(){
        var first=row(1);insert(first);var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(data));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{insert(row(2));throw new IllegalStateException("EVIDENCE_FAILURE");}));
        assertEquals(java.util.List.of(first),history.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(1L,100L)));
    }
    @Test void reversedEffectivePeriodIsRejectedAndRepeatedNonPrimaryOriginalRowsRemainDistinct(){
        var invalid=row(1);invalid.setEffectiveTo(original.minusDays(1));assertThrows(RuntimeException.class,()->insert(invalid));
        insert(row(2));insert(row(3));assertEquals(2,history.selectProjectForUpdate(new FinalCustomerContactMigrationMapper.ProjectQuery(1L,100L)).size());
    }
    @Test void finalCustomerHistoryCannotCompleteBuyerContactRequirements(){
        insert(row(1));assertTrue(ordinary.selectCompletionForUpdate(new ProjectContactCompletionQuery(1L,100L,8L)).isEmpty());
        var buyer=row(2);buyer.setProjectPartyId(null);buyer.setCustomerId(8L);buyer.setCustomerContactId(9L);insert(buyer);
        assertEquals(java.util.List.of(buyer),ordinary.selectCompletionForUpdate(new ProjectContactCompletionQuery(1L,100L,8L)));
    }
}
