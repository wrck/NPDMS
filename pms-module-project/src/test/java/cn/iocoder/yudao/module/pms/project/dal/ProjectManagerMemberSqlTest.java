package cn.iocoder.yudao.module.pms.project.dal;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectManagerMemberQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectManagerMemberSqlTest {
    @Test void accountBasedManagerCommandExcludesUnlinkedHistoryAndKeepsProjectTenantAndEffectiveScope() throws Exception {
        var configuration = new Configuration();
        for (var resource : List.of("/mapper/authorization/ProjectViewGrantReadSql.xml",
                "/mapper/projectscope/ProjectViewReadScopeSql.xml", "/mapper/projectmanual/ProjectMemberAssignmentMapper.xml")) {
            try (var xml = getClass().getResourceAsStream(resource)) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        var now = LocalDateTime.of(2026, 10, 30, 12, 0);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:manager_member_accounts;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE proj_project_member_assignment(id BIGINT,tenant_id BIGINT,project_id BIGINT,user_id BIGINT,member_role VARCHAR(32),status VARCHAR(16),deleted BOOLEAN,effective_from TIMESTAMP,effective_to TIMESTAMP)");
            statement.execute("INSERT INTO proj_project_member_assignment VALUES (1,1,100,NULL,'PROJECT_MANAGER','ACTIVE',false,NULL,NULL),(2,1,100,40,'PROJECT_MANAGER','ACTIVE',false,NULL,NULL),(3,2,100,41,'PROJECT_MANAGER','ACTIVE',false,NULL,NULL),(4,1,101,42,'PROJECT_MANAGER','ACTIVE',false,NULL,NULL),(5,1,100,43,'PROJECT_MANAGER','ACTIVE',false,NULL,'2026-10-29 12:00:00'),(6,1,100,44,'PROJECT_MANAGER','ACTIVE',false,'2026-10-31 12:00:00',NULL),(7,1,100,45,'PROJECT_MANAGER','INACTIVE',false,NULL,NULL),(8,1,100,46,'PROJECT_MANAGER','ACTIVE',true,NULL,NULL)");
            var parameters = Map.of("query", new ProjectManagerMemberQuery(1L, 100L, now));
            var bound = configuration.getMappedStatement("cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectProjectManagersForUpdate").getBoundSql(parameters);
            try (var query = connection.prepareStatement(bound.getSql().replace("b'0'", "false"))) {
                int index = 1;
                for (var mapping : bound.getParameterMappings()) {
                    query.setObject(index++, configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
                }
                var ids = new ArrayList<Long>();
                try (var rows = query.executeQuery()) { while (rows.next()) ids.add(rows.getLong("id")); }
                assertEquals(List.of(2L), ids);
            }
        }
    }
}
