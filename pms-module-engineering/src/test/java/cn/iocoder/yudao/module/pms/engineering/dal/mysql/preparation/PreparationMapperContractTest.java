package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparationMapperContractTest {

    @Test
    void exposesOnlyScenarioSpecificPersistenceMethods() {
        assertMapperContract(PreparationMapper.class, Set.of(
                "insert", "selectCurrent", "selectCurrentForUpdate", "selectById", "selectForUpdate",
                "selectPage", "selectBusinessVersionForUpdate", "updateLifecycleIfMatch", "clearCurrentMarkerIfMatch",
                "invalidateReadinessIfMatch", "updateReadinessIfMatch"));
        assertMapperContract(PreparationItemMapper.class, Set.of(
                "insert", "selectByObjectId", "selectCurrentByEvidenceObjectId", "selectForUpdate",
                "selectList", "selectListForUpdate", "selectPage",
                "updateDraftIfMatch", "updateReviewIfMatch"));
        assertMapperContract(DynamicFormInstanceMapper.class, Set.of(
                "insert", "selectForUpdate", "selectByItemForUpdate", "selectList", "selectListForUpdate", "selectListByItemIds",
                "updateDraftIfMatch", "touchDraftIfMatch", "freezeIfMatch"));
        assertMapperContract(PreparationSurveyMapper.class, Set.of("selectByPreparation", "insert", "update"));
        assertMapperContract(PreparationSurveyResultMapper.class, Set.of("selectByItem", "insert", "update"));
        assertMapperContract(PreparationSourceReferenceMapper.class, Set.of(
                "insert", "selectList", "selectListForUpdate", "updateSyncIfMatch"));
        assertMapperContract(PreparationItemWaiverMapper.class, Set.of(
                "insert", "selectForUpdate", "selectList", "selectListForUpdate", "selectBusinessList",
                "selectBusinessListForUpdate", "selectPage", "updateStatusIfMatch"));
        assertMapperContract(PreparationReadinessSnapshotMapper.class, Set.of("insert", "selectById", "selectPage"));
    }

    @Test
    void keepsTenantLocksStableCursorsAndImmutableSnapshotBoundary() throws IOException {
        String preparation = mapperXml("PreparationMapper.xml");
        String item = mapperXml("PreparationItemMapper.xml");
        String form = mapperXml("DynamicFormInstanceMapper.xml");
        String source = mapperXml("PreparationSourceReferenceMapper.xml");
        String waiver = mapperXml("PreparationItemWaiverMapper.xml");
        String snapshot = mapperXml("PreparationReadinessSnapshotMapper.xml");

        for (String xml : List.of(preparation, item, form, source, waiver, snapshot)) {
            assertTrue(xml.contains("tenant_id = #{"));
            assertFalse(xml.contains("${"));
        }
        assertTrue(preparation.contains("FOR UPDATE"));
        assertTrue(item.contains("ORDER BY sort_order ASC, item_code ASC, id ASC"));
        assertTrue(item.contains("<otherwise>AND 1 = 0</otherwise>"));
        assertTrue(form.contains("frozen_at IS NULL"));
        assertTrue(source.contains("FOR UPDATE"));
        assertTrue(waiver.contains("ORDER BY waiver_no ASC, id ASC"));
        assertTrue(snapshot.contains("ORDER BY snapshot_no ASC, id ASC"));
        assertFalse(snapshot.contains("<update"));
        assertFalse(snapshot.contains("<delete"));
    }

    @Test
    void surveyXmlBindsExplicitBusinessColumnsAndFormTouchNeverWritesJson() throws IOException {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        for (String name : List.of("PreparationSurveyMapper.xml", "PreparationSurveyResultMapper.xml", "DynamicFormInstanceMapper.xml")) {
            String resource = "mapper/preparation/" + name;
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream);
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream, configuration, resource,
                        configuration.getSqlFragments()).parse();
            }
        }
        var query = new cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery(1L, 2L, 3L);
        String sql = configuration.getMappedStatement(PreparationSurveyResultMapper.class.getName() + ".selectByItem")
                .getBoundSql(java.util.Map.of("query", query)).getSql();
        assertTrue(sql.contains("tenant_id = ?")); assertTrue(sql.contains("preparation_id = ?"));
        assertTrue(sql.contains("item_id = ?")); assertTrue(sql.contains("power_supply"));
        var touch = new cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.DynamicFormDraftUpdate(
                1L, 2L, 3L, 4L, 5, null, "7");
        String touchSql = configuration.getMappedStatement(DynamicFormInstanceMapper.class.getName() + ".touchDraftIfMatch")
                .getBoundSql(java.util.Map.of("update", touch)).getSql();
        assertFalse(touchSql.contains("value_snapshot")); assertTrue(touchSql.contains("version = ?"));
        assertTrue(touchSql.contains("frozen_at IS NULL"));
        String resultXml = mapperXml("PreparationSurveyResultMapper.xml");
        assertFalse(resultXml.contains("value_snapshot")); assertFalse(resultXml.contains("JSON"));
        assertFalse(resultXml.contains("grounding")); assertFalse(resultXml.contains("construction_resource"));
        String surveyXml = mapperXml("PreparationSurveyMapper.xml");
        assertTrue(surveyXml.contains("grounding")); assertTrue(surveyXml.contains("construction_resource"));
    }

    private static void assertMapperContract(Class<?> mapperType, Set<String> expectedMethods) {
        assertEquals(0, mapperType.getInterfaces().length,
                () -> mapperType.getSimpleName() + " 不得继承通用CRUD接口");
        Set<String> actualMethods = Arrays.stream(mapperType.getMethods())
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(expectedMethods, actualMethods);
    }

    private static String mapperXml(String fileName) throws IOException {
        try (InputStream input = PreparationMapperContractTest.class.getClassLoader()
                .getResourceAsStream("mapper/preparation/" + fileName)) {
            assertNotNull(input, fileName);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
