package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.LegacyCutoverSourceRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.LegacyCutoverTargetIdentityQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCutoverMapperContractTest {

    @Test
    void legacyMigrationQueriesResolveThroughMyBatisBindings() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, Path.of("src/main/resources/mapper/task/CutTaskMapper.xml"));
        parse(configuration, Path.of("src/main/resources/mapper/taskv2/CutoverTaskMapper.xml"));

        assertBindings(configuration, CutTaskMapper.class.getName() + ".selectLegacySourceForUpdate",
                new LegacyCutoverSourceRowQuery(1L, 91L));
        assertBindings(configuration, CutoverTaskMapper.class.getName() + ".countLegacyIdentityConflicts",
                new LegacyCutoverTargetIdentityQuery(1L, 100L, "CUT-91", 91L));
    }

    private static void parse(Configuration configuration, Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
    }

    private static void assertBindings(Configuration configuration, String statement, Object query) {
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(statement).getBoundSql(parameters);
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
        boundSql.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
    }
}
