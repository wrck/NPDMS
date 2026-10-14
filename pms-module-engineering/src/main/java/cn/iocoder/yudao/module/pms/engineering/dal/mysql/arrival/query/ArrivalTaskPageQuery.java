package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query;

public record ArrivalTaskPageQuery(Long tenantId, Long projectId, Long afterId, int pageSize) { }
