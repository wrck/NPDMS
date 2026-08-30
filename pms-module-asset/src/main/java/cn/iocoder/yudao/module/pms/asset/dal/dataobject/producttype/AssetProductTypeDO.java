package cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_product_type")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductTypeDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String typeCode;
    private String displayName;
    private Boolean enabled;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private String payloadHash;
    private String syncStatus;
    private LocalDateTime lastSyncAttemptAt;
    private LocalDateTime syncedAt;
    @Version
    private Integer version;
}
