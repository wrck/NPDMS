package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "npdms.deviceScope.mysql", matches = "true")
class DevicePageProjectScopeMySqlTest {
    @Test
    void nativeCteKeepsExactPairsAndTenantWhenScopeChangesOnTheSameConnection() throws Exception {
        var configuration = new Configuration();
        for (var resource : List.of("mapper/authorization/ProjectViewGrantReadSql.xml",
                "mapper/projectscope/ProjectViewReadScopeSql.xml")) {
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml, resource);
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        String mapper = """
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="DevicePageScopeProbe"><select id="select" resultType="long">
                WITH <include refid="cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeSql.commonTables"/>
                SELECT project_id FROM visible_projects
                </select></mapper>
                """;
        new XMLMapperBuilder(new ByteArrayInputStream(mapper.getBytes(StandardCharsets.UTF_8)), configuration,
                "DevicePageScopeProbe", configuration.getSqlFragments()).parse();
        try (var db = DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + System.getenv("NPDMS_MYSQL_PORT")
                + "/" + System.getenv("NPDMS_DB_NAME") + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"))) {
            try (var sql = db.createStatement()) {
                // Connection-local shadow only; no writes to the existing business table.
                sql.execute("CREATE TEMPORARY TABLE proj_project_tree_path(tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,deleted BIT)");
                sql.execute("INSERT INTO proj_project_tree_path VALUES (1,10,7,10,11,0),(1,20,8,20,21,0),(2,10,7,10,99,0),(1,10,7,10,98,1)");
            }
            var current = List.of(new ProjectViewReadScopeApi.Tree(10L, 7L), new ProjectViewReadScopeApi.Tree(20L, 8L));
            var crossed = List.of(new ProjectViewReadScopeApi.Tree(10L, 8L), new ProjectViewReadScopeApi.Tree(20L, 7L));
            assertEquals(Set.of(11L, 21L), projects(db, configuration, 1L, current));
            assertEquals(Set.of(), projects(db, configuration, 1L, crossed));
            assertEquals(Set.of(), projects(db, configuration, 1L, List.of()));
            assertEquals(Set.of(), projects(db, configuration, 2L, current));
            assertEquals(Set.of(11L, 21L), projects(db, configuration, 1L, current));
        }
    }

    private Set<Long> projects(Connection db, Configuration configuration, Long scopeTenant,
                               List<ProjectViewReadScopeApi.Tree> trees) throws Exception {
        var scope = new ProjectViewReadScopeApi.ReadScope(scopeTenant, 7L, ProjectViewReadScopeApi.Mode.TREE_ADMIN,
                LocalDateTime.of(2026, 10, 24, 12, 0), trees);
        var parameters = Map.of("query", VisibleDevicePageQuery.builder().tenantId(1L)
                .readScope(new DevicePageReadScope(scope, null)).build());
        var bound = configuration.getMappedStatement("DevicePageScopeProbe.select").getBoundSql(parameters);
        try (var statement = db.prepareStatement(bound.getSql())) {
            int position = 1;
            for (var mapping : bound.getParameterMappings()) {
                String property = mapping.getProperty();
                statement.setObject(position++, bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property)
                        : configuration.newMetaObject(parameters).getValue(property));
            }
            var ids = new HashSet<Long>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) ids.add(rows.getLong(1));
            }
            return ids;
        }
    }
}
