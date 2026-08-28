package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;

/** 状态机草稿发布 CAS。 */
public record TaskStateMachinePublishUpdate(
        Long tenantId,
        Long revisionId,
        Integer expectedVersion,
        Long publishedBy,
        LocalDateTime publishedAt,
        String updater) {
}
