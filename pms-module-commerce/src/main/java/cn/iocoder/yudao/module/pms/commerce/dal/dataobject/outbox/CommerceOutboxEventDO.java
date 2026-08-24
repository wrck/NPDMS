package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("com_outbox_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOutboxEventDO extends BaseDO {
    @TableId
    private Long id;
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateKey;
    private Long scopeVersion;
    private String payload;
    private String status;
    private LocalDateTime occurredAt;
    private Integer retryCount;
    private Long tenantId;
}
