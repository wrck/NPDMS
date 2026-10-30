package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

/**
 * 项目创建取值预览的订单解析查询：合同主档单入口。
 * 主路径=订单头主导列 contract_no + company_code 业务键精确匹配（排退货 order_type='0'）；
 * 兼容分支=显式 com_order_contract_relation 关系（承接权威接收路径）。
 */
public record ContractCreationOrderQuery(Long tenantId, Long contractId,
                                         String contractNo, String companyCode) {
}
