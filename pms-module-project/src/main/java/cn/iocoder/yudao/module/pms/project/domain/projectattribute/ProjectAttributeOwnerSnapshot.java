package cn.iocoder.yudao.module.pms.project.domain.projectattribute;

/** 四属性在本次判定中的Owner快照。 */
public record ProjectAttributeOwnerSnapshot(
        String signingMethodOwner,
        String projectCategoryOwner,
        String implementationModeOwner,
        String majorProjectLevelOwner) {

    public static ProjectAttributeOwnerSnapshot manualProject() {
        return new ProjectAttributeOwnerSnapshot("PROJ_MANUAL", "PROJ", "PROJ_MANUAL", "CRM");
    }

    public static ProjectAttributeOwnerSnapshot classification(boolean manualSource) {
        return manualSource ? manualProject() : sourceCorrection();
    }

    public static ProjectAttributeOwnerSnapshot sourceCorrection() {
        return new ProjectAttributeOwnerSnapshot("CRM", "PROJ", "CRM", "CRM");
    }
}
