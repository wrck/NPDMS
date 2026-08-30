package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalDueExemptionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPredecessorQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactAllocationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactVersionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryUpdate;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArrivalAcceptanceMapperContractTest {

    private static String mapperXml;
    private static String mapperJava;
    private static Path deliveryEvidenceMapperPath;
    private static Path arrivalAcceptanceMapperPath;
    private static Path arrivalDifferenceMapperPath;

    @BeforeAll
    static void loadMapperSources() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = Files.exists(moduleDirectory.resolve("pms-module-engineering"))
                ? moduleDirectory.resolve("pms-module-engineering") : moduleDirectory;
        mapperXml = readTree(root.resolve("src/main/resources/mapper/arrivalacceptance"));
        deliveryEvidenceMapperPath = root.resolve(
                "src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml");
        arrivalAcceptanceMapperPath = root.resolve(
                "src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml");
        arrivalDifferenceMapperPath = root.resolve(
                "src/main/resources/mapper/arrivalacceptance/ArrivalDifferenceMapper.xml");
        mapperJava = readTree(root.resolve(
                "src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance"));
    }

    @Test
    void mapsExactlyTheFiveOwnerTables() {
        assertEquals("imp_arrival_acceptance", tableName(ArrivalAcceptanceDO.class));
        assertEquals("imp_arrival_line", tableName(ArrivalLineDO.class));
        assertEquals("imp_arrival_difference", tableName(ArrivalDifferenceDO.class));
        assertEquals("imp_delivery_evidence", tableName(DeliveryEvidenceDO.class));
        assertEquals("imp_delivery_evidence_revision", tableName(DeliveryEvidenceRevisionDO.class));
    }

    @Test
    void emptyVisibleProjectScopeReturnsEmptyWithoutExecutingSql() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class, CALLS_REAL_METHODS);
        ArrivalPageQuery query = new ArrivalPageQuery(1L, Set.of(), null, 0, 20);

        assertEquals(List.of(), mapper.selectPageRows(query));
        assertEquals(0L, mapper.selectPageCount(query));
        verify(mapper, never()).selectPageRowsInternal(query);
        verify(mapper, never()).selectPageCountInternal(query);
    }

    @Test
    void keepsDynamicAndLockSqlInXmlWithStablePositivePathOrder() {
        assertTrue(mapperXml.contains("FOR UPDATE"));
        assertTrue(mapperXml.contains("ORDER BY line_no, id"));
        assertTrue(mapperXml.contains("ORDER BY difference_no, revision_no, id"));
        assertTrue(mapperXml.contains("ORDER BY arrived_at DESC, id DESC"));
        assertTrue(mapperXml.contains(
                "project_version, project_participant_fact_version, project_scope_version"));
        assertTrue(mapperXml.contains("batch_code, batch_root_marker, logistics_no"));
        assertTrue(mapperXml.contains("AND batch_root_marker = 1"));
        assertTrue(mapperXml.contains("id=\"selectSuccessorForUpdate\""));
        assertTrue(mapperXml.contains("predecessor_acceptance_id = #{query.predecessorAcceptanceId}"));
        assertTrue(mapperXml.contains("id=\"selectDueExemptions\""));
        assertTrue(mapperXml.contains("d.exemption_expires_at &lt;= #{query.processingTime}"));
        assertTrue(mapperXml.contains("successor.successor_reason = 'EXEMPTION_INVALIDATION'"));
        assertTrue(mapperXml.contains("ORDER BY d.exemption_expires_at, d.tenant_id"));
        assertTrue(mapperXml.contains("id=\"updateSubmittedIfMatch\""));
        assertTrue(mapperXml.contains("id=\"updateConfirmedIfMatch\""));
        assertTrue(mapperXml.contains("status IN ('PARTIALLY_ACCEPTED', 'ACCEPTED')"));
        assertTrue(mapperXml.contains("project_fact_version IS NULL"));
        assertTrue(mapperXml.contains("id=\"markPublishedPendingAccIfMatch\""));
        assertTrue(mapperXml.contains("acc_sync_status = 'PUBLISHED_PENDING_ACC'"));
        assertTrue(mapperXml.contains("AND acc_sync_status = 'NOT_PUBLISHED'"));
        assertTrue(mapperXml.contains("id=\"selectByIdentityForUpdate\""));
        assertTrue(mapperXml.contains("id=\"markAcceptedPendingArchiveIfMatch\""));
        assertTrue(mapperXml.contains("id=\"markArchivedIfMatch\""));
        assertTrue(mapperXml.contains("id=\"registerFirstCallbackWatermarkIfMatch\""));
        assertTrue(mapperXml.contains("id=\"selectNextDueForRetry\""));
        assertTrue(mapperXml.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(mapperXml.contains("id=\"enterRetryStateIfMatch\""));
        assertTrue(mapperXml.contains("id=\"advanceRetryIfMatch\""));
        assertTrue(mapperXml.contains("acc_retry_count = #{query.newRetryCount}"));
        assertTrue(mapperXml.contains("AND acc_retry_count = #{query.expectedRetryCount}"));
        assertFalse(mapperXml.contains("#{query.retryCount}"));
        assertTrue(mapperXml.contains("acc_correlation_id = #{query.correlationId}"));
        assertTrue(mapperXml.contains("acc_correlation_id IS NULL"));
        assertTrue(mapperXml.contains(
                "acc_sync_status IN ('PUBLISHED_PENDING_ACC', 'ARCHIVE_PENDING_RETRY')"));
        assertTrue(mapperXml.contains(
                "acc_sync_status IN ('ACCEPTED_PENDING_ARCHIVE', 'ARCHIVE_ACK_PENDING_RETRY')"));
        assertTrue(mapperXml.contains("current_revision_no = #{query.currentRevision}"));
        assertTrue(mapperXml.contains("acc_accepted_record_id IS NOT NULL"));
        assertTrue(mapperXml.contains("AND status = 'DRAFT'"));
        assertTrue(mapperXml.contains("AND version = #{query.expectedVersion}"));
        assertTrue(mapperXml.contains("<foreach collection=\"query.visibleProjectIds\""));
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperJava.contains("@Select"));
        assertFalse(mapperJava.contains(".last("));
        assertFalse(mapperJava.contains("Map<"));
    }

    @Test
    void retryUpdateParametersResolveThroughRealMyBatisDynamicSql() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Files.newInputStream(deliveryEvidenceMapperPath)) {
            new XMLMapperBuilder(input, configuration, deliveryEvidenceMapperPath.toString(),
                    configuration.getSqlFragments()).parse();
        }
        DeliveryEvidenceRetryUpdate query = new DeliveryEvidenceRetryUpdate(
                1L, 50L, 1, 4, "ARCHIVE_PENDING_RETRY", "PUBLISHED_PENDING_ACC",
                0, 1, LocalDateTime.of(2026, 8, 30, 10, 1), "evt-1",
                LocalDateTime.of(2026, 8, 30, 10, 0));
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(
                DeliveryEvidenceMapper.class.getName() + ".advanceRetryIfMatch")
                .getBoundSql(parameters);

        List<String> properties = boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).toList();
        assertTrue(properties.contains("query.newRetryCount"));
        assertTrue(properties.contains("query.expectedRetryCount"));
        properties.forEach(property -> configuration.newMetaObject(parameters).getValue(property));
    }

    @Test
    void successorAndExpiryQueriesResolveThroughRealMyBatisSql() throws IOException {
        Configuration configuration = new Configuration();
        parseMapper(configuration, arrivalAcceptanceMapperPath);
        parseMapper(configuration, arrivalDifferenceMapperPath);

        Map<String, Object> predecessor = Map.of("query", new ArrivalPredecessorQuery(1L, 900L));
        BoundSql successor = configuration.getMappedStatement(
                        ArrivalAcceptanceMapper.class.getName() + ".selectSuccessorForUpdate")
                .getBoundSql(predecessor);
        successor.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(predecessor).getValue(mapping.getProperty()));
        assertTrue(successor.getSql().contains("predecessor_acceptance_id = ?"));
        assertTrue(successor.getSql().contains("FOR UPDATE"));

        Map<String, Object> dueQuery = Map.of("query", new ArrivalDueExemptionQuery(
                LocalDateTime.of(2026, 8, 30, 12, 0), 20));
        BoundSql due = configuration.getMappedStatement(
                        ArrivalDifferenceMapper.class.getName() + ".selectDueExemptions")
                .getBoundSql(dueQuery);
        due.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(dueQuery).getValue(mapping.getProperty()));
        assertTrue(due.getSql().contains("d.exemption_expires_at <= ?"));
        assertTrue(due.getSql().contains("LIMIT ?"));
        assertFalse(due.getSql().contains("FOR UPDATE"));
    }

    @Test
    void projectFactQueriesUseOnlyConfirmedAcceptedAndEffectiveExplicitExemptions() {
        assertTrue(mapperXml.contains("id=\"selectConfirmedByProject\""));
        assertTrue(mapperXml.contains("id=\"selectConfirmedAcceptedByProject\""));
        assertTrue(mapperXml.contains("id=\"selectEffectiveExemptionsByProject\""));
        assertTrue(mapperXml.contains("a.status = 'CONFIRMED'"));
        assertTrue(mapperXml.contains("l.status = 'ACCEPTED'"));
        assertTrue(mapperXml.contains("d.resolution_status = 'EXEMPTED'"));
        assertTrue(mapperXml.contains("d.exemption_expires_at &gt; #{query.checkedAt}"));
    }

    @Test
    void projectFactVersionAllocationSetUnionsRootAndDifferenceNonNullValues() {
        assertTrue(mapperXml.contains("id=\"selectLatestProjectFactAllocations\""));
        assertTrue(mapperXml.contains("'ACCEPTANCE' AS source_type"));
        assertTrue(mapperXml.contains("'DIFFERENCE' AS source_type"));
        assertTrue(mapperXml.contains("ORDER BY allocated.project_fact_version DESC"));
        assertTrue(mapperXml.contains("allocated.source_type"));
        assertTrue(mapperXml.contains("allocated.source_id"));
        assertTrue(mapperXml.contains("LIMIT 2"));
        assertTrue(mapperXml.contains("id=\"selectLatestAllocatedRootsForUpdate\""));
        assertTrue(mapperXml.contains("id=\"selectLatestAllocatedDifferencesForUpdate\""));
        assertTrue(mapperXml.contains("ORDER BY a.project_fact_version DESC, a.id"));
        assertTrue(mapperXml.contains("ORDER BY d.project_fact_version DESC, d.id"));
        assertTrue(mapperXml.contains("id=\"selectMaxAllocatedProjectFactVersion\""));
        assertTrue(mapperXml.contains("SELECT MAX(allocated.project_fact_version)"));
        assertTrue(mapperXml.contains("FROM imp_arrival_acceptance a"));
        assertTrue(mapperXml.contains("UNION ALL"));
        assertTrue(mapperXml.contains("FROM imp_arrival_difference d"));
        assertTrue(mapperXml.contains("a.project_fact_version IS NOT NULL"));
        assertTrue(mapperXml.contains("d.project_fact_version IS NOT NULL"));
        assertTrue(mapperXml.contains("a.project_id = #{query.projectId}"));
        assertTrue(mapperXml.contains("d.tenant_id = #{query.tenantId}"));
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalProjectFactVersionQuery(1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalProjectFactAllocationQuery(null, 1L));
    }

    private static String tableName(Class<?> type) {
        return type.getAnnotation(TableName.class).value();
    }

    private static void parseMapper(Configuration configuration, Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(),
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readTree(Path directory) throws IOException {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                source.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return source.toString();
    }
}
