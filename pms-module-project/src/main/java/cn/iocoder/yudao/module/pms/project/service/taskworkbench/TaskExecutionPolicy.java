package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import java.util.Collection;
import java.util.Objects;

/** 成员必须来自当前租户、当前用户、当前生效区间的受信查询；不替代功能权限、项目范围或状态校验。 */
public final class TaskExecutionPolicy {
    private TaskExecutionPolicy() { }

    /** 第08项补充裁决：只取消未指定任务的指派前置，仍使用本任务冻结修订中的开始定义。 */
    public static String transitionSource(String status, String action, boolean designated) {
        return !designated && "PENDING_ASSIGN".equals(status) && "START".equals(action)
                ? "PENDING_START" : status;
    }
    public static boolean isProjectMember(Long projectId, Collection<ProjectMemberAssignmentDO> memberships) {
        return memberships.stream().anyMatch(member -> Objects.equals(projectId, member.getProjectId())
                && member.getMemberRole() != null && ProjectMemberRoles.TASK_EXECUTION_CODES.contains(member.getMemberRole()));
    }
    public static boolean permits(Long projectId, Long actorId, ProjectTaskAssignmentDO designated,
                                   Collection<ProjectMemberAssignmentDO> memberships) {
        return actorId != null && (designated == null ? isProjectMember(projectId, memberships)
                : Objects.equals(actorId, designated.getAssigneeUserId()));
    }
}
