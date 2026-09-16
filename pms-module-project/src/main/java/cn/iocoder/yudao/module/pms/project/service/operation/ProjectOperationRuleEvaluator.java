package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContract;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Shared interpretation of S2's frozen synchronous operation rules, without project state writes. */
@Component
@RequiredArgsConstructor
public class ProjectOperationRuleEvaluator {
    private final ProjectRuleEvaluationService rules;
    private final ProjectDecisionTableService decisions;

    public record Evaluation(String outcome, String reason) {
        public boolean permits() { return "MATCHED".equals(outcome) || "NO_ADDITIONAL_RULE".equals(outcome); }
        public static Evaluation unknown(String reason) { return new Evaluation("UNKNOWN", reason); }
        public static Evaluation pending() { return new Evaluation("NOT_EVALUATED", "OPERATION_RESULT_NOT_AVAILABLE"); }
    }

    public Evaluation evaluate(String reference, FrozenOperationContract contract, TemplateOperationContract.Check check,
            ProjectMasterDO project) {
        if (check == null) return Evaluation.unknown("OPERATION_CHECK_MISSING");
        if ("NONE".equals(check.mode()) && check.ruleKey() == null)
            return new Evaluation("NO_ADDITIONAL_RULE", null);
        var program = contract.programs().get(check.ruleKey());
        if (!"RULE".equals(check.mode()) || program == null) return Evaluation.unknown("OPERATION_RULE_MISSING");
        try {
            var result = rules.evaluate(reference + ":rule:" + check.ruleKey(), program, leaf -> {
                return switch (leaf.predicate()) {
                    case "CONSTANT" -> RuleFact.known(leaf.parameters().path("value").asBoolean());
                    case "FIELD" -> ProjectRuleFields.read(project, leaf.parameters().path("fieldCode").asText());
                    case "DECISION" -> decisions.resolve(project.getTenantId(), reference, leaf,
                            code -> ProjectRuleFields.read(project, code));
                    case "TIME_REACHED" -> AbsoluteTimeCondition.evaluate(leaf.parameters(), Instant.now());
                    default -> RuleFact.unknown("OPERATION_FACT_UNSUPPORTED");
                };
            });
            return new Evaluation(result.outcome().name(), result.reasonCode());
        } catch (RuntimeException unavailable) {
            return Evaluation.unknown("OPERATION_RULE_UNAVAILABLE");
        }
    }
}
