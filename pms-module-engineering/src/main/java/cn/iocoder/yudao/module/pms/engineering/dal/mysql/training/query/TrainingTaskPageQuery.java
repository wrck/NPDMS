package cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query;

public record TrainingTaskPageQuery(Long tenantId, Long projectId, Long afterId, int pageSize) { }
