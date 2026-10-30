package cn.iocoder.yudao.module.pms.project.api.rule;

import java.util.List;
import java.util.Map;

/** Owner access to field conditions in the project's exact published template. */
public interface ProjectFieldRuleApi {
    Evaluation evaluate(Query query);
    Evaluation lockAndEvaluate(Query query);
    record Query(Long tenantId, Long actorId, Long projectId, List<String> ruleKeys) { }
    record Evaluation(boolean configured, Map<String, String> outcomes, String evidenceJson) { }
}
