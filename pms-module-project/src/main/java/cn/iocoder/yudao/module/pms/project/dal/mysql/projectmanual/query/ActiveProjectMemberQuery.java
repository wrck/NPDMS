package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

public record ActiveProjectMemberQuery(Long tenantId, Long userId, LocalDateTime effectiveAt) {
}
