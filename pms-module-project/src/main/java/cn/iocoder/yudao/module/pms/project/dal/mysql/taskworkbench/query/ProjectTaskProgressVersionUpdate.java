package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;

public record ProjectTaskProgressVersionUpdate(Long tenantId, Long projectId, Long expectedVersion,
                                               LocalDateTime occurredAt, String updater) {
}
