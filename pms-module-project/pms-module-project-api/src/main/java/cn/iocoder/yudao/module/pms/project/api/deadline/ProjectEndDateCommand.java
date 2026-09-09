package cn.iocoder.yudao.module.pms.project.api.deadline;

import java.time.LocalDate;

public record ProjectEndDateCommand(Long tenantId, Long actorUserId, Long projectId,
                                    Integer expectedProjectVersion, LocalDate endDate) {
}
