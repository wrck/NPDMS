package cn.iocoder.yudao.module.pms.asset.domain.device;

import cn.iocoder.yudao.module.pms.asset.enums.DeviceArchiveStatusEnum;

/**
 * 设备档案状态机规则（FR-RES-001，自 pms_equipment 状态机承接为 String 值域）。
 * 仅做规则校验，不依赖持久化层；调用方在校验通过后自行写库。
 */
public final class DeviceStatusRules {

    public enum Action {
        DEPLOY,
        REPORT_FAULT,
        START_REPAIR,
        COMPLETE_REPAIR,
        SCRAP
    }

    private DeviceStatusRules() {
    }

    public static void requireTransition(String current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("设备状态与动作均不能为空");
        }
        if (DeviceArchiveStatusEnum.RETIRED.equals(current)) {
            throw new IllegalStateException("设备已报废，无法执行任何状态变更操作");
        }
        switch (action) {
            case DEPLOY -> requireCurrentIn(current, DeviceArchiveStatusEnum.IN_STOCK, "deploy");
            case REPORT_FAULT -> requireCurrentIn(current, DeviceArchiveStatusEnum.IN_USE, "reportFault");
            case START_REPAIR -> requireCurrentIn(current, DeviceArchiveStatusEnum.FAULT, "startRepair");
            case COMPLETE_REPAIR -> requireCurrentIn(current, DeviceArchiveStatusEnum.REPAIRING, "completeRepair");
            case SCRAP -> { /* 任意非终态均允许 */ }
        }
    }

    public static void requireCompleteRepair(String current, String targetStatus) {
        if (current == null || targetStatus == null) {
            throw new IllegalArgumentException("设备状态与目标状态均不能为空");
        }
        if (!DeviceArchiveStatusEnum.REPAIRING.equals(current)) {
            throw new IllegalStateException("仅维修中状态可执行维修完成操作，当前状态：" + current);
        }
        if (!DeviceArchiveStatusEnum.IN_STOCK.equals(targetStatus)
                && !DeviceArchiveStatusEnum.IN_USE.equals(targetStatus)) {
            throw new IllegalStateException("维修完成目标状态仅支持在库或在用，目标状态：" + targetStatus);
        }
    }

    public static String targetStatus(Action action) {
        return switch (action) {
            case DEPLOY -> DeviceArchiveStatusEnum.IN_USE;
            case REPORT_FAULT -> DeviceArchiveStatusEnum.FAULT;
            case START_REPAIR -> DeviceArchiveStatusEnum.REPAIRING;
            case SCRAP -> DeviceArchiveStatusEnum.RETIRED;
            case COMPLETE_REPAIR ->
                    throw new IllegalArgumentException("completeRepair 需显式指定目标状态，请使用 requireCompleteRepair");
        };
    }

    public static String toChangeType(Action action) {
        return switch (action) {
            case DEPLOY -> "DEPLOY";
            case REPORT_FAULT -> "REPORT_FAULT";
            case START_REPAIR -> "START_REPAIR";
            case COMPLETE_REPAIR -> "COMPLETE_REPAIR";
            case SCRAP -> "SCRAP";
        };
    }

    private static void requireCurrentIn(String current, String expected, String actionName) {
        if (!expected.equals(current)) {
            throw new IllegalStateException("动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
