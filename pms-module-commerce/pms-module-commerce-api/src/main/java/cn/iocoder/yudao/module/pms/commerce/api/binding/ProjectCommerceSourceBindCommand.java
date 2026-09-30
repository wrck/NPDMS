package cn.iocoder.yudao.module.pms.commerce.api.binding;

/** 创建绑定命令：subjectUserId=创建人（授权快照主体），operationId=创建幂等操作号。 */
public record ProjectCommerceSourceBindCommand(Long tenantId, Long projectId, Long contractId,
                                               Long subjectUserId, String operationId) {
}
