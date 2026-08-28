package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_collection_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private String platformTaskId;
    private String sourceContext;
    private String sourceObjectType;
    private String sourceObjectId;
    private String projectId;
    private String deviceId;
    private String deviceName;
    private String host;
    private Integer port;
    private String protocol;
    private String templateId;
    private String templateVersion;
    private String templateHash;
    private String credentialMode;
    private Long credentialId;
    private Long grantSnapshotId;
    private String idempotencyKey;
    private String completionMode;
    private String status;
    private String technicalStage;
    private String externalTaskId;
    private String externalStatus;
    private Long resultVersion;
    private Long fileVersionId;
    private String quarantineEvidenceId;
    private String failureCategory;
    private String consumerContext;
    private String consumerObjectType;
    private String consumerObjectId;
    private Long consumedResultVersion;
}
