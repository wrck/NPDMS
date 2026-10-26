package cn.iocoder.yudao.module.pms.asset.api.device.dto;

/** Existing project shipment and installation context; none of these fields assigns the device to a project. */
public record ProjectShipmentFact(Long deviceId, Long projectId, Long installationId,
        String contractNo, String deviceSn, String physicalSn, String productCode, String productName,
        String physicalProductCode, String physicalProductName, String installAddress,
        String receiveName, String emsNum, String emsCompany, String packdate) {}
