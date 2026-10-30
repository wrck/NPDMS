package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

@TableName("proj_project_tree_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTreeVersionDO extends BaseBusinessEntity {
    private Long rootProjectId;
    private Long treeVersion;
    private String status;
    private String changeBatchId;
    private Integer nodeCount;
    private Integer pathCount;
    private LocalDateTime activatedAt;
    private String failedReason;
}
