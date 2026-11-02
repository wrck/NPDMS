package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;

/** Device visibility is checked by the Owner before querying its history. */
public record DeviceSoftwareVersionHistoryPageQuery(Long tenantId, Long deviceId,
                                                   int pageNo, int pageSize) {
}
