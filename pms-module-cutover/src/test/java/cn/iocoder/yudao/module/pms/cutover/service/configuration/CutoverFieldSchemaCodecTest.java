package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CutoverFieldSchemaCodecTest {
    @Test
    void all133PublishedSeedSchemasPreserveConfigurationMeaning() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("sql/migrations"))) root = root.getParent();
        assertNotNull(root);
        String sql = Files.readString(root.resolve("sql/migrations/V452__cutover_full_configuration_seed.sql"));
        String marker = "SET @cut_seed = '";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0);
        start += marker.length();
        int end = sql.indexOf("';", start);
        var seed = JsonUtils.parseTree(sql.substring(start, end).replace("''", "'").replace("\\\\", "\\"));
        assertEquals(133, seed.path("items").size());
        int ordinal = 0;
        for (var item : seed.path("items")) {
            Map<String, Object> schema = JsonUtils.parseObject(item.path("interfaceSchema").toString(),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
            var encoded = CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder()
                    .tenantId(7L).itemDefinitionRevisionId(1000L + ordinal++)
                    .businessCategoryCode(item.path("businessCategoryCode").asText())
                    .itemTypeCode(item.path("itemType").asText()).schema(schema).build());
            assertEquals(normalized(schema), normalized(CutoverFieldSchemaCodec.decode(encoded)), item.path("stableItemKey").asText());
        }
    }

    @Test
    void nestedTableColumnsRemainDistinctFromFieldsAndCustomExtensions() {
        var schema = Map.<String, Object>of("type", "OBJECT", "fields", List.of(
                Map.of("code", "rows", "type", "TABLE", "columns", List.of(
                        Map.of("code", "remark", "type", "TEXT"))),
                Map.of("code", "section", "type", "OBJECT", "fields", List.of(
                        Map.of("code", "remark", "type", "TEXT")))));
        var definition = CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder()
                .tenantId(7L).customChecklistItemId(20L).businessCategoryCode("CUSTOM")
                .itemTypeCode("BUSINESS_SURVEY").schema(schema).build());
        assertEquals(normalized(schema), normalized(CutoverFieldSchemaCodec.decode(definition)));
        assertEquals(1, definition.fields().stream().filter(field -> "COLUMN".equals(field.getNodeKindCode())).count());
        assertEquals(3, definition.fields().stream().filter(field -> "FIELD".equals(field.getNodeKindCode())).count());
        assertTrue(definition.fields().stream().allMatch(field -> field.getBuiltinFieldCode() == null));
        assertTrue(definition.fields().stream().allMatch(field -> field.getExtensionFieldKey() != null));
    }

    @Test
    void booleanVisibilityAndStringVisibilityAreNotInterchangeable() {
        var schema = Map.<String, Object>of("fields", List.of(
                Map.of("code", "booleanCondition", "visibleWhen", Map.of("field", "toggle", "equals", true)),
                Map.of("code", "stringCondition", "visibleWhen", Map.of("field", "toggle", "equals", "true"))));
        var decoded = CutoverFieldSchemaCodec.decode(encode(schema));
        var fields = (List<?>) decoded.get("fields");
        assertEquals(true, ((Map<?, ?>) ((Map<?, ?>) fields.get(0)).get("visibleWhen")).get("equals"));
        assertEquals("true", ((Map<?, ?>) ((Map<?, ?>) fields.get(1)).get("visibleWhen")).get("equals"));
    }

    @Test
    void backgroundBusinessFieldsCannotBecomeExtensions() {
        var definition = CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder()
                .tenantId(7L).itemDefinitionRevisionId(10L).businessCategoryCode("CUTOVER_BACKGROUND")
                .itemTypeCode("BUSINESS_SURVEY").schema(Map.of("fields", List.of(
                        Map.of("code", "backgroundDescription", "type", "TEXT"),
                        Map.of("code", "issueTicketNo", "type", "TEXT")))).build());
        for (var field : definition.fields()) {
            assertNotNull(field.getBuiltinFieldCode());
            assertNull(field.getExtensionFieldKey());
        }
    }

    @Test
    void rejectsMissingTenantAndAmbiguousOrMissingOwner() {
        assertThrows(IllegalArgumentException.class, () -> CutoverFieldSchemaCodec.encode(
                CutoverFieldSchemaCodec.Write.builder().itemDefinitionRevisionId(1L).schema(Map.of()).build()));
        assertThrows(IllegalArgumentException.class, () -> CutoverFieldSchemaCodec.encode(
                CutoverFieldSchemaCodec.Write.builder().tenantId(7L).schema(Map.of()).build()));
        assertThrows(IllegalArgumentException.class, () -> CutoverFieldSchemaCodec.encode(
                CutoverFieldSchemaCodec.Write.builder().tenantId(7L).itemDefinitionRevisionId(1L)
                        .customChecklistItemId(2L).schema(Map.of()).build()));
    }

    @Test
    void rejectsUnknownMetadataAndNonScalarOptionsOrVisibility() {
        assertThrows(IllegalArgumentException.class, () -> encode(Map.of("businessOwner", "CUSTOM")));
        assertThrows(IllegalArgumentException.class, () -> encode(Map.of("source", Map.of("unknown", 1))));
        assertThrows(IllegalArgumentException.class, () -> encode(Map.of("options", List.of(Map.of("label", "bad", "value", List.of(1))))));
        assertThrows(IllegalArgumentException.class, () -> encode(Map.of("options", List.of(Map.of("value", 1, "extra", true)))));
        assertThrows(IllegalArgumentException.class, () -> encode(Map.of("visibleWhen", Map.of("field", "toggle", "equals", Map.of("value", true)))));
        assertThrows(IllegalArgumentException.class, () -> CutoverMetadataScalar.from(List.of(1)));
    }

    @Test
    void scalarRoundTripPreservesNullNumberBooleanTextAndDatetime() {
        assertNull(CutoverMetadataScalar.from(null).value());
        assertEquals(new BigDecimal("12.50"), CutoverMetadataScalar.from(new BigDecimal("12.50")).value());
        assertEquals(true, CutoverMetadataScalar.from(true).value());
        assertEquals("true", CutoverMetadataScalar.from("true").value());
        var date = LocalDateTime.of(2026, 10, 13, 12, 0);
        assertEquals(date, CutoverMetadataScalar.from(date).value());
        assertThrows(IllegalArgumentException.class, () -> new CutoverMetadataScalar("OBJECT", null, null, null, null).value());
    }

    private static CutoverFieldSchemaCodec.Definition encode(Map<String, Object> schema) {
        return CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder().tenantId(7L)
                .itemDefinitionRevisionId(10L).businessCategoryCode("CUSTOM").itemTypeCode("RISK").schema(schema).build());
    }

    private static Object normalized(Object value) {
        if (value instanceof Map<?, ?> source) {
            var target = new LinkedHashMap<Object, Object>();
            source.forEach((key, item) -> {
                // Optional required=false is semantically identical to an absent required flag.
                if (!("required".equals(key) && Boolean.FALSE.equals(item))) target.put(key, normalized(item));
            });
            return target;
        }
        if (value instanceof List<?> items) return items.stream().map(CutoverFieldSchemaCodecTest::normalized).toList();
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros();
        return value;
    }
}
