package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskArchiveUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceReleaseUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverClosureMapperContractTest {

    @Test
    void parsesEveryClosureLockCasCountArchiveAndReleaseBinding() throws IOException {
        Configuration configuration = configuration();
        assertBindings(configuration, CutoverClosureMapper.class, "selectByTask", new CutoverClosureRowQuery(1L, 2L));
        assertBindings(configuration, CutoverClosureMapper.class, "selectByTaskForUpdate", new CutoverClosureRowQuery(1L, 2L));
        assertBindings(configuration, CutoverClosureMapper.class, "submitIfMatch",
                new CutoverClosureSubmitUpdate(1L, 3L, 4, "SUCCESS", "CUTOVER_CLOSURE:3:5", 6L,
                        LocalDateTime.parse("2026-09-02T10:00:00")));
        assertBindings(configuration, CutoverClosureMapper.class, "advanceDraftVersionIfMatch",
                new CutoverClosureVersionUpdate(1L, 3L, 4, "6",
                        LocalDateTime.parse("2026-09-02T10:00:00")));
        var children = new CutoverClosureChildrenQuery(1L, 3L);
        assertBindings(configuration, CutoverClosureAttachmentMapper.class, "selectListByClosure", children);
        assertBindings(configuration, CutoverClosureAttachmentMapper.class, "selectListByClosureForUpdate", children);
        assertBindings(configuration, CutoverClosureAttachmentMapper.class, "deleteDraftRows", children);
        assertBindings(configuration, CutoverCollectionEvidenceMapper.class, "selectListByClosure", children);
        assertBindings(configuration, CutoverCollectionEvidenceMapper.class, "selectListByClosureForUpdate", children);
        assertBindings(configuration, CutoverCollectionEvidenceMapper.class, "selectUnresolvedDispatchCount", children);
        assertBindings(configuration, CutoverTaskMapper.class, "archiveFromP6IfMatch", new CutoverTaskArchiveUpdate(1L, 2L, 7));
        assertBindings(configuration, CutoverTaskDeviceScopeMapper.class, "selectActiveByTaskForUpdate",
                new CutoverTaskDeviceListQuery(1L, 2L));
        assertBindings(configuration, CutoverTaskDeviceScopeMapper.class, "releaseActiveByTask",
                new CutoverTaskDeviceReleaseUpdate(1L, 2L));
    }

    @Test
    void keepsTenantDeleteLocksAndStableOrderingInXml() throws IOException {
        assertThat(read("closure/CutoverClosureMapper.xml"))
                .contains("tenant_id = #{query.tenantId}", "deleted = b'0'", "FOR UPDATE", "version = #{query.expectedVersion}")
                .doesNotContain("${");
        assertThat(read("closure/CutoverClosureAttachmentMapper.xml"))
                .contains("ORDER BY purpose_code, reference_key, id", "FOR UPDATE",
                        "purpose_code &lt;&gt; 'MANUAL_COLLECTION_RESULT'")
                .doesNotContain("${");
        assertThat(read("closure/CutoverCollectionEvidenceMapper.xml"))
                .contains("ORDER BY occurred_at, id", "FOR UPDATE", "NOT EXISTS")
                .doesNotContain("${");
        assertThat(read("taskv2/CutoverTaskDeviceScopeMapper.xml"))
                .contains("ORDER BY device_id ASC", "active_marker = NULL")
                .doesNotContain("${");
    }

    private static Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, "closure/CutoverClosureMapper.xml");
        parse(configuration, "closure/CutoverClosureAttachmentMapper.xml");
        parse(configuration, "closure/CutoverCollectionEvidenceMapper.xml");
        parse(configuration, "taskv2/CutoverTaskMapper.xml");
        parse(configuration, "taskv2/CutoverTaskDeviceScopeMapper.xml");
        return configuration;
    }

    private static void parse(Configuration configuration, String file) throws IOException {
        Path path = Path.of("src/main/resources/mapper", file);
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of("src/main/resources/mapper", file));
    }

    private static void assertBindings(Configuration configuration, Class<?> mapper, String method, Object query) {
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
        boundSql.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
    }
}
