package cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("int_sync_connection")
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncConnectionDO extends TenantBaseDO {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private Long dataSourceId;
    private String name;
}

