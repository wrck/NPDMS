package cn.iocoder.yudao.module.pms.asset.api.device.dto;

public record SelectedProjectDevice(Long equipmentId, String sn, String name,
                                    String productCode, String productModel, String contractNo) { }
