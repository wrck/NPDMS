package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionResolver;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: publication rejects rule facts outside the actual template, even within ALL/ANY. */
class TemplateDefinitionRuleTargetTest {
    private final DeliveryDefinitionResolver resolver = mock(DeliveryDefinitionResolver.class);
    private final TemplateDefinitionReferenceAssembler assembler = new TemplateDefinitionReferenceAssembler(resolver);

    @ParameterizedTest
    @CsvSource({"TASK,UNKNOWN", "MILESTONE,UNKNOWN", "DELIVERABLE,UNKNOWN", "STATE,S6_COMPLETED"})
    void unknownOrUnconfiguredTemplateTargetRejected(String predicate, String code) {
        var exception = assertThrows(ServiceException.class, () -> assembler.validateRuleTargets(content(),
                List.of(snapshot(DeliveryDefinitionKind.COMPLETION_RULE, rule(predicate, code)))));
        assertTrue(exception.getMessage().contains("parameters.refCode"));
        assertTrue(exception.getMessage().contains(code));
    }

    @ParameterizedTest
    @CsvSource({"TASK,T_OTHER", "MILESTONE,M_OTHER", "DELIVERABLE,D_OTHER", "STATE,S4_COMPLETED"})
    void publishedDefinitionDoesNotMakeAnotherTemplatesTargetAvailable(String predicate, String code) {
        var foreignTemplate = content();
        foreignTemplate.getTasks().getFirst().setTaskCode("T_OTHER");
        foreignTemplate.getMilestones().getFirst().setMilestoneCode("M_OTHER");
        foreignTemplate.getDeliverables().getFirst().setDeliverableCode("D_OTHER");
        foreignTemplate.getStages().getFirst().setStageCode("S4");
        var publishedRule = snapshot(DeliveryDefinitionKind.COMPLETION_RULE, rule(predicate, code));
        assertDoesNotThrow(() -> assembler.validateRuleTargets(foreignTemplate, List.of(publishedRule)));
        assertThrows(ServiceException.class, () -> assembler.validateRuleTargets(content(), List.of(publishedRule)));
    }

    @Test void nestedAnyCannotHideMissingTargetBehindValidAlternative() {
        String nested = "{\"operator\":\"ALL\",\"rules\":[" + rule("TASK", "T1")
                + ",{\"operator\":\"ANY\",\"rules\":[" + rule("STATE", "S0_COMPLETED") + ","
                + rule("DELIVERABLE", "D_MISSING") + "]}]}";
        var exception = assertThrows(ServiceException.class, () -> assembler.validateRuleTargets(content(),
                List.of(snapshot(DeliveryDefinitionKind.COMPLETION_RULE, nested))));
        assertTrue(exception.getMessage().contains("rules[1].rules[1].parameters.refCode"));
    }

    @Test void deliverableConfirmationRuleAlsoRejectsNestedForeignTarget() {
        String payload = "{\"confirmationRule\":{\"operator\":\"ANY\",\"rules\":["
                + rule("TASK", "T1") + "," + rule("MILESTONE", "M_OTHER") + "]}}";
        var exception = assertThrows(ServiceException.class, () -> assembler.validateRuleTargets(content(),
                List.of(snapshot(DeliveryDefinitionKind.DELIVERABLE, payload))));
        assertTrue(exception.getMessage().contains("confirmationRule.rules[1].parameters.refCode"));
    }

    @Test void actualTargetsAndNativeImplicitTargetsRemainValid() {
        String valid = "{\"operator\":\"ALL\",\"rules\":[" + rule("TASK", "T1") + ","
                + rule("MILESTONE", "M1") + "," + rule("DELIVERABLE", "D1") + "," + rule("STATE", "S0_COMPLETED")
                + ",{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}"
                + ",{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}]}";
        assertDoesNotThrow(() -> assembler.validateRuleTargets(content(),
                List.of(snapshot(DeliveryDefinitionKind.COMPLETION_RULE, valid))));
    }

    @Test void resolveRejectsTransitionRuleBeforeWritingSnapshot() {
        var content = new TemplateDefinitionContent();
        var edge = new TemplateDefinitionContent.TransitionDef(); edge.setRevisionNo(1L); edge.setConditionRuleRevisionId(90L);
        content.setTransitions(List.of(edge));
        when(resolver.resolve(anyList(), isNull(), eq(true))).thenReturn(
                Map.of(90L, snapshot(DeliveryDefinitionKind.COMPLETION_RULE, rule("TASK", "T_FROM_OTHER_TEMPLATE"))));
        assertThrows(ServiceException.class, () -> assembler.resolve(content, true));
        assertNull(content.getDefinitionSnapshot());
    }

    private TemplateDefinitionContent content() {
        var content = new TemplateDefinitionContent();
        var stage = new TemplateDefinitionContent.StageDef(); stage.setStageCode("S0"); content.setStages(List.of(stage));
        var task = new TemplateDefinitionContent.TaskDef(); task.setTaskCode("T1"); content.setTasks(List.of(task));
        var milestone = new TemplateDefinitionContent.MilestoneDef(); milestone.setMilestoneCode("M1"); content.setMilestones(List.of(milestone));
        var deliverable = new TemplateDefinitionContent.DeliverableDef(); deliverable.setDeliverableCode("D1"); content.setDeliverables(List.of(deliverable));
        return content;
    }
    private Snapshot snapshot(DeliveryDefinitionKind kind, String payload) {
        return new Snapshot(new Revision(90L, kind, "RULE", 1L, "PUBLISHED", 1,
                JsonUtils.parseObject(payload, JsonNode.class), List.of(), LocalDateTime.of(2026,9,8,0,0), null, 0), null);
    }
    private String rule(String predicate, String code) {
        return "{\"predicate\":\"" + predicate + "\",\"parameters\":{\"refCode\":\"" + code + "\"}}";
    }
}
