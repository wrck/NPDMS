package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeSerialListQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceScopeFactMapperContractTest {

    private static final Path QUERY_XML = Path.of(
            "src/main/resources/mapper/device/DeviceQueryMapper.xml");
    private static final Path COMMAND_XML = Path.of(
            "src/main/resources/mapper/device/DeviceCommandMapper.xml");

    @Test
    void scopeQueriesBindScenarioObjectsAndUseStableLockOrder() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, QUERY_XML);
        parse(configuration, COMMAND_XML);

        Map<String, Object> serialParameters = Map.of("query",
                new DeviceScopeSerialListQuery(1L, List.of("SN-A", "SN-B")));
        BoundSql serials = configuration.getMappedStatement(
                        DeviceMapper.class.getName() + ".selectListByScopeSerials")
                .getBoundSql(serialParameters);
        serials.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(serialParameters).getValue(mapping.getProperty()));
        assertTrue(serials.getSql().contains("d.tenant_id = ?"));
        assertTrue(serials.getSql().contains("d.deleted = b'0'"));
        assertTrue(serials.getSql().contains("ORDER BY d.id"));

        Map<String, Object> lockParameters = Map.of("query",
                new DeviceScopeLockQuery(1L, List.of(22L, 11L)));
        BoundSql locked = configuration.getMappedStatement(
                        DeviceMapper.class.getName() + ".selectScopeDevicesForUpdate")
                .getBoundSql(lockParameters);
        locked.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(lockParameters).getValue(mapping.getProperty()));
        assertTrue(locked.getSql().contains("tenant_id = ?"));
        assertTrue(locked.getSql().contains("ORDER BY id"));
        assertTrue(locked.getSql().contains("FOR UPDATE"));
        assertFalse(locked.getSql().contains("${"));
    }

    private static void parse(Configuration configuration, Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(),
                    configuration.getSqlFragments()).parse();
        }
    }
}
