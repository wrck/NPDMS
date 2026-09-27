package cn.iocoder.yudao.module.pms.bindings.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 事件驱动后端消费检查点：事件身份唯一，处理完成才落检查点。 */
@TableName("pms_bind_event_checkpoint")
@Getter
@Setter
public class EventCheckpointDO extends TenantBaseDO {

    private Long id;
    private String eventId;
    private LocalDateTime processedAt;
}
