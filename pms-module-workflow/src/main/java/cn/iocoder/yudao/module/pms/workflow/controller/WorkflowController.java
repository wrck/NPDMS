package cn.iocoder.yudao.module.pms.workflow.controller;

import cn.iocoder.yudao.module.pms.workflow.annotation.OperLog;
import cn.iocoder.yudao.module.pms.workflow.common.Result;
import cn.iocoder.yudao.module.pms.workflow.dto.CompleteTaskRequest;
import cn.iocoder.yudao.module.pms.workflow.dto.ProcessInstanceDTO;
import cn.iocoder.yudao.module.pms.workflow.dto.StartProcessRequest;
import cn.iocoder.yudao.module.pms.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for workflow management operations.
 */
@Tag(name = "工作流管理", description = "Workflow management APIs")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "部署流程定义")
    @PostMapping("/deploy")
    @PreAuthorize("@ss.hasPermission('workflow:definition:deploy')")
    @OperLog(title = "工作流管理", businessType = 1)
    public Result<Map<String, Object>> deploy(@RequestParam("file") MultipartFile file) {
        return workflowService.deployProcess(file);
    }

    @Operation(summary = "分页查询流程定义")
    @GetMapping("/definition/list")
    public Result<Map<String, Object>> listDefinitions(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return workflowService.listProcessDefinitions(page, size);
    }

    @Operation(summary = "删除部署")
    @DeleteMapping("/deployment/{deploymentId}")
    @PreAuthorize("@ss.hasPermission('workflow:definition:remove')")
    @OperLog(title = "工作流管理", businessType = 3)
    public Result<Void> deleteDeployment(@PathVariable String deploymentId) {
        return workflowService.deleteDeployment(deploymentId);
    }

    @Operation(summary = "启动流程实例")
    @PostMapping("/start")
    @PreAuthorize("@ss.hasPermission('workflow:instance:start')")
    @OperLog(title = "工作流管理", businessType = 1)
    public Result<ProcessInstanceDTO> start(@Valid @RequestBody StartProcessRequest request) {
        return workflowService.startProcess(request);
    }

    @Operation(summary = "完成任务")
    @PostMapping("/task/complete")
    @PreAuthorize("@ss.hasPermission('workflow:task:complete')")
    @OperLog(title = "工作流管理", businessType = 2)
    public Result<Void> completeTask(@Valid @RequestBody CompleteTaskRequest request) {
        return workflowService.completeTask(request);
    }

    @Operation(summary = "撤回任务")
    @PostMapping("/task/withdraw")
    @PreAuthorize("@ss.hasPermission('workflow:task:withdraw')")
    @OperLog(title = "工作流管理", businessType = 2)
    public Result<Void> withdrawTask(@RequestParam String processInstanceId,
                                     @RequestParam String currentTaskId) {
        return workflowService.withdrawTask(processInstanceId, currentTaskId);
    }

    @Operation(summary = "转办任务")
    @PostMapping("/task/transfer")
    @PreAuthorize("@ss.hasPermission('workflow:task:transfer')")
    @OperLog(title = "工作流管理", businessType = 2)
    public Result<Void> transferTask(@RequestParam String taskId,
                                     @RequestParam String targetUserId) {
        return workflowService.transferTask(taskId, targetUserId);
    }

    @Operation(summary = "查询待办任务")
    @GetMapping("/task/todo")
    public Result<Map<String, Object>> todoTasks(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return workflowService.getTodoTasks(page, size);
    }

    @Operation(summary = "查询已办任务")
    @GetMapping("/task/done")
    public Result<Map<String, Object>> doneTasks(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return workflowService.getDoneTasks(page, size);
    }

    @Operation(summary = "查询流程实例详情")
    @GetMapping("/instance/{processInstanceId}")
    public Result<ProcessInstanceDTO> getProcessInstance(@PathVariable String processInstanceId) {
        return workflowService.getProcessInstance(processInstanceId);
    }

    @Operation(summary = "获取流程图")
    @GetMapping("/diagram/{processInstanceId}")
    public ResponseEntity<byte[]> getProcessDiagram(@PathVariable String processInstanceId) {
        byte[] diagram = workflowService.getProcessDiagram(processInstanceId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(diagram);
    }

    @Operation(summary = "查询流程历史")
    @GetMapping("/history/{processInstanceId}")
    public Result<List<Map<String, Object>>> getProcessHistory(@PathVariable String processInstanceId) {
        return workflowService.getProcessHistory(processInstanceId);
    }
}
