package cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_migration_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class MigrationBatchDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String ownerContextCode;
    private String purposeCode;
    private String releaseId;
    private String sourceSystem;
    private String sourceTable;
    private String manifestSchemaVersion;
    private Long expectedRowCount;
    private String contentSha256;
    private LocalDateTime exportedAt;
    private Long previousBatchId;
    private Long previousIssueId;
    private String batchStatus;
    private Long sourceCount;
    private Long mappedCount;
    private Long issueCount;
    private Long retainedCount;
    private String failureCode;
    private String ruleVersion;
    private Integer version;
}
