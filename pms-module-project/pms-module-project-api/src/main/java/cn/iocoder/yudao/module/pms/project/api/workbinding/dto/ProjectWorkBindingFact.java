package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** 已冻结的项目节点办理契约。任务与阶段身份互斥；PRE-02解析字段在其他受控目标下为空。 */
public record ProjectWorkBindingFact(
        Long projectId,
        Integer projectVersion,
        Long projectTaskId,
        Integer projectTaskVersion,
        Long executionContractId,
        Integer contractVersion,
        Long projectTemplateId,
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
        String bindingParameterSnapshot,
        Long dynamicFormTemplateId,
        Long dynamicFormTemplateRevisionId,
        Integer dynamicFormRevisionNo,
        Integer dynamicFormRevisionFactVersion,
        Long projectStageId,
        Integer projectStageVersion) {

    /** 任务办理契约；不以任务身份承载阶段。 */
    public ProjectWorkBindingFact(Long projectId, Integer projectVersion, Long projectTaskId, Integer projectTaskVersion,
            Long executionContractId, Integer contractVersion, Long projectTemplateId, Integer sourceDefinitionVersion,
            String workBindingTypeCode, String targetContextCode, String targetObjectType, String targetObjectKey,
            String preparationTemplateCode, Integer preparationTemplateRevision, Integer fixedFormCatalogVersion,
            String itemConfigurationSnapshot, Long templateRevisionId, Integer templateRevisionNo,
            String bindingParameterSnapshot, Long dynamicFormTemplateId, Long dynamicFormTemplateRevisionId,
            Integer dynamicFormRevisionNo, Integer dynamicFormRevisionFactVersion) {
        this(projectId, projectVersion, projectTaskId, projectTaskVersion, executionContractId, contractVersion,
                projectTemplateId, sourceDefinitionVersion, workBindingTypeCode, targetContextCode, targetObjectType,
                targetObjectKey, preparationTemplateCode, preparationTemplateRevision, fixedFormCatalogVersion,
                itemConfigurationSnapshot, templateRevisionId, templateRevisionNo, bindingParameterSnapshot,
                dynamicFormTemplateId, dynamicFormTemplateRevisionId, dynamicFormRevisionNo, dynamicFormRevisionFactVersion, null, null);
    }

    /** 无动态表单的冻结绑定事实。 */
    public ProjectWorkBindingFact(
            Long projectId,
            Integer projectVersion,
            Long projectTaskId,
            Integer projectTaskVersion,
            Long executionContractId,
            Integer contractVersion,
            Long projectTemplateId,
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
        this(projectId, projectVersion, projectTaskId, projectTaskVersion, executionContractId,
                contractVersion, projectTemplateId, sourceDefinitionVersion, workBindingTypeCode,
                targetContextCode, targetObjectType, targetObjectKey, preparationTemplateCode,
                preparationTemplateRevision, fixedFormCatalogVersion, itemConfigurationSnapshot,
                templateRevisionId, templateRevisionNo, bindingParameterSnapshot, null, null, null, null);
    }

    /** 准备项绑定事实。 */
    public ProjectWorkBindingFact(
            Long projectId,
            Integer projectVersion,
            Long projectTaskId,
            Integer projectTaskVersion,
            Long executionContractId,
            Integer contractVersion,
            Long projectTemplateId,
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
                contractVersion, projectTemplateId, sourceDefinitionVersion, workBindingTypeCode,
                targetContextCode, targetObjectType, targetObjectKey, preparationTemplateCode,
                preparationTemplateRevision, fixedFormCatalogVersion, itemConfigurationSnapshot,
                null, null, null, null, null, null, null);
    }
}
