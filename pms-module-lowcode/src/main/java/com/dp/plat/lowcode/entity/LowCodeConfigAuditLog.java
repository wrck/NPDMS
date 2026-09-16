package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码配置审计日志实体（缺口2）。
 *
 * <p>记录平台配置（实体/表单/列表/标签页/关联页/微流/规则/连接器等）的写操作，
 * 含 before/after JSON 快照与变更摘要，便于事后追溯与合规审计。</p>
 *
 * <p>本表通过 AOP 切面 {@code ConfigAuditAspect} 在 8 大 ConfigService 的
 * save/update/delete 方法被调用时自动写入，记录为 best-effort，
 * 异常不抛出（不影响主业务）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_config_audit_log")
public class LowCodeConfigAuditLog extends BaseEntity {

    /** 操作人（用户名） */
    @NotBlank(message = "操作人不能为空")
    @Size(max = 64, message = "操作人长度不能超过 64 个字符")
    private String actor;

    /** 配置类型: ENTITY/FORM/LIST/MICROFLOW/RULE/CONNECTOR/TRIGGER/TAB/RELATED_PAGE */
    @NotBlank(message = "配置类型不能为空")
    @Size(max = 32, message = "配置类型长度不能超过 32 个字符")
    private String configType;

    /** 配置ID（可为 null，例如 CREATE 之前尚无 ID） */
    private Long configId;

    /** 配置编码 */
    @Size(max = 128, message = "配置编码长度不能超过 128 个字符")
    private String configCode;

    /** 动作: CREATE/UPDATE/DELETE/PUBLISH/ROLLBACK/PROMOTE */
    @NotBlank(message = "动作不能为空")
    @Size(max = 16, message = "动作长度不能超过 16 个字符")
    private String action;

    /** 操作前 JSON 快照 */
    private String beforeSnapshot;

    /** 操作后 JSON 快照 */
    private String afterSnapshot;

    /** 变更摘要 */
    @Size(max = 512, message = "变更摘要长度不能超过 512 个字符")
    private String diffSummary;

    /** 操作IP */
    @Size(max = 64, message = "IP长度不能超过 64 个字符")
    private String ip;

    /** User-Agent */
    @Size(max = 256, message = "User-Agent长度不能超过 256 个字符")
    private String userAgent;

    /** 租户ID（预留） */
    @Size(max = 64, message = "租户ID长度不能超过 64 个字符")
    private String tenantId;

    /** 操作时间（与 create_time 一致，单独冗余便于查询） */
    private LocalDateTime operateTime;
}
