package cn.iocoder.yudao.module.pms.project.controller.admin.projectexecution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectControlledBusinessOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** The request cannot downgrade itself to independent mode. Owner and project authorizations run server-side. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/project-execution/operations")
public class ProjectOperationCommandController {
    private final ProjectControlledBusinessOperations operations;
    @PostMapping("/{operationCode}")
    public CommonResult<ProjectOperationResult> execute(@PathVariable String operationCode,
            @RequestHeader("Idempotency-Key") String key, @RequestBody ProjectOperationCommand body) {
        if (body == null) throw exception(BAD_REQUEST, "OPERATION_COMMAND_INVALID");
        var command = body.withKey(key);
        return success(switch (operationCode) {
            case "SOL.SITE_SURVEY.CREATE" -> operations.surveyCreate(command);
            case "SOL.SITE_SURVEY.UPDATE" -> operations.surveyUpdate(command);
            case "SOL.SITE_SURVEY.DELETE" -> operations.surveyDelete(command);
            case "SOL.SITE_SURVEY.CONFIRM" -> operations.surveyConfirm(command);
            case "SOL.SITE_SURVEY.REJECT" -> operations.surveyReject(command);
            case "SOL.SITE_SURVEY.ARCHIVE" -> operations.surveyArchive(command);
            case "SOL.REQUIREMENT_ANALYSIS.CREATE" -> operations.analysisCreate(command);
            case "SOL.REQUIREMENT_ANALYSIS.SAVE" -> operations.analysisSave(command);
            case "SOL.REQUIREMENT_ANALYSIS.COMPLETE" -> operations.analysisComplete(command);
            case "SOL.REQUIREMENT_ANALYSIS.COPY" -> operations.analysisCopy(command);
            case "ACC.ACCEPTANCE_REPORT.CREATE_DRAFT" -> operations.reportCreate(command);
            case "ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT" -> operations.reportUpdate(command);
            case "ACC.ACCEPTANCE_REPORT.PUBLISH" -> operations.reportPublish(command);
            case "ACC.ACCEPTANCE_REPORT.REVOKE" -> operations.reportRevoke(command);
            default -> throw exception(BAD_REQUEST, "OPERATION_NOT_REGISTERED");
        });
    }
}
