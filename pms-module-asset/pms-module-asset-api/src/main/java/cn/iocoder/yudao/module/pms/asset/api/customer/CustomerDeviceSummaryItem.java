package cn.iocoder.yudao.module.pms.asset.api.customer;

public record CustomerDeviceSummaryItem(
        Long deviceId,
        String deviceCode,
        String deviceName,
        String status) {
}
