package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

/** 结束项目当前有效服务经理责任区间的更新参数。 */
public record ProjectServiceManagerIntervalClose(
        Long tenantId,
        Long projectId,
        LocalDateTime closedAt,
        String updater) {
}
