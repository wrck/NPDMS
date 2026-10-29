package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationRoleScopeQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** 仅内存合成 H2；不连接178库，不声称MySQL执行计划或真实竞争已验收。 */
class OrganizationRoleScopeSqlTest {
 static final Set<String> CODES=Set.of("SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2");
 Configuration config;
 @BeforeEach void setup() throws Exception {
  config=new Configuration();String resource="mapper/permission/UserCompanyDepartmentScopeMapper.xml";
  try(var xml=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(xml);new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();}
 }
 Connection database() throws Exception {
  var c=DriverManager.getConnection("jdbc:h2:mem:role_scope_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1500");
  var s=c.createStatement();
  s.execute("CREATE TABLE system_users(id BIGINT PRIMARY KEY,tenant_id BIGINT,status INT,deleted INT)");
  s.execute("CREATE TABLE system_role(id BIGINT PRIMARY KEY,tenant_id BIGINT,code VARCHAR(32),status INT,deleted INT)");
  s.execute("CREATE TABLE system_user_role(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,tenant_id BIGINT,deleted INT)");
  s.execute("CREATE TABLE system_user_company_department_scope(id BIGINT PRIMARY KEY,user_id BIGINT,tenant_id BIGINT,company_id BIGINT,department_id BIGINT,scope_role VARCHAR(32),status INT,deleted INT,effective_from TIMESTAMP,effective_to TIMESTAMP,version INT)");
  s.execute("INSERT INTO system_users VALUES(9,1,0,0)");s.execute("INSERT INTO system_role VALUES(200,1,'SERVICE_MANAGER',0,0)");s.execute("INSERT INTO system_user_role VALUES(100,9,200,1,0)");
  s.execute("INSERT INTO system_user_company_department_scope VALUES(1,9,1,11,21,'SERVICE_MANAGER',0,0,'2020-01-01',NULL,0)");return c;
 }
 Long selected(Connection c,String method,OrganizationRoleScopeQuery query) throws Exception {
  var params=Map.of("query",query);var bound=config.getMappedStatement("cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper."+method).getBoundSql(params);
  try(var statement=c.prepareStatement(bound.getSql().replace(" AS BINARY)"," AS VARBINARY)"))) {
   int i=1;for(var p:bound.getParameterMappings()){String key=p.getProperty();statement.setObject(i++,bound.hasAdditionalParameter(key)?bound.getAdditionalParameter(key):config.newMetaObject(params).getValue(key));}
   try(var rows=statement.executeQuery()){return rows.next()?rows.getLong("id"):null;}
  }
 }
 OrganizationRoleScopeQuery query(){return new OrganizationRoleScopeQuery(1L,9L,11L,21L,CODES);}
 @TestFactory Stream<DynamicTest> currentQualificationMatrix() {
  record Case(String name,String mutation,boolean allowed){}
  return Stream.of(
   new Case("same company and department","",true),
   new Case("company-wide grant","UPDATE system_user_company_department_scope SET department_id=NULL",true),
   new Case("cross company","UPDATE system_user_company_department_scope SET company_id=12",false),
   new Case("cross department","UPDATE system_user_company_department_scope SET department_id=22",false),
   new Case("expired grant","UPDATE system_user_company_department_scope SET effective_to='2020-02-01'",false),
   new Case("future grant","UPDATE system_user_company_department_scope SET effective_from='2099-01-01'",false),
   new Case("disabled grant","UPDATE system_user_company_department_scope SET status=1",false),
   new Case("deleted grant","UPDATE system_user_company_department_scope SET deleted=1",false),
   new Case("only global role no scope","DELETE FROM system_user_company_department_scope",false),
   new Case("scope only no global role","DELETE FROM system_user_role",false),
   new Case("pm global role","UPDATE system_role SET code='PROJECT_MANAGER'",false),
   new Case("pm scope role","UPDATE system_user_company_department_scope SET scope_role='PROJECT_MANAGER'",false),
   new Case("role code Java equals semantics","UPDATE system_role SET code='service_manager'",false),
   new Case("scope code Java equals semantics","UPDATE system_user_company_department_scope SET scope_role='SERVICE_MANAGER '",false),
   new Case("disabled role","UPDATE system_role SET status=1",false),
   new Case("disabled user","UPDATE system_users SET status=1",false),
   new Case("cross tenant association","UPDATE system_user_role SET tenant_id=2",false),
   new Case("cross tenant role","UPDATE system_role SET tenant_id=2",false),
   new Case("cross tenant scope","UPDATE system_user_company_department_scope SET tenant_id=2",false),
   new Case("legacy role aliases preserve current rows semantics","UPDATE system_role SET code='SERVICE_MANAGER_L1'; UPDATE system_user_company_department_scope SET scope_role='SERVICE_MANAGER_L2'",true)
  ).map(item->DynamicTest.dynamicTest(item.name(),()->{try(var c=database()){
   if(!item.mutation().isEmpty())for(String mutation:item.mutation().split(";"))c.createStatement().execute(mutation);
   assertEquals(item.allowed()?1L:null,selected(c,"selectRoleScope",query()));
   c.setAutoCommit(false);assertEquals(item.allowed()?1L:null,selected(c,"selectRoleScopeForUpdate",query()));c.rollback();
  }}));
 }
 @Test void emptyRoleSetAndCrossTenantQueryFailClosed() throws Exception {
  try(var c=database()){
   assertNull(selected(c,"selectRoleScope",new OrganizationRoleScopeQuery(1L,9L,11L,21L,Set.of())));
   assertNull(selected(c,"selectRoleScope",new OrganizationRoleScopeQuery(2L,9L,11L,21L,CODES)));
  }
 }
 @Test void scopeRevocationWaitsForCurrentWriteAndNextWriteSeesRevokedGrant() throws Exception {
  try(var first=database()){
   first.setAutoCommit(false);assertEquals(1L,selected(first,"selectRoleScopeForUpdate",query()));
   var started=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
   try {
    var revoked=executor.submit(()->{try(var second=DriverManager.getConnection(first.getMetaData().getURL())){started.countDown();return second.createStatement().executeUpdate("UPDATE system_user_company_department_scope SET status=1,version=version+1 WHERE id=1");}});
    assertTrue(started.await(1,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->revoked.get(150,TimeUnit.MILLISECONDS));
    first.commit();assertEquals(1,revoked.get(2,TimeUnit.SECONDS));
    assertNull(selected(first,"selectRoleScopeForUpdate",query()));first.rollback();
   } finally {first.rollback();executor.shutdownNow();}
  }
 }
}
