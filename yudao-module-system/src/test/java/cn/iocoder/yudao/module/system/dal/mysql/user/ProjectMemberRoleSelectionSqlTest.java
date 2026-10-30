package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.module.system.dal.mysql.user.query.ProjectMemberRoleUserPageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 合成H2角色事实；没有项目或公司表，不声称已执行真实MySQL或浏览器验收。 */
class ProjectMemberRoleSelectionSqlTest {
    SqlSession session;
    ProjectMemberRoleSelectionMapper mapper;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:role_selection_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE system_users(id BIGINT,tenant_id BIGINT,username VARCHAR(64),nickname VARCHAR(64),dept_id BIGINT,status INT,deleted INT,email VARCHAR(255))");
            sql.execute("CREATE TABLE system_role(id BIGINT,tenant_id BIGINT,code VARCHAR(64),status INT,deleted INT)");
            sql.execute("CREATE TABLE system_user_role(user_id BIGINT,role_id BIGINT,tenant_id BIGINT,deleted INT)");
            sql.execute("INSERT INTO system_users VALUES(1,1,'pm','项目经理',10,0,0,'zhangsan.pm@test.invalid'),(2,1,'sm','服务经理',20,0,0,'zhangsan.sm@test.invalid')");
            sql.execute("INSERT INTO system_role VALUES(10,1,'PROJECT_MANAGER',0,0),(20,1,'SERVICE_MANAGER',0,0)");
            sql.execute("INSERT INTO system_user_role VALUES(1,10,1,0),(2,20,1,0)");
        }
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("role-selection", new JdbcTransactionFactory(), source));
        String resource = "mapper/user/ProjectMemberRoleSelectionMapper.xml";
        try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(xml);
            var text = new String(xml.readAllBytes(), StandardCharsets.UTF_8).replace("b'0'", "0");
            new XMLMapperBuilder(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), configuration,
                    resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(ProjectMemberRoleSelectionMapper.class);
    }

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void roleCandidatesRemainAvailableWhenActorHasNoVisibleProjectMembers() {
        var query = query(Set.of("PROJECT_MANAGER", "SERVICE_MANAGER"), "zhangsan", 1, 20, Set.of());
        assertEquals(2L, mapper.selectCount(query));
        assertEquals(List.of(1L, 2L), mapper.selectPage(query).stream().map(user -> user.getId()).toList());
    }

    private static final Set<String> MANAGEMENT = Set.of("PROJECT_MANAGER", "SERVICE_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    @Test
    void roleUnionCountsEachUserOnceAndPagesInIdOrder() throws Exception {
        matrix();
        var request = query(MANAGEMENT, null, 1, 1, Set.of());
        assertEquals(5L, mapper.selectCount(request));
        for (int page = 1; page <= 5; page++) {
            assertEquals(List.of((long) page), mapper.selectPage(query(MANAGEMENT, null, page, 1, Set.of()))
                    .stream().map(user -> user.getId()).toList());
        }
        assertTrue(mapper.selectPage(query(MANAGEMENT, null, 6, 1, Set.of())).isEmpty());
        assertEquals(4L, mapper.selectCount(query(Set.of("SERVICE_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2"), null, 1, 20, Set.of())));
        assertEquals(List.of(6L), mapper.selectPage(query(Set.of("SALES_REPRESENTATIVE"), null, 1, 20, Set.of()))
                .stream().map(user -> user.getId()).toList());
    }

    @Test
    void searchSupportsIdentityAndOnlyPermittedDepartmentMatchesWithinRoleQualification() throws Exception {
        matrix();
        assertEquals(5L, mapper.selectCount(query(MANAGEMENT, "zhangsan", 1, 20, Set.of())));
        assertEquals(List.of(1L), mapper.selectPage(query(MANAGEMENT, "pm@test.invalid", 1, 20, Set.of()))
                .stream().map(user -> user.getId()).toList());
        assertEquals(List.of(2L), mapper.selectPage(query(MANAGEMENT, "服务经理", 1, 20, Set.of()))
                .stream().map(user -> user.getId()).toList());
        assertEquals(1L, mapper.selectCount(query(MANAGEMENT, "safeDepartment", 1, 20, Set.of(10L))));
        assertEquals(0L, mapper.selectCount(query(MANAGEMENT, "safeDepartment", 1, 20, Set.of())));
        assertEquals(0L, mapper.selectCount(query(MANAGEMENT, "sales-only", 1, 20, Set.of())));
    }

    @Test
    void emptyOrNullRoleSetsFailClosedAndDepartmentIdsUseSameRoleQualification() throws Exception {
        matrix();
        assertEquals(Set.of(10L, 20L, 30L, 40L, 50L), mapper.selectDepartmentIds(query(MANAGEMENT, null, 1, 20, Set.of())));
        for (var roles : Arrays.asList(Set.<String>of(), null)) {
            var request = query(roles, null, 1, 20, Set.of());
            assertEquals(0L, mapper.selectCount(request));
            assertTrue(mapper.selectPage(request).isEmpty());
            assertTrue(mapper.selectDepartmentIds(request).isEmpty());
        }
    }

    private ProjectMemberRoleUserPageQuery query(Set<String> roles, String keyword, int page, int size, Set<Long> departments) {
        return new ProjectMemberRoleUserPageQuery(1L, roles, keyword, page, size, departments);
    }

    private void matrix() throws Exception {
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO system_users VALUES(3,1,'both','双角色',30,0,0,'zhangsan.both@test.invalid'),(4,1,'legacy-l1','L1',40,0,0,'zhangsan.l1@test.invalid'),(5,1,'legacy-l2','L2',50,0,0,'zhangsan.l2@test.invalid'),(6,1,'sales-only','销售',60,0,0,'zhangsan.sales@test.invalid'),(7,1,'unrelated','其他',70,0,0,'zhangsan.other@test.invalid'),(8,1,'disabled-user','停用用户',80,1,0,'zhangsan@test.invalid'),(9,1,'deleted-user','删除用户',90,0,1,'zhangsan@test.invalid'),(10,1,'disabled-role','停用角色',100,0,0,'zhangsan@test.invalid'),(11,1,'deleted-role','删除角色',110,0,0,'zhangsan@test.invalid'),(12,1,'deleted-assignment','删除绑定',120,0,0,'zhangsan@test.invalid'),(13,2,'foreign','其他租户',130,0,0,'zhangsan@test.invalid'),(14,1,'foreign-assignment','跨租户绑定',140,0,0,'zhangsan@test.invalid'),(15,1,'foreign-role','跨租户角色',150,0,0,'zhangsan@test.invalid'),(16,1,'no-role','无角色',160,0,0,'zhangsan@test.invalid')");
            sql.execute("INSERT INTO system_role VALUES(30,1,'SERVICE_MANAGER_L1',0,0),(40,1,'SERVICE_MANAGER_L2',0,0),(50,1,'SALES_REPRESENTATIVE',0,0),(60,1,'ADMIN',0,0),(70,1,'PROJECT_MANAGER',1,0),(80,1,'PROJECT_MANAGER',0,1),(90,2,'PROJECT_MANAGER',0,0)");
            sql.execute("INSERT INTO system_user_role VALUES(3,10,1,0),(3,20,1,0),(3,20,1,0),(4,30,1,0),(5,40,1,0),(6,50,1,0),(7,60,1,0),(8,10,1,0),(9,10,1,0),(10,70,1,0),(11,80,1,0),(12,10,1,1),(13,90,2,0),(14,10,2,0),(15,90,1,0)");
        }
    }

}
