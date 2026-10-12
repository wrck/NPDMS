package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_device_log_parse")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceLogParseDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long actorId;
    private String requestKey;
    private String requestDigest;
    private String inputName;
    private String sourceEntry;
    private Long sourceObjectId;
    private Long sourceExecutionId;
    private String sourceCollectionId;
    private Long sourceFileVersionId;
    private String state;
    private String externalTaskId;
    private String releaseVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String waitReason;
}
