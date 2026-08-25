package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 冻结状态机版本及其迁移定义。 */
public record TaskStateMachineDefinition(
        TaskStateMachineRevisionDO revision,
        List<TaskStateTransitionDO> transitions) {

    private static final Set<String> CORE_STATUSES = Set.of(
            "PENDING_ASSIGN", "PENDING_START", "IN_PROGRESS", "PENDING_ACCEPT", "DONE", "CLOSED");
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION",
            "CURRENT_EFFECTIVE_ASSIGNEE",
            "CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER");
    private static final Set<String> CONDITION_KEYS = Set.of(
            "schemaVersion", "permissionCode", "currentAssignmentRequired", "actualStartTimeRequired",
            "progress", "completionRuleSatisfied", "reasonRequired");
    private static final Map<String, String> CORE_TRANSITIONS = Map.ofEntries(
            Map.entry("PENDING_ASSIGN|ASSIGN", "PENDING_START"),
            Map.entry("PENDING_START|START", "IN_PROGRESS"),
            Map.entry("IN_PROGRESS|SUBMIT", "PENDING_ACCEPT"),
            Map.entry("PENDING_ACCEPT|COMPLETE", "DONE"),
            Map.entry("PENDING_ASSIGN|CANCEL", "CLOSED"),
            Map.entry("PENDING_START|CANCEL", "CLOSED"),
            Map.entry("IN_PROGRESS|CANCEL", "CLOSED"),
            Map.entry("PENDING_ACCEPT|CANCEL", "CLOSED"));

    public void validateForPublish() {
        if (revision == null || transitions == null || transitions.isEmpty()) {
            throw new IllegalArgumentException("状态机版本及迁移不能为空");
        }
        Set<String> uniqueActions = new HashSet<>();
        Map<String, String> actualCoreTransitions = new java.util.HashMap<>();
        for (TaskStateTransitionDO transition : transitions) {
            String key = transition.getFromStatusCode() + "|" + transition.getActionCode();
            if (!uniqueActions.add(key)) {
                throw new IllegalArgumentException("同一来源状态和动作必须唯一：" + key);
            }
            if (!CORE_STATUSES.contains(transition.getStandardStatusMapping())) {
                throw new IllegalArgumentException("未知标准状态映射：" + transition.getStandardStatusMapping());
            }
            if (!ALLOWED_ROLES.contains(transition.getAllowedRoleCode())) {
                throw new IllegalArgumentException("未知适用主体约束：" + transition.getAllowedRoleCode());
            }
            validateCondition(transition.getEntryCondition());
            validateCondition(transition.getExitCondition());
            if (CORE_TRANSITIONS.containsKey(key)) {
                String expectedTarget = CORE_TRANSITIONS.get(key);
                if (!expectedTarget.equals(transition.getToStatusCode())
                        || !expectedTarget.equals(transition.getStandardStatusMapping())) {
                    throw new IllegalArgumentException("核心状态迁移缺失或改义：" + key);
                }
                actualCoreTransitions.put(key, transition.getToStatusCode());
            }
        }
        if (!CORE_TRANSITIONS.equals(actualCoreTransitions)) {
            throw new IllegalArgumentException("核心状态迁移缺失或改义");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateCondition(String json) {
        Map<String, Object> condition;
        try {
            condition = JsonUtils.parseObject(json, Map.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("状态条件不是合法JSON对象", exception);
        }
        if (condition == null || !Integer.valueOf(1).equals(condition.get("schemaVersion"))
                || !CONDITION_KEYS.containsAll(condition.keySet())) {
            throw new IllegalArgumentException("未知状态条件");
        }
    }
}
