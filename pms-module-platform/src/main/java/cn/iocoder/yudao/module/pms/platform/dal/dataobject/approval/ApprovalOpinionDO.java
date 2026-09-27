package cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批意见记录：每次发起/决定/撤回追加一行，随尝试保留，不因重提覆盖。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_approval_opinion")
public class ApprovalOpinionDO extends TenantBaseDO {

    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";

    @TableId
    private Long id;
    private Long attemptRowId;
    private String action;
    private String comment;
    private Long actorUserId;
}
