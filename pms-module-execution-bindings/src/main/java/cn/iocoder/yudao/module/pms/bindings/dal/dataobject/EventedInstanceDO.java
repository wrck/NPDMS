package cn.iocoder.yudao.module.pms.bindings.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 事件驱动后端自持实例：同一事件对同一定义只产生一条实例。 */
@TableName("pms_bind_evented_instance")
@Getter
@Setter
public class EventedInstanceDO extends TenantBaseDO {

    private Long id;
    private String definitionCode;
    private Integer definitionVersion;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String eventId;
    /** SATISFIED / UNSATISFIED / UNKNOWN */
    private String verdict;
    private String resultId;
}
