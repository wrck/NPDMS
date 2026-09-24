package cn.iocoder.yudao.module.pms.project.api.deadline;

/** Project-owned required finish date, supplied by site survey and consumed by backward planning. */
public interface ProjectEndDateApi {
    void updateFromSurvey(ProjectEndDateCommand command);

    /**
     * Validate a duration-entry actor and project state (manage scope, ACTIVE project, expected
     * version); the duration window itself may diverge from the survey deadline. Never writes a
     * plan back to Project.
     */
    void validatePlanningEndDate(ProjectEndDateCommand command);
}
