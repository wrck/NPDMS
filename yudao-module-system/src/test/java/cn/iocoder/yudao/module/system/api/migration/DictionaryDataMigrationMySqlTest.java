package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi.Item;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryDataMigrationMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.dictionary-data.mysql",matches="true")
class DictionaryDataMigrationMySqlTest {
    Connection db;SqlSession session;DictionaryDataMigrationMapper mapper;
    @BeforeEach void setup() throws Exception{
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        String ddl;try(var statement=db.createStatement();var rows=statement.executeQuery("SHOW CREATE TABLE system_dict_data")){
            assertTrue(rows.next());ddl=rows.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE");
        }
        try(var statement=db.createStatement()){statement.execute(ddl);}db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/dict/DictionaryDataMigrationMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(DictionaryDataMigrationMapper.class);
    }
    Item record(long id,String value,boolean deleted){
        var time=LocalDateTime.parse("2015-01-02T03:04:05");return new Item(id,0,"测试快递",value,"01",0,null,null,null,null,time,null,time,deleted);
    }
    @AfterEach void close() throws Exception{if(session!=null)session.close();if(db!=null && !db.isClosed())db.close();}
    @Test void exactFourteenColumnInsertPreservesNullAuditorsAndHistory(){
        var expected=record(88001,"VALUE",false);assertEquals(1,mapper.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(expected)));
        assertEquals(expected,mapper.selectConflictingValuesForUpdate(new DictionaryDataMigrationMapper.IdentityQuery(88001L,"01","VALUE")).getFirst());
        assertEquals("01",mapper.selectTypesForUpdate(new DictionaryDataMigrationMapper.TypeQuery("01")).getFirst().type());
    }
    @Test void actualDatabaseCollationAndDeletedBindingStayProtected(){
        mapper.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(record(88001,"VALUE",false)));
        mapper.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(record(88002,"OLD",true)));
        assertEquals(1,mapper.selectConflictingValuesForUpdate(new DictionaryDataMigrationMapper.IdentityQuery(null,"01","value")).size());
        assertTrue(mapper.selectConflictingValuesForUpdate(new DictionaryDataMigrationMapper.IdentityQuery(88002L,"01","unused")).getFirst().deleted());
    }
    @Test void rollbackLeavesNoDictionaryValues(){
        mapper.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(record(88001,"TEMP",false)));
        session.rollback();session.clearCache();
        assertTrue(mapper.selectConflictingValuesForUpdate(new DictionaryDataMigrationMapper.IdentityQuery(null,"01","TEMP")).isEmpty());
    }
    @Test void existingNotNullConstraintIsNotWeakenedForEmptySourceValue(){
        var original=record(88001,"VALUE",false);var invalid=new Item(original.id(),original.sort(),original.label(),null,original.dictType(),original.status(),
                null,null,null,null,original.createTime(),null,original.updateTime(),false);
        assertThrows(Exception.class,()->mapper.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(invalid)));
    }
}
