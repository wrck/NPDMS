package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

@lombok.Builder(toBuilder = true)
public record ProjectSerialExportQuery(Long tenantId, Long projectId, String sn, String name,
                                       String productCode, Long beforeId, int limit) { }
