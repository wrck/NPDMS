package cn.iocoder.yudao.module.pms.platform.dal.dataobject.command;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 平台事务Outbox事件。 */
@TableName("plt_outbox_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformOutboxEventDO extends BaseDO {

    @TableId
    private Long id;
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateKey;
    private String payload;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime nextRetryTime;
    private Integer retryCount;
    private Long tenantId;
}
