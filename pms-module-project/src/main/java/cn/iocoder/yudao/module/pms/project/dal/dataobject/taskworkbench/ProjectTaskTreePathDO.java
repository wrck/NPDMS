package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目任务树祖先闭包当前投影 DO。
 */
@TableName("proj_task_tree_path")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskTreePathDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private Long ancestorTaskId;
    private Long descendantTaskId;
    private Integer distance;
    private Integer version;
}
