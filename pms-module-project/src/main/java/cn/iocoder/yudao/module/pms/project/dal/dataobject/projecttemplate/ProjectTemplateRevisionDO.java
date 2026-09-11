package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Project template revision.
 *
 * <p>V2 separates editable authoring truth from immutable runtime truth:</p>
 * <ul>
 *   <li>DRAFT: {@code designerDocument} is writable; execution snapshot is null.</li>
 *   <li>PUBLISHED: designer and execution snapshot are immutable publication artifacts.</li>
 *   <li>Legacy rows can keep all V2 fields null and are interpreted through the legacy reader.</li>
 * </ul>
 */
@TableName("proj_project_template_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateRevisionDO extends TenantBaseDO {

    /** Legacy publication closure. Read-only compatibility evidence for rows published before V2. */
    private String definitionSnapshot;

    /** V2 authoring schema version. */
    private Integer designerSchemaVersion;

    /** V2 editable/frozen designer JSON. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String designerDocument;

    /** V2 immutable execution schema version; null for drafts and legacy rows. */
    private Integer executionSchemaVersion;

    /** V2 immutable runtime snapshot; null for drafts and legacy rows. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String executionSnapshot;

    /** Compiler identity used to build the execution snapshot. */
    private String compilerVersion;

    /** SHA-256 over canonical runtime semantics. */
    private String snapshotHash;

    /** Dedicated closure rule; null means not configured. Kept for query/legacy compatibility. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closurePolicy;

    @TableId
    private Long id;
    private Long templateId;
    /** 0=draft; positive values are immutable published revisions. */
    private Integer revisionNo;
    private String status;

    private String signingMethod;
    private String projectCategory;
    private String implementationMethod;
    private String majorProjectLevel;

    /** BPM definition key reference only. */
    private String processDefinitionKey;
    /** Historical compatibility field; V2 does not persist a PMS process version. */
    private String processDefinitionVersion;

    private String validationSummary;
    private String publishedBy;
    private LocalDateTime publishedTime;
}
