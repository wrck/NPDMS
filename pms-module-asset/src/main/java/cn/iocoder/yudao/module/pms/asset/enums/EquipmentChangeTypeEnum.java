package cn.iocoder.yudao.module.pms.asset.enums;

/**
 * 设备版本变更类型枚举（FR-RES-002）。
 * <p>
 * 与 {@code EquipmentStatusRules} 中的状态机动作保持一致；CREATE/UPDATE 用于档案本身的写入。
 */
public interface EquipmentChangeTypeEnum {

    /**
     * 创建
     */
    String CREATE = "CREATE";
    /**
     * 更新
     */
    String UPDATE = "UPDATE";
    /**
     * 部署（在库 → 在用）
     */
    String DEPLOY = "DEPLOY";
    /**
     * 已确认安装、迁移或拆除使当前位置生效。
     */
    String LOCATION_EFFECTIVE = "LOCATION_EFFECTIVE";
    /**
     * 报障（在用 → 故障）
     */
    String REPORT_FAULT = "REPORT_FAULT";
    /**
     * 开始维修（故障 → 维修中）
     */
    String START_REPAIR = "START_REPAIR";
    /**
     * 维修完成（维修中 → 在库/在用）
     */
    String COMPLETE_REPAIR = "COMPLETE_REPAIR";
    /**
     * 报废（任意 → 已报废，终态）
     */
    String SCRAP = "SCRAP";
}
