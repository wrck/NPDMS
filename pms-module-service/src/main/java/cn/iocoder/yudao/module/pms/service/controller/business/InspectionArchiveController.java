package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}/actions")
public class InspectionArchiveController {
    private final InspectionArchiveService archives;
    private final InspectionCommandExecutor commands;
    public record Description(@NotBlank @Size(max = 2000) String description) { }
    private record Input(Long version, Description body) { }
    private record Write(InspectionRequestContext context, Long taskId, Description input, boolean finalArchive) { }
    @PostMapping("/request-archive")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:request-archive')")
    public CommonResult<InspectionViews.TaskSummary> request(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                           @Valid @RequestBody Description input) {
        return execute(new Write(InspectionRequestContext.write(headers), taskId, input, false));
    }
    @PostMapping("/archive")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:archive')")
    public CommonResult<InspectionViews.TaskSummary> archive(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                           @Valid @RequestBody Description input) {
        return execute(new Write(InspectionRequestContext.write(headers), taskId, input, true));
    }
    private CommonResult<InspectionViews.TaskSummary> execute(Write write) {
        var command = new InspectionArchiveService.Command(write.context(), write.taskId(), write.input().description());
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(write.context()).scope(write.taskId().toString()).operation(write.finalArchive() ? "ARCHIVE" : "REQUEST_ARCHIVE")
                .input(new Input(write.context().requireVersion(), write.input())).responseType(InspectionViews.TaskSummary.class)
                .action(() -> write.finalArchive() ? archives.archive(command) : archives.request(command))
                .resourceKey(result -> result.getId().toString()).build()));
    }
}
