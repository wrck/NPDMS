package cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion;
public record CutoverCompletionQuery(Long tenantId, Long projectId, Long objectId, Long afterId, int pageSize, boolean lock) { }
