package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import com.yomahub.liteflow.flow.FlowBus;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionResolver;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Revision;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRulePublicationValidatorTest {
    private static RuleEngineTestFixture rules;
    private static DmnEngine decisions;
    private static ProjectRulePublicationValidator validator;

    @BeforeAll static void start() {
        rules = new RuleEngineTestFixture();
        decisions = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        validator = new ProjectRulePublicationValidator(new ProjectRuleCompiler(), new ProjectDecisionTableService(decisions),
                new cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry(List.of()));
    }
    @AfterAll static void stop() { if (decisions != null) decisions.close(); if (rules != null) rules.close(); }

    @Test void nativeValidationDoesNotPublishOrExecuteAChain() {
        var before = Map.copyOf(FlowBus.getChainMap());
        assertTrue(validator.validate(document(condition("""
                {"predicate":"CONSTANT","parameters":{"value":true}}
                """))).isEmpty());
        assertEquals(before, FlowBus.getChainMap());
    }

    @Test void assetConditionsUseNativePublicationChecksWithoutRegisteringOrRunningChains() {
        var before = Map.copyOf(FlowBus.getChainMap());
        var expression = JsonUtils.parseTree("""
                {"id":"group_1","operator":"NOT","rules":[{"id":"field_1","predicate":"FIELD",
                 "parameters":{"fieldCode":"project.isChild","valueType":"BOOLEAN","operator":"=","value":true}}]}
                """);
        assertTrue(validator.validateCondition(expression).isEmpty());
        assertDoesNotThrow(() -> resolveAsset(expression));
        assertEquals(before, FlowBus.getChainMap());
        var component = FlowBus.getNode("pmsRuleField");
        FlowBus.removeNode("pmsRuleField");
        try { assertThrows(RuntimeException.class, () -> resolveAsset(expression)); }
        finally { FlowBus.getNodeMap().put("pmsRuleField", component); }
        var unavailable = assertThrows(RuntimeException.class, () -> resolveAsset(JsonUtils.parseTree(expression.toString()
                .replace("project.isChild", "project.secret"))));
        assertTrue(unavailable.getMessage().contains("rules[0]"));
        assertFalse(unavailable.getMessage().contains("project.secret"));
        assertThrows(RuntimeException.class, () -> resolveAsset(JsonUtils.parseTree(expression.toString()
                .replace("BOOLEAN", "TEXT"))));
    }

    @Test void assetDecisionConditionsKeepInlineTablesAndRejectUnresolvedVersionReferences() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var table = new DecisionTableDefinition(original.key(), original.name(), original.decisionKey(), original.xml(),
                Map.of("amount", "project.majorProjectLevel"));
        var expression = JsonUtils.parseTree(JsonUtils.toJsonString(Map.of("id", "decision_1", "predicate", "DECISION",
                "parameters", Map.of("table", table, "fieldCode", "allowed", "valueType", "BOOLEAN",
                        "operator", "=", "value", true, "quantifier", "ANY"))));
        assertTrue(validator.validateCondition(expression).isEmpty());
        assertDoesNotThrow(() -> resolveAsset(expression));
        assertThrows(RuntimeException.class, () -> resolveAsset(JsonUtils.parseTree(expression.toString()
                .replace("\"allowed\"", "\"missing\""))));
        assertThrows(RuntimeException.class, () -> resolveAsset(JsonUtils.parseTree("""
                {"predicate":"DECISION","parameters":{"ruleKey":"anotherVersion","fieldCode":"approved",
                "valueType":"BOOLEAN","operator":"=","value":true}}
                """)));
    }

    private static void resolveAsset(JsonNode expression) {
        var providers = mock(cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry.class);
        var views = mock(cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi.class);
        var resolver = new DeliveryDefinitionResolver(
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.DeliveryDefinitionRevisionMapper.class),
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.DeliveryDefinitionReferenceMapper.class),
                views, providers, mock(cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi.class),
                mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry.class), validator);
        TenantContextHolder.setTenantId(7L);
        try {
            resolver.resolveDefinition(new Revision(1L, DeliveryDefinitionKind.COMPLETION_RULE, "ASSET_RULE", 1L,
                    "DRAFT", 1, expression, List.of(), null, null, 0), true);
            verifyNoInteractions(providers, views);
        } finally { TenantContextHolder.clear(); }
    }

    @Test void missingComponentIsRejectedBeforePublication() {
        var component = FlowBus.getNode("pmsRuleMatched");
        FlowBus.removeNode("pmsRuleMatched");
        try {
            assertTrue(validator.validate(document(condition("""
                    {"predicate":"CONSTANT","parameters":{"value":true}}
                    """))).stream().anyMatch(issue -> issue.code().equals("RULE_COMPONENT_UNAVAILABLE")));
        } finally {
            FlowBus.getNodeMap().put("pmsRuleMatched", component);
        }
    }

    @Test void gateOnlyDefinitionAlsoValidatesItsGeneratedPredicateComponentBeforePublication() {
        var document = new TemplateDesignerDocument();
        var gate = new TemplateDesignerDocument.GateNode(); gate.setNodeKey("gate:ready");
        var ref = new TemplateDesignerDocument.GateReference(); ref.setRefType("TASK"); ref.setRefCode("T1");
        gate.setReferences(List.of(ref)); document.getGates().add(gate);
        assertTrue(validator.validate(document).isEmpty());
        var component = FlowBus.getNode("pmsRulePredicate");
        FlowBus.removeNode("pmsRulePredicate");
        try {
            assertTrue(validator.validate(document).stream().anyMatch(issue -> issue.field().equals("gates.gate:ready")
                    && issue.code().equals("RULE_COMPONENT_UNAVAILABLE")));
        } finally { FlowBus.getNodeMap().put("pmsRulePredicate",component); }
    }

    @Test void matchingOnlyUsesCreationTimeFactsAndRetainsDeclaredTypes() {
        var source = document(condition("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.lifecycleStatus","valueType":"TEXT","operator":"=","value":"ACTIVE"}}
                """));
        assertTrue(validator.validate(source).isEmpty());
        source.setMatchRuleKey("rule");
        assertTrue(validator.validate(source).stream().anyMatch(issue -> issue.code().equals("RULE_FIELD_UNAVAILABLE")));
        var wrongType = document(condition("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.isChild","valueType":"TEXT","operator":"=","value":"false"}}
                """));
        assertTrue(validator.validate(wrongType).stream().anyMatch(issue -> issue.code().equals("RULE_FIELD_TYPE_MISMATCH")));
    }

    @Test void nativeDecisionExpressionsAndOpenInputBindingsAreChecked() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var table = new DecisionTableDefinition(original.key(), original.name(), original.decisionKey(), original.xml(),
                Map.of("amount", "project.majorProjectLevel"));
        var rule = new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, table);
        assertTrue(validator.validate(document(rule)).isEmpty());
        var invalid = new DecisionTableDefinition(table.key(), table.name(), table.decisionKey(),
                table.xml().replace("<text>true</text>", "<text>${missing.invoke()}</text>"), table.inputFields());
        assertTrue(validator.validate(document(new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, invalid)))
                .stream().anyMatch(issue -> issue.code().equals("RULE_EXECUTION_DEFINITION_INVALID")));
        assertFalse(validator.validate(document(new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, original))).isEmpty());
    }

    @Test void explicitComparisonMustReferenceAnExistingDecisionOutput() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var table = new DecisionTableDefinition(original.key(), original.name(), original.decisionKey(), original.xml(),
                Map.of("amount", "project.majorProjectLevel"));
        var condition = condition("""
                {"predicate":"DECISION","parameters":{"ruleKey":"strategy","fieldCode":"missing","valueType":"BOOLEAN","operator":"=","value":true}}
                """);
        var source = document(condition);
        source.setRules(List.of(condition, new VersionRule("strategy", "策略", VersionRule.Kind.DECISION, false, null, table)));
        assertTrue(validator.validate(source).stream().anyMatch(issue -> issue.code().equals("DECISION_OUTPUT_UNAVAILABLE")));
    }

    @Test void sharedBusinessRuleIsCheckedAgainstEveryConsumingNode() {
        var survey = businessProvider("SOL", "SITE_SURVEY", true, Set.of("SURVEY_CONFIRMED"));
        var acceptance = businessProvider("ACC", "ACCEPTANCE", false, Set.of("REPORT_EFFECTIVE"));
        var checks = businessValidator(survey, acceptance);
        var source = boundDocument();
        assertTrue(checks.validate(source).isEmpty());
        source.getTasks().getFirst().getWorkBinding().setTargetContextCode("ACC");
        source.getTasks().getFirst().getWorkBinding().setTargetObjectType("ACCEPTANCE");
        var issues = checks.validate(source);
        assertTrue(issues.stream().anyMatch(issue -> issue.field().startsWith("tasks[0].completionRuleKey")
                && issue.code().equals("RULE_BUSINESS_BINDING_UNAVAILABLE")));
        assertFalse(issues.stream().anyMatch(issue -> issue.field().startsWith("stages[0]")));
        metadataOnly(survey, acceptance);
    }

    @Test void stageCapabilityAndBusinessHostAreRequiredIndependentlyOfTaskSupport() {
        var survey = businessProvider("SOL", "SITE_SURVEY", false, Set.of("SURVEY_CONFIRMED"));
        var checks = businessValidator(survey);
        var source = boundDocument();
        assertTrue(checks.validate(source).stream().anyMatch(issue -> issue.field().startsWith("stages[0]")
                && issue.code().equals("RULE_BUSINESS_BINDING_UNAVAILABLE")));
        assertFalse(checks.validate(source).stream().anyMatch(issue -> issue.field().startsWith("tasks[0]")));
        when(survey.supportsStageCompletionFacts()).thenReturn(true);
        assertTrue(checks.validate(source).isEmpty());
        for (String type : List.of("STAGE_NATIVE", "DYNAMIC_FORM", "APPROVAL", "COMPOSITE")) {
            source.getStages().getFirst().getWorkBinding().setType(type);
            assertTrue(checks.validate(source).stream().anyMatch(issue -> issue.code().equals("RULE_BUSINESS_BINDING_UNAVAILABLE")));
        }
        metadataOnly(survey);
    }

    @Test void admissionCompletionAndExitCannotBorrowFactsWithoutTheirOwnBinding() {
        var survey = businessProvider("SOL", "SITE_SURVEY", true, Set.of("SURVEY_CONFIRMED"));
        var source = boundDocument();
        var task = source.getTasks().getFirst();
        task.setAdmissionRuleKey("business"); task.setExitRuleKey("business"); task.setWorkBinding(null);
        var issues = businessValidator(survey).validate(source);
        for (String slot : List.of("admissionRuleKey", "completionRuleKey", "exitRuleKey"))
            assertTrue(issues.stream().anyMatch(issue -> issue.field().startsWith("tasks[0]." + slot)
                    && issue.code().equals("RULE_BUSINESS_BINDING_UNAVAILABLE")));
        var unknown = businessValidator().validate(source);
        assertTrue(unknown.stream().anyMatch(issue -> issue.code().equals("RULE_BUSINESS_FACT_UNAVAILABLE")));
        metadataOnly(survey);
    }

    @Test void explicitSourceUsesItsBindingNotTheConsumerAndMustExistInThisVersion() {
        var survey = businessProvider("SOL", "SITE_SURVEY", true, Set.of("SURVEY_CONFIRMED"));
        var checks = businessValidator(survey);
        var source = boundDocument();
        var task = source.getTasks().getFirst(); task.setWorkBinding(null); task.setCompletionRuleKey(null);
        task.setAdmissionRuleKey("from-stage");
        var rule = new VersionRule("from-stage", "工前准备业务结果", VersionRule.Kind.CONDITION, false, JsonUtils.parseTree("""
                {"predicate":"BUSINESS_FACT","parameters":{"sourceNodeKey":"stage:prep","factCode":"SURVEY_CONFIRMED","quantifier":"ALL"}}
                """), null);
        source.setRules(List.of(source.getRules().getFirst(), rule));
        assertTrue(checks.validate(source).isEmpty());
        assertFalse(checks.validateCondition(rule.expression()).isEmpty());
        source.setStages(List.of());
        assertTrue(checks.validate(source).stream().anyMatch(issue -> issue.code().equals("RULE_BUSINESS_SOURCE_UNAVAILABLE")));
        metadataOnly(survey);
    }

    @Test void admissionCannotDependOnItsOwnUnstartedBusinessEvidence() {
        var checks = businessValidator(businessProvider("SOL", "SITE_SURVEY", true, Set.of("SURVEY_CONFIRMED")));
        var source = boundDocument(); source.getTasks().getFirst().setAdmissionRuleKey("business");
        assertTrue(checks.validate(source).stream().anyMatch(issue -> issue.code().equals("RULE_BUSINESS_SOURCE_REQUIRED")));
    }

    private static TemplateDesignerDocument boundDocument() {
        var expression = JsonUtils.parseTree("""
                {"operator":"NOT","rules":[{"operator":"ALL","rules":[
                {"predicate":"BUSINESS_FACT","parameters":{"factCode":"SURVEY_CONFIRMED","quantifier":"ALL"}}]}]}
                """);
        var source = document(new VersionRule("business", "业务条件", VersionRule.Kind.CONDITION, true, expression, null));
        var stage = new TemplateDesignerDocument.StageNode(); stage.setNodeKey("stage:prep");
        stage.setCompletionRuleKey("business"); stage.setWorkBinding(surveyBinding("BUSINESS_OBJECT"));
        var task = new TemplateDesignerDocument.TaskNode(); task.setNodeKey("task:survey");
        task.setCompletionRuleKey("business"); task.setWorkBinding(surveyBinding("BUSINESS_COMPONENT"));
        source.setStages(List.of(stage)); source.setTasks(List.of(task));
        return source;
    }

    private static TemplateDesignerDocument.WorkBindingSpec surveyBinding(String type) {
        var binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType(type); binding.setTargetContextCode("SOL"); binding.setTargetObjectType("SITE_SURVEY");
        return binding;
    }

    private static ProjectRulePublicationValidator businessValidator(TaskBusinessObjectProvider... providers) {
        return new ProjectRulePublicationValidator(new ProjectRuleCompiler(), new ProjectDecisionTableService(decisions),
                new TaskBusinessProviderRegistry(List.of(providers)));
    }

    private static TaskBusinessObjectProvider businessProvider(String owner, String type, boolean stages, Set<String> codes) {
        var provider = mock(TaskBusinessObjectProvider.class);
        when(provider.ownerContext()).thenReturn(owner); when(provider.objectType()).thenReturn(type);
        when(provider.completionFactCodes()).thenReturn(codes); when(provider.supportsStageCompletionFacts()).thenReturn(stages);
        return provider;
    }

    private static void metadataOnly(TaskBusinessObjectProvider... providers) {
        for (var provider : providers) {
            verify(provider, atLeast(0)).ownerContext(); verify(provider, atLeast(0)).objectType();
            verify(provider, atLeast(0)).completionFactCodes(); verify(provider, atLeast(0)).supportsStageCompletionFacts();
            verifyNoMoreInteractions(provider);
        }
    }

    private static VersionRule condition(String expression) {
        return new VersionRule("rule", "条件", VersionRule.Kind.CONDITION, false, JsonUtils.parseObject(expression, JsonNode.class), null);
    }
    private static TemplateDesignerDocument document(VersionRule rule) {
        var source = new TemplateDesignerDocument(); source.setRules(List.of(rule)); return source;
    }
}
