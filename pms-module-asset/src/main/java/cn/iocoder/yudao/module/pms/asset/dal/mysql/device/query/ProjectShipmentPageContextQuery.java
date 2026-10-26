package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.List;

public record ProjectShipmentPageContextQuery(Long tenantId, Long projectId, List<Long> deviceIds) {}
