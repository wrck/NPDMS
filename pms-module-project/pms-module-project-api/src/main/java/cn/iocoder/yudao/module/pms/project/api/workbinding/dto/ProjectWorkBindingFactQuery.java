package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** 冻结WorkBinding查询；tenantId由PROJ受信边界固定，目标仅允许公开受控四元组。 */
public record ProjectWorkBindingFactQuery(Long projectId, ProjectWorkBindingTarget target) {

    /** 保持PRE-02既有调用兼容。 */
    public ProjectWorkBindingFactQuery(Long projectId) {
        this(projectId, ProjectWorkBindingTarget.SITE_SURVEY_PREPARATION);
    }
}
