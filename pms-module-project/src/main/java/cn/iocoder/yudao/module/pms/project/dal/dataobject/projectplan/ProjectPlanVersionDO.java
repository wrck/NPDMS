package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_plan_version")
public class ProjectPlanVersionDO extends BaseBusinessEntity {
    private Long projectId;
    private Integer revisionNo;
    private String status;
    private Long sourceTemplateRevisionId;
    private Long basePlanVersionId;
    private String designerDocument;
    private String executionSnapshot;
    private LocalDateTime effectiveAt;
    private LocalDateTime closedAt;
    private String closureResult;
}
