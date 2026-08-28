package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

public record NewTaskTreePathInsert(Long tenantId, Long projectId, Long taskId,
                                    Long parentTaskId, String creator) {
}
