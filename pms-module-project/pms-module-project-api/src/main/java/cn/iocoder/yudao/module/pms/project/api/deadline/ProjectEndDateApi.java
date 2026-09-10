package cn.iocoder.yudao.module.pms.project.api.deadline;

/** Project-owned required finish date, supplied by site survey and consumed by backward planning. */
public interface ProjectEndDateApi {
    void updateFromSurvey(ProjectEndDateCommand command);

    /** Validate the proposed duration against the survey-owned deadline; never writes a plan back to Project. */
    void validatePlanningEndDate(ProjectEndDateCommand command);
}
