package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

public record CutoverIntakeSourceQuery(Long tenantId, String sourceType, String sourceSystem,
                                      String sourceBusinessNo, String businessEventId) { }
