package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverIntakeSourceQuery(Long tenantId, String sourceType, String sourceSystem,
                                      String sourceBusinessNo, String businessEventId) { }
