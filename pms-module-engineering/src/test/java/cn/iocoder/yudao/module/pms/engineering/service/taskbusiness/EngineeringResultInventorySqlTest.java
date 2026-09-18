package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementResultInventoryQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SurveyResultInventoryQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production inventory SQL on a minimal embedded schema; not native MySQL migration or business-chain validation. */
class EngineeringResultInventorySqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private RequirementAnalysisMapper requirements;
    private SiteSurveyEntityMapper surveys;
    @BeforeEach void before() throws Exception {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:inventory_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sol_requirement_analysis_revision(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,entity_id BIGINT,revision_state VARCHAR(20),effective_marker INT,deleted INT)");
        jdbc.execute("INSERT INTO sol_requirement_analysis_revision VALUES(40,1,3,100,'FROZEN',1,0),(41,1,3,100,'FROZEN',NULL,0),(42,1,3,100,'DRAFT',NULL,0),(43,1,3,101,'FROZEN',1,0),(44,2,3,100,'FROZEN',1,0),(45,1,4,100,'FROZEN',1,0),(46,1,3,100,'FROZEN',1,1),(47,1,3,102,'FROZEN',1,0)");
        jdbc.execute("CREATE TABLE sol_site_survey(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status INT,deleted INT)");
        jdbc.execute("INSERT INTO sol_site_survey VALUES(40,1,3,1,0),(41,1,3,3,0),(42,1,3,0,0),(43,1,3,2,0),(44,2,3,1,0),(45,1,4,1,0),(46,1,3,1,1),(47,1,3,1,0)");
        var cfg=new Configuration(new Environment("native-inventory-test",new JdbcTransactionFactory(),dataSource));cfg.setMapUnderscoreToCamelCase(true);
        for(String path:List.of("mapper/requirement/RequirementAnalysisMapper.xml","mapper/sitesurvey/entity/SiteSurveyEntityMapper.xml")){
            try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,cfg,path,cfg.getSqlFragments()).parse();}}
        session=new SqlSessionFactoryBuilder().build(cfg).openSession(true);requirements=session.getMapper(RequirementAnalysisMapper.class);surveys=session.getMapper(SiteSurveyEntityMapper.class);
    }
    @AfterEach void after(){if(session!=null)session.close();jdbc.execute("SHUTDOWN");}
    @Test void requirementHistoryAndCurrentQueriesUseNativeEntityIdentityAndScopedKeys(){
        assertEquals(List.of("40","43","47"),requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,null,null,10,false)));
        assertEquals(List.of("40","41","43","47"),requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,null,null,10,true)));
        assertEquals(List.of("40","41"),requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,List.of(100L),null,10,true)));
        assertTrue(requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,List.of(40L),null,10,true)).isEmpty());
    }
    @Test void surveyInventoryNeverTurnsDraftOrRejectedRowsIntoResults(){
        assertEquals(List.of("40","41","47"),surveys.selectResultInventory(new SurveyResultInventoryQuery(1L,3L,null,null,10,false)));
        assertEquals(List.of("41"),surveys.selectResultInventory(new SurveyResultInventoryQuery(1L,3L,List.of(41L),null,10,false)));
    }
    @Test void emptyObjectListsNeverExpandTheNativeQuery(){
        assertTrue(requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,List.of(),null,10,true)).isEmpty());
        assertTrue(surveys.selectResultInventory(new SurveyResultInventoryQuery(1L,3L,List.of(),null,10,false)).isEmpty());
    }
    @Test void limitAndKeysetDoNotUseOffsetsOrCommitTimeAssumptions(){
        assertEquals(List.of("41","43"),requirements.selectResultInventory(new RequirementResultInventoryQuery(1L,3L,null,40L,2,true)));
        assertEquals(List.of("41"),surveys.selectResultInventory(new SurveyResultInventoryQuery(1L,3L,null,40L,1,false)));
        assertEquals(List.of("47"),surveys.selectResultInventory(new SurveyResultInventoryQuery(1L,3L,null,41L,2,false)));
    }
}
