package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.DeletedBusinessObjectLinksQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessUnlinkUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeletedBusinessObjectLinksMapperTest {
    @Test void exactObjectCleanupExcludesOtherScopesAndEndedHistoryAndRollsBackWithTheOwner() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:deleted_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,active_plan_version_id BIGINT,deleted TINYINT DEFAULT 0)");
        db.execute("CREATE TABLE proj_project_plan_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status VARCHAR(32),deleted TINYINT DEFAULT 0)");
        db.execute("CREATE TABLE proj_project_node_execution(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,plan_version_id BIGINT,node_kind VARCHAR(16),node_instance_id BIGINT,current_marker INT,status VARCHAR(32),ended_at TIMESTAMP,deleted TINYINT DEFAULT 0)");
        db.execute("CREATE TABLE proj_task_business_link(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,task_id BIGINT,stage_id BIGINT,node_execution_id BIGINT,execution_contract_id BIGINT,owner_context VARCHAR(32),object_type VARCHAR(32),object_id VARCHAR(32),version INT DEFAULT 0,active_marker INT DEFAULT 1,linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,unlinked_at TIMESTAMP,unlinked_by BIGINT,updater VARCHAR(64),update_time TIMESTAMP)");
        db.execute("CREATE TABLE test_survey(id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO test_survey VALUES(12)");
        db.update("INSERT INTO proj_project VALUES(7,1,50,0),(8,1,51,0),(9,2,52,0)");
        db.update("INSERT INTO proj_project_plan_version VALUES(50,1,7,'EFFECTIVE',0),(51,1,8,'EFFECTIVE',0),(52,2,9,'EFFECTIVE',0)");
        for(int i=1;i<=8;i++) {
            long tenant=i==8?2:1,project=i==7?8:i==8?9:7,plan=i==7?51:i==8?52:50;
            db.update("INSERT INTO proj_project_node_execution(id,tenant_id,project_id,contract_id,plan_version_id,node_kind,node_instance_id,current_marker,status,ended_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    100+i,tenant,project,30+i,plan,i==2?"STAGE":"TASK",200+i,i==6?0:1,i==3?"DONE":"ACTIVE",i==3?LocalDateTime.now():null);
            db.update("INSERT INTO proj_task_business_link(id,tenant_id,project_id,task_id,stage_id,node_execution_id,execution_contract_id,owner_context,object_type,object_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    i,tenant,project,i==2?null:200+i,i==2?200+i:null,100+i,30+i,i==5?"OTHER":"SOL","SITE_SURVEY",i==4?"13":"12");
        }
        var config=new Configuration(new Environment("deleted-links",new SpringManagedTransactionFactory(),ds));
        config.setMapUnderscoreToCamelCase(true);
        String resource="mapper/taskbusiness/ProjectTaskBusinessLinkMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            // Only MySQL bit literal syntax differs in the embedded database.
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("b'0'","0");
            new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),config,resource,config.getSqlFragments()).parse();
        }
        var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(ProjectTaskBusinessLinkMapper.class);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var query=new DeletedBusinessObjectLinksQuery(1L,7L,"SOL","SITE_SURVEY","12");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status -> {
            db.update("DELETE FROM test_survey WHERE id=12");
            var rows=mapper.selectDeletedObjectLiveLinksForUpdate(query);
            assertEquals(List.of(1L,2L),rows.stream().map(row->row.getId()).toList());
            for(var row:rows) assertEquals(1,mapper.unlinkIfMatch(new TaskBusinessUnlinkUpdate(1L,7L,row.getTaskId(),row.getId(),0,9L,LocalDateTime.now(),row.getStageId())));
            assertTrue(mapper.selectDeletedObjectLiveLinksForUpdate(query).isEmpty());
            throw new IllegalStateException("Owner callback failed");
        }));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM test_survey WHERE id=12",Integer.class));
        assertEquals(8,db.queryForObject("SELECT COUNT(*) FROM proj_task_business_link WHERE unlinked_at IS NULL",Integer.class));
        tx.executeWithoutResult(status -> assertEquals(List.of(1L,2L),mapper.selectDeletedObjectLiveLinksForUpdate(query).stream().map(row->row.getId()).toList()));
        assertEquals(8,db.queryForObject("SELECT COUNT(*) FROM proj_task_business_link",Integer.class));
    }
}
