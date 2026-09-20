package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;
import java.time.LocalDate;
/** A version-checked project-owned date update, never a lifecycle transition. */
public record ProjectNodeScheduleUpdate(Long tenantId, Long projectId, Long nodeId,
        Integer expectedVersion, LocalDate start, LocalDate end) { }
