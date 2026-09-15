package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only adapter from creation facts to the version-pinned execution program. */
@Component
public class ProjectTemplateMatchRuleEvaluator {
    private static final Set<String> CREATION_FIELDS = ProjectRuleFields.catalog().stream()
            .filter(ProjectRuleFields.Field::availableAtCreation).map(ProjectRuleFields.Field::code)
            .collect(Collectors.toUnmodifiableSet());
    private final ProjectRuleEvaluationService evaluator;
    private final ProjectDecisionTableService decisions;
    private final RuleProgram unrestricted;

    public ProjectTemplateMatchRuleEvaluator(ProjectRuleCompiler compiler, ProjectRuleEvaluationService evaluator,
                                             ProjectDecisionTableService decisions) {
        this.evaluator = evaluator;
        this.decisions = decisions;
        this.unrestricted = compiler.compile(JsonUtils.parseObject(
                "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}", JsonNode.class));
    }

    public RuleEvaluation evaluate(Long tenantId, Long revisionId, TemplateExecutionSnapshot snapshot,
                                   Map<String, RuleFact> facts) {
        String key = snapshot.getMatchRuleKey();
        String reference = "tenant:" + tenantId + ":template-revision:" + revisionId + ":match:"
                + (key == null || key.isBlank() ? "unrestricted" : key);
        RuleProgram program = key == null || key.isBlank() ? unrestricted
                : snapshot.getRulePrograms() == null ? null : snapshot.getRulePrograms().get(key);
        // Do not recompile from editable/latest definitions or fall back to the old four dimensions.
        if (program == null || program.kind() != VersionRule.Kind.CONDITION)
            return new RuleEvaluation(reference, RuleEvaluation.Outcome.UNKNOWN, "MATCH_PROGRAM_UNAVAILABLE",
                    List.of(), List.of(), List.of());
        return evaluator.evaluate(reference, program, leaf -> switch (leaf.predicate()) {
            case "FIELD" -> read(facts, leaf.parameters().path("fieldCode").asText());
            case "DECISION" -> decisions.resolve(tenantId, reference, leaf, code -> read(facts, code));
            default -> RuleFact.unknown("MATCH_REQUIRES_CREATION_FACTS");
        });
    }

    private RuleFact read(Map<String, RuleFact> facts, String code) {
        if (!CREATION_FIELDS.contains(code)) return RuleFact.unknown("MATCH_FIELD_NOT_AVAILABLE_AT_CREATION");
        RuleFact fact = facts.get(code);
        return fact == null ? RuleFact.unknown("MATCH_FIELD_UNAVAILABLE") : fact;
    }
}
