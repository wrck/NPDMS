package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection;

import lombok.Data;
import java.time.LocalDateTime;

/** Scope-limited facts consumed by the existing project status presentation. */
@Data
public class ProjectOverdueNodeFact {
    private Long projectId;
    private String nodeKind;
    private Long nodeId;
    private String nodeName;
    private LocalDateTime planEndTime;
    private LocalDateTime acceptanceTime;
    private LocalDateTime completedAt;
    private Boolean workOverdue;
    private Boolean acceptanceOverdue;
}
