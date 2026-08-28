package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板里程碑定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_milestone_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateMilestoneDefinitionDO extends TenantBaseDO {

    /**
     * 里程碑定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 里程碑码（版本内唯一）
     */
    private String milestoneCode;
    /**
     * 里程碑名称
     */
    private String name;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 时点说明
     */
    private String timing;
    /**
     * 达成标准
     */
    private String criteria;
}
