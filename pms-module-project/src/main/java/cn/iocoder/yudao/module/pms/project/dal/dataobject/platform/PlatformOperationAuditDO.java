package cn.iocoder.yudao.module.pms.project.dal.dataobject.platform;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 平台操作审计事实。 */
@TableName("plt_operation_audit")
@Data
public class PlatformOperationAuditDO implements Serializable {

    @TableId
    private Long id;
    private String operationCode;
    private String aggregateType;
    private String aggregateKey;
    private Long actorId;
    private String correlationId;
    private String idempotencyKeyDigest;
    private String resultCode;
    private String detailSnapshot;
    private LocalDateTime occurredAt;
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long tenantId;
}
