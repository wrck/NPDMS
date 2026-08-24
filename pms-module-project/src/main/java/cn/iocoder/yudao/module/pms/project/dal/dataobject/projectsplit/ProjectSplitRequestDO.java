package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("proj_project_split_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSplitRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private Long parentProjectId;
    private String status;
    private Integer draftVersion;
    private Integer parentVersion;
    private Long scopeVersion;
    private Long treeVersion;
    private Long templateRevisionId;
    private String previewHash;
    private String validationStatus;
    private String validationSummary;
    private LocalDateTime validatedAt;
    private String appliedChangeBatchId;
    private Integer version;
}
