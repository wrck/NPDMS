package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_node")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalNodeDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Integer nodeNo;
    private String nodeCode;
    private String statusCode;
    private Long originalApproverUserId;
    private Long currentApproverUserId;
    private String candidateFactSnapshot;
    private Long projectScopeVersion;
    private String assessmentReviewDecisionCode;
    private String assessmentReviewReason;
    private String feedback;
    private LocalDateTime decisionAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer pendingMarker;
    @Version
    private Integer version;
}
