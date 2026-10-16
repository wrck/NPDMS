package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Original sanitized parser envelope, written once per archived device log. */
@TableName("ast_device_log_parse_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceLogParseSnapshotDO extends TenantBaseDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private Long id;
    private Long projectId;
    private String parserTaskId;
    private String parserVersion;
    private Long fileReferenceId;
    private Long artifactId;
    private Integer artifactVersion;
    private String sourceSha256;
    private String resultJson;
}
