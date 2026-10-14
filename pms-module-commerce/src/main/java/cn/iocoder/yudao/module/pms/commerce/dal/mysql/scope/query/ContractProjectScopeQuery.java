package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

/** 项目显式关联合同的完整订单来源及该项目既有范围；tenant由受信上下文解析。 */
public record ContractProjectScopeQuery(Long tenantId, Long projectId) { }
