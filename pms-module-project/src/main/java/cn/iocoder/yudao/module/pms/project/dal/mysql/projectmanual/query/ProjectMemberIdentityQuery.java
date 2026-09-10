package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

public record ProjectMemberIdentityQuery(Long tenantId, Long projectId, Long userId,
                                         String memberRole, LocalDateTime effectiveAt) { }
