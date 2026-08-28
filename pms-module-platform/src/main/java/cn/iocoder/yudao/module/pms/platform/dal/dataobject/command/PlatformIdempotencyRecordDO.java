package cn.iocoder.yudao.module.pms.platform.dal.dataobject.command;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 平台命令幂等记录。 */
@TableName("plt_idempotency_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformIdempotencyRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String scopeCode;
    private Long actorId;
    private String idempotencyKey;
    private String requestDigest;
    private String status;
    private String resourceType;
    private String resourceKey;
    private String responsePayload;
    private Integer version;
}
