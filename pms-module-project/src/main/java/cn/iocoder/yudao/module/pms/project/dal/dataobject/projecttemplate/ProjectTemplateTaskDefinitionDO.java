package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目模板任务定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_task_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateTaskDefinitionDO extends TenantBaseDO {

    /**
     * 任务定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 任务码（版本内唯一）
     */
    private String taskCode;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 父任务码（NULL=顶层）
     */
    private String parentTaskCode;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 优先级
     */
    private Integer priority;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 预估工时
     */
    private BigDecimal estimatedHours;
    /**
     * 满意度适用时点（NULL=不适用，由ACC-02消费）
     */
    private String satisfactionTiming;
    /**
     * 任务说明
     */
    private String description;
}
