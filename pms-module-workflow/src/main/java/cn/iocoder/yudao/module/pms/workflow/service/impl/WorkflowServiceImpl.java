package cn.iocoder.yudao.module.pms.workflow.service.impl;

import cn.iocoder.yudao.module.pms.workflow.exception.BusinessException;
import cn.iocoder.yudao.module.pms.workflow.common.Result;
import cn.iocoder.yudao.module.pms.workflow.security.SecurityUtils;
import cn.iocoder.yudao.module.pms.workflow.dto.CompleteTaskRequest;
import cn.iocoder.yudao.module.pms.workflow.dto.ProcessDefinitionDTO;
import cn.iocoder.yudao.module.pms.workflow.dto.ProcessInstanceDTO;
import cn.iocoder.yudao.module.pms.workflow.dto.StartProcessRequest;
import cn.iocoder.yudao.module.pms.workflow.dto.TaskDTO;
import cn.iocoder.yudao.module.pms.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flowable-backed implementation of {@link WorkflowService}.
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final String IMAGE_TYPE_PNG = "png";
    private static final String ACTIVITY_TYPE_USER_TASK = "userTask";
    private static final String FONT_NAME = "sans-serif";
    private static final double DEFAULT_SCALE = 1.0;

    /** Flowable process variable that globally enables skipExpression evaluation. */
    private static final String VAR_SKIP_EXPRESSION_ENABLED = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";
    /** Process variable holding the initiator (start user) id, used by skipExpression. */
    private static final String VAR_INITIATOR = "initiator";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    @Override
    public Result<Map<String, Object>> deployProcess(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("部署文件不能为空");
        }
        String resourceName = file.getOriginalFilename();
        if (!StringUtils.hasText(resourceName)) {
            resourceName = "process.bpmn20.xml";
        }
        DeploymentBuilder builder = repositoryService.createDeployment()
                .name(resourceName);
        try (InputStream in = file.getInputStream()) {
            builder.addInputStream(resourceName, in);
        } catch (IOException e) {
            throw new BusinessException("读取部署文件失败: " + e.getMessage());
        }
        Deployment deployment = builder.deploy();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", deployment.getId());
        data.put("name", deployment.getName());
        data.put("deployTime", deployment.getDeploymentTime());
        data.put("category", deployment.getCategory());
        data.put("tenantId", deployment.getTenantId());
        return Result.ok(data);
    }

    @Override
    public Result<Map<String, Object>> listProcessDefinitions(int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int firstResult = toFirstResult(safePage, safeSize);
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active()
                .orderByProcessDefinitionKey().asc();

        long total = query.count();
        List<ProcessDefinition> definitions = query.listPage(firstResult, safeSize);

        Set<String> deploymentIds = definitions.stream()
                .map(ProcessDefinition::getDeploymentId)
                .collect(Collectors.toSet());
        Map<String, Deployment> deploymentMap = loadDeployments(deploymentIds);

        List<ProcessDefinitionDTO> records = definitions.stream()
                .map(def -> toProcessDefinitionDTO(def, deploymentMap))
                .toList();
        return Result.ok(pageResult(records, total, safePage, safeSize));
    }

    @Override
    public Result<Void> deleteDeployment(String deploymentId) {
        if (!StringUtils.hasText(deploymentId)) {
            throw new BusinessException("部署ID不能为空");
        }
        repositoryService.deleteDeployment(deploymentId, true);
        return Result.ok();
    }

    @Override
    public String getProcessDefinitionBpmnXml(String processDefinitionKey) {
        if (!StringUtils.hasText(processDefinitionKey)) {
            throw new BusinessException("流程定义Key不能为空");
        }
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .singleResult();
        if (def == null) {
            throw new BusinessException("流程定义不存在: " + processDefinitionKey);
        }
        String resourceName = StringUtils.hasText(def.getResourceName())
                ? def.getResourceName()
                : processDefinitionKey + ".bpmn20.xml";
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), resourceName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取流程定义XML失败: " + e.getMessage());
        }
    }

    @Override
    public Result<ProcessInstanceDTO> startProcess(StartProcessRequest request) {
        if (request == null || !StringUtils.hasText(request.getProcessDefinitionKey())) {
            throw new BusinessException("流程定义Key不能为空");
        }
        String currentUserId = currentUserId();
        Authentication.setAuthenticatedUserId(currentUserId);

        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.put(VAR_SKIP_EXPRESSION_ENABLED, Boolean.TRUE);
        variables.put(VAR_INITIATOR, currentUserId);

        ProcessInstance instance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(request.getProcessDefinitionKey())
                .businessKey(request.getBusinessKey())
                .variables(variables)
                .start();

        return Result.ok(toProcessInstanceDTO(instance, null));
    }

    @Override
    public Result<Void> completeTask(CompleteTaskRequest request) {
        if (request == null || !StringUtils.hasText(request.getTaskId())) {
            throw new BusinessException("任务ID不能为空");
        }
        Task task = taskService.createTaskQuery().taskId(request.getTaskId()).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在或已被处理");
        }
        if (StringUtils.hasText(request.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), request.getComment());
        }
        if (!CollectionUtils.isEmpty(request.getVariables())) {
            taskService.complete(task.getId(), request.getVariables());
        } else {
            taskService.complete(task.getId());
        }
        return Result.ok();
    }

    @Override
    public Result<Void> withdrawTask(String processInstanceId, String currentTaskId) {
        if (!StringUtils.hasText(processInstanceId) || !StringUtils.hasText(currentTaskId)) {
            throw new BusinessException("流程实例ID与任务ID不能为空");
        }
        Task currentTask = taskService.createTaskQuery()
                .taskId(currentTaskId)
                .processInstanceId(processInstanceId)
                .singleResult();
        if (currentTask == null) {
            throw new BusinessException("当前任务不存在");
        }
        String currentActivityId = currentTask.getTaskDefinitionKey();

        List<HistoricActivityInstance> finishedActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType(ACTIVITY_TYPE_USER_TASK)
                .finished()
                .list();
        if (finishedActivities.isEmpty()) {
            throw new BusinessException("未找到可回退的历史任务");
        }
        HistoricActivityInstance previous = finishedActivities.stream()
                .filter(a -> a.getEndTime() != null)
                .max(Comparator.comparing(HistoricActivityInstance::getEndTime))
                .orElseThrow(() -> new BusinessException("未找到可回退的历史任务"));

        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(currentActivityId, previous.getActivityId())
                .changeState();
        return Result.ok();
    }

    @Override
    public Result<Void> transferTask(String taskId, String targetUserId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException("任务ID不能为空");
        }
        if (!StringUtils.hasText(targetUserId)) {
            throw new BusinessException("目标用户不能为空");
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        taskService.setAssignee(taskId, targetUserId);
        return Result.ok();
    }

    @Override
    public Result<Map<String, Object>> getTodoTasks(int page, int size) {
        String userId = currentUserId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int firstResult = toFirstResult(safePage, safeSize);

        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .orderByTaskCreateTime().desc()
                .listPage(firstResult, safeSize);
        long total = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .count();

        Map<String, ProcessInstance> instanceMap = loadProcessInstances(
                tasks.stream().map(Task::getProcessInstanceId).collect(Collectors.toSet()));

        List<TaskDTO> records = tasks.stream()
                .map(task -> toTaskDTO(task, instanceMap))
                .toList();
        return Result.ok(pageResult(records, total, safePage, safeSize));
    }

    @Override
    public Result<Map<String, Object>> getDoneTasks(int page, int size) {
        String userId = currentUserId();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int firstResult = toFirstResult(safePage, safeSize);

        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .listPage(firstResult, safeSize);
        long total = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .finished()
                .count();

        Map<String, HistoricProcessInstance> instanceMap = loadHistoricProcessInstances(
                tasks.stream().map(HistoricTaskInstance::getProcessInstanceId).collect(Collectors.toSet()));

        List<TaskDTO> records = tasks.stream()
                .map(task -> toHistoricTaskDTO(task, instanceMap))
                .toList();
        return Result.ok(pageResult(records, total, safePage, safeSize));
    }

    @Override
    public Result<ProcessInstanceDTO> getProcessInstance(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException("流程实例ID不能为空");
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance != null) {
            String currentTaskName = currentTaskName(processInstanceId);
            return Result.ok(toProcessInstanceDTO(instance, currentTaskName));
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historic == null) {
            throw new BusinessException("流程实例不存在");
        }
        return Result.ok(toProcessInstanceDTO(historic));
    }

    @Override
    public byte[] getProcessDiagram(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException("流程实例ID不能为空");
        }
        String processDefinitionId;
        List<String> activeActivityIds;
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance != null) {
            processDefinitionId = instance.getProcessDefinitionId();
            activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        } else {
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            if (historic == null) {
                throw new BusinessException("流程实例不存在");
            }
            processDefinitionId = historic.getProcessDefinitionId();
            activeActivityIds = List.of();
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null) {
            throw new BusinessException("无法获取流程图模型");
        }
        DefaultProcessDiagramGenerator generator = new DefaultProcessDiagramGenerator();
        try (InputStream in = generator.generateDiagram(bpmnModel, IMAGE_TYPE_PNG,
                activeActivityIds, List.of(),
                FONT_NAME, FONT_NAME, FONT_NAME, null, DEFAULT_SCALE, false);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成流程图失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<Map<String, Object>>> getProcessHistory(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException("流程实例ID不能为空");
        }
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        List<Map<String, Object>> history = new ArrayList<>(activities.size());
        for (HistoricActivityInstance activity : activities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", activity.getActivityId());
            item.put("activityName", activity.getActivityName());
            item.put("activityType", activity.getActivityType());
            item.put("assignee", activity.getAssignee());
            item.put("taskId", activity.getTaskId());
            item.put("startTime", activity.getStartTime());
            item.put("endTime", activity.getEndTime());
            item.put("durationInMillis", activity.getDurationInMillis());
            history.add(item);
        }
        return Result.ok(history);
    }

    // ===== Helper methods =====

    private int toFirstResult(int page, int size) {
        return (normalizePage(page) - 1) * normalizeSize(size);
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        return size < 1 ? 10 : size;
    }

    private String currentUserId() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private String currentTaskName(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        if (tasks.isEmpty()) {
            return null;
        }
        return tasks.stream().map(Task::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
    }

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

    private Map<String, ProcessInstance> loadProcessInstances(Set<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list();
        return instances.stream()
                .collect(Collectors.toMap(ProcessInstance::getId, i -> i, (a, b) -> a));
    }

    private Map<String, HistoricProcessInstance> loadHistoricProcessInstances(Set<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list();
        return instances.stream()
                .collect(Collectors.toMap(HistoricProcessInstance::getId, i -> i, (a, b) -> a));
    }

    private ProcessDefinitionDTO toProcessDefinitionDTO(ProcessDefinition def, Map<String, Deployment> deploymentMap) {
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

    private ProcessInstanceDTO toProcessInstanceDTO(ProcessInstance instance, String currentTaskName) {
        ProcessInstanceDTO dto = new ProcessInstanceDTO();
        dto.setId(instance.getId());
        dto.setProcessDefinitionKey(instance.getProcessDefinitionKey());
        dto.setProcessDefinitionName(instance.getProcessDefinitionName());
        dto.setBusinessKey(instance.getBusinessKey());
        dto.setStartUserId(instance.getStartUserId());
        dto.setStartTime(instance.getStartTime());
        dto.setEndTime(null);
        dto.setStatus(instance.isSuspended() ? "挂起" : "运行中");
        dto.setCurrentTaskName(currentTaskName);
        return dto;
    }

    private ProcessInstanceDTO toProcessInstanceDTO(HistoricProcessInstance instance) {
        ProcessInstanceDTO dto = new ProcessInstanceDTO();
        dto.setId(instance.getId());
        dto.setProcessDefinitionKey(instance.getProcessDefinitionKey());
        dto.setProcessDefinitionName(instance.getProcessDefinitionName());
        dto.setBusinessKey(instance.getBusinessKey());
        dto.setStartUserId(instance.getStartUserId());
        dto.setStartTime(instance.getStartTime());
        dto.setEndTime(instance.getEndTime());
        String status;
        if (instance.getEndTime() == null) {
            status = "运行中";
        } else if (StringUtils.hasText(instance.getDeleteReason())) {
            status = "已终止";
        } else {
            status = "已完成";
        }
        dto.setStatus(status);
        dto.setCurrentTaskName(null);
        return dto;
    }

    private TaskDTO toTaskDTO(Task task, Map<String, ProcessInstance> instanceMap) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setAssignee(task.getAssignee());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setCreateTime(task.getCreateTime());
        dto.setDescription(task.getDescription());
        ProcessInstance instance = instanceMap.get(task.getProcessInstanceId());
        if (instance != null) {
            dto.setProcessDefinitionName(instance.getProcessDefinitionName());
            dto.setBusinessKey(instance.getBusinessKey());
        }
        return dto;
    }

    private TaskDTO toHistoricTaskDTO(HistoricTaskInstance task,
                                       Map<String, HistoricProcessInstance> instanceMap) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setAssignee(task.getAssignee());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setCreateTime(task.getCreateTime());
        dto.setDescription(task.getDescription());
        HistoricProcessInstance instance = instanceMap.get(task.getProcessInstanceId());
        if (instance != null) {
            dto.setProcessDefinitionName(instance.getProcessDefinitionName());
            dto.setBusinessKey(instance.getBusinessKey());
        }
        return dto;
    }

    private Map<String, Object> pageResult(List<?> records, long total, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
