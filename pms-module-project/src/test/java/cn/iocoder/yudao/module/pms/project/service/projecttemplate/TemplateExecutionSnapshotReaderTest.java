package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class TemplateExecutionSnapshotReaderTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "null", "[]", "2", "{}", "{\"compilerVersion\":\"old\"}"})
    void rejectsMissingDocumentOrSchema(String json) {
        assertThrows(RuntimeException.class, () -> TemplateExecutionSnapshotReader.read(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "\"2\"", "2.0", "2.5", "2e0", "[]", "{}",
            "0", "1", "3", "-1", "4294967298", "999999999999999999999999999999"})
    void doesNotCoerceOrDefaultStoredVersion(String token) {
        String json = "{\"executionSchemaVersion\":" + token + "}";
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionSnapshotReader.read(json));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 1, 4, Integer.MAX_VALUE})
    void rejectsUnsupportedTypedVersion(Integer version) {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateExecutionSnapshotReader.requireSupportedVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2300", "0.00000000000000000001", "12345678901234567890.1234567890"})
    void bindsOriginalNumericTextWithoutChangingHistoricalHash(String decimal) {
        String json = "{\"executionSchemaVersion\":2,\"compilerVersion\":\"historical\","
                + "\"tasks\":[{\"nodeKey\":\"task-a\",\"estimatedHours\":" + decimal + "}]}";
        TemplateExecutionSnapshot legacyRead = JsonUtils.parseObject(json, TemplateExecutionSnapshot.class);

        TemplateExecutionSnapshot result = TemplateExecutionSnapshotReader.read(json);

        assertEquals(legacyRead.getTasks().getFirst().getEstimatedHours(), result.getTasks().getFirst().getEstimatedHours());
        assertEquals(TemplateExecutionSnapshotHasher.hash(legacyRead), TemplateExecutionSnapshotHasher.hash(result));
    }

    @Test
    void readsHistoricalCompilerWithoutChangingSnapshotOrLegacyHash() {
        TemplateExecutionSnapshot original = new TemplateExecutionSnapshot();
        original.setCompilerVersion("historical-compiler");
        original.setClosureRuleKey("closure");
        var stage = new TemplateExecutionSnapshot.StageContract();
        stage.setNodeKey("stage-a");
        stage.setAdmissionRuleKey("admission");
        stage.setExitRuleKey("exit");
        original.getStages().add(stage);
        String serialized = JsonUtils.toJsonString(original);
        JsonNode document = JsonUtils.parseObject(serialized, JsonNode.class);
        JsonNode before = document.deepCopy();

        TemplateExecutionSnapshot result = TemplateExecutionSnapshotReader.read(document);

        assertEquals(before, document);
        assertEquals(before, JsonUtils.parseObject(JsonUtils.toJsonString(result), JsonNode.class));
        assertEquals(TemplateExecutionSnapshotHasher.hash(original), TemplateExecutionSnapshotHasher.hash(result));
        assertEquals("historical-compiler", result.getCompilerVersion());
        assertDoesNotThrow(() -> TemplateExecutionSnapshotReader.requireSupportedVersion(2));
        assertNotSame(original, result);
    }
}
