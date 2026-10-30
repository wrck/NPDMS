package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.SolutionCompletionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.SolutionCompletionQuery;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the production wrapper in H2; does not modify the running application's database. */
class SolutionCompletionCandidateSqlTest {
    @Test void onlyApprovedSolutionsEnterAutomaticAssociationWithScopeAndKeysetIntact() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:solution_candidates_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sol_eng_solution(id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, "
                    + "solution_type VARCHAR(32), status INT, deleted INT)");
            sql.execute("INSERT INTO sol_eng_solution VALUES "
                    + "(10,1,9,'IMPLEMENTATION',4,0),(11,1,9,'IMPLEMENTATION',3,0),"
                    + "(16,1,9,'IMPLEMENTATION',3,0),(22,1,9,'IMPLEMENTATION',0,0),"
                    + "(23,1,9,'IMPLEMENTATION',1,0),(24,1,9,'IMPLEMENTATION',2,0),"
                    + "(25,1,9,'IMPLEMENTATION',5,0),(26,1,9,'IMPLEMENTATION',6,0),"
                    + "(27,2,9,'IMPLEMENTATION',3,0),(28,1,8,'IMPLEMENTATION',3,0),"
                    + "(29,1,9,'IMPLEMENTATION',3,1),(30,1,9,'OTHER',3,0)");
            var config = new MybatisConfiguration();
            config.setEnvironment(new Environment("solution-candidates", new JdbcTransactionFactory(), source));
            config.setMapUnderscoreToCamelCase(true);
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
            config.addInterceptor(plugins);
            config.addMapper(SolutionCompletionMapper.class);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                var mapper = session.getMapper(SolutionCompletionMapper.class);
                assertEquals(List.of(11L, 16L), mapper.page(new SolutionCompletionQuery(1L,9L,null,null,100,false))
                        .stream().map(row -> row.getId()).toList());
                assertEquals(List.of(11L), mapper.page(new SolutionCompletionQuery(1L,9L,null,null,1,false))
                        .stream().map(row -> row.getId()).toList());
                assertEquals(List.of(16L), mapper.page(new SolutionCompletionQuery(1L,9L,null,11L,1,false))
                        .stream().map(row -> row.getId()).toList());
            }
            try (var rows = sql.executeQuery("SELECT status FROM sol_eng_solution WHERE id=10")) {
                assertTrue(rows.next()); assertEquals(4, rows.getInt(1));
            }
            sql.execute("SHUTDOWN");
        }
    }
}
