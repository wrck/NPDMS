package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目任务实例 DO（F-PM01 / V57 `proj_project_task`）
 * <p>
 * 实例化时从模板冻结快照，初始状态 PENDING_ASSIGN（待分配）。
 * `source_definition_id` 为定义行映射槽（内容模型无定义行 ID 时为 NULL）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskInstanceDO extends TenantBaseDO {

    /**
     * 任务实例ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 任务码（实例化时冻结，项目内唯一）
     */
    private String taskCode;
    /**
     * 任务名称（快照）
     */
    private String name;
    /**
     * 父任务码（NULL=顶层）
     */
    private String parentTaskCode;
    /**
     * 当前直接父任务ID（NULL=根任务）
     */
    private Long parentTaskId;
    /**
     * 当前根任务ID（根任务为自身ID）
     */
    private Long rootTaskId;
    /**
     * 结构深度（根任务为0）
     */
    private Integer treeDepth;
    /**
     * 业务层级编码（与结构深度无关）
     */
    private String businessLevelCode;
    /**
     * 关联里程碑实例ID
     */
    private Long milestoneId;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    /**
     * 任务进度0～100
     */
    private BigDecimal progress;
    /**
     * 创建时冻结的任务状态机版本ID
     */
    private Long stateMachineRevisionId;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 优先级（快照）
     */
    private Integer priority;
    /**
     * 排序（快照）
     */
    private Integer sortOrder;
    /**
     * 预估工时（快照）
     */
    private BigDecimal estimatedHours;
    /**
     * 满意度适用时点（快照，由 ACC-02 消费）
     */
    private String satisfactionTiming;
    /** ACC满意度模板冻结事实。 */
    private Long accSatisfactionTemplateId;
    private Long templateRevisionId;
    private Integer templateVersion;
    private String satisfactionRuleVersion;
    private BigDecimal satisfactionThreshold;
    /**
     * 任务说明（快照）
     */
    private String description;
    /**
     * 冻结来源：模板任务定义ID（内容模型无定义行 ID 时为 NULL）
     */
    private Long sourceDefinitionId;
    /**
     * 任务实例状态（字典 pms_project_task_status，初始 PENDING_ASSIGN 待分配）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
