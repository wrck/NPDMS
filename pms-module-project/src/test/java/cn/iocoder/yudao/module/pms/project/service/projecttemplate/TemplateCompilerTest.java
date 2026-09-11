package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class TemplateCompilerTest {

    private final TemplateCompiler compiler = new TemplateCompiler();

    @Test
    void compilesSelfContainedSnapshotAndStableHash() {
        TemplateDesignerDocument designer = validDesigner();
        var first = compiler.compile(designer);
        assertTrue(first.valid(), () -> first.issues().toString());
        assertNotNull(first.snapshot());
        assertEquals(2, first.snapshot().getExecutionSchemaVersion());
        assertEquals("stage:S0", first.snapshot().getStages().getFirst().getNodeKey());
        assertEquals("TASK_NATIVE", first.snapshot().getTasks().getFirst().getBinding().getType());
        assertNotNull(first.snapshotHash());
        assertEquals(64, first.snapshotHash().length());

        // Legacy source ids are audit evidence and must not change runtime semantic hash.
        designer.getStages().getFirst().getSource().setDefinitionRevisionId(999999L);
        designer.getTasks().getFirst().getSource().setWorkBindingRevisionId(888888L);
        var second = compiler.compile(designer);
        assertTrue(second.valid(), () -> second.issues().toString());
        assertEquals(first.snapshotHash(), second.snapshotHash());
    }

    @Test
    void rejectsBusinessBindingWithNativeCompletion() {
        TemplateDesignerDocument designer = validDesigner();
        var task = designer.getTasks().getFirst();
        task.getWorkBinding().setType("BUSINESS_OBJECT");
        task.getWorkBinding().setTargetContextCode("SOL");
        task.getWorkBinding().setTargetObjectType("RequirementAnalysis");
        task.getWorkBinding().setTargetObjectKey("PROJECT");
        var result = compiler.compile(designer);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> "OWNER_FACT_REQUIRED".equals(issue.code())));
    }

    @Test
    void rejectsRuleTargetOutsideTemplate() {
        TemplateDesignerDocument designer = validDesigner();
        designer.getStages().getFirst().setCompletionRule(rule("TASK", "NOT_CONFIGURED"));
        var result = compiler.compile(designer);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> "RULE_TARGET_NOT_CONFIGURED".equals(issue.code())));
    }

    @Test
    void runtimeProjectionCarriesCompiledKeysAndRuleSnapshots() {
        var result = compiler.compile(validDesigner());
        assertTrue(result.valid(), () -> result.issues().toString());
        var runtime = result.snapshot().toRuntimeContent();
        assertEquals(2, runtime.getExecutionSnapshot().path("executionSchemaVersion").asInt());
        assertEquals("stage:S0", runtime.getStages().getFirst().getSourceNodeKey());
        assertNotNull(runtime.getStages().getFirst().getCompletionRuleSnapshot());
        assertEquals("transition:S0-S1", runtime.getTransitions().getFirst().getSourceTransitionKey());
        assertNull(runtime.getTransitions().getFirst().getConditionRuleRevisionId());
        assertEquals("task:T1", runtime.getTasks().getFirst().getSourceNodeKey());
        assertNotNull(runtime.getTasks().getFirst().getPermissionSnapshot());
    }

    private TemplateDesignerDocument validDesigner() {
        TemplateDesignerDocument designer = new TemplateDesignerDocument();
        designer.getStages().add(stage("stage:S0", "S0", true, false));
        designer.getStages().add(stage("stage:S1", "S1", false, true));

        TemplateDesignerDocument.TaskNode task = new TemplateDesignerDocument.TaskNode();
        task.setNodeKey("task:T1");
        task.setCode("T1");
        task.setName("需求分析");
        task.setStageCode("S1");
        task.setPriority(10);
        task.setSortOrder(10);
        task.setWorkBinding(binding("TASK_NATIVE"));
        task.setPermission(permission("PROJECT_TASK_NATIVE_DEFAULT"));
        task.setCompletionRule(rule("TASK_NATIVE_STATUS", "DONE"));
        task.setSource(new TemplateDesignerDocument.SourcePin());
        designer.getTasks().add(task);

        TemplateDesignerDocument.TransitionNode transition = new TemplateDesignerDocument.TransitionNode();
        transition.setEdgeKey("transition:S0-S1");
        transition.setCode("S0_TO_S1");
        transition.setFromStageCode("S0");
        transition.setToStageCode("S1");
        transition.setPriority(10);
        transition.setDefaultBranch(true);
        transition.setSource(new TemplateDesignerDocument.SourcePin());
        designer.getTransitions().add(transition);
        return designer;
    }

    private TemplateDesignerDocument.StageNode stage(String key, String code, boolean start, boolean terminal) {
        TemplateDesignerDocument.StageNode stage = new TemplateDesignerDocument.StageNode();
        stage.setNodeKey(key);
        stage.setCode(code);
        stage.setName(code);
        stage.setSortOrder(start ? 0 : 10);
        stage.setStart(start);
        stage.setTerminal(terminal);
        stage.setWorkBinding(binding("STAGE_NATIVE"));
        stage.setPermission(permission("PROJECT_STAGE_NATIVE_DEFAULT"));
        stage.setCompletionRule(rule("STAGE_NATIVE_STATUS", "DONE"));
        stage.setSource(new TemplateDesignerDocument.SourcePin());
        return stage;
    }

    private TemplateDesignerDocument.WorkBindingSpec binding(String type) {
        TemplateDesignerDocument.WorkBindingSpec binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType(type);
        binding.setParameters(JsonUtils.parseObject("{}", JsonNode.class));
        return binding;
    }

    private TemplateDesignerDocument.PermissionRequirement permission(String ref) {
        TemplateDesignerDocument.PermissionRequirement permission = new TemplateDesignerDocument.PermissionRequirement();
        permission.setPolicyRef(ref);
        return permission;
    }

    private TemplateDesignerDocument.RuleSpec rule(String predicate, String value) {
        TemplateDesignerDocument.RuleSpec rule = new TemplateDesignerDocument.RuleSpec();
        String json = predicate.endsWith("_NATIVE_STATUS")
                ? "{\"predicate\":\"" + predicate + "\",\"parameters\":{\"requiredStatus\":\"" + value + "\"}}"
                : "{\"predicate\":\"" + predicate + "\",\"parameters\":{\"refCode\":\"" + value + "\"}}";
        rule.setExpression(JsonUtils.parseObject(json, JsonNode.class));
        return rule;
    }
}
