package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** 已冻结的项目任务执行契约事实。PRE-02解析字段在其他受控目标下为空。 */
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
        String itemConfigurationSnapshot,
        Long templateRevisionId,
        Integer templateRevisionNo,
        String bindingParameterSnapshot) {

    /** 保持PRE-02既有测试与调用方构造兼容。 */
    public ProjectWorkBindingFact(
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
        this(projectId, projectVersion, projectTaskId, projectTaskVersion, executionContractId,
                contractVersion, templateTaskDefinitionId, sourceDefinitionVersion, workBindingTypeCode,
                targetContextCode, targetObjectType, targetObjectKey, preparationTemplateCode,
                preparationTemplateRevision, fixedFormCatalogVersion, itemConfigurationSnapshot,
                null, null, null);
    }
}
