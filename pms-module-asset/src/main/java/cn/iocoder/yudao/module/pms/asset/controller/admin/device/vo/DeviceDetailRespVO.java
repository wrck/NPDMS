package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;

public record DeviceDetailRespVO(
        DeviceSummaryDTO summary,
        DeviceSourceSliceRespVO factory,
        DeviceSourceSliceRespVO official,
        DeviceSourceSliceRespVO networkVersion,
        DeviceSourceSliceRespVO technicalNotice,
        DeviceSourceSliceRespVO warranty,
        DeviceSourceSliceRespVO configurationLog) {
}
