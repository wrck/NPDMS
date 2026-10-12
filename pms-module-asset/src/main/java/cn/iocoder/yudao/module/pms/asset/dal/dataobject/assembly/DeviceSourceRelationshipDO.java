package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@TableName("ast_device_relationship")
@Data
@EqualsAndHashCode(callSuper=true)
public class DeviceSourceRelationshipDO extends TenantBaseDO {
    @TableId private Long id;
    private String sourceDeviceSn;
    private String targetDeviceSn;
    private String sourceItem;
    private String targetItem;
    private String relationshipType;
    private String contractNo;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Integer version;
}
