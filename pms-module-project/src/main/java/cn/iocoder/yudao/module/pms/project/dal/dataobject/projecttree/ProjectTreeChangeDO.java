package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

@TableName("proj_project_tree_change")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTreeChangeDO extends BaseBusinessEntity {
    private String changeBatchId;
    private String operationType;
    private Long projectId;
    private Long parentIdBefore;
    private Long parentIdAfter;
    private Long baseTreeVersion;
    private Long newTreeVersion;
    private Long actorId;
    private String reason;
    private LocalDateTime occurredAt;
}
