package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目任务基础依赖 DO。 */
@TableName("proj_task_dependency")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskDependencyDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private Long predecessorTaskId;
    private Long successorTaskId;
    private String dependencyTypeCode;
    private Integer version;
}
