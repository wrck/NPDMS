package cn.iocoder.yudao.module.system.dal.mysql.user;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserPageQuery;import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.mapping.Environment;import org.apache.ibatis.session.*;import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;import org.h2.jdbcx.JdbcDataSource;import org.junit.jupiter.api.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ScopedActiveUserSqlTest {
 SqlSession session;AdminUserMapper mapper;
 @BeforeEach void setup()throws Exception{var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:identity_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");try(var c=source.getConnection();var s=c.createStatement()){s.execute("CREATE TABLE system_users(id BIGINT,tenant_id BIGINT,username VARCHAR(64),nickname VARCHAR(64),dept_id BIGINT,status INT,deleted INT)");s.execute("INSERT INTO system_users VALUES(1,1,'self','Self',10,0,0),(2,1,'h06523','Other',20,0,0),(3,1,'disabled','Other',30,1,0),(4,1,'deleted','Other',40,0,1),(5,2,'foreign','Other',50,0,0),(6,1,'second','Other',60,0,0)");}var c=new Configuration();c.setMapUnderscoreToCamelCase(true);c.setEnvironment(new Environment("identity",new JdbcTransactionFactory(),source));try(var xml=getClass().getClassLoader().getResourceAsStream("mapper/user/AdminUserMapper.xml")){var text=new String(xml.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0");new XMLMapperBuilder(new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),c,"user",c.getSqlFragments()).parse();}session=new SqlSessionFactoryBuilder().build(c).openSession();mapper=session.getMapper(AdminUserMapper.class);}
 @AfterEach void close(){session.close();}
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
