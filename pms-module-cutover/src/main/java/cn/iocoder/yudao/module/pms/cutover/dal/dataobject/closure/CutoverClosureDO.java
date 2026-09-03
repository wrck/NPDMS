package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_closure")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverClosureDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long taskId;
    private Long projectId;
    private Long approvalInstanceId;
    private Integer approvalVersion;
    private Long planRevisionId;
    private Integer planRevisionNo;
    private Integer planVersion;
    private Integer taskVersionAtP6;
    private String deviceScopeWatermark;
    private String statusCode;
    private Boolean preCheckNormal;
    private String preCheckDetail;
    private Boolean executionNormal;
    private String executionDetail;
    private Boolean testNormal;
    private String testDetail;
    private Boolean rollbackOccurred;
    private Boolean rollbackSuccessful;
    private String rollbackReason;
    private String legacyItems;
    private String finalResultCode;
    private String resultRef;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime archivedAt;
    @Version
    private Integer version;
}
