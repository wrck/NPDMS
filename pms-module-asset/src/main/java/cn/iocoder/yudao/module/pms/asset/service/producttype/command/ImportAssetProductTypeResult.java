package cn.iocoder.yudao.module.pms.asset.service.producttype.command;

public record ImportAssetProductTypeResult(
        Long productTypeId,
        Long sourceMappingId,
        String productTypeCode,
        boolean replayed) {
}
