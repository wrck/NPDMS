package cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo;

public record SalesOrderRespVO(
        Long id, String sourceSystem, String sourceVersion, String companyCode, String companyName,
        String orderType, String orderNo, String customerCode, String customerName, String status, Integer version) {
}
