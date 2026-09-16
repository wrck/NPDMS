package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.DeployBpmnRequest;
import com.dp.plat.lowcode.entity.LowCodeProcessBinding;
import com.dp.plat.lowcode.service.LowCodeProcessBindingService;
import com.dp.plat.workflow.dto.ProcessInstanceDTO;
import com.dp.plat.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 低代码流程 Controller。
 *
 * <p>提供流程绑定 CRUD、Flowable 流程定义查询、BPMN XML 部署与已部署流程
 * 定义的 BPMN XML 读取，复用 pms-workflow 的 WorkflowService。</p>
 */
@Tag(name = "低代码流程", description = "LowCode process binding & integration")
@RestController
@RequestMapping("/api/lowcode/process")
@RequiredArgsConstructor
public class LowCodeProcessController {

    private final LowCodeProcessBindingService bindingService;
    private final WorkflowService workflowService;
    /** Flowable RuntimeService，用于查询流程实例当前活动节点（预览高亮） */
    private final RuntimeService runtimeService;
    /** Flowable TaskService，用于查询流程实例当前任务名称 */
    private final TaskService taskService;
    /** Flowable HistoryService，用于查询已完成的流程实例 */
    private final HistoryService historyService;

    /** 默认查询的流程实例数量上限，避免一次性返回过多数据 */
    private static final int INSTANCE_LIST_LIMIT = 200;

    @Operation(summary = "查询流程绑定列表")
    @GetMapping("/bindings")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<List<LowCodeProcessBinding>> listBindings() {
        return Result.ok(bindingService.list());
    }

