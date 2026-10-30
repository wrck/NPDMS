package cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_product_type_source_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductTypeSourceMappingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private String payloadHash;
    private Long productTypeId;
    private String mappingStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conflictProductTypeCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conflictSourceVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime conflictSourceUpdatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conflictPayloadHash;
    private LocalDateTime syncedAt;
    @Version
    private Integer version;
}
