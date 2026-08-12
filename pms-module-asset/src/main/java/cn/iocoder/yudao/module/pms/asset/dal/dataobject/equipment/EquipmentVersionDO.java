package cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 设备版本历史 DO（追加只读，仅 INSERT，不提供 UPDATE/DELETE 业务通道）
 */
@TableName("pms_equipment_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentVersionDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 版本号（按设备递增）
     */
    private Integer versionNo;
    /**
     * 变更类型：CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP
     */
    private String changeType;
    /**
     * 变更描述
     */
    private String changeDescription;
    /**
     * 变更前快照(JSON)
     */
    private String beforeSnapshot;
    /**
     * 变更后快照(JSON)
     */
    private String afterSnapshot;

}
