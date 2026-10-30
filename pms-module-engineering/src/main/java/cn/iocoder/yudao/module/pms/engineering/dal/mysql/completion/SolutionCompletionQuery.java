package cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion;
public record SolutionCompletionQuery(Long tenantId, Long projectId, Long objectId, Long afterId, int pageSize, boolean lock) { }
