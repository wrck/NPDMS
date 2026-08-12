package cn.iocoder.yudao.module.pms.asset.enums;

/**
 * 设备状态枚举（FR-RES-001）。
 * <p>
 * 状态机：
 * <ul>
 *   <li>0=在库 → 1=在用 (deploy 部署)</li>
 *   <li>1=在用 → 2=故障 (reportFault 报障)</li>
 *   <li>2=故障 → 3=维修中 (startRepair 开始维修)</li>
 *   <li>3=维修中 → 0=在库 (completeRepair 维修完成入库) 或 1=在用 (completeRepair 维修完成投用)</li>
 *   <li>任意状态 → 4=已报废 (scrap 报废)，终态</li>
 * </ul>
 */
public interface EquipmentStatusEnum {

    /**
     * 在库
     */
    Integer IN_STOCK = 0;
    /**
     * 在用
     */
    Integer IN_USE = 1;
    /**
     * 故障
     */
    Integer FAULTY = 2;
    /**
     * 维修中
     */
    Integer REPAIRING = 3;
    /**
     * 已报废（终态）
     */
    Integer SCRAPPED = 4;
}
