package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目模板 DO
 * <p>
 * 模板内容以 JSON 快照形式存储在 {@link #snapshotJson}，包含 phases/tasks/teamRoles 三类子模板。
 */
@TableName(value = "proj_project_template_revision", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 模板编码（全局唯一）
     */
    private String code;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 适用项目类型（字典 pms_project_type）
     */
    private String projectType;
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
     * 模板内容快照（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private TemplateSnapshot snapshotJson;

}
