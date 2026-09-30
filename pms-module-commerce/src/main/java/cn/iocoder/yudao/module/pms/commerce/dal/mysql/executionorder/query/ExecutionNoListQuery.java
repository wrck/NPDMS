package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query;

import java.util.List;

/** 按执行单号集合解析有效CRM执行单（用于合同→订单→执行单取值链）。 */
public record ExecutionNoListQuery(Long tenantId, List<String> executionNos) {
}
