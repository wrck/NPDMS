package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

/** Real Owner adapter. It is not a reflective URL/bean proxy and does not implement a second state machine. */
public interface ProjectBusinessOperationCommandAdapter {
    boolean supports(String operationCode, int operationVersion);
    /** Recheck current functional permission and business data scope, without requiring the old object state. */
    void authorizeReplay(String operationCode, ProjectOperationCommand command);
    /** Called only inside the controlled transaction, after project PRE and current Owner capability checks. */
    ProjectOperationResult invoke(String operationCode, ProjectOperationCommand command);
}
