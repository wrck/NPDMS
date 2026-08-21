package cn.iocoder.yudao.module.system.dal.dataobject.idempotency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("plt_idempotency_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String scopeCode;
    private Long actorId;
    private String idempotencyKey;
    private String requestSha256;
    private String status;
    private String responseJson;
    private Long resourceId;
    private String correlationId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
