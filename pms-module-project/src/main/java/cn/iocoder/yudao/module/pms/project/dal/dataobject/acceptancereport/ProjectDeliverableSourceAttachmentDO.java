package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_project_deliverable_source_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDeliverableSourceAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deliverableSourceVersionId;
    private Integer attachmentSequence;
    private Long fileArtifactId;
    private Integer fileVersionNo;
    private String referenceKey;
    private Integer artifactVersion;
    private Integer referenceVersion;
    private Integer availabilityVersion;
    private Long scopeVersion;
    private String fileHash;
}
