package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

import lombok.Builder;
import java.time.LocalDateTime;

/** 同一任务修订事务中的来源失效身份与审计事实。 */
@Builder
public record CutoverRevisionInvalidationUpdate(Long tenantId, Long taskId, Long sourceId, Long expectedVersion,
                                               Long actorId, LocalDateTime time, String reason,
                                               Long approvalVersion) { }
