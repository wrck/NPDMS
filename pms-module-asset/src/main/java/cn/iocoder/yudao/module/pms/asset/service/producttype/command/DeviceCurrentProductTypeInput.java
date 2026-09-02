package cn.iocoder.yudao.module.pms.asset.service.producttype.command;

public record DeviceCurrentProductTypeInput(
        Long deviceId,
        String resolutionStatus) {
}
