package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_collection_callback_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionCallbackRecordDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String platformTaskId;
    private String callbackId;
    private Long receiptId;
    private Long sequenceNo;
    private String externalTaskId;
    private String externalStatus;
    private String mappedStatus;
    private Long resultVersion;
    private Long fileVersionId;
    private String quarantineEvidenceId;
    private String failureCategory;
    private String processingResult;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String traceId;
}
