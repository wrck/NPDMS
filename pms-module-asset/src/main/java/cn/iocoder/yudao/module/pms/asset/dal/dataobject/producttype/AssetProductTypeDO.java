package cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_product_type")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductTypeDO extends BaseBusinessEntity {

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
}
