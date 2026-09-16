package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.module.pms.lowcode.enums.LowCodeException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.DeployBpmnRequest;
import cn.iocoder.yudao.module.pms.lowcode.dto.ProcessDefinitionDTO;
import cn.iocoder.yudao.module.pms.lowcode.dto.ProcessInstanceDTO;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeProcessBinding;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeProcessBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 低代码流程 Controller。
 *
 * <p>提供流程绑定 CRUD、Flowable 流程定义查询、BPMN XML 部署与已部署流程
 * 定义的 BPMN XML 读取。Flowable 引擎由 yudao-module-bpm 提供，
 * 本模块直接使用 Flowable 引擎服务。</p>
 */
@Tag(name = "低代码流程", description = "LowCode process binding & integration")
@RestController
@RequestMapping("/api/lowcode/process")
@RequiredArgsConstructor
public class LowCodeProcessController {

    private final LowCodeProcessBindingService bindingService;
    private final RepositoryService repositoryService;
    /** Flowable RuntimeService，用于查询/启动/终止流程实例 */
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
    public CommonResult<List<LowCodeProcessBinding>> listBindings() {
        return CommonResult.success(bindingService.list());
    }

    @Operation(summary = "保存流程绑定")
    @PostMapping("/bindings")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    public CommonResult<LowCodeProcessBinding> saveBinding(@RequestBody LowCodeProcessBinding binding) {
        bindingService.saveOrUpdate(binding);
        return CommonResult.success(binding);
    }

    @Operation(summary = "查询 Flowable 流程定义列表")
    @GetMapping("/definitions")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public CommonResult<Map<String, Object>> listDefinitions(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size < 1 ? 10 : size;
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active()
                .orderByProcessDefinitionKey().asc();
        long total = query.count();
        List<ProcessDefinition> definitions = query.listPage((safePage - 1) * safeSize, safeSize);

        Set<String> deploymentIds = definitions.stream()
                .map(ProcessDefinition::getDeploymentId)
                .collect(Collectors.toSet());
        Map<String, Deployment> deploymentMap = loadDeployments(deploymentIds);
        List<ProcessDefinitionDTO> records = definitions.stream()
                .map(def -> toDefinitionDto(def, deploymentMap))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return CommonResult.success(result);
    }

    @Operation(summary = "根据 task 获取绑定的表单 code")
    @GetMapping("/task-form")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public CommonResult<String> getTaskForm(@RequestParam String processDefinitionKey,
                                      @RequestParam String nodeId) {
        return CommonResult.success(bindingService.getFormCodeForNode(processDefinitionKey, nodeId));
    }

    @Operation(summary = "部署 BPMN XML 到 Flowable")
    @PostMapping("/deploy")
    @PreAuthorize("@ss.hasPermission('lowcode:process:edit')")
    public CommonResult<Map<String, Object>> deployBpmnXml(@Valid @RequestBody DeployBpmnRequest request) {
        String resourceName = request.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(resourceName)
                .addBytes(resourceName, request.getXml().getBytes(StandardCharsets.UTF_8))
                .deploy();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", deployment.getId());
        data.put("name", deployment.getName());
        data.put("deployTime", deployment.getDeploymentTime());
        data.put("category", deployment.getCategory());
        data.put("tenantId", deployment.getTenantId());
        return CommonResult.success(data);
    }

    @Operation(summary = "获取已部署流程定义的 BPMN XML")
    @GetMapping("/bpmn-xml")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public CommonResult<String> getBpmnXml(@RequestParam String processDefinitionKey) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .singleResult();
        if (def == null) {
            throw new LowCodeException("流程定义不存在: " + processDefinitionKey);
        }
        String resourceName = StringUtils.hasText(def.getResourceName())
                ? def.getResourceName()
                : processDefinitionKey + ".bpmn20.xml";
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), resourceName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return CommonResult.success(out.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LowCodeException("读取流程定义XML失败: " + e.getMessage());
        }
    }

    @Operation(summary = "查询流程实例当前活动节点 ID 列表")
    @GetMapping("/instance/activity-ids")
    @PreAuthorize("@ss.hasPermission('lowcode:process:list')")
    public CommonResult<List<String>> getActivityIds(@RequestParam String processInstanceId) {
        return CommonResult.success(runtimeService.getActiveActivityIds(processInstanceId));
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
    public CommonResult<List<ProcessInstanceDTO>> listInstances(
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
        return CommonResult.success(result);
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
    public CommonResult<Void> terminateInstance(@PathVariable("id") String processInstanceId,
                                           @RequestParam(required = false) String reason) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new LowCodeException("流程实例ID不能为空");
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new LowCodeException(
                    "流程实例不存在或已结束：" + processInstanceId);
        }
        String deleteReason = StringUtils.hasText(reason) ? reason : "手动终止";
        runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
        return CommonResult.success(null);
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
    public CommonResult<ProcessInstanceDTO> startInstance(
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
        return CommonResult.success(toRunningDto(instance, null));
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
    public CommonResult<Void> suspendInstance(@PathVariable("id") String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
        return CommonResult.success(null);
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
    public CommonResult<Void> activateInstance(@PathVariable("id") String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
        return CommonResult.success(null);
    }

    /** 批量加载流程定义的部署信息（用于返回部署时间） */
    private Map<String, Deployment> loadDeployments(Set<String> deploymentIds) {
        if (deploymentIds.isEmpty()) {
            return Map.of();
        }
        List<Deployment> deployments = repositoryService.createDeploymentQuery()
                .deploymentIds(new ArrayList<>(deploymentIds))
                .list();
        return deployments.stream()
                .collect(Collectors.toMap(Deployment::getId, d -> d, (a, b) -> a));
    }

    /** 流程定义 → DTO（含部署时间） */
    private ProcessDefinitionDTO toDefinitionDto(ProcessDefinition def, Map<String, Deployment> deploymentMap) {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setId(def.getId());
        dto.setName(def.getName());
        dto.setKey(def.getKey());
        dto.setVersion(def.getVersion());
        dto.setDeploymentId(def.getDeploymentId());
        dto.setResourceName(def.getResourceName());
        dto.setSuspended(def.isSuspended());
        Deployment deployment = deploymentMap.get(def.getDeploymentId());
        if (deployment != null) {
            dto.setDeployTime(deployment.getDeploymentTime());
        }
        return dto;
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
}
