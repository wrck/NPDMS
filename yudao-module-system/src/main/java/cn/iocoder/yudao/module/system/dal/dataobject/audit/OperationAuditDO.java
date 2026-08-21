package cn.iocoder.yudao.module.system.dal.dataobject.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("plt_operation_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationAuditDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long actorId;
    private String operationCode;
    private String resourceType;
    private String resourceId;
    private String decisionCode;
    private String detailJson;
    private String correlationId;
    private LocalDateTime operationTime;
    private LocalDateTime createTime;
}
