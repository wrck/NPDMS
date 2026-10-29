package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;
import java.time.LocalDateTime;
public record DurationProjectManagerQuery(Long tenantId, Long projectId, Long userId, LocalDateTime effectiveAt) { }
