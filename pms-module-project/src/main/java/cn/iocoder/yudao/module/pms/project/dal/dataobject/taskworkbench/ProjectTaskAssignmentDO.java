package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/** 项目任务负责人责任区间 DO。 */
@TableName("proj_project_task_assignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskAssignmentDO extends BaseBusinessEntity {

    private Long projectTaskId;
    private Long assigneeUserId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer currentMarker;
    private Long assignedBy;
    private String reason;
}
