package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_collection_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionBatchDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String batchNo;
    private String sourceContext;
    private String sourceObjectType;
    private String sourceObjectId;
    private String idempotencyKey;
    private String status;
    private Integer taskCount;
    private Integer successCount;
    private Integer failureCount;
}
