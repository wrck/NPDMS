package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_collection_result_consumption")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionResultConsumptionDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String platformTaskId;
    private String consumerContext;
    private String consumerObjectType;
    private String consumerObjectId;
    private Long resultVersion;
    private String consumptionResult;
    private LocalDateTime consumedAt;
    private String traceId;
}
