package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

/** ProjectTask与当前ExecutionContract的精确只读投影。 */
public record ProjectWorkBindingFactRecord(
        Long tenantId,
        Long projectId,
        Integer projectVersion,
        Long projectTaskId,
        Integer projectTaskVersion,
        Long sourceDefinitionId,
        Long executionContractId,
        Long templateTaskDefinitionId,
        String workBindingTypeCode,
        String targetContextCode,
        String targetObjectType,
        String targetObjectKey,
        String bindingParameterSnapshot,
        Integer sourceDefinitionVersion,
        Integer contractVersion) {
}
