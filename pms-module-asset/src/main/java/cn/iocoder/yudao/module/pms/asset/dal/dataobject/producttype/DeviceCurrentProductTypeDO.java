package cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_current_product_type")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceCurrentProductTypeDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long deviceId;
    private Long productTypeId;
    private String productTypeCode;
    private Long sourceMappingId;
    private String resolutionStatus;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer currentMarker;
    @Version
    private Integer version;
}
