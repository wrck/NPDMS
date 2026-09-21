package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("imp_collection_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImplementationCollectionLogDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String entry;
    private Long objectId;
    private Long projectId;
    private Long deviceId;
    private Long executionId;
    private Long actorId;
    private String platformTaskId;
    private Long resultVersion;
    private Long fileVersionId;
    private String protocol;
    private String externalStatus;
    private String failureCategory;
    private String commandText;
    private String templateName;
    private LocalDateTime receivedAt;
}
