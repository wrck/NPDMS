package cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query.SpareApplicationQueries;
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

class CutoverSpareMapperContractTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-02T15:00:00");

    @Test
    void parsesEveryLockReadAndCasBinding() throws IOException {
        Configuration configuration = configuration();
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "selectByIdForUpdate",
                new SpareApplicationQueries.ById(1L, 10L));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "selectByPlatformRequestForUpdate",
                new SpareApplicationQueries.ByPlatformRequest(1L, "request-1"));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "selectByExternalApplicationForUpdate",
                new SpareApplicationQueries.ByExternalApplication(1L, "SPARE", "APP-1"));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "selectByTask",
                new SpareApplicationQueries.ByTask(1L, 20L));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "storeInitiateResultIfMatch",
                new SpareApplicationQueries.StoreInitiateResult(1L, 10L, 0, "REQUEST_PENDING",
                        "EXTERNAL_REFERENCED", "external-request-1", "APP-1", null, NOW, "9", NOW));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "storeFailureIfMatch",
                new SpareApplicationQueries.StoreFailure(1L, 10L, 1, "RETRY_PENDING", 1,
                        "TIMEOUT", "retry", NOW, "9", NOW));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "bindExternalReferenceIfMatch",
                new SpareApplicationQueries.BindExternalReference(1L, 10L, 1,
                        "external-request-1", "APP-1", "9", NOW));
        assertBindings(configuration, CutoverSpareApplicationReferenceMapper.class, "moveCurrentStatusIfMatch",
                new SpareApplicationQueries.MoveCurrentStatus(1L, 10L, 2, 30L, "9", NOW));
        assertBindings(configuration, CutoverSpareStatusRevisionMapper.class, "selectCurrentForUpdate",
                new SpareApplicationQueries.StatusByApplication(1L, 10L));
        assertBindings(configuration, CutoverSpareStatusRevisionMapper.class, "selectByEvent",
                new SpareApplicationQueries.StatusByEvent(1L, "event-1"));
        assertBindings(configuration, CutoverSpareStatusRevisionMapper.class, "selectByApplication",
                new SpareApplicationQueries.StatusByApplication(1L, 10L));
        assertBindings(configuration, CutoverSpareStatusRevisionMapper.class, "clearCurrentMarkerIfMatch",
                new SpareApplicationQueries.ClearCurrentStatus(1L, 10L, 30L));
        assertBindings(configuration, CutoverSpareManualEvidenceMapper.class, "selectByTask",
                new SpareApplicationQueries.EvidenceByTask(1L, 20L));
    }

    @Test
    void keepsTenantDeleteLocksCasAndStableOrderingInXml() throws IOException {
        String application = read("CutoverSpareApplicationReferenceMapper.xml");
        String status = read("CutoverSpareStatusRevisionMapper.xml");
        String evidence = read("CutoverSpareManualEvidenceMapper.xml");
        assertThat(application).contains("tenant_id = #{query.tenantId}", "deleted = b'0'", "FOR UPDATE",
                        "version = #{query.expectedVersion}", "ORDER BY id ASC")
                .doesNotContain("${");
        assertThat(status).contains("tenant_id = #{query.tenantId}", "current_marker = 1", "FOR UPDATE",
                        "ORDER BY status_version ASC, id ASC")
                .doesNotContain("${");
        assertThat(evidence).contains("tenant_id = #{query.tenantId}", "ORDER BY id ASC")
                .doesNotContain("${");
    }

    private static Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, "CutoverSpareApplicationReferenceMapper.xml");
        parse(configuration, "CutoverSpareStatusRevisionMapper.xml");
        parse(configuration, "CutoverSpareManualEvidenceMapper.xml");
        return configuration;
    }

    private static void parse(Configuration configuration, String file) throws IOException {
        Path path = Path.of("src/main/resources/mapper/spare", file);
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of("src/main/resources/mapper/spare", file));
    }

    private static void assertBindings(Configuration configuration, Class<?> mapper, String method, Object query) {
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
        boundSql.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
    }
}
