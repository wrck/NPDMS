package cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query;

import java.time.LocalDateTime;

public record TaskBusinessUnlinkUpdate(Long tenantId, Long projectId, Long taskId, Long linkId,
        Integer expectedVersion, Long actorId, LocalDateTime unlinkedAt, Long stageId) {
    public TaskBusinessUnlinkUpdate(Long tenantId, Long projectId, Long taskId, Long linkId,
            Integer expectedVersion, Long actorId, LocalDateTime unlinkedAt) {
        this(tenantId,projectId,taskId,linkId,expectedVersion,actorId,unlinkedAt,null);
    }
}
