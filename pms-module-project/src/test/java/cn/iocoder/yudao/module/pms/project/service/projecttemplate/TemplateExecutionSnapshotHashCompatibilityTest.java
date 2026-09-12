package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateExecutionSnapshotHashCompatibilityTest {

    @Test
    void compilerPublicationHashMatchesSchemaV2RuntimeVerifier() {
        TemplateCompiler.Compilation compilation = new TemplateCompiler().compile(validDesigner());

        assertTrue(compilation.valid(), () -> compilation.issues().toString());
        assertEquals(compilation.snapshotHash(), TemplateExecutionSnapshotHasher.hash(compilation.snapshot()));
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
        task.setWorkBinding(binding("TASK_NATIVE"));
        task.setPermission(permission("PROJECT_TASK_NATIVE_DEFAULT"));
        task.setCompletionRule(rule("TASK_NATIVE_STATUS", "DONE"));
        designer.getTasks().add(task);

        TemplateDesignerDocument.TransitionNode transition = new TemplateDesignerDocument.TransitionNode();
        transition.setEdgeKey("transition:S0-S1");
        transition.setCode("S0_TO_S1");
        transition.setFromStageCode("S0");
        transition.setToStageCode("S1");
        transition.setPriority(10);
        transition.setDefaultBranch(true);
        designer.getTransitions().add(transition);
        return designer;
    }

    private TemplateDesignerDocument.StageNode stage(String key, String code, boolean start, boolean terminal) {
        TemplateDesignerDocument.StageNode stage = new TemplateDesignerDocument.StageNode();
        stage.setNodeKey(key);
        stage.setCode(code);
        stage.setName(code);
        stage.setStart(start);
        stage.setTerminal(terminal);
        stage.setWorkBinding(binding("STAGE_NATIVE"));
        stage.setPermission(permission("PROJECT_STAGE_NATIVE_DEFAULT"));
        stage.setCompletionRule(rule("STAGE_NATIVE_STATUS", "DONE"));
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
        rule.setExpression(JsonUtils.parseObject(
                "{\"predicate\":\"" + predicate + "\",\"parameters\":{\"requiredStatus\":\"" + value + "\"}}",
                JsonNode.class));
        return rule;
    }
}
