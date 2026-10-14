package cn.iocoder.yudao.module.pms.cutover.service.taskv2.query;

import lombok.Builder;

@Builder
public record CutoverWorkbenchTaskListQuery(ListKind listKind, Long projectId, String currentStage,
                                           String taskStatus, int pageNo, int pageSize) {
    public enum ListKind { TODO, SELF_CREATED }
}
