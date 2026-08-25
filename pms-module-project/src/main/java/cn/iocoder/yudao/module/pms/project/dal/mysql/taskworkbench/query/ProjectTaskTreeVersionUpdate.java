package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 项目任务树版本水位CAS参数。 */
public record ProjectTaskTreeVersionUpdate(
        Long tenantId,
        Long projectId,
        Long expectedTaskTreeVersion,
        String updater) {
}
