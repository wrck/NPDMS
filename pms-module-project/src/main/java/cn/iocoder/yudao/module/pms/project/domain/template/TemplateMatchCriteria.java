package cn.iocoder.yudao.module.pms.project.domain.template;

/** PM-03 四维模板匹配输入；四个编码互相独立。 */
public record TemplateMatchCriteria(
        String signingMethodCode,
        String projectCategoryCode,
        String implementationModeCode,
        String majorProjectLevelCode,
        String businessSceneCode,
        long customerId,
        long officeId,
        long implementationLocationId) {
}
