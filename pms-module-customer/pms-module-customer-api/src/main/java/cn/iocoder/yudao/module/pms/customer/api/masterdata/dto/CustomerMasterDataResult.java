package cn.iocoder.yudao.module.pms.customer.api.masterdata.dto;

public record CustomerMasterDataResult(
        Long customerId,
        Long version,
        boolean replayed) {
}
