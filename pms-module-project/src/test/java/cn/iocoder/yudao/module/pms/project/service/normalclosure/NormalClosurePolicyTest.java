package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NormalClosurePolicyTest {
    static String policy() {
        return "{\"closureType\":\"NORMAL\",\"ruleRevision\":1,\"requireTerminalStage\":true,\"requireAllTasksDone\":true,"
                + "\"revalidateBusinessFacts\":true,\"processDefinitionKey\":\"PMS_MINIMAL_NORMAL_CLOSURE\",\"reviewerUserId\":23}";
    }
    @Test void absenceNeverTreatsTerminalAsImplicitClosurePolicy() {
        for (String value : new String[]{null, "", "null"})
            assertEquals("CLOSURE_POLICY_NOT_FROZEN", assertThrows(RuntimeException.class,
                    () -> NormalClosurePolicy.parseFrozen(value)).getMessage());
        assertThrows(RuntimeException.class, () -> NormalClosurePolicy.parseFrozen("{\"terminal\":true}"));
    }
    @Test void frozenPolicySurvivesFrameworkLongSerialization() {
        var expected = NormalClosurePolicy.parseFrozen(policy());
        assertEquals(expected, NormalClosurePolicy.parseFrozen(JsonUtils.toJsonString(expected)));
        String large = policy().replace("23}", "\"9223372036854775807\"}");
        assertEquals(Long.MAX_VALUE, NormalClosurePolicy.parseFrozen(large).reviewerUserId());
    }
    @Test void rejectsConditionBypassesUnknownFieldsAndCoercion() {
        for (String value : new String[]{policy().replace("true", "false"), policy().replace("true", "\"true\""),
                policy().replace("\"ruleRevision\":1", "\"ruleRevision\":2"), policy().replace("23}", "0}"),
                policy().replace("23}", "1.2}"), policy().replace("23}", "\"9223372036854775808\"}"),
                policy().replace("}", ",\"conditions\":[]}"), policy().replace("PMS_MINIMAL_NORMAL_CLOSURE", "OTHER")}) {
            assertThrows(RuntimeException.class, () -> NormalClosurePolicy.parseFrozen(value), value);
        }
    }
    @Test void sourceDigestCanonicalizesNestedObjectKeysWithoutReorderingEvidenceArrays() {
        JsonNode a = JsonUtils.parseObject("{\"b\":{\"z\":2,\"a\":1},\"a\":[2,1]}", JsonNode.class);
        JsonNode b = JsonUtils.parseObject("{\"a\":[2,1],\"b\":{\"a\":1,\"z\":2}}", JsonNode.class);
        assertEquals(NormalClosureCheckService.canonical(a), NormalClosureCheckService.canonical(b));
        assertTrue(NormalClosureCheckService.canonical(a).contains("[2,1]"));
    }
}
