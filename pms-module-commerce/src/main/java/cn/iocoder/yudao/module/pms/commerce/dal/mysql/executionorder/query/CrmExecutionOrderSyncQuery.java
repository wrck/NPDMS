package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query;

import java.util.List;

public record CrmExecutionOrderSyncQuery(Long tenantId, String sourceSystem, List<String> executionNumbers) {}
