package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateVersionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateVersionSnapshotTest {

    @Test
    void preservesEveryCompiledFieldWithoutTouchingTheDesignerOrLegacyHash() {
        TemplateDesignerDocument designer = designer();
        String source = JsonUtils.toJsonString(designer);
        var compilation = new TemplateCompiler().compile(designer);
        assertTrue(compilation.valid(), () -> compilation.issues().toString());
        var legacy = compilation.snapshot();
        String legacyHash = TemplateExecutionSnapshotHasher.hash(legacy);
        TemplateExecutionSnapshot snapshot = JsonUtils.parseObject(JsonUtils.toJsonString(legacy), TemplateExecutionSnapshot.class);
        snapshot.setExecutionSchemaVersion(3);
        snapshot.getTasks().getFirst().setEstimatedHours(new BigDecimal("12345678901234567890.123456789000"));
        String frozen = JsonUtils.toJsonString(snapshot);

        TemplateExecutionSnapshot first = TemplateExecutionSnapshotReader.read(frozen);
        TemplateExecutionSnapshot second = TemplateExecutionSnapshotReader.read(frozen);

        assertEquals(snapshot, first);
        assertEquals(first, second);
        assertNotSame(first, second);
        assertNotSame(first.getStages(), second.getStages());
        assertEquals(source, JsonUtils.toJsonString(designer));
        assertEquals(legacyHash, TemplateExecutionSnapshotHasher.hash(legacy));
        assertEquals(2, legacy.getExecutionSchemaVersion());
        assertEquals("ready", first.getMatchRuleKey());
        assertEquals("ready", first.getClosureRuleKey());
        assertEquals("ready", first.getStages().getFirst().getAdmissionRuleKey());
        assertEquals("ready", first.getTasks().getFirst().getExitRuleKey());
        first.getStages().getFirst().setName("仅修改调用方副本");
        assertEquals(snapshot, TemplateExecutionSnapshotReader.read(frozen));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rules", "rulePrograms", "stages", "tasks", "milestones", "deliverables", "gates", "transitions", "match"})
    void rejectsMissingOrNullFrozenCollectionsInsteadOfApplyingDefaults(String field) {
        ObjectNode document = json(snapshot());
        document.remove(field);
        assertThrows(IllegalArgumentException.class, () -> read(document));
        document.putNull(field);
        assertThrows(IllegalArgumentException.class, () -> read(document));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "STAGE", "TASK", "PROGRAM", "LEAF"})
    void rejectsUnknownModelFieldsRatherThanDiscardingConfiguration(String target) {
        ObjectNode document = json(snapshot());
        ObjectNode selected = switch (target) {
            case "ROOT" -> document;
            case "STAGE" -> (ObjectNode) document.path("stages").get(0);
            case "TASK" -> (ObjectNode) document.path("tasks").get(0);
            case "PROGRAM" -> (ObjectNode) document.path("rulePrograms").path("ready");
            case "LEAF" -> (ObjectNode) document.path("rulePrograms").path("ready").path("leaves").get(0);
            default -> throw new AssertionError(target);
        };
        selected.put("unrecognizedExecutionRule", true);
        assertThrows(RuntimeException.class, () -> read(document));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_PROGRAM", "EXTRA_PROGRAM", "BAD_KIND", "MISSING_LEAF", "EXTRA_LEAF", "CHANGED_LEAF",
            "MATCH_REF", "CLOSURE_REF", "ADMISSION_REF", "EXIT_REF", "COMPLETION_REF", "COMPLETION_BODY",
            "DUPLICATE_NODE", "DUPLICATE_CODE", "DUPLICATE_RULE", "PARENT_MISSING", "PARENT_CYCLE",
            "TASK_STAGE", "GATE_KEY", "GATE_PROGRAM", "GATE_REFERENCE", "MILESTONE_STAGE", "DELIVERABLE_TASK", "EDGE"})
    void rejectsBrokenFrozenClosure(String damage) {
        TemplateExecutionSnapshot snapshot = snapshot();
        RuleProgram ready = snapshot.getRulePrograms().get("ready");
        switch (damage) {
            case "MISSING_PROGRAM" -> snapshot.getRulePrograms().remove("ready");
            case "EXTRA_PROGRAM" -> snapshot.getRulePrograms().put("extra", ready);
            case "BAD_KIND" -> snapshot.getRulePrograms().put("ready", new RuleProgram(VersionRule.Kind.DECISION, ready.el(), ready.leaves()));
            case "MISSING_LEAF" -> snapshot.getRulePrograms().put("ready", new RuleProgram(ready.kind(), ready.el(), List.of()));
            case "EXTRA_LEAF" -> {
                var leaves = new ArrayList<>(ready.leaves());
                leaves.add(new RuleProgram.Leaf("extra", "rule", "CONSTANT", tree("{\"value\":true}")));
                snapshot.getRulePrograms().put("ready", new RuleProgram(ready.kind(), ready.el(), leaves));
            }
            case "CHANGED_LEAF" -> snapshot.getRulePrograms().put("ready", new RuleProgram(ready.kind(), ready.el(),
                    List.of(new RuleProgram.Leaf("condition0", "rule", "CONSTANT", tree("{\"value\":false}")))));
            case "MATCH_REF" -> snapshot.setMatchRuleKey("missing");
            case "CLOSURE_REF" -> snapshot.setClosureRuleKey("decision");
            case "ADMISSION_REF" -> snapshot.getStages().getFirst().setAdmissionRuleKey("missing");
            case "EXIT_REF" -> snapshot.getTasks().getFirst().setExitRuleKey("missing");
            case "COMPLETION_REF" -> snapshot.getTasks().getFirst().setCompletionRuleKey(null);
            case "COMPLETION_BODY" -> snapshot.getTasks().getFirst().setCompletionRule(tree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}"));
            case "DUPLICATE_NODE" -> snapshot.getMilestones().getFirst().setNodeKey(snapshot.getStages().getFirst().getNodeKey());
            case "DUPLICATE_CODE" -> snapshot.getStages().get(1).setCode(snapshot.getStages().getFirst().getCode());
            case "DUPLICATE_RULE" -> {
                var rules = new ArrayList<>(snapshot.getRules()); rules.add(rules.getFirst()); snapshot.setRules(rules);
            }
            case "PARENT_MISSING" -> snapshot.getTasks().getFirst().setParentTaskCode("missing");
            case "PARENT_CYCLE" -> snapshot.getTasks().getFirst().setParentTaskCode("T1");
            case "TASK_STAGE" -> snapshot.getTasks().getFirst().setStageCode("missing");
            case "GATE_KEY" -> snapshot.getGates().getFirst().setConditionRuleKey("ready");
            case "GATE_PROGRAM" -> snapshot.getRulePrograms().remove(snapshot.getGates().getFirst().getConditionRuleKey());
            case "GATE_REFERENCE" -> snapshot.getGates().getFirst().getReferences().getFirst().setRefCode("missing");
            case "MILESTONE_STAGE" -> snapshot.getMilestones().getFirst().setStageCode("missing");
            case "DELIVERABLE_TASK" -> snapshot.getDeliverables().getFirst().setTaskCode("missing");
            case "EDGE" -> snapshot.getTransitions().getFirst().setToStageCode("missing");
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(snapshot)));
    }

    @Test
    void requiresInlineDecisionTableAndKeepsItsExactDefinition() {
        var snapshot = snapshot();
        var decision = snapshot.getRulePrograms().get("decision");
        assertNotNull(decision);
        assertDoesNotThrow(() -> TemplateVersionSnapshot.validate(snapshot));
        ((ObjectNode) decision.leaves().getFirst().parameters()).remove("table");
        assertThrows(IllegalArgumentException.class, () -> TemplateVersionSnapshot.validate(snapshot));
    }

    @Test
    void operationProgramsMustBeTheSameVersionLocalPrograms() {
        var snapshot = snapshot();
        var binding = snapshot.getTasks().getFirst().getBinding();
        ObjectNode operation = (ObjectNode) tree("""
                {"version":1,"operations":[{"operationCode":"SOL.SITE_SURVEY.CONFIRM","operationVersion":1,
                  "pre":{"mode":"RULE","ruleKey":"ready"},"post":{"mode":"NONE"}}],"programs":{}}
                """);
        ((ObjectNode) operation.path("programs")).set("ready", json(snapshot.getRulePrograms().get("ready")));
        binding.setOperationContract(operation);
        assertDoesNotThrow(() -> read(json(snapshot)));
        ((ObjectNode) operation.path("programs").path("ready")).put("el", "changed");
        assertThrows(IllegalArgumentException.class, () -> read(json(snapshot)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"executionSchemaVersion\":2,\"executionSchemaVersion\":2}",
            "{\"executionSchemaVersion\":3,\"executionSchemaVersion\":2}",
            "{\"executionSchemaVersion\":2} {}"})
    void rejectsDuplicateAttributesAndTrailingDocuments(String json) {
        assertThrows(RuntimeException.class, () -> TemplateExecutionSnapshotReader.read(json));
    }

    @Test
    void preservesOriginalCompilationFormatUntilPublicationWiringIsReady() {
        var compilation = new TemplateCompiler().compile(designer());
        assertTrue(compilation.valid());
        assertEquals(2, compilation.snapshot().getExecutionSchemaVersion());
        assertNotNull(compilation.snapshotHash());
        assertDoesNotThrow(() -> TemplateExecutionSnapshotReader.requireSupportedVersion(3));
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionSnapshotReader.requireSupportedVersion(4));
    }

    static TemplateExecutionSnapshot snapshot() {
        var compilation = new TemplateCompiler().compile(designer());
        assertTrue(compilation.valid(), () -> compilation.issues().toString());
        var snapshot = compilation.snapshot();
        snapshot.setExecutionSchemaVersion(3);
        return snapshot;
    }

    static TemplateDesignerDocument designer() {
        var designer = new TemplateDesignerDocument();
        designer.getRules().add(new VersionRule("ready", "可办理", VersionRule.Kind.CONDITION, true,
                tree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"), null));
        designer.getRules().add(new VersionRule("decision", "策略", VersionRule.Kind.DECISION, false, null,
                new DecisionTableDefinition("decision", "策略", "decision", """
                        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="rules" name="rules" namespace="urn:test">
                          <decision id="decision" name="decision"><literalExpression><text>1</text></literalExpression></decision>
                        </definitions>
                        """, Map.of())));
        designer.setMatchRuleKey("ready"); designer.setClosureRuleKey("ready");
        for (int i = 0; i < 2; i++) {
            var stage = new TemplateDesignerDocument.StageNode();
            stage.setNodeKey("stage:S" + i); stage.setCode("S" + i); stage.setLifecycleStage("S" + i); stage.setName("阶段" + i);
            stage.setStart(i == 0); stage.setTerminal(i == 1); stage.setAdmissionRuleKey("ready");
            stage.setWorkBinding(binding("STAGE_NATIVE")); stage.setPermission(permission());
            stage.setCompletionRule(rule("STAGE_NATIVE_STATUS")); designer.getStages().add(stage);
        }
        var task = new TemplateDesignerDocument.TaskNode();
        task.setNodeKey("task:T1"); task.setCode("T1"); task.setName("任务"); task.setStageCode("S1");
        task.setWorkBinding(binding("TASK_NATIVE")); task.setPermission(permission()); task.setCompletionRule(rule("TASK_NATIVE_STATUS"));
        task.setExitRuleKey("ready"); designer.getTasks().add(task);
        var edge = new TemplateDesignerDocument.TransitionNode();
        edge.setEdgeKey("edge:S0-S1"); edge.setCode("S0_TO_S1"); edge.setFromStageCode("S0"); edge.setToStageCode("S1");
        designer.getTransitions().add(edge);
        var milestone = new TemplateDesignerDocument.MilestoneNode();
        milestone.setNodeKey("milestone:M1"); milestone.setCode("M1"); milestone.setName("里程碑"); milestone.setStageCode("S1");
        designer.getMilestones().add(milestone);
        var deliverable = new TemplateDesignerDocument.DeliverableNode();
        deliverable.setNodeKey("deliverable:D1"); deliverable.setCode("D1"); deliverable.setName("交付件"); deliverable.setStageCode("S1");
        deliverable.setTaskCode("T1"); designer.getDeliverables().add(deliverable);
        var gate = new TemplateDesignerDocument.GateNode();
        gate.setNodeKey("gate:G1"); gate.setCode("G1"); gate.setName("门禁"); gate.setGateType("EXIT"); gate.setStageCode("S1");
        var ref = new TemplateDesignerDocument.GateReference(); ref.setRefType("TASK"); ref.setRefCode("T1");
        gate.getReferences().add(ref); designer.getGates().add(gate);
        return designer;
    }

    private static TemplateDesignerDocument.WorkBindingSpec binding(String type) {
        var binding = new TemplateDesignerDocument.WorkBindingSpec(); binding.setType(type); binding.setParameters(tree("{}")); return binding;
    }
    private static TemplateDesignerDocument.PermissionRequirement permission() {
        var permission = new TemplateDesignerDocument.PermissionRequirement(); permission.setPolicyRef("DEFAULT"); return permission;
    }
    private static TemplateDesignerDocument.RuleSpec rule(String predicate) {
        var rule = new TemplateDesignerDocument.RuleSpec();
        rule.setExpression(tree("{\"predicate\":\"" + predicate + "\",\"parameters\":{\"requiredStatus\":\"DONE\"}}")); return rule;
    }
    private static JsonNode tree(String json) { return JsonUtils.parseTree(json); }
    private static ObjectNode json(Object object) { return (ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(object)); }
    private static TemplateExecutionSnapshot read(ObjectNode document) { return TemplateExecutionSnapshotReader.read(document); }
}
