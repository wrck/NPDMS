package cn.iocoder.yudao.module.pms.asset.domain.equipment;

import cn.iocoder.yudao.module.pms.asset.enums.EquipmentChangeTypeEnum;
import cn.iocoder.yudao.module.pms.asset.enums.EquipmentStatusEnum;

/**
 * 设备状态机规则（FR-RES-001）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>0=在库 → 1=在用 (deploy)</li>
 *   <li>1=在用 → 2=故障 (reportFault)</li>
 *   <li>2=故障 → 3=维修中 (startRepair)</li>
 *   <li>3=维修中 → 0=在库 或 1=在用 (completeRepair)</li>
 *   <li>任意非终态 → 4=已报废 (scrap)，终态不可流转</li>
 * </ul>
 * 该类仅做规则校验，不依赖持久化层；调用方在校验通过后自行写库。
 */
public final class EquipmentStatusRules {

    /**
     * 支持的状态机动作
     */
    public enum Action {
        DEPLOY,
        REPORT_FAULT,
        START_REPAIR,
        COMPLETE_REPAIR,
        SCRAP
    }

    private EquipmentStatusRules() {
    }

    /**
     * 校验状态流转是否合法；不合法抛出 {@link IllegalStateException}。
     *
     * @param current 当前状态
     * @param action  动作
     */
    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("设备状态与动作均不能为空");
        }
        // 终态不可流转
        if (EquipmentStatusEnum.SCRAPPED.equals(current)) {
            throw new IllegalStateException("设备已报废，无法执行任何状态变更操作");
        }
        switch (action) {
            case DEPLOY:
                requireCurrentIn(current, EquipmentStatusEnum.IN_STOCK, "deploy");
                break;
            case REPORT_FAULT:
                requireCurrentIn(current, EquipmentStatusEnum.IN_USE, "reportFault");
                break;
            case START_REPAIR:
                requireCurrentIn(current, EquipmentStatusEnum.FAULTY, "startRepair");
                break;
            case COMPLETE_REPAIR:
                requireCurrentIn(current, EquipmentStatusEnum.REPAIRING, "completeRepair");
                break;
            case SCRAP:
                // 任意非终态均允许
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    /**
     * 维修完成动作需要显式指定目标状态（在库或在用）。
     *
     * @param current       当前状态（必须为维修中）
     * @param targetStatus  目标状态（在库或在用）
     */
    public static void requireCompleteRepair(Integer current, Integer targetStatus) {
        if (current == null || targetStatus == null) {
            throw new IllegalArgumentException("设备状态与目标状态均不能为空");
        }
        if (!EquipmentStatusEnum.REPAIRING.equals(current)) {
            throw new IllegalStateException("仅维修中状态可执行维修完成操作，当前状态：" + current);
        }
        if (!EquipmentStatusEnum.IN_STOCK.equals(targetStatus)
                && !EquipmentStatusEnum.IN_USE.equals(targetStatus)) {
            throw new IllegalStateException("维修完成目标状态仅支持在库或在用，目标状态：" + targetStatus);
        }
    }

    /**
     * 推导动作的目标状态；对于 COMPLETE_REPAIR 需调用 {@link #requireCompleteRepair(Integer, Integer)} 显式校验。
     *
     * @param action 动作
     * @return 目标状态
     */
    public static Integer targetStatus(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("动作不能为空");
        }
        switch (action) {
            case DEPLOY:
                return EquipmentStatusEnum.IN_USE;
            case REPORT_FAULT:
                return EquipmentStatusEnum.FAULTY;
            case START_REPAIR:
                return EquipmentStatusEnum.REPAIRING;
            case COMPLETE_REPAIR:
                throw new IllegalArgumentException("completeRepair 需显式指定目标状态，请使用 requireCompleteRepair");
            case SCRAP:
                return EquipmentStatusEnum.SCRAPPED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    /**
     * 将状态机动作映射为版本变更类型。
     *
     * @param action 状态机动作
     * @return 版本变更类型字符串
     */
    public static String toChangeType(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("动作不能为空");
        }
        switch (action) {
            case DEPLOY:
                return EquipmentChangeTypeEnum.DEPLOY;
            case REPORT_FAULT:
                return EquipmentChangeTypeEnum.REPORT_FAULT;
            case START_REPAIR:
                return EquipmentChangeTypeEnum.START_REPAIR;
            case COMPLETE_REPAIR:
                return EquipmentChangeTypeEnum.COMPLETE_REPAIR;
            case SCRAP:
                return EquipmentChangeTypeEnum.SCRAP;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!expected.equals(current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
