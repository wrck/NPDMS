package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Integration of the real JSON model, compiler and hasher; does not substitute for a project build. */
class TemplateOperationCompilationTest {
    public static class TestOwner { public void confirm() { } }
    private static final String OP = "SOL.SITE_SURVEY.CONFIRM";

    private ProjectBusinessOperationRegistry registry() {
        ProjectBusinessOperationProvider provider = () -> List.of(new ProjectBusinessOperationDescriptor(
                OP, 1, "SOL", "SITE_SURVEY", "确认工勘", "CONFIRM", Set.of("PRE", "POST"), TestOwner.class, "confirm"));
        return new ProjectBusinessOperationRegistry(List.of(provider), List.of());
    }

    private TemplateCompiler compiler() throws ReflectiveOperationException {
        var result = new TemplateCompiler();
        var field = TemplateCompiler.class.getDeclaredField("operationCompilation");
        field.setAccessible(true);
        field.set(result, new TemplateOperationCompilation(registry(), new ProjectRuleCompiler()));
        return result;
    }

    @Test
    void legacyFieldRemainsAbsentInDesignerAndSnapshot() throws Exception {
        var designer = designer(false, true);
        String before = JsonUtils.toJsonString(designer);
        assertFalse(before.contains("operationContract"));
        var roundTrip = JsonUtils.parseObject(before, TemplateDesignerDocument.class);
        assertNull(roundTrip.getTasks().getFirst().getWorkBinding().getOperationContract());
        var oldCompiler = new TemplateCompiler().compile(designer);
        var wiredCompiler = compiler().compile(roundTrip);
        assertTrue(oldCompiler.valid(), () -> oldCompiler.issues().toString());
        assertTrue(wiredCompiler.valid(), () -> wiredCompiler.issues().toString());
        assertEquals(oldCompiler.snapshotHash(), wiredCompiler.snapshotHash());
        assertFalse(JsonUtils.toJsonString(wiredCompiler.snapshot()).contains("operationContract"));
    }

    @Test
    void authoringSaveReopenAndCompilationPinExactRules() throws Exception {
        var input = designer(true, true);
        var reopened = TemplateRuleCollection.forEditing(JsonUtils.parseObject(JsonUtils.toJsonString(input), TemplateDesignerDocument.class));
        var result = compiler().compile(reopened);
        assertTrue(result.valid(), () -> result.issues().toString());
        var contract = result.snapshot().getTasks().getFirst().getBinding().getOperationContract();
        assertEquals(1, contract.path("version").asInt());
        assertEquals(OP, contract.path("operations").get(0).path("operationCode").asText());
        assertEquals("CONSTANT", contract.path("programs").path("pre_rule").path("leaves").get(0).path("predicate").asText());
        var runtime = JsonUtils.parseObject(JsonUtils.toJsonString(result.snapshot()), TemplateExecutionSnapshot.class);
        assertEquals(result.snapshotHash(), TemplateExecutionSnapshotHasher.hash(runtime));
        assertEquals(contract, runtime.toRuntimeContent().getExecutionSnapshot().path("tasks").get(0).path("binding").path("operationContract"));
        assertFalse(reopened.getTasks().getFirst().getWorkBinding().getOperationContract().has("programs"));
    }

    @Test
    void sameRuleKeyDifferentSemanticContentChangesNewHash() throws Exception {
        var first = compiler().compile(designer(true, true));
        var second = compiler().compile(designer(true, false));
        assertTrue(first.valid(), () -> first.issues().toString());
        assertTrue(second.valid(), () -> second.issues().toString());
        assertNotEquals(first.snapshotHash(), second.snapshotHash());
    }

    @Test
    void stageDirectBindingUsesTheSameOperationContract() throws Exception {
        var input = designer(true, true);
        input.getStages().getFirst().setWorkBinding(input.getTasks().getFirst().getWorkBinding());
        input.getStages().getFirst().setPermission(permission());
        input.getTasks().clear();
        var result = compiler().compile(input);
        assertTrue(result.valid(), () -> result.issues().toString());
        assertNotNull(result.snapshot().getStages().getFirst().getBinding().getOperationContract());
        assertTrue(result.snapshot().getTasks().isEmpty());
    }

    @Test
    void unknownOperationVersionAndOwnerMismatchFailCompilation() throws Exception {
        var wrongVersion = designer(true, true);
        wrongVersion.getTasks().getFirst().getWorkBinding().setOperationContract(JsonUtils.parseTree(
                JsonUtils.toJsonString(wrongVersion.getTasks().getFirst().getWorkBinding().getOperationContract())
                        .replace("\"operationVersion\":1", "\"operationVersion\":2")));
        assertFalse(compiler().compile(wrongVersion).valid());
        var wrongOwner = designer(true, true);
        wrongOwner.getTasks().getFirst().getWorkBinding().setTargetContextCode("ACC");
        assertFalse(compiler().compile(wrongOwner).valid());
    }

