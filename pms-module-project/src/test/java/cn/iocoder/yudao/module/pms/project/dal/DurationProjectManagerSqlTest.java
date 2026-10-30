package cn.iocoder.yudao.module.pms.project.dal;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.DurationProjectManagerQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DurationProjectManagerSqlTest {
 @Test void currentLockedPmNeedsConfirmedSameProjectUserAndEffectiveInterval() throws Exception {
  var config=new Configuration();String resource="mapper/projectmanual/ProjectMemberAssignmentMapper.xml";
  for(String mapping:List.of("mapper/authorization/ProjectViewGrantReadSql.xml","mapper/projectscope/ProjectViewReadScopeSql.xml",resource)) {
   try(var xml=getClass().getClassLoader().getResourceAsStream(mapping)){new XMLMapperBuilder(xml,config,mapping,config.getSqlFragments()).parse();}
  }
  try(var c=DriverManager.getConnection("jdbc:h2:mem:duration_pm_"+UUID.randomUUID()+";MODE=MySQL")){
   var s=c.createStatement();s.execute("CREATE TABLE proj_project_member_assignment(id BIGINT,tenant_id BIGINT,project_id BIGINT,user_id BIGINT,member_role VARCHAR(32),authorization_confirmed INT,status VARCHAR(20),deleted INT,effective_from TIMESTAMP,effective_to TIMESTAMP)");
   s.execute("INSERT INTO proj_project_member_assignment VALUES(1,1,100,9,'PROJECT_MANAGER',1,'ACTIVE',0,'2020-01-01',NULL)");
   assertEquals(1,count(c,config));
   assertEquals(1,countCurrent(c,config));
   for(String mutation:List.of("authorization_confirmed=0","effective_to='2021-01-01'","effective_from='2099-01-01'","member_role='SERVICE_MANAGER'","tenant_id=2","project_id=101","user_id=10","deleted=1","status='DISABLED'")){
    c.setAutoCommit(false);s.executeUpdate("UPDATE proj_project_member_assignment SET "+mutation);assertEquals(0,count(c,config),mutation);assertEquals(mutation.equals("user_id=10")?1:0,countCurrent(c,config),mutation);c.rollback();
   }
  }
 }
 int countCurrent(Connection c,Configuration config) throws Exception {
  var params=Map.of("query",new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.DurationCurrentProjectManagerQuery(1L,100L));
  var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentDurationProjectManagersForUpdate").getBoundSql(params);
  try(var p=c.prepareStatement(bound.getSql())){int i=1;for(var mapping:bound.getParameterMappings())p.setObject(i++,config.newMetaObject(params).getValue(mapping.getProperty()));try(var rows=p.executeQuery()){int count=0;while(rows.next())count++;return count;}}
 }
 int count(Connection c,Configuration config) throws Exception {
  var params=Map.of("query",new DurationProjectManagerQuery(1L,100L,9L,LocalDateTime.now()));var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectDurationProjectManagersForUpdate").getBoundSql(params);
  try(var p=c.prepareStatement(bound.getSql())){int i=1;for(var mapping:bound.getParameterMappings())p.setObject(i++,config.newMetaObject(params).getValue(mapping.getProperty()));try(var rows=p.executeQuery()){int count=0;while(rows.next())count++;return count;}}
 }
}
