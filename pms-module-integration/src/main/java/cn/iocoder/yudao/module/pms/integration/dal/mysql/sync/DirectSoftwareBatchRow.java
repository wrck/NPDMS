package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A historical project registration batch; technical identity belongs to INT/PLT. */
@TableName("ast_device_software_change_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class DirectSoftwareBatchRow extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private String changeVersion;
    private String changeRemark;
    private Integer latest;
}
