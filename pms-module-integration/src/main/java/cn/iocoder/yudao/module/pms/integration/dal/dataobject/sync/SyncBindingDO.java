package cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("int_sync_binding")
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncBindingDO extends TenantBaseDO {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private Long taskId;
    private String objectKey;
    private String sourceObject;
    private String sourceKey;
    private Long targetId;
    private Boolean targetShared;
    private String fieldsJson;
    private Long lastRunId;
}
