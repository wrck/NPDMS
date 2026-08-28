package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 按受信租户与模板任务定义稳定键查询发布版本事实。 */
public record ProjectTemplateRevisionFactQuery(Long tenantId, Long templateTaskDefinitionId) {
}
