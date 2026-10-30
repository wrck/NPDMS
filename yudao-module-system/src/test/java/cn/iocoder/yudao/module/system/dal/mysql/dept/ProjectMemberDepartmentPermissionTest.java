package cn.iocoder.yudao.module.system.dal.mysql.dept;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ProjectMemberDepartmentPermissionTest {
 SqlSession session;DeptMapper departments;PermissionCommonApi permissions;
 @BeforeEach void setup()throws Exception{
  TenantContextHolder.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L).setUserType(UserTypeEnum.ADMIN.getValue()),new MockHttpServletRequest());
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:dept_permission_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  try(var c=source.getConnection();var sql=c.createStatement()){
   sql.execute("CREATE TABLE system_dept(id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(100),status INT,deleted BOOLEAN)");
   sql.execute("INSERT INTO system_dept VALUES(10,1,'可见团队',1,FALSE),(20,1,'不可见团队',0,FALSE),(30,1,'已删除团队',0,TRUE),(40,2,'外租户团队',0,FALSE)");
  }
  var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("dept-permission",new JdbcTransactionFactory(),source));config.addMapper(DeptMapper.class);
  permissions=mock(PermissionCommonApi.class);when(permissions.getDeptDataPermission(7L)).thenReturn(new DeptDataPermissionRespDTO().setDeptIds(Set.of(10L,30L,40L)));
  var rule=new DeptDataPermissionRule(permissions);rule.addDeptColumn(DeptDO.class,"id");
  var interceptor=new MybatisPlusInterceptor();interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(new DataPermissionRuleFactoryImpl(List.of(rule)))));config.addInterceptor(interceptor);
  session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);departments=session.getMapper(DeptMapper.class);
 }
 @AfterEach void clear(){if(session!=null)session.close();TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 @Test void actualDepartmentInterceptorRestrictsNameSearchAndDisplayWithoutDroppingDisabledVisibleNames(){
  var ids=Set.of(10L,20L,30L,40L);
  assertEquals(List.of(10L),departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,ids,"团队")).stream().map(DeptDO::getId).toList());
  assertEquals(List.of("可见团队"),departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,ids,null)).stream().map(DeptDO::getName).toList());
  assertTrue(departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,Set.of(20L),"不可见")).isEmpty());
 }
 @Test void selfOnlyDepartmentPermissionAndEmptyCandidateIdsCannotBecomeAllDepartments(){
  when(permissions.getDeptDataPermission(7L)).thenReturn(new DeptDataPermissionRespDTO().setSelf(true));
  assertTrue(departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,Set.of(10L,20L),"团队")).isEmpty());
  assertTrue(departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,Set.of(),null)).isEmpty());
  assertTrue(departments.selectProjectMemberDepartments(new ProjectMemberDepartmentsQuery(1L,null,null)).isEmpty());
 }
}
