package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.math.BigDecimal;

public record DeliveryScopeOrderFieldsRow(Long scopeId, String contractNo, BigDecimal orderQuantity, String unitCode) {}
