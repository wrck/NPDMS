package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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
    @TableField("template_code")
    private String code;
    /**
     * 模板名称
     */
    @TableField("template_name")
    private String name;
    /** 稳定模板编号，同一模板的 revision 共用。 */
    private Long templateId;
    /** revision 号。 */
    private Integer revisionNo;
    /** 四个独立业务维度的适用条件。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private TemplateApplicability applicabilitySnapshot;
    private String businessSceneCode;
    private Integer matchPriority;
    private Boolean defaultFlag;
    private String workflowDefinitionKey;
    private Integer workflowDefinitionVersion;
    /**
     * 适用项目类型（字典 pms_project_type）
     */
    private String projectType;
    /**
     * 描述
     */
    private String description;
    /**
     * revision 状态：DRAFT / PUBLISHED / DISABLED
     */
    private String status;
    /**
     * 排序号
     */
    private Integer sort;
    /**
     * 模板内容快照（JSON）
     */
    @TableField(value = "definition_snapshot", typeHandler = JacksonTypeHandler.class)
    private TemplateSnapshot snapshotJson;

    private String contentSha256;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime publishedAt;

}
