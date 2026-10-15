package cn.iocoder.yudao.module.pms.project.domain.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

/** 外部最终用户资料是PROJ成员事实，不代表系统账号或办理授权。 */
public final class ProjectEndUserCompletionCondition {
    public static final String PREDICATE = "PROJECT_END_USER_EXISTS";
    public static final String ROUTE = "/pms/project-management/project-master-detail";
    private ProjectEndUserCompletionCondition() { }

    public static boolean memberPage(String type, String parameters) {
        return "PAGE".equals(type) && parameters != null
                && ROUTE.equals(JsonUtils.parseTree(parameters).path("routePath").asText(null));
    }

    public static boolean automatic(RuleProgram program) {
        return program != null && program.leaves().size() == 1
                && PREDICATE.equals(program.leaves().getFirst().predicate())
                && "rule".equals(program.leaves().getFirst().path());
    }
}
