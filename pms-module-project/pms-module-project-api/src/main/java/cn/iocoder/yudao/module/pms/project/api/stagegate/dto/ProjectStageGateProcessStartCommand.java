package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

import java.util.Map;

/** 服务端构造的Gate流程启动命令；客户端不能覆盖tenant、actor、businessKey和变量。 */
public record ProjectStageGateProcessStartCommand(
        Long tenantId,
        Long actorUserId,
        Long projectId,
        String currentStageCode,
        Long gateId,
        Long gateReferenceId,
        String refType,
        String processDefinitionKey,
        String selectedProcessDefinitionId,
        String businessKey,
        String operationId,
        String requestDigest,
        Map<String, Object> variables) {
}
