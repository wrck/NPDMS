package cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_migration_issue")
@Data
@EqualsAndHashCode(callSuper = true)
public class MigrationIssueDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private Long sourceRecordId;
    private String issueKey;
    private String issueType;
    private String rawBusinessKey;
    private String candidateTargetIds;
    private String rawPayload;
    private String issueStatus;
    private Long resolverUserId;
    private String ruleVersion;
    private String targetResult;
    private LocalDateTime resolvedAt;
    private Integer version;
}
