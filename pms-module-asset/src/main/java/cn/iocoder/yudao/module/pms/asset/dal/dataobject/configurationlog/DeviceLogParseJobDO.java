package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@TableName("ast_device_log_parse_job") @Data @EqualsAndHashCode(callSuper=true)
public class DeviceLogParseJobDO extends TenantBaseDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT) private Long id;
    private Long projectId;
    private Long actorId;
    private LocalDateTime logReceivedAt;
    private String state;
    private String externalTaskId;
    private String parserVersion;
    private Boolean projected;
}
