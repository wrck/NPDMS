package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.Type;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryTypeMigrationMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.dictionary-type.mysql",matches="true")
class DictionaryTypeMigrationMySqlTest {
    Connection db;SqlSession session;DictionaryTypeMigrationMapper mapper;
    @BeforeEach void setup() throws Exception{
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        String ddl;try(var statement=db.createStatement();var rows=statement.executeQuery("SHOW CREATE TABLE system_dict_type")){
            assertTrue(rows.next());ddl=rows.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE");
        }
        try(var statement=db.createStatement()){statement.execute(ddl);}db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/dict/DictionaryTypeMigrationMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(DictionaryTypeMigrationMapper.class);
    }
    Type record(long id,String name,String type,boolean deleted){
        var time=LocalDateTime.parse("2015-01-02T03:04:05");return new Type(id,name,type,0,null,null,time,null,time,deleted,null);
    }
    @AfterEach void close() throws Exception{if(session!=null)session.close();if(db!=null && !db.isClosed())db.close();}
    @Test void realElevenColumnInsertPreservesNullActorsAndOriginalTimes(){
        var expected=record(88001,"项目状态","02",false);
        assertEquals(1,mapper.insertHistorical(new DictionaryTypeMigrationMapper.HistoricalInsert(expected)));
        assertEquals(expected,mapper.selectConflictingDefinitionsForUpdate(new DictionaryTypeMigrationMapper.IdentityQuery(88001L,"02","项目状态")).getFirst());
    }
    @Test void databaseCollationCatchesNameOrTypeCollisionAndDeletedBindingStaysVisible(){
        mapper.insertHistorical(new DictionaryTypeMigrationMapper.HistoricalInsert(record(88001,"Visible","TYPE",false)));
        mapper.insertHistorical(new DictionaryTypeMigrationMapper.HistoricalInsert(record(88002,"Deleted","OLD",true)));
        assertEquals(1,mapper.selectConflictingDefinitionsForUpdate(new DictionaryTypeMigrationMapper.IdentityQuery(null,"type","unused")).size());
        assertEquals(1,mapper.selectConflictingDefinitionsForUpdate(new DictionaryTypeMigrationMapper.IdentityQuery(null,"unused","visible")).size());
        assertTrue(mapper.selectConflictingDefinitionsForUpdate(new DictionaryTypeMigrationMapper.IdentityQuery(88002L,"NEW","New")).getFirst().deleted());
    }
    @Test void rollbackDoesNotLeaveMigratedDefinitions(){
        mapper.insertHistorical(new DictionaryTypeMigrationMapper.HistoricalInsert(record(88001,"Temporary","TEMP",false)));
        session.rollback();session.clearCache();
        assertTrue(mapper.selectConflictingDefinitionsForUpdate(new DictionaryTypeMigrationMapper.IdentityQuery(null,"TEMP","Temporary")).isEmpty());
    }
}