    @Test
    void explicitNullAndBrowserSuppliedProgramsAreNotLegacy() {
        assertThrows(IllegalArgumentException.class, () -> TemplateOperationContractJson.readAuthoring(JsonUtils.parseTree("null")));
        assertThrows(IllegalArgumentException.class, () -> TemplateOperationContractJson.readAuthoring(JsonUtils.parseTree(
                "{\"version\":1,\"operations\":[],\"programs\":{}}")));
    }

    @Test
    void futureCompletionFactCannotBeAnOperationPostcondition() throws Exception {
        var input = designer(true, true);
        input.getRules().add(new VersionRule("future", "等待任务", VersionRule.Kind.CONDITION, false,
                JsonUtils.parseTree("{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"T1\",\"requiredStatus\":\"DONE\"}}"), null));
        var raw = JsonUtils.toJsonString(input.getTasks().getFirst().getWorkBinding().getOperationContract());
        input.getTasks().getFirst().getWorkBinding().setOperationContract(JsonUtils.parseTree(raw.replace(
                "\"post\":{\"mode\":\"NONE\"}", "\"post\":{\"mode\":\"RULE\",\"ruleKey\":\"future\"}")));
        var result = compiler().compile(input);
        assertFalse(result.valid());
    }

    @Test
    void operationRuleReferencesParticipateInSharedRuleProtection() throws Exception {
        var input = designer(true, true);
        var second = JsonUtils.parseObject(JsonUtils.toJsonString(input.getTasks().getFirst()), TemplateDesignerDocument.TaskNode.class);
        second.setNodeKey("task:T2"); second.setCode("T2");
        input.getTasks().add(second);
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(input));
        var old = input.getRules().getFirst();
        input.getRules().set(0, new VersionRule(old.key(), old.name(), old.kind(), true, old.expression(), old.decision()));
        assertTrue(compiler().compile(input).valid());
    }

    @Test
    void publicationRequiresRealRuntimeAdapterButDraftCompilationDoesNot() throws Exception {
        var input = designer(true, true);
        assertTrue(compiler().compile(input).valid());
        var issues = new TemplateOperationPublicationValidator(registry()).validate(input);
        assertTrue(issues.stream().anyMatch(issue -> "OPERATION_RUNTIME_NOT_INSTALLED".equals(issue.code())));
        assertTrue(new TemplateOperationPublicationValidator(registry()).validate(designer(false, true)).isEmpty());
    }

    @Test
    void duplicateRuntimeHandlersCannotClaimAvailability() {
        var descriptor = registry().all().getFirst();
        var duplicate = new ProjectBusinessOperationRegistry(List.of(() -> List.of(descriptor)),
                List.of((code, version) -> true, (code, version) -> true));
        assertFalse(duplicate.runtimeAvailable(OP, 1));
    }

    private TemplateDesignerDocument designer(boolean operations, boolean value) {
        var document = new TemplateDesignerDocument();
        var stage = new TemplateDesignerDocument.StageNode();
        stage.setNodeKey("stage:PREP"); stage.setCode("PREP"); stage.setName("准备");
        stage.setLifecycleStage("S1"); stage.setStart(true); stage.setTerminal(true);
        stage.setCompletionRule(constant(true)); document.getStages().add(stage);
        var task = new TemplateDesignerDocument.TaskNode();
        task.setNodeKey("task:T1"); task.setCode("T1"); task.setName("业务结果"); task.setStageCode("PREP");
        var binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("SITE_SURVEY"); binding.setTargetObjectKey("SURVEY");
        binding.setParameters(JsonUtils.parseTree("{}"));
        if (operations) {
            binding.setOperationContract(JsonUtils.parseTree("{\"version\":1,\"operations\":[{\"operationCode\":\"" + OP
                    + "\",\"operationVersion\":1,\"pre\":{\"mode\":\"RULE\",\"ruleKey\":\"pre_rule\"},\"post\":{\"mode\":\"NONE\"}}]}"));
            document.getRules().add(new VersionRule("pre_rule", "操作前置", VersionRule.Kind.CONDITION, false,
                    constant(value).getExpression(), null));
        }
        task.setWorkBinding(binding); task.setPermission(permission()); task.setCompletionRule(constant(true));
        document.getTasks().add(task);
        return document;
    }
    private TemplateDesignerDocument.PermissionRequirement permission() {
        var permission = new TemplateDesignerDocument.PermissionRequirement(); permission.setPolicyRef("OWNER"); return permission;
    }
    private TemplateDesignerDocument.RuleSpec constant(boolean value) {
        var result = new TemplateDesignerDocument.RuleSpec();
        result.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":" + value + "}}"));
        return result;
    }
}
