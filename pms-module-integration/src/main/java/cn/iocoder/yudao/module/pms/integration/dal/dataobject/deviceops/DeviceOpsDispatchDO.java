package cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DAC 下发映射：platform_task_id 与 DAC collection_id 的持久化绑定，
 * 用于下发中断后的对账查询与故障恢复。
 */
@TableName("int_device_ops_dispatch")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceOpsDispatchDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String platformTaskId;
    private String idempotencyKey;
    private String namespace;
    private String collectionId;
    private String externalStatus;
    private String traceId;
}
