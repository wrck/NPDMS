package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

@TableName("proj_project_split_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSplitRequestDO extends BaseBusinessEntity {
    private Long parentProjectId;
    private String status;
    private Integer draftVersion;
    private Long parentVersion;
    private Long scopeVersion;
    private Long treeVersion;
    private Long templateRevisionId;
    private String previewHash;
    private String validationStatus;
    private String validationSummary;
    private LocalDateTime validatedAt;
    private String appliedChangeBatchId;
}
