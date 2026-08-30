package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactVersionQuery;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @BeforeAll
    static void loadMapperSources() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = Files.exists(moduleDirectory.resolve("pms-module-engineering"))
                ? moduleDirectory.resolve("pms-module-engineering") : moduleDirectory;
        mapperXml = readTree(root.resolve("src/main/resources/mapper/arrivalacceptance"));
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
        assertTrue(mapperXml.contains("device_id, order_line_id, arrival_acceptance_id, line_no, id"));
        assertTrue(mapperXml.contains("ORDER BY arrived_at DESC, id DESC"));
        assertTrue(mapperXml.contains(
                "project_version, project_participant_fact_version, project_scope_version"));
        assertTrue(mapperXml.contains("id=\"updateSubmittedIfMatch\""));
        assertTrue(mapperXml.contains("AND status = 'DRAFT'"));
        assertTrue(mapperXml.contains("AND version = #{query.expectedVersion}"));
        assertTrue(mapperXml.contains("<foreach collection=\"query.visibleProjectIds\""));
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperJava.contains("@Select"));
        assertFalse(mapperJava.contains(".last("));
        assertFalse(mapperJava.contains("Map<"));
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
    }

    private static String tableName(Class<?> type) {
        return type.getAnnotation(TableName.class).value();
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
