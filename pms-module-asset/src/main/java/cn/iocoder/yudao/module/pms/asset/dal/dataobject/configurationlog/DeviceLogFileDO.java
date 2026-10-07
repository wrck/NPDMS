package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("ast_device_log_file") @Data @EqualsAndHashCode(callSuper=true)
public class DeviceLogFileDO extends TenantBaseDO {
    @TableId private Long id;
    private Long importId;
    private Long projectId;
    private Long deviceId;
    private String serialNumber;
    private Long deviceVersion;
    private String sourceType;
    private String fileName;
    private Long materialId;
    private Long fileReferenceId;
    private Long artifactId;
    private Integer artifactVersion;
    private String sha256;
}
