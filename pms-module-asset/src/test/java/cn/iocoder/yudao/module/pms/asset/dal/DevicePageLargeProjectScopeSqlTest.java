package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class DevicePageLargeProjectScopeSqlTest {
    @Test
    void thousandsOfProjectTreesSurviveProductionParserCacheForBothProjectPanels() throws Exception {
        new YudaoMybatisAutoConfiguration();
        var configuration = new Configuration();
        for (var resource : List.of("mapper/authorization/ProjectViewGrantReadSql.xml",
                "mapper/projectscope/ProjectViewReadScopeSql.xml", "mapper/contract/ContractDeviceReadScopeSql.xml",
                "mapper/device/DeviceCurrentModelMapper.xml", "mapper/device/DeviceQueryMapper.xml", "mapper/device/DeviceJoinedPageMapper.xml")) {
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml, resource);
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        var scope = new ProjectViewReadScopeApi.ReadScope(1L, 7L, ProjectViewReadScopeApi.Mode.SCOPED,
                LocalDateTime.of(2026, 10, 25, 6, 0), LongStream.rangeClosed(1, 5000)
                .mapToObj(id -> new ProjectViewReadScopeApi.Tree(id, id % 7 + 1)).toList());
        var query = VisibleDevicePageQuery.builder().tenantId(1L).projectId(44637L).pageNo(1).pageSize(100)
                .organizationGrants(List.of()).readScope(new DevicePageReadScope(scope, null)).build();
        var statement = configuration.getMappedStatement(DeviceMapper.class.getName() + ".selectJoinedDevicePageRows");
        var batchStatement = configuration.getMappedStatement(DeviceMapper.class.getName() + ".selectJoinedDeviceIds");
        var batchQuery = new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDeviceIdsQuery(
                1L, java.util.Set.of(11L, 12L), null, List.of(), new DevicePageReadScope(scope, null), false);
        try (var executor = Executors.newSingleThreadExecutor(task ->
                new Thread(null, task, "device-project-scope", 512 * 1024))) {
            executor.submit(() -> {
                for (int size : List.of(100, 20)) {
                    for (var sql : List.of(statement.getBoundSql(Map.of("query", query.toBuilder().pageSize(size).build())).getSql(),
                            batchStatement.getBoundSql(Map.of("query", batchQuery)).getSql())) {
                        var parsed = JsqlParserGlobal.parse(sql);
                        var cached = JsqlParserGlobal.parse(sql);
                        assertNotSame(parsed, cached, "Each tenant interception must receive an independent statement");
                    }
                }
                return null;
            }).get(60, TimeUnit.SECONDS);
        }
    }
}
