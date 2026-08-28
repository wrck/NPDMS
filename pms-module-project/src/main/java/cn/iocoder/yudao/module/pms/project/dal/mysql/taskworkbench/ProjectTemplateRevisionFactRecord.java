package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

/** ExecutionContract来源模板任务定义及其冻结版本事实。 */
public record ProjectTemplateRevisionFactRecord(
        Long templateTaskDefinitionId,
        Long templateRevisionId,
        Integer templateRevisionNo) {
}
