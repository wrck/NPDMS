package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("plt_collection_request") @Data @EqualsAndHashCode(callSuper = true)
public class CollectionRequestDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String entry;
    private Long objectId;
    private Long actorId;
    private String requestKey;
    private String requestDigest;
    private String platformTaskId;
    @TableField(updateStrategy = FieldStrategy.NEVER) private String commandText;
    private String templateName;
    private Long templateRevisionId;
    private Long credentialVersion;
    private Long retryOfId;
    private Long consumedResultVersion;
}
