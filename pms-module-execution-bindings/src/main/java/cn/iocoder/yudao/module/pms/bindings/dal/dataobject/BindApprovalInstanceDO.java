package cn.iocoder.yudao.module.pms.bindings.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 本地审批引擎实例：单步决定的真实流程；状态机 PENDING→APPROVED/REJECTED，或 WITHDRAWN。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_bind_approval_instance")
public class BindApprovalInstanceDO extends TenantBaseDO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @TableId
    private Long id;
    private String instanceRef;
    private String attemptHint;
    private String purpose;
    private String status;
    private String decisionComment;
    private LocalDateTime decidedTime;
}
