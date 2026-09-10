package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** Relationship history only; no business body, logical deletion or mutable artifact snapshots. */
@Data
@TableName("proj_task_business_link")
public class ProjectTaskBusinessLinkDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long taskId;
    private Long executionContractId;
    private Integer contractVersion;
    private String ownerContext;
    private String objectType;
    private String objectId;
    private String factVersion;
    private Long linkedBy;
    private LocalDateTime linkedAt;
    private Long unlinkedBy;
    private LocalDateTime unlinkedAt;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
