package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Builder;
import java.util.Set;

@Builder
public record CutoverWorkbenchTaskPageQuery(Long tenantId, Set<Long> visibleProjectIds, Set<Long> approvalTaskIds,
                                           Set<Long> taskIds, String intakeSourceType,
                                           Long projectId, String currentStage, String taskStatus,
                                           PageParam pageParam) {
    public CutoverWorkbenchTaskPageQuery {
        visibleProjectIds = Set.copyOf(visibleProjectIds);
        approvalTaskIds = approvalTaskIds == null ? Set.of() : Set.copyOf(approvalTaskIds);
        taskIds = taskIds == null ? null : Set.copyOf(taskIds);
    }
}
