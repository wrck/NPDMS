package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;
public record CollectionConnectionQuery(Long tenantId, Long actorId, Long projectId, Long deviceId,
                                        String protocol, String templateId, java.time.LocalDateTime now) { }
