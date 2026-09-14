package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StageCompletionEvidenceTest {
    @Test void nativeTypedEvidenceRoundTripsAndKeepsTheConditionDiscriminator() {
        var result = new RuleEvaluation("plan:1", RuleEvaluation.Outcome.MATCHED, null, List.of(), List.of(), List.of());
        var evidence = new StageCompletionEvidence(31L, 51L, result, result, List.of(), null);
        String json = JsonUtils.toJsonString(evidence);
        assertEquals("CONDITION", JsonUtils.parseTree(json).path("completion").path("kind").asText());
        assertEquals(evidence, JsonUtils.parseObject(json, StageCompletionEvidence.class));
    }
    @Test void businessEvidenceRoundTripsWithItsAssociationAndOwnerResultVersion() {
        var result = new RuleEvaluation("plan:1", RuleEvaluation.Outcome.MATCHED, null, List.of(), List.of(), List.of());
        var evidence = new StageCompletionEvidence(31L, 51L, result, result,
                List.of(new StageCompletionEvidence.BusinessResult(61L, "SOL", "REQUIREMENT_ANALYSIS", "71", "completed-v2")), null);
        assertEquals(evidence, JsonUtils.parseObject(JsonUtils.toJsonString(evidence), StageCompletionEvidence.class));
    }
    @Test void decisionCannotBeDeserializedAsACompletionCondition() {
        assertThrows(RuntimeException.class, () -> JsonUtils.parseObject(
                "{\"kind\":\"DECISION\",\"status\":\"AVAILABLE\",\"values\":[{\"price\":4}],\"conditions\":[],\"steps\":[],\"diagnostics\":[]}", RuleEvaluation.class));
    }
}
