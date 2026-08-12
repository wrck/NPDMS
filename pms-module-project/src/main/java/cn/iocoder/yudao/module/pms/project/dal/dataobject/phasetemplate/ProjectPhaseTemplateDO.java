package cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目阶段模板 DO
 */
@TableName("pms_project_phase_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPhaseTemplateDO extends TenantBaseDO {

    /**
     * 模板编号
     */
    @TableId
    private Long id;
    /**
     * 模板阶段名称
     */
    private String name;
    /**
     * 模板阶段编码，全局唯一
     */
    private String code;
    /**
     * 适用项目类型
     */
    private String projectType;
    /**
     * 所属项目模板编号（NULL=独立阶段模板，兼容现有数据）
     */
    private Long projectTemplateId;
    /**
     * 描述
     */
    private String description;
    /**
     * 状态：0启用 1停用
     */
    private Integer status;
    /**
     * 排序号
     */
    private Integer sort;
    /**
     * 准入条件
     */
    private String entryCriteria;
    /**
     * 退出条件
     */
    private String exitCriteria;
    /**
     * 负责角色编码
     */
    private String responsibleRole;

}
