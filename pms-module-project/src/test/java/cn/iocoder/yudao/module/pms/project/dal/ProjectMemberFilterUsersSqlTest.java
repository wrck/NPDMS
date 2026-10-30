package cn.iocoder.yudao.module.pms.project.dal;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberFilterUsersQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.mapping.Environment;import org.apache.ibatis.session.*;import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;import org.h2.jdbcx.JdbcDataSource;import org.junit.jupiter.api.*;
import java.util.*;import java.time.LocalDateTime;import static org.junit.jupiter.api.Assertions.*;
class ProjectMemberFilterUsersSqlTest {
 static final LocalDateTime AT=LocalDateTime.of(2026,10,28,12,0);SqlSession session;ProjectMemberAssignmentMapper mapper;
 @BeforeEach void setup()throws Exception{
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:member_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  try(var c=source.getConnection();var s=c.createStatement()){
   s.execute("CREATE TABLE proj_project(id BIGINT,tenant_id BIGINT,company_id BIGINT,department_id BIGINT,creator VARCHAR(64),deleted INT)");
   s.execute("CREATE TABLE proj_project_tree_path(tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,deleted INT)");
   s.execute("CREATE TABLE proj_project_member_assignment(tenant_id BIGINT,project_id BIGINT,user_id BIGINT,member_role VARCHAR(64),status VARCHAR(20),authorization_confirmed INT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted INT)");
   s.execute("CREATE TABLE plt_authorization_grant(tenant_id BIGINT,subject_type_code VARCHAR(20),subject_id BIGINT,resource_context_code VARCHAR(20),resource_type_code VARCHAR(20),resource_id BIGINT,action_code VARCHAR(40),scope_code VARCHAR(40),status_code VARCHAR(20),current_marker INT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted INT)");
   s.execute("INSERT INTO proj_project VALUES(10,1,NULL,NULL,'999',0),(11,1,NULL,NULL,'999',0),(12,1,NULL,NULL,'999',0),(13,1,NULL,NULL,'999',1),(20,1,NULL,NULL,'999',0),(30,2,NULL,NULL,'999',0)");
   s.execute("INSERT INTO proj_project_tree_path VALUES(1,10,7,10,10,0),(1,10,7,10,11,0),(1,10,7,10,12,0),(1,10,7,10,13,0),(1,20,8,20,20,0),(2,30,7,30,30,0)");
   s.execute("INSERT INTO proj_project_member_assignment VALUES(1,11,7,'TEAM_MEMBER','ACTIVE',1,NULL,NULL,0),(1,13,7,'TEAM_MEMBER','ACTIVE',1,NULL,NULL,0),"
    +"(1,11,2,'PROJECT_MANAGER','ACTIVE',0,NULL,NULL,0),(1,11,2,'PROJECT_MANAGER','ACTIVE',0,NULL,NULL,0),"
    +"(1,12,3,'PROJECT_MANAGER','ACTIVE',1,NULL,NULL,0),(1,20,4,'PROJECT_MANAGER','ACTIVE',1,NULL,NULL,0),"
    +"(1,11,5,'SERVICE_MANAGER_L2','ACTIVE',0,NULL,NULL,0),(1,11,6,'SALES_REPRESENTATIVE','ACTIVE',0,NULL,NULL,0),"
    +"(1,11,8,'PROJECT_MANAGER','ACTIVE',1,'2026-10-28 12:00:01',NULL,0),(1,11,9,'PROJECT_MANAGER','ACTIVE',1,NULL,'2026-10-28 12:00:00',0),"
    +"(1,11,10,'PROJECT_MANAGER','ACTIVE',1,NULL,NULL,1),(2,11,12,'PROJECT_MANAGER','ACTIVE',1,NULL,NULL,0),"
    +"(1,13,13,'PROJECT_MANAGER','ACTIVE',1,NULL,NULL,0),(1,11,14,'PROJECT_MANAGER','INACTIVE',1,NULL,NULL,0),"
    +"(1,11,15,'SERVICE_MANAGER_L1','ACTIVE',0,'2026-10-28 12:00:00',NULL,0),(1,11,16,'END_USER','ACTIVE',1,NULL,NULL,0)");
  }
  var config=new Configuration();config.setEnvironment(new Environment("members",new JdbcTransactionFactory(),source));
  for(var name:List.of("mapper/authorization/ProjectViewGrantReadSql.xml","mapper/projectscope/ProjectViewReadScopeSql.xml","mapper/projectmanual/ProjectMemberAssignmentMapper.xml")){
   try(var in=getClass().getClassLoader().getResourceAsStream(name)){assertNotNull(in,name);String xml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0").replace("b'1'","1");new XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),config,name,config.getSqlFragments()).parse();}
  }
  session=new SqlSessionFactoryBuilder().build(config).openSession();mapper=session.getMapper(ProjectMemberAssignmentMapper.class);
 }
 @AfterEach void close(){session.close();}
 ProjectViewReadScopeApi.ReadScope scope(){return new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,AT,List.of(new ProjectViewReadScopeApi.Tree(10L,7L)));}
 ProjectMemberFilterUsersQuery query(Set<String> roles,ProjectViewReadScopeApi.ReadScope scope){return new ProjectMemberFilterUsersQuery(1L,roles,AT,new ProjectMemberFilterUsersQuery.Scope(scope));}
 @Test void fullVisibleProjectsOnlyAndAllManagementAliasesWithoutSnapshotOrAuthorizationFlagNarrowing(){assertEquals(Set.of(2L,5L,15L),mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,scope())));}
 @Test void serviceSalesAndEmptyRolesAreIndependent(){assertEquals(Set.of(5L,15L),mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.SERVICE_CODES,scope())));assertEquals(Set.of(6L),mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.SALES_CODES,scope())));assertTrue(mapper.selectMemberFilterUserIds(query(Set.of(),scope())).isEmpty());}
 @Test void noOwnerForeignTenantAndMismatchedTreeVersionCannotReturnCandidates(){
  assertTrue(mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,null)).isEmpty());
  var foreign=new ProjectViewReadScopeApi.ReadScope(2L,7L,ProjectViewReadScopeApi.Mode.TENANT_ADMIN,AT,List.of());
  assertTrue(mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,foreign)).isEmpty());
  var stale=new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,AT,List.of(new ProjectViewReadScopeApi.Tree(10L,8L)));
  assertTrue(mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,stale)).isEmpty());
 }
 @Test void tenantAdminAddsOtherProjectsButStillExcludesDeletedFutureExpiredForeignAndInactiveMembers(){assertEquals(Set.of(2L,3L,4L,5L,15L),mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.TENANT_ADMIN,AT,List.of()))));}
 @Test void officeReadonlyProjectDoesNotFormOrdinaryCandidates(){
  var office=new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,AT,List.of(new ProjectViewReadScopeApi.Tree(10L,7L)),List.of(),12L);
  assertTrue(mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,office)).isEmpty());
 }
 @Test void bothSqlPlansPassProductionParser(){for(var scope:List.of(scope(),new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.TENANT_ADMIN,AT,List.of()))){var sql=session.getConfiguration().getMappedStatement(ProjectMemberAssignmentMapper.class.getName()+".selectMemberFilterUserIds").getBoundSql(Map.of("query",query(ProjectMemberRoles.MANAGEMENT_CODES,scope))).getSql();assertDoesNotThrow(()->net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));assertFalse(sql.contains("system_users"));}}

 @Test void unconfirmedActorIntervalDoesNotCreateOwnerFullCandidates()throws Exception{
  try(var sql=session.getConnection().createStatement()){sql.executeUpdate("UPDATE proj_project_member_assignment SET authorization_confirmed=0 WHERE user_id=7");}
  assertTrue(mapper.selectMemberFilterUserIds(query(ProjectMemberRoles.MANAGEMENT_CODES,scope())).isEmpty());
 }
}
