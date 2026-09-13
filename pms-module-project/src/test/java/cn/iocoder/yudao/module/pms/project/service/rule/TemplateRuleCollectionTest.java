package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRuleCollectionTest {
    @Test void sidebarRulesBecomeIndependentVersionLocalReferencesWithoutMutatingTheInput() {
        var source = document();
        for (var node : source.getStages()) node.setCompletionRule(inline(true));
        var normalized = TemplateRuleCollection.forEditing(source);
        assertEquals(2, normalized.getRules().size());
        assertNotEquals(normalized.getStages().get(0).getCompletionRuleKey(), normalized.getStages().get(1).getCompletionRuleKey());
        assertNull(normalized.getStages().getFirst().getCompletionRule());
        assertNotNull(source.getStages().getFirst().getCompletionRule());
        assertTrue(source.getRules().isEmpty());
        var frozen = TemplateRuleCollection.forCompilation(normalized);
        assertTrue(frozen.getStages().getFirst().getCompletionRule().getExpression().path("parameters").path("value").asBoolean());
        assertNull(normalized.getStages().getFirst().getCompletionRule());
    }

    @Test void sharedRulesRequireAnExplicitDeclarationAndMissingKeysNeverFindLatestElsewhere() {
        var source = document();
        source.setRules(new ArrayList<>(List.of(new VersionRule("shared", "共同条件", VersionRule.Kind.CONDITION, false, expression(true), null))));
        source.getStages().forEach(node -> node.setCompletionRuleKey("shared"));
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
        source.setRules(List.of(new VersionRule("shared", "共同条件", VersionRule.Kind.CONDITION, true, expression(true), null)));
        assertEquals("shared", TemplateRuleCollection.forEditing(source).getStages().getFirst().getCompletionRuleKey());
        source.getStages().getFirst().setCompletionRuleKey("otherVersionRule");
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
    }

    @Test void twoConflictingEditableSourcesAreRejected() {
        var source = document();
        source.setRules(List.of(new VersionRule("rule", "完成", VersionRule.Kind.CONDITION, false, expression(false), null)));
        source.getStages().getFirst().setCompletionRuleKey("rule");
        source.getStages().getFirst().setCompletionRule(inline(true));
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
    }

    @Test void decisionOutputIsNotImplicitlyACheck() {
        var table = ProjectDecisionTableServiceTest.table("100", "true");
        var rules = TemplateRuleCollection.index(List.of(new VersionRule("strategy", "策略", VersionRule.Kind.DECISION, false, null, table)));
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.condition(rules, "strategy"));
    }

    private static TemplateDesignerDocument document() {
        var document = new TemplateDesignerDocument();
        var a = new TemplateDesignerDocument.StageNode(); a.setNodeKey("prepare"); a.setName("准备");
        var b = new TemplateDesignerDocument.StageNode(); b.setNodeKey("deliver"); b.setName("交付");
        document.setStages(new ArrayList<>(List.of(a, b)));
        return document;
    }

    private static TemplateDesignerDocument.RuleSpec inline(boolean value) {
        var rule = new TemplateDesignerDocument.RuleSpec(); rule.setExpression(expression(value)); return rule;
    }

    private static JsonNode expression(boolean value) {
        return JsonUtils.parseObject("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":" + value + "}}", JsonNode.class);
    }
}
