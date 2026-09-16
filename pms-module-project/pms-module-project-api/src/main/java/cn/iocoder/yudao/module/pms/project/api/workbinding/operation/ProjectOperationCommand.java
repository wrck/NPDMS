package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import tools.jackson.databind.JsonNode;

/** Expected values are concurrency checks, never authorization. The HTTP layer supplies the key. */
public record ProjectOperationCommand(Long projectId, String nodeKind, Long nodeId,
        ProjectBusinessExecutionSelection execution, String objectId, Integer expectedBusinessVersion,
        String expectedObjectFactVersion, JsonNode input, String idempotencyKey) {
    public ProjectOperationCommand withExecution(ProjectBusinessExecutionSelection current) {
        return new ProjectOperationCommand(projectId, nodeKind, nodeId, current, objectId,
                expectedBusinessVersion, expectedObjectFactVersion, input, idempotencyKey);
    }
    public ProjectOperationCommand withKey(String key) {
        return new ProjectOperationCommand(projectId, nodeKind, nodeId, execution, objectId,
                expectedBusinessVersion, expectedObjectFactVersion, input, key);
    }
}
