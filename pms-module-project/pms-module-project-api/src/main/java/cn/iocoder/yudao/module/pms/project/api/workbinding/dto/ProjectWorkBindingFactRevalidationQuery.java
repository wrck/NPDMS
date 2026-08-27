package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** SOL写入前对同一冻结WorkBinding执行锁定重验；tenantId只取受信上下文。 */
public record ProjectWorkBindingFactRevalidationQuery(
        Long projectId,
        Long projectTaskId,
        Long executionContractId,
        Integer expectedProjectTaskVersion,
        Integer expectedContractVersion,
        Integer expectedProjectVersion,
        ProjectWorkBindingTarget target) {

    /** 保持PRE-02既有调用兼容。 */
    public ProjectWorkBindingFactRevalidationQuery(
            Long projectId,
            Long projectTaskId,
            Long executionContractId,
            Integer expectedProjectTaskVersion,
            Integer expectedContractVersion,
            Integer expectedProjectVersion) {
        this(projectId, projectTaskId, executionContractId, expectedProjectTaskVersion,
                expectedContractVersion, expectedProjectVersion,
                ProjectWorkBindingTarget.SITE_SURVEY_PREPARATION);
    }
}
