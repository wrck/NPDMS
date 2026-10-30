package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("ast_device_log_parsed_fact")
@Data @EqualsAndHashCode(callSuper = true)
public class DeviceLogParsedFactDO extends TenantBaseDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID) private Long id;
    private Long projectId;
    private Long actorId;
    private Long deviceId;
    private String requestKey;
    private String logKey;
    private LocalDateTime logReceivedAt;
    private String parserTaskId;
    private String parserVersion;
    private Long sourceFileId;
    private Long logVersion;
    private String serialNumber;
    private String conpVersion;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
    private String status;
    private String versionStatus;
}
