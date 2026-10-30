package cn.iocoder.yudao.module.pms.project.service.projectscope;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class ConfirmedProjectMemberScopeTest {
 SqlSession session;ProjectMemberAssignmentMapper members;ProjectTreeScopeService scopes;JdbcDataSource data;
 @BeforeEach void setup()throws Exception{
  TenantContextHolder.setTenantId(1L);data=new JdbcDataSource();data.setURL("jdbc:h2:mem:confirmed_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("confirmed",new JdbcTransactionFactory(),data));config.addMapper(ProjectMemberAssignmentMapper.class);
  var info=TableInfoHelper.getTableInfo(ProjectMemberAssignmentDO.class);var columns=new ArrayList<String>();columns.add("id BIGINT PRIMARY KEY");
  for(var field:info.getFieldList()){var type=field.getPropertyType();String sqlType=type==Long.class?"BIGINT":type==Integer.class?"INT":type==Boolean.class?"BOOLEAN":type==LocalDateTime.class?"TIMESTAMP":"VARCHAR(500)";columns.add(field.getColumn()+" "+sqlType);}
  try(var c=data.getConnection();var sql=c.createStatement()){
   sql.execute("CREATE TABLE proj_project_member_assignment("+String.join(",",columns)+")");
   sql.execute("INSERT INTO proj_project_member_assignment(id,tenant_id,project_id,user_id,member_role,status,authorization_confirmed,effective_from,effective_to,deleted) VALUES (1,1,2,9,'PROJECT_MANAGER','ACTIVE',FALSE,NULL,NULL,FALSE),(2,1,3,9,'PROJECT_MANAGER','ACTIVE',TRUE,NULL,NULL,FALSE),(3,1,4,9,'PROJECT_MANAGER','ACTIVE',TRUE,'2099-01-01',NULL,FALSE),(4,1,5,9,'PROJECT_MANAGER','ACTIVE',TRUE,NULL,'2000-01-01',FALSE),(5,2,6,9,'PROJECT_MANAGER','ACTIVE',TRUE,NULL,NULL,FALSE),(6,1,7,9,'PROJECT_MANAGER','ACTIVE',TRUE,NULL,NULL,TRUE),(7,1,8,9,'PROJECT_MANAGER','INACTIVE',TRUE,NULL,NULL,FALSE)");
  }
  for(var resource:List.of("mapper/authorization/ProjectViewGrantReadSql.xml","mapper/projectscope/ProjectViewReadScopeSql.xml","mapper/projectmanual/ProjectMemberAssignmentMapper.xml")){
   try(var input=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(input);var xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0").replace("b'1'","1");new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),config,resource,config.getSqlFragments()).parse();}
  }
  session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);members=session.getMapper(ProjectMemberAssignmentMapper.class);
  var projects=mock(ProjectMasterMapper.class);var paths=mock(ProjectTreePathMapper.class);var versions=mock(ProjectTreeVersionMapper.class);
  var version=new ProjectTreeVersionDO();version.setRootProjectId(1L);version.setTreeVersion(7L);
  when(versions.selectLatestActive(1L)).thenReturn(version);when(versions.selectLatestActiveBatch(any())).thenReturn(List.of(version));
  when(projects.selectById(anyLong())).thenAnswer(call->project(call.getArgument(0)));
  when(projects.selectBatchIds(anyCollection())).thenAnswer(call->((Collection<Long>)call.getArgument(0)).stream().map(this::project).toList());
  when(projects.selectListCreatedBy(any())).thenReturn(List.of());
  var nodes=java.util.stream.LongStream.rangeClosed(1,8).mapToObj(id->{var path=new ProjectTreePathDO();path.setRootProjectId(1L);path.setTreeVersion(7L);path.setAncestorProjectId(1L);path.setDescendantProjectId(id);return path;}).toList();
  when(paths.selectByAncestor(1L,7L,1L,null)).thenReturn(nodes);when(paths.selectScopeRootPaths(any())).thenReturn(nodes);
  var grants=mock(AuthorizationGrantApi.class);when(grants.page(any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult(List.of(),0));
  scopes=new ProjectTreeScopeService(projects,members,paths,versions,grants,mock(PermissionApi.class),mock(ProjectOrganizationScopeService.class));
 }
 ProjectMasterDO project(Long id){var p=new ProjectMasterDO();p.setId(id);p.setRootId(1L);p.setTenantId(1L);return p;}
 @AfterEach void close(){if(session!=null)session.close();TenantContextHolder.clear();}
 @Test void unconfirmedMembershipNeverGrantsViewEditOrManageButConfirmedCurrentMembershipStillDoes(){
  for(var action:List.of("PROJECT_VIEW","PROJECT_EDIT","PROJECT_MANAGE"))assertEquals(Set.of(3L),scopes.resolve(new ProjectScopeQuery(1L,9L,3L,action,7L)).fullProjectIds());
 }
 @Test void fullListAndPreparedOwnerScopeDoNotDiscoverFromUnconfirmedMembership()throws Exception{
  try(var c=data.getConnection();var sql=c.createStatement()){sql.executeUpdate("UPDATE proj_project_member_assignment SET authorization_confirmed=FALSE WHERE id=2");}
  assertTrue(scopes.resolveAllFullProjectIds(1L,9L,"PROJECT_VIEW").isEmpty());
  assertTrue(scopes.prepareViewReadScope(new ProjectAllScopeQuery(1L,9L,"PROJECT_VIEW")).trees().isEmpty());
 }
 @Test void ordinaryMemberFactsRetainUnconfirmedRowsWithoutGrantingPermissions(){
  assertEquals(Set.of(2L,3L),members.selectActiveByUser(new ActiveProjectMemberQuery(1L,9L,LocalDateTime.now())).stream().map(ProjectMemberAssignmentDO::getProjectId).collect(java.util.stream.Collectors.toSet()));
 }

 @Test void scopedAuthorizationLoadsOnlyRequestedProjectsWithTheSameIdentityAndIntervalRules() throws Exception {
  var at=LocalDateTime.of(2026,10,29,1,0);
  try(var c=data.getConnection();var sql=c.createStatement()) {
   sql.execute("INSERT INTO proj_project_member_assignment(id,tenant_id,project_id,user_id,member_role,assignment_type,status,authorization_confirmed,effective_from,effective_to,deleted) VALUES "
    +"(10,1,101,9,'SERVICE_MANAGER','COLLABORATOR','ACTIVE',TRUE,NULL,NULL,FALSE),"
    +"(11,1,101,10,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,NULL,NULL,FALSE),"
    +"(12,2,101,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,NULL,NULL,FALSE),"
    +"(13,1,102,9,'TEAM_MEMBER',NULL,'ACTIVE',TRUE,NULL,NULL,FALSE),"
    +"(14,1,103,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,'2026-10-29 01:00:01',NULL,FALSE),"
    +"(15,1,104,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,NULL,'2026-10-29 01:00:00',FALSE),"
    +"(16,1,105,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,'2026-10-29 01:00:00',NULL,FALSE),"
    +"(17,1,106,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,NULL,NULL,TRUE),"
    +"(18,1,107,9,'PROJECT_MANAGER',NULL,'INACTIVE',TRUE,NULL,NULL,FALSE),"
    +"(19,1,108,9,'PROJECT_MANAGER',NULL,'ACTIVE',FALSE,NULL,NULL,FALSE),"
    +"(20,1,999,9,'PROJECT_MANAGER',NULL,'ACTIVE',TRUE,NULL,NULL,FALSE)");
  }
  var selected=Set.of(3L,101L,102L,103L,104L,105L,106L,107L,108L);
  var query=new ActiveProjectMemberQuery(1L,9L,at,selected);
  assertEquals(List.of(3L,101L,102L,105L),members.selectAuthorizedByUser(query).stream().map(ProjectMemberAssignmentDO::getProjectId).toList());
  assertEquals(List.of(3L,101L,102L,105L,108L),members.selectActiveByUser(query).stream().map(ProjectMemberAssignmentDO::getProjectId).toList());
  assertTrue(members.selectAuthorizedByUser(new ActiveProjectMemberQuery(1L,9L,at)).stream().anyMatch(row->row.getProjectId()==999L));
 }

 @Test void explicitEmptyProjectScopeDoesNotExpandToAllUserMemberships() {
  var query=new ActiveProjectMemberQuery(1L,9L,LocalDateTime.now(),Set.of());
  assertTrue(members.selectAuthorizedByUser(query).isEmpty());
  assertTrue(members.selectActiveByUser(query).isEmpty());
 }

 @Test void collaboratingServiceAuthorizationKeepsConfirmationIntervalAndFactResponsibilitySeparate() throws Exception {
  try(var c=data.getConnection();var sql=c.createStatement()) {
   sql.executeUpdate("UPDATE proj_project_member_assignment SET member_role='SERVICE_MANAGER',assignment_type='COLLABORATOR' WHERE id IN (1,2,3,4,5,6,7)");
  }
  var at=LocalDateTime.now();
  var read=ProjectParticipantFactLookupQuery.builder().tenantId(1L).projectId(3L).subjectUserId(9L).requiredRoleCodes(Set.of("SERVICE_MANAGER")).effectiveAt(at).build();
  assertEquals(1,members.selectAuthorizationParticipantFacts(read).size());
  assertTrue(members.selectParticipantFacts(read).isEmpty());
  var lock=ProjectParticipantFactLockQuery.builder().tenantId(1L).projectId(3L).userId(9L).requiredRoleCodes(Set.of("SERVICE_MANAGER")).build();
  assertEquals(1,members.selectAuthorizationParticipantFactsForUpdate(lock).size());
  assertTrue(members.selectParticipantFactsForUpdate(lock).isEmpty());
  for(long projectId:List.of(2L,4L,5L,6L,7L,8L)) {
   assertTrue(members.selectAuthorizationParticipantFacts(ProjectParticipantFactLookupQuery.builder().tenantId(1L).projectId(projectId).subjectUserId(9L).requiredRoleCodes(Set.of("SERVICE_MANAGER")).effectiveAt(at).build()).isEmpty());
   assertTrue(members.selectAuthorizationParticipantFactsForUpdate(ProjectParticipantFactLockQuery.builder().tenantId(1L).projectId(projectId).userId(9L).requiredRoleCodes(Set.of("SERVICE_MANAGER")).build()).isEmpty());
  }
 }

 @Test void authorizedLockExcludesUnconfirmedButFactLockStillRetainsItsRow(){
  var at=LocalDateTime.now();var unconfirmed=new ActiveProjectMemberForUpdateQuery(1L,2L,9L,at);
  assertTrue(members.selectAuthorizedByUserForUpdate(unconfirmed).isEmpty());
  assertEquals(1,members.selectActiveByUserForUpdate(unconfirmed).size());
  assertEquals(1,members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(1L,3L,9L,at)).size());
  assertTrue(members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(1L,6L,9L,at)).isEmpty());
 }
 @Test void confirmationAndBothQueriesUseTheSameLeftClosedRightOpenInterval()throws Exception{
  var at=LocalDateTime.of(2026,10,29,1,0);
  try(var c=data.getConnection();var sql=c.prepareStatement("UPDATE proj_project_member_assignment SET effective_from=?,effective_to=? WHERE id=2")){
   sql.setObject(1,at);sql.setObject(2,at.plusSeconds(1));sql.executeUpdate();
  }
  var query=new ActiveProjectMemberQuery(1L,9L,at);
  assertEquals(List.of(3L),members.selectAuthorizedByUser(query).stream().map(ProjectMemberAssignmentDO::getProjectId).toList());
  assertEquals(1,members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(1L,3L,9L,at)).size());
  assertTrue(members.selectAuthorizedByUser(new ActiveProjectMemberQuery(1L,9L,at.plusSeconds(1))).isEmpty());
  assertTrue(members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(1L,3L,9L,at.plusSeconds(1))).isEmpty());
  assertTrue(members.selectAuthorizedByUser(new ActiveProjectMemberQuery(1L,null,at)).isEmpty());
 }

 @Test void missingAuthorizationIdentityOrTimeCannotLockAnyMember(){
  assertTrue(members.selectAuthorizedByUserForUpdate(null).isEmpty());
  assertTrue(members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(1L,3L,9L,null)).isEmpty());
  assertTrue(members.selectAuthorizedByUserForUpdate(new ActiveProjectMemberForUpdateQuery(null,3L,9L,LocalDateTime.now())).isEmpty());
  assertTrue(members.selectAuthorizedByUser(null).isEmpty());
 }

 cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi participantFacts(){
  var projects=mock(ProjectMasterMapper.class);
  org.mockito.stubbing.Answer<ProjectMasterDO> answer=call->{var p=project((Long)call.getArgument(0));p.setManagerId(9L);p.setVersion(3L);p.setLifecycleStatus("ACTIVE");p.setCurrentStage("S1");return p;};
  when(projects.selectById(anyLong())).thenAnswer(answer);when(projects.selectByIdForUpdate(anyLong())).thenAnswer(answer);
  return new cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApiImpl(projects,members);
 }
 @Test void factualParticipantRowsAndPointerRemainButCannotSubstituteForAuthorization(){
  var api=participantFacts();var roles=Set.of("PROJECT_MANAGER");
  var q=new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery(2L,9L,roles,LocalDateTime.now());
  assertEquals(9L,api.inspect(q).userId());
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.inspectAuthorization(q));
  var locked=new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery(2L,9L,3L,"ACTIVE",null,roles);
  assertEquals(9L,api.lockAndRevalidate(locked).userId());
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.lockAndRevalidateAuthorization(locked));
  var pointer=new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery(2L,null,roles,LocalDateTime.now());
  assertEquals(9L,api.inspect(pointer).userId());assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.inspectAuthorization(pointer));
 }
 @Test void confirmedAuthorizationUsesOwnerCurrentTimeAndRetainsProjectVersionRevalidation(){
  var api=participantFacts();var roles=Set.of("PROJECT_MANAGER");
  assertEquals(9L,api.inspectAuthorization(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery(3L,9L,roles,LocalDateTime.now())).userId());
  assertEquals(9L,api.lockAndRevalidateAuthorization(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery(3L,9L,3L,"ACTIVE",null,roles)).userId());
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.lockAndRevalidateAuthorization(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery(3L,9L,4L,"ACTIVE",null,roles)));
  for(var id:List.of(4L,5L,6L,7L,8L))assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->api.inspectAuthorization(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery(id,9L,roles,LocalDateTime.of(2100,1,1,0,0))));
 }
}
