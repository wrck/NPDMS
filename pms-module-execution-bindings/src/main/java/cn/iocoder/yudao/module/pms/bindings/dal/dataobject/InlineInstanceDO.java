package cn.iocoder.yudao.module.pms.bindings.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 同步直执后端自持实例：幂等键唯一，重复执行按原实例返回。 */
@TableName("pms_bind_inline_instance")
@Getter
@Setter
public class InlineInstanceDO extends TenantBaseDO {

    private Long id;
    private String definitionCode;
    private Integer definitionVersion;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String idempotencyKey;
    /** SATISFIED / UNSATISFIED / UNKNOWN */
    private String verdict;
    private String resultId;
}
