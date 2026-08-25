package cn.iocoder.yudao.module.pms.project.domain.projectattribute;

/** 参与模板匹配的四个独立项目业务属性。 */
public record ProjectAttributeSnapshot(
        String signingMethod,
        String projectCategory,
        String implementationMode,
        String majorProjectLevel) {
}
