package cn.iocoder.yudao.module.system.dal.dataobject.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("plt_outbox_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Integer eventVersion;
    private String payloadJson;
    private String publishStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime publishedTime;
    private String correlationId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
