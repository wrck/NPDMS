package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import lombok.Builder;

import java.util.Set;

/** 项目任务树场景查询。 */
@Builder
public record ProjectTaskTreeQuery(
        Long tenantId,
        Set<Long> projectIds,
        Set<Long> visibleTaskIds,
        TaskVisibilityQuery visibilityQuery,
        Mode mode,
        Long parentTaskId,
        Long targetTaskId,
        String businessLevelCode,
        String keyword,
        Integer cursorSortOrder,
        Long cursorTaskId,
        Integer pageSize) {

    public enum Mode {
        DIRECT_CHILDREN,
        ALL_DESCENDANTS,
        ANCESTOR_CHAIN,
        BUSINESS_LEVEL,
        LOCATE
    }
}
