package cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("int_sync_run")
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncRunDO extends TenantBaseDO {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private Long taskId;
    private Long parentRunId;
    private Integer pageNumber;
    private String pagingJson;
    private String requestKey;
    private String status;
    private Boolean preview;
    private Boolean fullSnapshot;
    private Boolean adoptExisting;
    private String configSnapshot;
    private Integer configVersion;
    private String resultJson;
    private String summaryJson;
    private Integer readCount;
    private String errorMessage;
    private String evidenceJson;
    private java.time.LocalDateTime sourceUpper;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime finishedAt;
    private Boolean cachePending;
}
