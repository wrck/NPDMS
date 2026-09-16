package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class TemplateCompilerTest {
    private final TemplateCompiler compiler = new TemplateCompiler();

    @Test void lifecycleBindingIsRequiredFrozenAndIndependentOfCustomStageCode() {
        var designer = validDesigner();
        designer.getStages().getFirst().setLifecycleStage(null);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "INVALID_LIFECYCLE_STAGE".equals(issue.code())));
        designer.getStages().getFirst().setLifecycleStage("S7");
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "INVALID_LIFECYCLE_STAGE".equals(issue.code())));
        designer.getStages().forEach(stage -> stage.setLifecycleStage("S1"));
        var result = compiler.compile(designer);
        assertTrue(result.issues().isEmpty(), result.issues().toString());
        assertTrue(result.snapshot().getStages().stream().allMatch(stage -> "S1".equals(stage.getLifecycleStage())));
    }

    @Test void processGateRequiresAndPreservesExactDefinitionIdInSnapshotAndProjection() {
        var designer = validDesigner();
        var gate = new TemplateDesignerDocument.GateNode();
        gate.setNodeKey("gate:approval"); gate.setCode("APPROVAL_GATE"); gate.setName("阶段审批");
        gate.setStageCode("S1"); gate.setGateType("ENTRY");
        var ref = new TemplateDesignerDocument.GateReference(); ref.setRefType("APPROVAL"); ref.setRefCode("approval");
        gate.setReferences(new java.util.ArrayList<>(java.util.List.of(ref))); designer.getGates().add(gate);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "GATE_PROCESS_DEFINITION_REQUIRED".equals(issue.code())));
        ref.setRefVersion("approval:1:101");
        var compiled = compiler.compile(designer); assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var snapshot = JsonUtils.parseObject(JsonUtils.toJsonString(compiled.snapshot()),
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.class);
        assertEquals("approval:1:101", snapshot.getGates().getFirst().getReferences().getFirst().getRefVersion());
        assertEquals("approval:1:101", snapshot.toRuntimeContent().getGates().getFirst().getReferences().getFirst().getRefVersion());
        ref.setRefVersion("approval:2:202");
        assertEquals("approval:1:101", snapshot.getGates().getFirst().getReferences().getFirst().getRefVersion());
    }

    @Test void approvalBindingRequiresAnExactDefinitionAndSurvivesExecutionContractFreezing() {
        var designer = validDesigner(); var task = designer.getTasks().getFirst();
        task.getWorkBinding().setType("APPROVAL"); task.getWorkBinding().setApprovalDefinitionKey("approval");
        var completion = new TemplateDesignerDocument.RuleSpec();
        completion.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        task.setCompletionRule(completion);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "APPROVAL_DEFINITION_REQUIRED".equals(issue.code())));
        task.getWorkBinding().setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"approval:1:101\"}"));
        var compiled = compiler.compile(designer); assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var reopened = JsonUtils.parseObject(JsonUtils.toJsonString(compiled.snapshot()),
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.class);
        var contract = new cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory()
                .create(11L, null, reopened.toRuntimeContent().getTasks().getFirst(), java.time.LocalDateTime.now());
        assertEquals("approval:1:101", JsonUtils.parseTree(contract.getBindingParameterSnapshot()).path("processDefinitionId").asText());
        assertEquals("approval", JsonUtils.parseTree(contract.getBindingParameterSnapshot()).path("approvalDefinitionKey").asText());
        task.getWorkBinding().setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"approval:2:202\"}"));
        assertEquals("approval:1:101", reopened.getTasks().getFirst().getBinding().getParameters().path("processDefinitionId").asText());
        assertNotEquals(compiled.snapshotHash(), compiler.compile(designer).snapshotHash());
    }

    @Test void customStageCodesAndReferencesFreezeWithoutAddingPresetStages() {
        var designer = validDesigner();
        designer.getStages().getFirst().setCode("PREP_WORK");
        designer.getTransitions().getFirst().setFromStageCode("PREP_WORK");
        var gate = new TemplateDesignerDocument.GateNode();
        gate.setNodeKey("gate:ready"); gate.setCode("READY"); gate.setName("工前准备已完成");
        gate.setGateType("ENTRY"); gate.setStageCode("S1");
        var reference = new TemplateDesignerDocument.GateReference();
        reference.setRefType("STATE"); reference.setRefCode("PREP_WORK_COMPLETED");
        gate.setReferences(new java.util.ArrayList<>(java.util.List.of(reference)));
        designer.getGates().add(gate);
        var result = compiler.compile(designer);
        assertTrue(result.valid(), () -> result.issues().toString());
        assertEquals(java.util.List.of("PREP_WORK", "S1"), result.snapshot().getStages().stream()
                .map(cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.StageContract::getCode).toList());
        var frozenGate = result.snapshot().getGates().getFirst();
        assertEquals("PREP_WORK_COMPLETED", result.snapshot().getRulePrograms().get(frozenGate.getConditionRuleKey())
                .leaves().getFirst().parameters().path("refCode").asText());
        reference.setRefCode("MISSING_COMPLETED");
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "INVALID_GATE_REFERENCE".equals(issue.code())));
        designer.getStages().getFirst().setCode("A".repeat(33));
        assertFalse(compiler.compile(designer).valid());
    }

    @Test void gateReferencesCompileIntoOneFrozenLiteFlowProgramAndRejectDuplicateOrDanglingReferences() {
        var designer = validDesigner();
        var gate = new TemplateDesignerDocument.GateNode();
        gate.setNodeKey("gate:ready"); gate.setCode("READY"); gate.setName("工前准备门禁");
        gate.setGateType("ENTRY"); gate.setStageCode("S1");
        var task = new TemplateDesignerDocument.GateReference(); task.setRefType("TASK"); task.setRefCode("T1");
        var state = new TemplateDesignerDocument.GateReference(); state.setRefType("STATE"); state.setRefCode("S0_COMPLETED");
        gate.setReferences(new java.util.ArrayList<>(java.util.List.of(task,state)));
        designer.getGates().add(gate);
        var compiled = compiler.compile(designer);
        assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var frozenGate = compiled.snapshot().getGates().getFirst();
        var program = compiled.snapshot().getRulePrograms().get(frozenGate.getConditionRuleKey());
        assertEquals(java.util.List.of("TASK","STATE"), program.leaves().stream().map(leaf -> leaf.predicate()).toList());
        assertTrue(program.el().contains("AND("));
        var reopened = JsonUtils.parseObject(JsonUtils.toJsonString(compiled.snapshot()),
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.class);
        assertEquals(program, reopened.getRulePrograms().get(reopened.getGates().getFirst().getConditionRuleKey()));
        task.setRefCode("NOT_CONFIGURED");
        assertEquals("T1", program.leaves().getFirst().parameters().path("refCode").asText());
        assertFalse(compiler.compile(designer).valid());
        task.setRefCode("T1"); gate.getReferences().add(task);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "DUPLICATE_GATE_REFERENCE".equals(issue.code())));
        gate.getReferences().removeLast(); designer.getTasks().getFirst().setGateRef("MISSING");
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "DANGLING_GATE".equals(issue.code())));
    }

    @Test
    void organizationStageRequiresTaskOrBusinessCompletionInsteadOfAnUnavailableManualSubmission() {
        var designer = validDesigner();
        designer.getStages().getFirst().setWorkBinding(null);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "STAGE_HANDLING_NOT_CONFIGURED".equals(issue.code())));
        var nested = new TemplateDesignerDocument.RuleSpec();
        nested.setExpression(JsonUtils.parseTree("{\"operator\":\"ALL\",\"rules\":[{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}},{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"T1\"}}]}"));
        designer.getStages().getFirst().setCompletionRule(nested);
        assertTrue(compiler.compile(designer).issues().stream().anyMatch(issue -> "STAGE_HANDLING_NOT_CONFIGURED".equals(issue.code())));
        designer.getStages().getFirst().setCompletionRule(rule("TASK", "T1"));
        assertTrue(compiler.compile(designer).valid());
    }

    @Test
    void rejectsAnIndependentEdgeConditionInsteadOfPublishingAnIgnoredSecondRuleSource() {
        var designer = validDesigner();
        designer.getTransitions().getFirst().setDefaultBranch(false);
        designer.getTransitions().getFirst().setCondition(rule("STATE", "S0_COMPLETED"));
        var result = compiler.compile(designer);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> "EDGE_RULE_MUST_USE_ADMISSION".equals(issue.code())));
    }

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

    @Test
    void rejectsStageCodesThatCannotFitTheRuntimeColumn() {
        var designer = validDesigner();
        designer.getStages().getFirst().setCode("S".repeat(33));
        org.junit.jupiter.api.Assertions.assertTrue(new TemplateCompiler().compile(designer).issues().stream()
                .anyMatch(issue -> "INVALID_STAGE_CODE".equals(issue.code())));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "null", "[]", "{}", "{\"requiredActions\":[]}",
            "{\"requiredActions\":[\"VIEW\",\"VIEW\"]}",
            "{\"requiredActions\":[false]}", "{\"requiredActions\":[\"bad action\"]}",
            "{\"requiredActions\":[\"VIEW\"],\"grantAll\":true}"
    })
    void rejectsMalformedInlinePermissionEvenWithPolicyReference(String json) {
        var designer = validDesigner();
        designer.getTasks().getFirst().getPermission().setPolicySnapshot(JsonUtils.parseTree(json));
        var result = compiler.compile(designer);

        assertFalse(result.valid());
        assertNull(result.snapshot());
        assertTrue(result.issues().stream().anyMatch(issue ->
                "tasks[0].permission.policySnapshot".equals(issue.field())
                        && "INVALID_PERMISSION_POLICY".equals(issue.code())));
    }

    @Test
    void validatesStagePermissionUsingTheSamePayloadContract() {
        var designer = validDesigner();
        designer.getStages().getFirst().getPermission().setPolicySnapshot(JsonUtils.parseTree("{}"));
        var result = compiler.compile(designer);

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue ->
                "stages[0].permission.policySnapshot".equals(issue.field())
                        && "INVALID_PERMISSION_POLICY".equals(issue.code())));
    }

    @Test
    void freezesValidInlinePermissionWithoutRequiringPublishedAsset() {
        var designer = validDesigner();
        var permission = designer.getTasks().getFirst().getPermission();
        permission.setPolicyRef(null);
        permission.setPolicySnapshot(JsonUtils.parseTree("{\"requiredActions\":[\"QUERY\",\"UPDATE\"]}"));
        var result = compiler.compile(designer);

        assertTrue(result.valid(), () -> result.issues().toString());
        assertEquals(permission.getPolicySnapshot(), result.snapshot().getTasks().getFirst().getPermission().getPolicySnapshot());
        assertNull(result.snapshot().getTasks().getFirst().getPermission().getSourceRevisionId());
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
        stage.setLifecycleStage(code);
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
