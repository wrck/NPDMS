package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板交付件定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_deliverable_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDeliverableDefinitionDO extends TenantBaseDO {

    /**
     * 交付件定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 交付件码（版本内唯一）
     */
    private String deliverableCode;
    /**
     * 交付件名称
     */
    private String name;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 关联任务码（NULL=阶段级）
     */
    private String taskCode;
    /**
     * 必需标志
     */
    private Boolean required;
}
