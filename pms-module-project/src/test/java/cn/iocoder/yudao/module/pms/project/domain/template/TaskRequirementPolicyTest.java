package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TaskRequirementPolicyTest {
    private final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    private static final String TASK = "{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"SURVEY\"}}";
    private static final String FILE = "{\"predicate\":\"DELIVERABLE\",\"parameters\":{\"refCode\":\"SURVEY_FILE\"}}";
    private static final String OWNER = "{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"sourceNodeKey\":\"task:survey\",\"factCode\":\"SURVEY_CONFIRMED\",\"quantifier\":\"ALL\"}}";
    private static final String WAIT = "{\"predicate\":\"WAIT_ELAPSED\",\"parameters\":{\"sourceNodeKey\":\"task:survey\",\"anchor\":\"NODE_COMPLETED\",\"duration\":\"PT1H\"}}";

    @Test void optionalTaskCannotBeRequiredByAnotherTaskOrStageThroughItsDeliverable() {
        var snapshot = snapshot(false);
        snapshot.getTasks().get(1).setAdmissionRuleKey("task-dependency"); rule(snapshot, "task-dependency", TASK);
        rejected(snapshot, "task-dependency");
        snapshot.getTasks().get(1).setAdmissionRuleKey(null);
        snapshot.getStages().getFirst().setCompletionRuleKey("file-dependency"); rule(snapshot, "file-dependency", FILE);
        rejected(snapshot, "file-dependency");
    }

    @Test void closureTransitionAndGateCannotIndirectlyRequireOptionalOwnerOrWaitFacts() {
        var snapshot = snapshot(false);
        snapshot.setClosureRuleKey("owner-dependency"); rule(snapshot, "owner-dependency", OWNER);
        rejected(snapshot, "owner-dependency"); snapshot.setClosureRuleKey(null);
        var transition = new TemplateExecutionSnapshot.TransitionContract(); transition.setConditionRuleKey("wait-dependency");
        snapshot.setTransitions(List.of(transition)); rule(snapshot, "wait-dependency", WAIT);
        rejected(snapshot, "wait-dependency"); snapshot.setTransitions(List.of());
        var gate = new TemplateExecutionSnapshot.GateContract(); gate.setConditionRuleKey("gate-dependency");
        snapshot.setGates(List.of(gate)); rule(snapshot, "gate-dependency", TASK);
        rejected(snapshot, "gate-dependency");
    }

    @Test void optionalTaskKeepsItsOwnOwnerDeliverableAndWaitCompletionRequirements() {
        var snapshot = snapshot(false);
        snapshot.getTasks().getFirst().setCompletionRuleKey("own-completion");
        rule(snapshot, "own-completion", "{\"operator\":\"ALL\",\"rules\":[" + OWNER + "," + FILE + "," + WAIT + "]}");
        assertDoesNotThrow(() -> TaskRequirementPolicy.validateDependencies(snapshot));
        assertEquals(3, snapshot.getRulePrograms().get("own-completion").leaves().size());
        assertEquals(Boolean.FALSE, snapshot.getTasks().getFirst().getRequired());
    }

    @Test void legacyMissingRequirementPreservesAllExistingDependencies() {
        var snapshot = snapshot(null);
        snapshot.getTasks().get(1).setAdmissionRuleKey("legacy-task"); rule(snapshot, "legacy-task", TASK);
        snapshot.getStages().getFirst().setCompletionRuleKey("legacy-file"); rule(snapshot, "legacy-file", FILE);
        snapshot.setClosureRuleKey("legacy-owner"); rule(snapshot, "legacy-owner", OWNER);
        var transition = new TemplateExecutionSnapshot.TransitionContract(); transition.setConditionRuleKey("legacy-wait");
        snapshot.setTransitions(List.of(transition)); rule(snapshot, "legacy-wait", WAIT);
        assertDoesNotThrow(() -> TaskRequirementPolicy.validateDependencies(snapshot));
    }

    private TemplateExecutionSnapshot snapshot(Boolean required) {
        var snapshot = new TemplateExecutionSnapshot();
        var optional = new TemplateExecutionSnapshot.TaskContract(); optional.setNodeKey("task:survey"); optional.setCode("SURVEY"); optional.setRequired(required);
        var other = new TemplateExecutionSnapshot.TaskContract(); other.setNodeKey("task:analysis"); other.setCode("ANALYSIS");
        snapshot.setTasks(List.of(optional, other));
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setCode("S1"); snapshot.setStages(List.of(stage));
        var file = new TemplateExecutionSnapshot.DeliverableContract(); file.setCode("SURVEY_FILE"); file.setTaskCode("SURVEY");
        snapshot.setDeliverables(List.of(file));
        return snapshot;
    }

    private void rule(TemplateExecutionSnapshot snapshot, String key, String expression) {
        snapshot.getRulePrograms().put(key, compiler.compile(JsonUtils.parseTree(expression)));
    }

    private void rejected(TemplateExecutionSnapshot snapshot, String ruleKey) {
        var error = assertThrows(IllegalArgumentException.class, () -> TaskRequirementPolicy.validateDependencies(snapshot));
        assertTrue(error.getMessage().contains(ruleKey)); assertTrue(error.getMessage().contains("SURVEY"));
    }
}
