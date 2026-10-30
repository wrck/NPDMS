package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationResult;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import java.util.List;
import java.util.Set;

/** Only explicit scalar fields of the Owner result are exposed; response payloads are never traversed. */
public final class ProjectOperationResultFields {
    private ProjectOperationResultFields() { }
    public static Set<String> codes() {
        return Set.of("transactionResult.resultCode", "transactionResult.objectId", "transactionResult.revisionId");
    }
    public static List<ProjectRuleFields.Field> catalog() {
        return List.of(new ProjectRuleFields.Field("transactionResult.resultCode", "本事务业务结果编码", "TEXT", false),
                new ProjectRuleFields.Field("transactionResult.objectId", "本事务业务对象标识", "TEXT", false),
                new ProjectRuleFields.Field("transactionResult.revisionId", "本事务业务修订标识", "TEXT", false));
    }
    public static RuleFact read(ProjectOperationResult result, String code) {
        if (!codes().contains(code)) return RuleFact.unknown("OPERATION_RESULT_FIELD_UNAVAILABLE");
        if (result == null) return RuleFact.unknown("OPERATION_RESULT_NOT_AVAILABLE");
        return RuleFact.known(switch (code) {
            case "transactionResult.resultCode" -> result.resultCode();
            case "transactionResult.objectId" -> result.objectId();
            case "transactionResult.revisionId" -> result.revisionId();
            default -> throw new IllegalArgumentException("OPERATION_RESULT_FIELD_UNAVAILABLE");
        });
    }
}
