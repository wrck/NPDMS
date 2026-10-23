package cn.iocoder.yudao.module.pms.cutover.service.task.query;

import lombok.Builder;

/** 割接任务列表的业务筛选；租户和数据范围由服务端上下文解析。 */
@Builder
public record CutoverTaskListQuery(ListKind listKind, Long projectId, String taskStatus, String currentStage,
                                  int pageNo, int pageSize) {
    public enum ListKind { ALL, TODO, SELF_CREATED, ARCHIVED, APPROVING, REJECTED }
}
