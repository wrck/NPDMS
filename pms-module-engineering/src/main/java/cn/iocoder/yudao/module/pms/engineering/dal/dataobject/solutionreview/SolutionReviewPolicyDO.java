package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("sol_solution_review_policy")
@Data @EqualsAndHashCode(callSuper = true)
public class SolutionReviewPolicyDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private Long solutionId;
    private Long sourceVersion;
    private Integer reviewLevel;
    private String evidenceJson;
}
