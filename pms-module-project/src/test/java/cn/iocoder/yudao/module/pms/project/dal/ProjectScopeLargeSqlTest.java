package cn.iocoder.yudao.module.pms.project.dal;

import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectScopeRootPathsQuery;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectScopeLargeSqlTest {
    @Test
    void thousandsOfRootVersionPairsSurviveProductionParserCache() throws Exception {
        new YudaoMybatisAutoConfiguration();
        var configuration = new Configuration();
        var resource = "mapper/projecttree/ProjectTreePathMapper.xml";
        try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var query = new ProjectScopeRootPathsQuery(1L, LongStream.rangeClosed(1, 5000)
                .mapToObj(id -> new ProjectScopeRootPathsQuery.TreeVersion(id, id % 7 + 1)).toList());
        var statement = configuration.getMappedStatement(ProjectTreePathMapper.class.getName() + ".selectScopeRootPaths");
        var sql = statement.getBoundSql(Map.of("query", query)).getSql();
        try (var executor = Executors.newSingleThreadExecutor(task ->
                new Thread(null, task, "project-scope-parser", 512 * 1024))) {
            executor.submit(() -> {
                var parsed = JsqlParserGlobal.parse(sql);
                var cached = JsqlParserGlobal.parse(sql);
                assertNotSame(parsed, cached, "Each tenant interception must receive an independent statement");
                return null;
            }).get(60, TimeUnit.SECONDS);
        }
    }
}
