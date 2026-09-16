package cn.iocoder.yudao.module.pms.workflow.service;

import cn.iocoder.yudao.module.pms.workflow.common.Result;
import cn.iocoder.yudao.module.pms.workflow.dto.CompleteTaskRequest;
import cn.iocoder.yudao.module.pms.workflow.dto.ProcessInstanceDTO;
import cn.iocoder.yudao.module.pms.workflow.dto.StartProcessRequest;
import cn.iocoder.yudao.module.pms.workflow.dto.TaskDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Workflow service abstraction built on top of the Flowable engine.
 *
 * <p>Exposes process deployment, process instance lifecycle, task handling and
 * history retrieval operations. All methods return a unified {@link Result}
 * wrapper.</p>
 */
public interface WorkflowService {

    /**
     * Deploy a BPMN process definition file.
     *
     * @param file the BPMN xml file to deploy
     * @return deployment information
     */
    Result<Map<String, Object>> deployProcess(MultipartFile file);

    /**
     * Paginated list of the latest active process definitions.
     *
     * @param page 1-based page number
     * @param size page size
     * @return paginated process definitions
     */
    Result<Map<String, Object>> listProcessDefinitions(int page, int size);

    /**
     * 获取已部署流程定义的最新版本 BPMN 2.0 XML 原文。
     *
     * <p>用于低代码流程设计器的只读预览（bpmn-js Viewer）与节点表单绑定时
     * 解析 UserTask 节点列表。返回部署时原始 XML，保留自定义扩展元素。</p>
     *
     * @param processDefinitionKey 流程定义 key
     * @return BPMN 2.0 XML 字符串
     */
    String getProcessDefinitionBpmnXml(String processDefinitionKey);

    /**
     * Delete a deployment (cascade).
     *
     * @param deploymentId the deployment identifier
     * @return operation result
     */
    Result<Void> deleteDeployment(String deploymentId);

    /**
     * Start a process instance by definition key.
     *
     * @param request start process request
     * @return the created process instance
     */
    Result<ProcessInstanceDTO> startProcess(StartProcessRequest request);

    /**
     * Complete a task with optional variables and a comment.
     *
     * @param request complete task request
     * @return operation result
     */
    Result<Void> completeTask(CompleteTaskRequest request);

    /**
     * Withdraw the current task and jump back to the previous task.
     *
     * @param processInstanceId process instance identifier
     * @param currentTaskId     current task identifier
     * @return operation result
     */
    Result<Void> withdrawTask(String processInstanceId, String currentTaskId);

    /**
     * Transfer a task to another user.
     *
     * @param taskId        task identifier
     * @param targetUserId  target user identifier
     * @return operation result
     */
    Result<Void> transferTask(String taskId, String targetUserId);

    /**
     * Paginated todo tasks of the current user (assignee or candidate).
     *
     * @param page 1-based page number
     * @param size page size
     * @return paginated tasks
     */
    Result<Map<String, Object>> getTodoTasks(int page, int size);

    /**
     * Paginated done tasks of the current user.
     *
     * @param page 1-based page number
     * @param size page size
     * @return paginated tasks
     */
    Result<Map<String, Object>> getDoneTasks(int page, int size);

    /**
     * Get process instance detail including the current task name.
     *
     * @param processInstanceId process instance identifier
     * @return process instance detail
     */
    Result<ProcessInstanceDTO> getProcessInstance(String processInstanceId);

    /**
     * Generate the process diagram image bytes.
     *
     * @param processInstanceId process instance identifier
     * @return png image bytes
     */
    byte[] getProcessDiagram(String processInstanceId);

    /**
     * Get the execution history of a process instance.
     *
     * @param processInstanceId process instance identifier
     * @return list of history items
     */
    Result<List<Map<String, Object>>> getProcessHistory(String processInstanceId);
}
