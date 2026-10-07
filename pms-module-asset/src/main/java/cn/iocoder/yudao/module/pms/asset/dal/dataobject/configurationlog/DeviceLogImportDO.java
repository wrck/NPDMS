package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("ast_device_log_import") @Data @EqualsAndHashCode(callSuper=true)
public class DeviceLogImportDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String sourceKey;
    private String sourceType;
    private String sourceFileName;
    private String inputSha256;
    private String outcome;
    private String rejectionReason;
    private Integer deviceCount;
    private Integer redactedValueCount;
    private Long actorId;
}
