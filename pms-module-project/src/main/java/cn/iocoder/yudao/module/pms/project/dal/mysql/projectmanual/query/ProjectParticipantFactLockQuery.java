package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import lombok.Builder;
import java.util.Set;

/** 项目行锁之后的当前参与人锁定查询。 */
@Builder
public record ProjectParticipantFactLockQuery(
        Long tenantId,
        Long projectId,
        Long userId,
        Set<String> requiredRoleCodes) {
}
