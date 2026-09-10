package cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("acc_closure_review")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureReviewDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long applicationId;
    private Long projectId;
    private String processInstanceId;
    private String processDefinitionId;
    private String taskId;
    private String taskDefinitionKey;
    private Long reviewerUserId;
    private String outcome;
    private String reason;
    private LocalDateTime reviewedAt;
}
