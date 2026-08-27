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
                "insert", "selectByObjectId", "selectForUpdate", "selectList", "selectListForUpdate", "selectPage",
                "updateDraftIfMatch", "updateReviewIfMatch"));
        assertMapperContract(DynamicFormInstanceMapper.class, Set.of(
                "insert", "selectForUpdate", "selectByItemForUpdate", "selectList", "selectListForUpdate", "selectListByItemIds",
                "updateDraftIfMatch", "freezeIfMatch"));
        assertMapperContract(PreparationSourceReferenceMapper.class, Set.of(
                "insert", "selectList", "selectListForUpdate", "updateSyncIfMatch"));
        assertMapperContract(PreparationItemWaiverMapper.class, Set.of(
                "insert", "selectForUpdate", "selectList", "selectListForUpdate", "selectPage", "updateStatusIfMatch"));
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
