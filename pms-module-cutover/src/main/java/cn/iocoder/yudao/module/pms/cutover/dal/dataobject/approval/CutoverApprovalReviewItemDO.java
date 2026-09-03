package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_approval_review_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalReviewItemDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long approvalInstanceId;
    private Long approvalNodeId;
    private String itemCode;
    private String decisionCode;
    private String unreasonableReason;
}
