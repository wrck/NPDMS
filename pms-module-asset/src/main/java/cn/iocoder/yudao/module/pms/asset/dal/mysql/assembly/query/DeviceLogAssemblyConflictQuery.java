package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query;

/** Current edges affected by one observed physical slot; locked before interval replacement. */
public record DeviceLogAssemblyConflictQuery(Long tenantId, String parentDeviceSn,
                                             String childDeviceSn, String positionCode) { }
