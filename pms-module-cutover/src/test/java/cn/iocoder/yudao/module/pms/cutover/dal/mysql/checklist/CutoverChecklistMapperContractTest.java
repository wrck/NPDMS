package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
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

class CutoverChecklistMapperContractTest {

    @Test
    void checklistLocksResolveThroughScenarioQueries() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, "CutoverChecklistMapper.xml");
        parse(configuration, "CutoverChecklistItemMapper.xml");
        parse(configuration, "CutoverChecklistItemResultMapper.xml");

        assertBindings(configuration, CutoverChecklistMapper.class.getName() + ".selectCurrentForUpdate",
                new CutoverChecklistRowQuery(1L, 10L, 20L));
        assertBindings(configuration, CutoverChecklistItemMapper.class.getName() + ".selectListForUpdate",
                new CutoverChecklistItemsQuery(1L, 20L));
        assertBindings(configuration, CutoverChecklistItemResultMapper.class.getName() + ".selectCurrentForUpdate",
                new CutoverChecklistCurrentResultQuery(1L, 30L));
    }

    private static void parse(Configuration configuration, String file) throws IOException {
        Path path = Path.of("src/main/resources/mapper/checklist", file);
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
