package cn.iocoder.yudao.module.pms.asset.service.producttype.command;

public record RecordAssetProductTypeSourceFailureCommand(
        String operationId,
        String sourceSystem,
        String sourceKey,
        String failureCode) {
}
