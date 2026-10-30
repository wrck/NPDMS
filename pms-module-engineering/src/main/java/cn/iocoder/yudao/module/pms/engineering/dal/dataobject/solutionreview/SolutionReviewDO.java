package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("sol_solution_review")
@Data @EqualsAndHashCode(callSuper = true)
public class SolutionReviewDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private Long solutionId;
    private Long sourceVersion;
    private Long requestVersion;
    private String processDefinitionId;
    private String processInstanceId;
    private String businessKey;
    private String candidatesJson;
    private String status;
    private String reviewsJson;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Long approvedVersion;
    @Version private Integer version;
}
