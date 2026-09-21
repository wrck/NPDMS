package cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("int_device_ops_callback_receipt")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceOpsCallbackReceiptDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String callbackId;
    private String platformTaskId;
    private String externalTaskId;
    private String evidenceDigest;
    private String responseJson;
}
