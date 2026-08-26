package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** 已冻结的PRE-02项目任务执行契约事实。 */
public record ProjectWorkBindingFact(
        Long projectId,
        Integer projectVersion,
        Long projectTaskId,
        Integer projectTaskVersion,
        Long executionContractId,
        Integer contractVersion,
        Long templateTaskDefinitionId,
        Integer sourceDefinitionVersion,
        String workBindingTypeCode,
        String targetContextCode,
        String targetObjectType,
        String targetObjectKey,
        String preparationTemplateCode,
        Integer preparationTemplateRevision,
        Integer fixedFormCatalogVersion,
        String itemConfigurationSnapshot) {
}
