package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectWaitTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectChildClosureFactsQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProjectChildWaitQueryTest {
    @Test void realQueriesKeepTenantLogicalDeletionAndFrozenTreeScopeAndNeverExpandEmptyIds() {
        // Dedicated in-memory database: no application config, environment credentials or external connection.
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE proj_project (id BIGINT PRIMARY KEY,tenant_id BIGINT,lifecycle_status VARCHAR(30),deleted INT)");
            jdbc.execute("INSERT INTO proj_project VALUES (9,7,'ACTIVE',0),(10,7,'NORMAL_CLOSED',0),(11,8,'EXCEPTION_CLOSED',0),(12,7,'NORMAL_CLOSED',1)");
            jdbc.execute("CREATE TABLE proj_project_tree_path (id BIGINT PRIMARY KEY,tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,distance INT,version INT,deleted INT,creator VARCHAR(30),updater VARCHAR(30),create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_tree_path (id,tenant_id,root_project_id,tree_version,ancestor_project_id,descendant_project_id,distance,deleted) VALUES "
                    + "(1,7,1,4,9,9,0,0),(2,7,1,4,9,10,1,0),(3,8,1,4,9,11,1,0),(4,7,1,4,9,12,1,1),(5,7,1,3,9,20,1,0),(6,7,2,4,9,21,1,0)");
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("child-wait", new JdbcTransactionFactory(), database));
            var interceptors = new MybatisPlusInterceptor();
            interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            configuration.addInterceptor(interceptors);
            configuration.addMapper(ProjectChildClosureMapper.class);
            configuration.addMapper(ProjectTreeHierarchyMapper.class);
            TenantContextHolder.setTenantId(7L);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                var projects = session.getMapper(ProjectChildClosureMapper.class);
                var paths = session.getMapper(ProjectTreeHierarchyMapper.class);
                assertEquals(1, projects.selectClosureFacts(new ProjectChildClosureFactsQuery(7L, Set.of(10L,11L,12L))).size());
                assertTrue(projects.selectClosureFacts(new ProjectChildClosureFactsQuery(7L, Set.of())).isEmpty());
                assertEquals(java.util.List.of(9L,10L), paths.selectWaitSubtree(new ProjectWaitTreeQuery(7L,1L,4L,9L))
                        .stream().map(path -> path.getDescendantProjectId()).toList());
                assertEquals(java.util.List.of(9L), paths.selectWaitAncestors(new ProjectWaitTreeQuery(7L,1L,4L,10L))
                        .stream().map(path -> path.getAncestorProjectId()).toList());
                assertTrue(paths.selectWaitAncestors(new ProjectWaitTreeQuery(7L,1L,4L,9L)).isEmpty());
                TenantContextHolder.setTenantId(8L);
                assertTrue(projects.selectClosureFacts(new ProjectChildClosureFactsQuery(7L, Set.of(10L))).isEmpty());
            }
        } finally { TenantContextHolder.clear(); database.shutdown(); }
    }
}
