package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.*;
import java.util.*;

@Data @EqualsAndHashCode(callSuper=true) @TableName("sol_stage_plan_batch")
@ProjectBusinessModel(ownerModule="SOL",entityType="stagePlanBatch",stableCode="SOL_STAGE_PLAN_BATCH",name="阶段施工计划",permissionPrefix="pms:imp-stage-plan",nativeEntityType="StagePlanBatch")
public class StagePlanBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="状态",writable=false) private Integer status;
    @BusinessModelField(name="工期版本",writable=false) private Long durationRevisionId;
    @BusinessModelField(name="推算开始",writable=false) private LocalDate calculatedStart;
    @BusinessModelField(name="推算结束",writable=false) private LocalDate calculatedEnd;
    @BusinessModelField(name="备注") private String remark;
    @BusinessModelField(name="提交时间",writable=false) private LocalDateTime submittedAt;
    @BusinessModelField(name="生效时间",writable=false) private LocalDateTime effectiveAt;
    @BusinessModelField(name="驳回原因",writable=false) private String rejectReason;
    @TableField(exist=false) @BusinessModelField(name="阶段计划",writable=false,listVisible=false,searchable=false,sortable=false) private List<StagePlanItemDO> items;
    private String inputSnapshot;
    private String taskPlansJson;
    private String bpmProcessKey;
    private String bpmProcessInstanceId;
    private Long submittedBy;
    private Long effectiveBy;
}
