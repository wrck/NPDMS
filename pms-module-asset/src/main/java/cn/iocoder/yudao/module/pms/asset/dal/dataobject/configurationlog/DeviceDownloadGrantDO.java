package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_download_grant")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceDownloadGrantDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String tokenDigest;
    private Long userId;
    private String deviceSn;
    private Long configurationLogId;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    @Version
    private Integer version;
}
