package cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公共审批尝试关联：业务主体、用途、尝试、提交依据与中性流程引用；
 * 引擎原生实例标识只进入本关联记录，不成为业务实体字段。
 * 重提生成新行并回链上一轮，旧尝试的结论与意见永不覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_approval_attempt")
public class ApprovalAttemptDO extends TenantBaseDO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @TableId
    private Long id;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String purpose;
    private String attemptId;
    private String submissionBasis;
    private String neutralProcessRef;
    private String backendId;
    private String instanceRef;
    private String status;
    private String conclusionBasis;
    private LocalDateTime decidedTime;
    private Long previousAttemptId;
    private String batchGroupRef;
    /**
     * 提交时的业务内容并发基准；生效执行时与当前版本比对，不一致即批准不再适用于当前内容。
     */
    private Integer submissionConcurrencyBasis;
    @Version
    private Integer version;
}
