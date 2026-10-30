package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;

import java.time.LocalDateTime;

public record ContractRespVO(
        Long id, String companyCode, String companyName, String contractNo, String contractType,
        String customerCode, String customerName, String contractName, java.math.BigDecimal contractAmount,
        String currencyCode, String sourceVersion, LocalDateTime sourceUpdatedAt, String status, Long version) {
}
