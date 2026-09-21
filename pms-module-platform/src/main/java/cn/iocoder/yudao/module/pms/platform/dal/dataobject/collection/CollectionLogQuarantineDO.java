package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("plt_collection_log_quarantine")
public class CollectionLogQuarantineDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String callbackId;
    private String platformTaskId;
    private String storageOperationId;
    private String sha256;
    private Long sizeBytes;
    private String reasonCode;
    private LocalDateTime quarantinedAt;
}
