package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/**
 * Resolve the frozen template revision from Project identity.
 * templateTaskDefinitionId is the frozen task runtime node id for V2 and a legacy row id for old projects.
 */
public record ProjectTemplateRevisionFactQuery(
        Long tenantId,
        Long projectId,
        Long templateTaskDefinitionId) {
}
