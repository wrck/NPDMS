package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageSubmissionService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectExecutionHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/node-executions")
public class ProjectNodeExecutionController {
    private final ProjectStageSubmissionService service;
    private final ProjectExecutionHistoryService history;
    public record Submission(@NotNull Integer expectedVersion, @NotBlank @Size(max=2000) String note) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<ProjectStageSubmissionService.ExecutionView>> list(@PathVariable("projectId") Long projectId) {
        return success(service.list(projectId, SecurityFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<ProjectExecutionHistoryService.History> history(@PathVariable("projectId") Long projectId) {
        return success(history.get(projectId, SecurityFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/{executionId}/submission")
    @PreAuthorize("@ss.hasPermission('pms:project-task:execute')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectStageSubmissionService.Submitted> submit(@PathVariable("projectId") Long projectId,
            @PathVariable("executionId") Long executionId, @Valid @RequestBody Submission body,
            @RequestHeader("Idempotency-Key") String key) {
        try {
            return success(service.submit(new ProjectStageSubmissionService.Command(projectId, executionId, body.expectedVersion(), body.note()),
                    SecurityFrameworkUtils.getLoginUserId(), key));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException expected) {
            throw expected;
        } catch (RuntimeException failed) {
            // Do not let the global unexpected-error logger persist the free-text evidence request body.
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NODE_EXECUTION_FAILED);
        }
    }
}
