package cn.iocoder.yudao.module.pms.asset.enums;

/**
 * 设备档案状态（ast_device.status，String 值域；原 pms_equipment Integer 状态机承接）。
 * <p>
 * 值域与 V114 旧链前向迁移及 ast_device 存量数据一致：FAULT/RETIRED。
 * 状态机：IN_STOCK→IN_USE(deploy)；IN_USE→FAULT(reportFault)；FAULT→REPAIRING(startRepair)；
 * REPAIRING→IN_STOCK/IN_USE(completeRepair)；任意非终态→RETIRED(scrap，终态)。
 */
public interface DeviceArchiveStatusEnum {

    String IN_STOCK = "IN_STOCK";
    String IN_USE = "IN_USE";
    String FAULT = "FAULT";
    String REPAIRING = "REPAIRING";
    /** 已报废（终态） */
    String RETIRED = "RETIRED";
}
