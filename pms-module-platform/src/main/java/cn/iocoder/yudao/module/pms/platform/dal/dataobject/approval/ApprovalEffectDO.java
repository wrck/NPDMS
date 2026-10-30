package cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 领域生效记录：真实批准后业务经 P03 执行其已定义命令，
 * 成功、待恢复或失败分别记录，与"流程批准"状态分列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_approval_effect")
public class ApprovalEffectDO extends TenantBaseDO {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PENDING_RECOVERY = "PENDING_RECOVERY";
    public static final String STATUS_FAILED = "FAILED";

    @TableId
    private Long id;
    private Long attemptRowId;
    private String operationCode;
    private String idempotencyKey;
    private String status;
    private String receiptOutcome;
    private Integer concurrencyBasis;
    private String detail;
}
