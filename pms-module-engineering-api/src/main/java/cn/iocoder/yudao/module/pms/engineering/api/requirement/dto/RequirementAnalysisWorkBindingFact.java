package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

/** PRE-04事实冻结的PROJ WorkBinding身份与版本轴。 */
public record RequirementAnalysisWorkBindingFact(
        Long projectTaskId,
        Integer projectTaskVersion,
        Long executionContractId,
        Integer executionContractVersion,
        Long templateTaskDefinitionId,
        Integer sourceDefinitionVersion,
        Long projectTemplateRevisionId,
        Integer projectTemplateRevisionNo,
        Long dynamicFormTemplateId,
        Long dynamicFormTemplateRevisionId,
        Integer dynamicFormRevisionNo,
        Integer dynamicFormRevisionFactVersion,
        String workBindingTypeCode,
        String targetContextCode,
        String targetObjectType,
        String targetObjectKey) {
}
