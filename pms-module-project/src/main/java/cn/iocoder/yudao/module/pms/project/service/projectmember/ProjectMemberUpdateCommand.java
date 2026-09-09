package cn.iocoder.yudao.module.pms.project.service.projectmember;

import java.util.Set;

/** PM-01：一个用户意图，可同时调整两类经理，不替换未提交成员。 */
public record ProjectMemberUpdateCommand(Long projectId, Integer expectedVersion,
        ServiceManager serviceManager, Set<Long> addUserIds, Set<Long> removeUserIds,
        Long primaryUserId, String reason, String idempotencyKey) {
    public ProjectMemberUpdateCommand {
        addUserIds = addUserIds == null ? Set.of() : Set.copyOf(addUserIds);
        removeUserIds = removeUserIds == null ? Set.of() : Set.copyOf(removeUserIds);
    }
    public record ServiceManager(String levelCode, Long managerId, Long siteId,
            String assignmentType, Long departmentId, String departmentCode) { }
}
