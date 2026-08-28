package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 查询指定受信租户在服务端时点生效的最新已发布任务状态机版本。
 */
@Value
@Builder
public class TaskStateMachinePublishedQuery {

    Long tenantId;
    LocalDateTime effectiveAt;
}
