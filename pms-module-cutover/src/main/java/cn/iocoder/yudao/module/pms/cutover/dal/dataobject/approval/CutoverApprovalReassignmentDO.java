package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_reassignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalReassignmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Long approvalNodeId;
    private Integer reassignmentNo;
    private Long fromApproverUserId;
    private Long toApproverUserId;
    private String reason;
    private String candidateFactSnapshot;
    private Long operatedBy;
    private LocalDateTime operatedAt;
}
