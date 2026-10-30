package cn.iocoder.yudao.module.system.dal.mysql.user;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserPageQuery;import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.mapping.Environment;import org.apache.ibatis.session.*;import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;import org.h2.jdbcx.JdbcDataSource;import org.junit.jupiter.api.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ScopedActiveUserSqlTest {
 SqlSession session;AdminUserMapper mapper;
 @BeforeEach void setup()throws Exception{var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:identity_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");try(var c=source.getConnection();var s=c.createStatement()){s.execute("CREATE TABLE system_users(id BIGINT,tenant_id BIGINT,username VARCHAR(64),nickname VARCHAR(64),dept_id BIGINT,status INT,deleted INT,email VARCHAR(255))");s.execute("INSERT INTO system_users(id,tenant_id,username,nickname,dept_id,status,deleted) VALUES(1,1,'self','Self',10,0,0),(2,1,'h06523','Other',20,0,0),(3,1,'disabled','Other',30,1,0),(4,1,'deleted','Other',40,0,1),(5,2,'foreign','Other',50,0,0),(6,1,'second','Other',60,0,0)");}var c=new Configuration();c.setMapUnderscoreToCamelCase(true);c.setEnvironment(new Environment("identity",new JdbcTransactionFactory(),source));try(var xml=getClass().getClassLoader().getResourceAsStream("mapper/user/AdminUserMapper.xml")){var text=new String(xml.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0");new XMLMapperBuilder(new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),c,"user",c.getSqlFragments()).parse();}session=new SqlSessionFactoryBuilder().build(c).openSession();mapper=session.getMapper(AdminUserMapper.class);}
 @AfterEach void close(){session.close();}
 @Test void emailPinyinSearchKeepsScopedIdentityBoundariesAndStablePaging() throws Exception {
  emailFixtures();
  var ids=Set.of(2L,3L,4L,5L,6L,7L);
  var query=new ScopedActiveUserPageQuery(1L,ids,"zhangsan",1,1,Set.of());
  assertEquals(3,mapper.selectScopedActiveUserCount(query));
  assertEquals(List.of(2L),mapper.selectScopedActiveUserPage(query).stream().map(user->user.getId()).toList());
  assertEquals(List.of(6L),mapper.selectScopedActiveUserPage(new ScopedActiveUserPageQuery(1L,ids,"zhangsan",2,1,Set.of())).stream().map(user->user.getId()).toList());
  assertEquals(1,mapper.selectScopedActiveUserCount(new ScopedActiveUserPageQuery(1L,ids,"zhangsan.one@example.test",1,20,Set.of())));
  assertEquals(0,mapper.selectScopedActiveUserCount(new ScopedActiveUserPageQuery(1L,Set.of(3L,4L,5L),"zhangsan",1,20,Set.of())));
  assertEquals(0,mapper.selectScopedActiveUserCount(new ScopedActiveUserPageQuery(1L,Set.of(),"zhangsan",1,20,Set.of())));
 }

 @Test void emailPinyinSearchKeepsSystemRoleQualificationAndCountPageConsistent() throws Exception {
  emailFixtures();
  var query=cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery.builder()
    .tenantId(1L).pageNo(1).pageSize(1).keyword("zhangsan").roleCode("PROJECT_MANAGER").build();
  assertEquals(3,mapper.selectActiveSelectionCount(query));
  assertEquals(List.of(1L),mapper.selectActiveSelectionPage(query).stream().map(user->user.getId()).toList());
  var second=cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery.builder()
    .tenantId(1L).pageNo(2).pageSize(1).keyword("zhangsan").roleCode("PROJECT_MANAGER").build();
  assertEquals(List.of(2L),mapper.selectActiveSelectionPage(second).stream().map(user->user.getId()).toList());
  for(var ids:List.of(Set.of(3L,4L,5L,7L,8L,9L,10L),Set.<Long>of())) {
   var restricted=cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery.builder()
     .tenantId(1L).pageNo(1).pageSize(20).keyword("zhangsan").roleCode("PROJECT_MANAGER").userIds(ids).build();
   assertEquals(0,mapper.selectActiveSelectionCount(restricted));
   assertTrue(mapper.selectActiveSelectionPage(restricted).isEmpty());
  }
  var qualified=cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery.builder()
    .tenantId(1L).pageNo(1).pageSize(20).keyword("zhangsan").roleCode("PROJECT_MANAGER")
    .companyId(8L).departmentId(20L).departmentCode("OFFICE").scopeRole("PROJECT_MANAGER")
    .effectiveAt(java.time.LocalDateTime.of(2026,10,30,0,0)).build();
  assertEquals(1,mapper.selectActiveSelectionCount(qualified));
  assertEquals(List.of(2L),mapper.selectActiveSelectionPage(qualified).stream().map(user->user.getId()).toList());
 }

 private void emailFixtures() throws Exception {
  try(var sql=session.getConnection().createStatement()) {
   sql.execute("UPDATE system_users SET email='zhangsan.other@example.test'");
   sql.execute("UPDATE system_users SET email='zhangsan.one@example.test' WHERE id=2");
   sql.execute("INSERT INTO system_users VALUES(7,1,'sales','Sales',70,0,0,'zhangsan.sales@example.test')");
   sql.execute("INSERT INTO system_users VALUES(8,1,'inactive-role','Role',80,0,0,'zhangsan.role@example.test'),(9,1,'deleted-role','Role',90,0,0,'zhangsan.role@example.test'),(10,1,'no-role','Role',100,0,0,'zhangsan.role@example.test')");
   sql.execute("CREATE TABLE system_role(id BIGINT,tenant_id BIGINT,code VARCHAR(64),status INT,deleted INT)");
   sql.execute("CREATE TABLE system_user_role(user_id BIGINT,role_id BIGINT,tenant_id BIGINT,deleted INT)");
   sql.execute("INSERT INTO system_role VALUES(10,1,'PROJECT_MANAGER',0,0),(20,2,'PROJECT_MANAGER',0,0),(30,1,'SALES_REPRESENTATIVE',0,0),(40,1,'PROJECT_MANAGER',1,0),(50,1,'PROJECT_MANAGER',0,1)");
   sql.execute("INSERT INTO system_user_role VALUES(1,10,1,0),(2,10,1,0),(3,10,1,0),(4,10,1,0),(5,20,2,0),(6,10,1,0),(7,30,1,0),(8,40,1,0),(9,50,1,0)");
   sql.execute("CREATE TABLE system_company(id BIGINT,tenant_id BIGINT,status INT,deleted INT)");
   sql.execute("CREATE TABLE system_dept(id BIGINT,tenant_id BIGINT,code VARCHAR(64),status INT,deleted INT)");
   sql.execute("CREATE TABLE system_user_company_department_scope(tenant_id BIGINT,user_id BIGINT,company_id BIGINT,department_id BIGINT,department_code VARCHAR(64),scope_role VARCHAR(64),status INT,deleted INT,effective_from TIMESTAMP,effective_to TIMESTAMP)");
   sql.execute("INSERT INTO system_company VALUES(8,1,0,0)");
   sql.execute("INSERT INTO system_dept VALUES(20,1,'OFFICE',0,0),(60,1,'OTHER',0,0)");
   sql.execute("INSERT INTO system_user_company_department_scope VALUES(1,1,8,20,'OFFICE','PROJECT_MANAGER',0,0,TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-10-29 00:00:00'),(1,2,8,20,'OFFICE','PROJECT_MANAGER',0,0,TIMESTAMP '2026-01-01 00:00:00',NULL),(1,6,8,60,'OTHER','PROJECT_MANAGER',0,0,TIMESTAMP '2026-01-01 00:00:00',NULL)");
  }
 }
 @Test void usernameAndNicknameSearchUsesOnlyAuthorizedCurrentEnabledUsersAndStablePaging(){var ids=Set.of(2L,3L,4L,5L,6L);var q=new ScopedActiveUserPageQuery(1L,ids,"Other",1,1, java.util.Set.of());assertEquals(2,mapper.selectScopedActiveUserCount(q));assertEquals(List.of(2L),mapper.selectScopedActiveUserPage(q).stream().map(u->u.getId()).toList());assertEquals(List.of(6L),mapper.selectScopedActiveUserPage(new ScopedActiveUserPageQuery(1L,ids,"Other",2,1, java.util.Set.of())).stream().map(u->u.getId()).toList());assertEquals(List.of(2L),mapper.selectScopedActiveUserPage(new ScopedActiveUserPageQuery(1L,ids,"h06523",1,20, java.util.Set.of())).stream().map(u->u.getId()).toList());assertEquals(0,mapper.selectScopedActiveUserCount(new ScopedActiveUserPageQuery(1L,ids,"self",1,20, java.util.Set.of())));}
 @Test void emptyAndNullIdsNeverMeanTheWholeDirectory(){for(var q:List.of(new ScopedActiveUserPageQuery(1L,Set.of(),null,1,20, java.util.Set.of()),new ScopedActiveUserPageQuery(1L,null,null,1,20, java.util.Set.of()))){assertEquals(0,mapper.selectScopedActiveUserCount(q));assertTrue(mapper.selectScopedActiveUserPage(q).isEmpty());}}

 @Test void departmentNameMatchesAreStillLimitedToAuthorizedActiveTenantUsers(){
  var q=new ScopedActiveUserPageQuery(1L,Set.of(1L,2L,3L,4L,5L,6L),"KnownDepartment",1,20,Set.of(20L,50L));
  assertEquals(1,mapper.selectScopedActiveUserCount(q));
  assertEquals(List.of(2L),mapper.selectScopedActiveUserPage(q).stream().map(user->user.getId()).toList());
  assertEquals(0,mapper.selectScopedActiveUserCount(new ScopedActiveUserPageQuery(1L,Set.of(1L),"KnownDepartment",1,20,Set.of(20L))));
 }

 @Test void departmentMetadataReadsOnlyQualifiedCurrentTenantUsers(){
  var query=new cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserDepartmentsQuery(1L,Set.of(2L,3L,4L,5L,6L));
  assertEquals(Set.of(20L,60L),mapper.selectScopedActiveUserDepartmentIds(query));
  assertTrue(mapper.selectScopedActiveUserDepartmentIds(new cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserDepartmentsQuery(1L,Set.of())).isEmpty());
 }
}
