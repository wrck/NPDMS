package cn.iocoder.yudao.module.pms.project.domain.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

/** Project contact existence is an Owner result, not a native manual submission. */
public final class ProjectContactCompletionCondition {
    public static final String PREDICATE = "PROJECT_CONTACT_EXISTS";
    public static final String ROUTE = "/customer-asset/customer-contact";
    private ProjectContactCompletionCondition() { }
    public static boolean contactPage(String type, String parameters) {
        return "PAGE".equals(type) && parameters != null
                && ROUTE.equals(JsonUtils.parseTree(parameters).path("routePath").asText(null));
    }
    public static boolean automatic(RuleProgram program) {
        // Only this exact completion predicate replaces submission. Boolean combinations cannot waive it.
        return program != null && program.leaves().size() == 1 && PREDICATE.equals(program.leaves().getFirst().predicate())
                && "rule".equals(program.leaves().getFirst().path());
    }
}
