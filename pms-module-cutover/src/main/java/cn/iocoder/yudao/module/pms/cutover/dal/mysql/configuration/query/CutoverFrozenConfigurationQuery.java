package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

public record CutoverFrozenConfigurationQuery(Long tenantId,
                                               Long configurationRevisionId,
                                               String configurationCode,
                                               Integer configurationRevisionNo) {
}
