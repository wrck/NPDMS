package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PMS 项目任务 WBS DO
 *
 * 物化路径模型：path 格式 /{rootId}/.../{selfId}/，depth 从 0 开始。
 */
@TableName("proj_project_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskDO extends TenantBaseDO {

    /**
     * 任务编号
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 父任务编号，根任务为 null
     */
    private Long parentId;
    /**
     * 根任务编号，根任务为自身 id
     */
    private Long rootId;
    /**
     * 物化路径，格式 /{rootId}/.../{selfId}/
     */
    @TableField("tree_path")
    private String path;
    /**
     * 路径深度，根任务为 0
     */
    @TableField("tree_depth")
    private Integer depth;
    /**
     * 同级排序号
     */
    @TableField("tree_sort")
    private Integer sort;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 任务编码，项目内唯一（不为空时唯一）
     */
    private String code;
    /**
     * 任务描述
     */
    private String description;
    /**
     * 负责人用户编号
     */
    private Long ownerUserId;
    /**
     * 执行人用户编号
     */
    private Long assigneeUserId;
    /**
     * 状态：0草稿 1待处理 2进行中 3受阻 4待验证 5已完成 6已取消
     */
    private Integer status;
    /**
     * 优先级
     */
    private Integer priority;
    /**
     * 计划开始时间
     */
    private LocalDateTime planStartTime;
    /**
     * 计划结束时间
     */
    private LocalDateTime planEndTime;
    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartTime;
    /**
     * 实际结束时间
     */
    private LocalDateTime actualEndTime;
    /**
     * 预估工时
     */
    private BigDecimal estimatedHours;
    /**
     * 实际工时
     */
    private BigDecimal actualHours;
    /**
     * 进度 0-100
     */
    private Integer progress;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
