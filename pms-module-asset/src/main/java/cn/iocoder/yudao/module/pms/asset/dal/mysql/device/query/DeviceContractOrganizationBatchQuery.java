package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

/** 合同归属刷新专用游标；只选择未绑定项目的设备。 */
public record DeviceContractOrganizationBatchQuery(Long tenantId, Set<String> contractNumbers,
        String afterContractNo, long afterId, int limit) {}
