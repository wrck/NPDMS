package cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("int_sync_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncTaskDO extends TenantBaseDO {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private String name;
    private Long connectionId;
    private String adapter;
    private String sourceSystem;
    private String definition;
    private Integer version;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Integer validatedVersion;
    private Boolean enabled;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long activeRunId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.LocalDateTime checkpoint;
    private java.time.LocalDateTime nextRunAt;
    private java.time.LocalDateTime nextFullAt;
    private Integer retryAttempt;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long lastFailedRunId;
}
