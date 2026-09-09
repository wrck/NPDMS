package cn.iocoder.yudao.module.pms.project.service.projectmember;

import java.time.LocalDateTime;

/** PM-01：projectManagers.version始终为整次操作的最终版本。 */
public record ProjectMemberUpdateResult(ServiceManager serviceManager,
        ProjectManagerMemberResult projectManagers) {
    public record ServiceManager(Long assignmentId, LocalDateTime effectiveFrom,
            Long previousPrimaryManagerId, Long currentPrimaryManagerId) { }
}
