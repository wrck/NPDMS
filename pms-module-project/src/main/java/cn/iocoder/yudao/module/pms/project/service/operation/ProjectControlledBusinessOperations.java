package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Explicit controlled application boundary; original independent Owner APIs are not globally annotated. */
@Service
@RequiredArgsConstructor
public class ProjectControlledBusinessOperations {
    private final ProjectOperationAdapters adapters;
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.CREATE")
    public ProjectOperationResult surveyCreate(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.CREATE", command); }
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.UPDATE")
    public ProjectOperationResult surveyUpdate(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.UPDATE", command); }
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.DELETE")
    public ProjectOperationResult surveyDelete(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.DELETE", command); }
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.CONFIRM")
    public ProjectOperationResult surveyConfirm(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.CONFIRM", command); }
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.REJECT")
    public ProjectOperationResult surveyReject(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.REJECT", command); }
    @ProjectExecutionControlled(operation = "SOL.SITE_SURVEY.ARCHIVE")
    public ProjectOperationResult surveyArchive(ProjectOperationCommand command) { return adapters.invoke("SOL.SITE_SURVEY.ARCHIVE", command); }
    @ProjectExecutionControlled(operation = "SOL.REQUIREMENT_ANALYSIS.CREATE")
    public ProjectOperationResult analysisCreate(ProjectOperationCommand command) { return adapters.invoke("SOL.REQUIREMENT_ANALYSIS.CREATE", command); }
    @ProjectExecutionControlled(operation = "SOL.REQUIREMENT_ANALYSIS.SAVE")
    public ProjectOperationResult analysisSave(ProjectOperationCommand command) { return adapters.invoke("SOL.REQUIREMENT_ANALYSIS.SAVE", command); }
    @ProjectExecutionControlled(operation = "SOL.REQUIREMENT_ANALYSIS.COMPLETE")
    public ProjectOperationResult analysisComplete(ProjectOperationCommand command) { return adapters.invoke("SOL.REQUIREMENT_ANALYSIS.COMPLETE", command); }
    @ProjectExecutionControlled(operation = "SOL.REQUIREMENT_ANALYSIS.COPY")
    public ProjectOperationResult analysisCopy(ProjectOperationCommand command) { return adapters.invoke("SOL.REQUIREMENT_ANALYSIS.COPY", command); }
    @ProjectExecutionControlled(operation = "ACC.ACCEPTANCE_REPORT.CREATE_DRAFT")
    public ProjectOperationResult reportCreate(ProjectOperationCommand command) { return adapters.invoke("ACC.ACCEPTANCE_REPORT.CREATE_DRAFT", command); }
    @ProjectExecutionControlled(operation = "ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT")
    public ProjectOperationResult reportUpdate(ProjectOperationCommand command) { return adapters.invoke("ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT", command); }
    @ProjectExecutionControlled(operation = "ACC.ACCEPTANCE_REPORT.PUBLISH")
    public ProjectOperationResult reportPublish(ProjectOperationCommand command) { return adapters.invoke("ACC.ACCEPTANCE_REPORT.PUBLISH", command); }
    @ProjectExecutionControlled(operation = "ACC.ACCEPTANCE_REPORT.REVOKE")
    public ProjectOperationResult reportRevoke(ProjectOperationCommand command) { return adapters.invoke("ACC.ACCEPTANCE_REPORT.REVOKE", command); }
}
