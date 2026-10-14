package cn.iocoder.yudao.module.pms.cutover.service.taskv2.query;

/** 割接任务列表的业务筛选；租户和数据范围由服务端上下文解析。 */
public record CutoverTaskListQuery(Long projectId, String taskStatus, String currentStage,
                                  int pageNo, int pageSize) {
}
