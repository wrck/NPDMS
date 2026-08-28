package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板阶段定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_stage_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateStageDefinitionDO extends TenantBaseDO {

    /**
     * 阶段定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 阶段码（S0～S6）
     */
    private String stageCode;
    /**
     * 阶段名称
     */
    private String name;
    /**
     * 阶段顺序
     */
    private Integer sortOrder;
    /**
     * 准入条件说明
     */
    private String entryCriteria;
    /**
     * 准出条件说明
     */
    private String exitCriteria;
}
