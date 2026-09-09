package cn.iocoder.yudao.module.pms.project.service.projectmember;

import java.util.Set;

/** PM-01：显式增删，不用集合替换暗中删除未提交的成员。 */
public record ProjectManagerMemberCommand(Long projectId, Integer expectedVersion,
                                         Set<Long> addUserIds, Set<Long> removeUserIds,
                                         Long primaryUserId, String reason, String idempotencyKey) {
    public ProjectManagerMemberCommand {
        addUserIds = addUserIds == null ? Set.of() : Set.copyOf(addUserIds);
        removeUserIds = removeUserIds == null ? Set.of() : Set.copyOf(removeUserIds);
    }
}
