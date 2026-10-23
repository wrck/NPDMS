package cn.iocoder.yudao.module.pms.project.domain.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

/** 联调PAGE可将对应交付件作为唯一完成来源；旧手工提交规则仍按原语义执行。 */
public final class JointTestDeliverableCompletionCondition {
    private JointTestDeliverableCompletionCondition() { }

    public static boolean jointTestPage(String type, String parameters) {
        return "PAGE".equals(type) && parameters != null
                && "/pms/engineering/execution/imp-joint-test"
                        .equals(JsonUtils.parseTree(parameters).path("routePath").asText(null));
    }

    public static boolean automatic(RuleProgram program) {
        return program != null && program.leaves().size() == 1
                && "DELIVERABLE".equals(program.leaves().getFirst().predicate())
                && "rule".equals(program.leaves().getFirst().path());
    }
}