    @Operation(summary = "保存流程绑定")
    @PostMapping("/bindings")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "低代码流程绑定", businessType = 1)
    public Result<LowCodeProcessBinding> saveBinding(@RequestBody LowCodeProcessBinding binding) {
        bindingService.saveOrUpdate(binding);
        return Result.ok(binding);
    }

    @Operation(summary = "查询 Flowable 流程定义列表")
    @GetMapping("/definitions")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<Map<String, Object>> listDefinitions(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return workflowService.listProcessDefinitions(page, size);
    }

    @Operation(summary = "根据 task 获取绑定的表单 code")
    @GetMapping("/task-form")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<String> getTaskForm(@RequestParam String processDefinitionKey,
                                      @RequestParam String nodeId) {
        return Result.ok(bindingService.getFormCodeForNode(processDefinitionKey, nodeId));
    }

    @Operation(summary = "部署 BPMN XML 到 Flowable")
    @PostMapping("/deploy")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "低代码流程部署", businessType = 1)
    public Result<Map<String, Object>> deployBpmnXml(@Valid @RequestBody DeployBpmnRequest request) {
        // 将 XML 字符串转为内存 MultipartFile，复用 WorkflowService.deployProcess
        String resourceName = request.getName() + ".bpmn20.xml";
        MultipartFile multipart = new InMemoryMultipartFile(
                "file", resourceName, "application/xml",
                request.getXml().getBytes(StandardCharsets.UTF_8));
        return workflowService.deployProcess(multipart);
    }

    @Operation(summary = "获取已部署流程定义的 BPMN XML")
    @GetMapping("/bpmn-xml")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<String> getBpmnXml(@RequestParam String processDefinitionKey) {
        return Result.ok(workflowService.getProcessDefinitionBpmnXml(processDefinitionKey));
    }

    @Operation(summary = "查询流程实例当前活动节点 ID 列表")
    @GetMapping("/instance/activity-ids")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<List<String>> getActivityIds(@RequestParam String processInstanceId) {
        return Result.ok(runtimeService.getActiveActivityIds(processInstanceId));
    }

    /**
     * 查询流程实例列表。
     *
     * <p>复用 Flowable RuntimeService/HistoryService：
     * <ul>
     *   <li>未指定 status 或 status=running：仅返回运行中实例（runtimeService）</li>
     *   <li>status=completed：仅返回已完成实例（historyService.finished）</li>
     *   <li>status=all：合并运行中 + 已完成（去重，按开始时间倒序）</li>
     * </ul>
     * 支持按 processDefinitionKey 过滤。返回 currentTaskName（运行中实例的当前任务名）与
     * status（运行中 / 已完成 / 已终止）。</p>
     */
    @Operation(summary = "查询流程实例列表")
    @GetMapping("/instances")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public Result<List<ProcessInstanceDTO>> listInstances(
            @RequestParam(required = false) String processDefinitionKey,
            @RequestParam(required = false) String status) {

        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        List<ProcessInstanceDTO> result = new ArrayList<>();

        // 运行中实例
        boolean includeRunning = normalizedStatus.isEmpty()
                || "running".equals(normalizedStatus)
                || "all".equals(normalizedStatus);
        // 已完成实例
        boolean includeCompleted = "completed".equals(normalizedStatus)
                || "all".equals(normalizedStatus);

        if (includeRunning) {
            org.flowable.engine.runtime.ProcessInstanceQuery runQuery =
                    runtimeService.createProcessInstanceQuery()
                            .orderByStartTime().desc();
            if (StringUtils.hasText(processDefinitionKey)) {
                runQuery.processDefinitionKey(processDefinitionKey);
            }
            List<ProcessInstance> running = runQuery.listPage(0, INSTANCE_LIST_LIMIT);
            // 批量查询当前任务名（同进程多任务用逗号拼接）
            Set<String> runningInstanceIds = running.stream()
                    .map(ProcessInstance::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> currentTaskNames = loadCurrentTaskNames(runningInstanceIds);
            for (ProcessInstance inst : running) {
                result.add(toRunningDto(inst, currentTaskNames.get(inst.getId())));
            }
        }

        if (includeCompleted) {
            org.flowable.engine.history.HistoricProcessInstanceQuery historyQuery =
                    historyService.createHistoricProcessInstanceQuery()
                            .finished()
                            .orderByProcessInstanceStartTime().desc();
            if (StringUtils.hasText(processDefinitionKey)) {
                historyQuery.processDefinitionKey(processDefinitionKey);
            }
            List<HistoricProcessInstance> finished =
                    historyQuery.listPage(0, INSTANCE_LIST_LIMIT);
            for (HistoricProcessInstance inst : finished) {
                result.add(toHistoricDto(inst));
            }
        }
        return Result.ok(result);
    }

    /**
     * 终止流程实例（级联删除）。
     *
     * <p>调用 {@link RuntimeService#deleteProcessInstance(String, String)}，
     * Flowable 会级联删除相关任务、变量、历史活动实例等。
     * 若实例已结束（不存在于运行时表），则返回提示信息。</p>
     */
    @Operation(summary = "终止流程实例")
    @DeleteMapping("/instances/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "终止流程实例", businessType = 2)
    public Result<Void> terminateInstance(@PathVariable("id") String processInstanceId,
                                           @RequestParam(required = false) String reason) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException("流程实例ID不能为空");
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new BusinessException(
                    "流程实例不存在或已结束：" + processInstanceId);
        }
        String deleteReason = StringUtils.hasText(reason) ? reason : "手动终止";
        runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
        return Result.ok();
    }

    /**
     * 启动流程实例（批次3-T4）。
     *
     * <p>调用 {@link RuntimeService#startProcessInstanceByKey}，
     * 支持传入 businessKey 和流程变量。</p>
     */
    @Operation(summary = "启动流程实例")
    @PostMapping("/instances")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "启动流程实例", businessType = 1)
    public Result<ProcessInstanceDTO> startInstance(
            @RequestParam String processDefinitionKey,
            @RequestParam(required = false) String businessKey,
            @RequestBody(required = false) Map<String, Object> variables) {
        ProcessInstance instance;
        if (StringUtils.hasText(businessKey)) {
            instance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey, businessKey, variables);
        } else {
            instance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey, variables);
        }
        return Result.ok(toRunningDto(instance, null));
    }

    /**
     * 挂起流程实例（批次3-T4）。
     *
     * <p>调用 {@link RuntimeService#suspendProcessInstanceById}，
     * 挂起后流程实例的任务不可完成，直到被重新激活。</p>
     */
    @Operation(summary = "挂起流程实例")
    @PostMapping("/instances/{id}/suspend")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "挂起流程实例", businessType = 1)
    public Result<Void> suspendInstance(@PathVariable("id") String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
        return Result.ok();
    }

    /**
     * 激活（恢复）流程实例（批次3-T4）。
     *
     * <p>调用 {@link RuntimeService#activateProcessInstanceById}，
     * 将已挂起的流程实例恢复为运行状态。</p>
     */
    @Operation(summary = "激活流程实例")
    @PostMapping("/instances/{id}/activate")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    @OperLog(title = "激活流程实例", businessType = 1)
    public Result<Void> activateInstance(@PathVariable("id") String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
        return Result.ok();
    }

    /** 批量加载流程实例的当前任务名（按 processInstanceId 分组，逗号拼接多任务名） */
    private Map<String, String> loadCurrentTaskNames(Set<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceIdIn(processInstanceIds)
                .list();
        return tasks.stream()
                .filter(t -> StringUtils.hasText(t.getName()))
                .collect(Collectors.groupingBy(
                        Task::getProcessInstanceId,
                        Collectors.mapping(Task::getName, Collectors.joining(","))));
    }

    /** 运行中实例 → DTO */
    private ProcessInstanceDTO toRunningDto(ProcessInstance inst, String currentTaskName) {
        ProcessInstanceDTO dto = new ProcessInstanceDTO();
        dto.setId(inst.getId());
        dto.setProcessDefinitionKey(inst.getProcessDefinitionKey());
        dto.setProcessDefinitionName(inst.getProcessDefinitionName());
        dto.setBusinessKey(inst.getBusinessKey());
        dto.setStartUserId(inst.getStartUserId());
        dto.setStartTime(inst.getStartTime());
        dto.setEndTime(null);
        dto.setStatus(inst.isSuspended() ? "挂起" : "运行中");
        dto.setCurrentTaskName(currentTaskName);
        return dto;
    }

    /** 已完成历史实例 → DTO */
    private ProcessInstanceDTO toHistoricDto(HistoricProcessInstance inst) {
        ProcessInstanceDTO dto = new ProcessInstanceDTO();
        dto.setId(inst.getId());
        dto.setProcessDefinitionKey(inst.getProcessDefinitionKey());
        dto.setProcessDefinitionName(inst.getProcessDefinitionName());
        dto.setBusinessKey(inst.getBusinessKey());
        dto.setStartUserId(inst.getStartUserId());
        dto.setStartTime(inst.getStartTime());
        dto.setEndTime(inst.getEndTime());
        String status;
        if (StringUtils.hasText(inst.getDeleteReason())) {
            status = "已终止";
        } else {
            status = "已完成";
        }
        dto.setStatus(status);
        dto.setCurrentTaskName(null);
        return dto;
    }

    /**
     * 内存 MultipartFile 实现。
     *
     * <p>用于将前端提交的 BPMN XML 字符串包装为 {@link MultipartFile}，
     * 以便复用 {@code WorkflowService.deployProcess(MultipartFile)}。
     * MockMultipartFile 位于 spring-test（test 作用域），不能用于主代码，
     * 故在此提供一个等价的最小实现。</p>
     */
    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            try (OutputStream out = Files.newOutputStream(dest.toPath())) {
                out.write(content);
            }
        }
    }
}
